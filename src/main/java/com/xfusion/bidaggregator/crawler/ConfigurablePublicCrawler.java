package com.xfusion.bidaggregator.crawler;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.CrawlResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.model.SourceStatus;
import com.xfusion.bidaggregator.service.InvalidPageFilter;
import com.xfusion.bidaggregator.service.LoginStateService;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ConfigurablePublicCrawler implements SourceCrawler {
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？!?]\\s*");
    private final AppProperties.SourceConfig config;
    private final int timeoutMs;
    private final int maxItems;
    private final LoginStateService loginStateService;
    private final InvalidPageFilter invalidPageFilter;

    public ConfigurablePublicCrawler(AppProperties.SourceConfig config, int timeoutMs, int maxItems,
            LoginStateService loginStateService, InvalidPageFilter invalidPageFilter) {
        this.config = config;
        this.timeoutMs = timeoutMs;
        this.maxItems = maxItems;
        this.loginStateService = loginStateService;
        this.invalidPageFilter = invalidPageFilter;
    }

    @Override
    public String sourceName() {
        return config.getName();
    }

    @Override
    public String sourceType() {
        return config.getType();
    }

    @Override
    public boolean needLogin() {
        return config.isNeedLogin();
    }

    @Override
    public CrawlResult crawl(SearchIntent intent) {
        long started = System.currentTimeMillis();
        SourceStatus status = new SourceStatus(sourceName(), sourceType(), needLogin());
        List<BidItem> output = new ArrayList<>();
        int filtered = 0;
        try {
            if (needLogin() && !loginStateService.status(config).isAvailable()) {
                status.setSuccess(false);
                status.setWarning("登录态不可用：请先完成该来源的登录态采集。");
            } else {
                Harvest harvest = needLogin() ? fetchAuthenticatedLinks(intent) : fetchPublicLinks(intent);
                output.addAll(harvest.items());
                filtered = harvest.filteredCount();
                if (output.isEmpty()) {
                    status.setSuccess(false);
                    status.setWarning(filtered > 0 ? "候选已过滤：" + filtered + " 条无效或弱相关页面。"
                            : "已处理但无命中。");
                } else {
                    status.setSuccess(true);
                    if (filtered > 0) {
                        status.setWarning("已采用 " + output.size() + " 条；另过滤无效候选 " + filtered + " 条。");
                    } else if (needLogin()) {
                        status.setWarning("已使用本地登录态 cookies 抓取；系统不保存账号密码。");
                    }
                }
            }
        } catch (Exception ex) {
            status.setSuccess(false);
            status.setWarning(readableFailure(ex));
        }
        int realCount = status.isSuccess() ? output.size() : 0;
        status.setFetchedCount(realCount + filtered);
        status.setSelectedCount(realCount);
        status.setElapsedMs(System.currentTimeMillis() - started);
        return new CrawlResult(status, output);
    }

    private Harvest fetchPublicLinks(SearchIntent intent) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Document document = baseConnection(config.getUrl()).get();
                return collectLinks(intent, document);
            } catch (Exception ex) {
                last = ex;
                if (!isRetryable(ex)) {
                    break;
                }
            }
        }
        throw last == null ? new IllegalStateException("source unavailable") : last;
    }

    private Harvest fetchAuthenticatedLinks(SearchIntent intent) throws Exception {
        Path statePath = loginStateService.statePath(config);
        if (!Files.exists(statePath)) {
            return new Harvest(List.of(), 0);
        }
        return fetchPublicLinks(intent);
    }

    private Harvest collectLinks(SearchIntent intent, Document document) {
        List<BidItem> items = new ArrayList<>();
        int filtered = 0;
        Elements links = document.select("a[href]");
        for (Element link : links) {
            if (items.size() >= maxItems) {
                break;
            }
            String text = normalize(link.text());
            String href = link.absUrl("href");
            if (href.isBlank()) {
                href = config.getUrl();
            }
            if (!looksRelevant(text, intent)) {
                continue;
            }
            BidItem item = baseItem(intent, text, href);
            DetailSnapshot detail = fetchDetail(href, text);
            item.setCoreContent(detail.summary());
            item.getAttachmentLinks().addAll(detail.attachments());
            if (invalidPageFilter.isInvalid(item, intent)) {
                filtered++;
                continue;
            }
            items.add(item);
        }
        return new Harvest(items, filtered);
    }

    private DetailSnapshot fetchDetail(String url, String fallbackTitle) {
        try {
            Document detail = baseConnection(url).timeout(Math.min(timeoutMs, 5000)).get();
            String text = extractReadableText(detail);
            List<String> attachments = extractAttachmentLinks(detail);
            if (text.isBlank()) {
                return new DetailSnapshot(fallbackTitle, attachments);
            }
            return new DetailSnapshot(truncateAtSentence(text, 360), attachments);
        } catch (Exception ignored) {
            return new DetailSnapshot(fallbackTitle + "。详情页正文暂未稳定抽取，请以来源链接原文为准。", List.of());
        }
    }

    private List<String> extractAttachmentLinks(Document detail) {
        List<String> attachments = new ArrayList<>();
        for (Element a : detail.select("a[href]")) {
            String text = normalize(a.text());
            String href = a.absUrl("href");
            String lower = href.toLowerCase();
            if (isAttachmentHref(lower) || isAttachmentText(text)) {
                attachments.add(href.isBlank() ? text : href);
            }
        }
        return attachments.stream().distinct().limit(8).toList();
    }

    private boolean isAttachmentHref(String lower) {
        return lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".zip")
                || lower.endsWith(".rar") || lower.contains("download") || lower.contains("attach");
    }

    private boolean isAttachmentText(String text) {
        return text.contains("附件") || text.contains("下载") || text.contains("采购文件")
                || text.contains("招标文件") || text.contains("响应文件") || text.contains("报名表");
    }

    private boolean looksRelevant(String text, SearchIntent intent) {
        if (text.length() < 6) {
            return false;
        }
        String merged = text;
        boolean bidLike = invalidPageFilter.bidTerms().stream().anyMatch(merged::contains)
                || invalidPageFilter.realBidTerms().stream().anyMatch(merged::contains);
        boolean keywordLike = invalidPageFilter.keywordTerms(intent).isEmpty()
                || invalidPageFilter.keywordTerms(intent).stream().anyMatch(merged::contains);
        return bidLike && keywordLike;
    }

    private BidItem baseItem(SearchIntent intent, String title, String url) {
        BidItem item = new BidItem();
        item.setTitle(title);
        item.setSourceName(sourceName());
        item.setSourceType(sourceType());
        item.setSourceUrl(url);
        item.setRegion(intent.getProvince() == null || intent.getProvince().isBlank() ? "全国" : intent.getProvince());
        item.getMergedSourceLinks().add(sourceName() + "：" + url);
        return item;
    }

    private Connection baseConnection(String url) {
        Connection connection = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                .header("Cache-Control", "no-cache")
                .referrer(config.getUrl())
                .followRedirects(true)
                .timeout(timeoutMs);
        if (needLogin()) {
            Map<String, String> cookies = loginStateService.cookiesFor(config);
            if (!cookies.isEmpty()) {
                connection.cookies(cookies);
            }
        }
        return connection;
    }

    private boolean isRetryable(Exception ex) {
        Throwable cause = rootCause(ex);
        if (cause instanceof SocketTimeoutException || cause instanceof SSLHandshakeException) {
            return true;
        }
        if (ex instanceof HttpStatusException http) {
            return http.getStatusCode() == 502 || http.getStatusCode() == 503 || http.getStatusCode() == 504;
        }
        return true;
    }

    private String readableFailure(Exception ex) {
        Throwable cause = rootCause(ex);
        if (ex instanceof HttpStatusException http) {
            if (http.getStatusCode() == 403) {
                return needLogin() ? "登录态失效，需要重新登录。" : "来源访问受限，已跳过。";
            }
            if (http.getStatusCode() == 502 || http.getStatusCode() == 503 || http.getStatusCode() == 504) {
                return "网络波动已跳过：来源暂时返回 " + http.getStatusCode() + "。";
            }
        }
        if (cause instanceof SocketTimeoutException) {
            return "网络波动已跳过：来源响应超时。";
        }
        if (cause instanceof SSLHandshakeException) {
            return "网络波动已跳过：来源 SSL 握手失败。";
        }
        return "自动跳过：来源暂时不可用或页面结构变化。";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String extractReadableText(Document document) {
        document.select("script, style, noscript, nav, footer, header, form").remove();
        String text = normalize(document.select("article, main, .content, .detail, .article, .notice, body").text());
        if (text.length() > 120) {
            return text;
        }
        return normalize(document.body() == null ? "" : document.body().text());
    }

    private String truncateAtSentence(String text, int maxLength) {
        String cleaned = normalize(text);
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        String clipped = cleaned.substring(0, maxLength);
        int lastEnd = -1;
        var matcher = SENTENCE_END.matcher(clipped);
        while (matcher.find()) {
            lastEnd = matcher.end();
        }
        if (lastEnd >= 120) {
            return clipped.substring(0, lastEnd).trim();
        }
        return clipped.trim() + "...";
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private record DetailSnapshot(String summary, List<String> attachments) {
    }

    private record Harvest(List<BidItem> items, int filteredCount) {
    }
}
