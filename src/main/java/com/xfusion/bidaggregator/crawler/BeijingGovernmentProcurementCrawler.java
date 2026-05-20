package com.xfusion.bidaggregator.crawler;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.CrawlResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.model.SourceStatus;
import com.xfusion.bidaggregator.service.InvalidPageFilter;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class BeijingGovernmentProcurementCrawler implements SourceCrawler {
    private static final String BASE = "http://www.ccgp-beijing.gov.cn";
    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-/.年]([01]?\\d)[-/.月]([0-3]?\\d)");
    private final AppProperties.SourceConfig config;
    private final int timeoutMs;
    private final int maxItems;
    private final InvalidPageFilter invalidPageFilter;

    public BeijingGovernmentProcurementCrawler(AppProperties.SourceConfig config, int timeoutMs, int maxItems,
            InvalidPageFilter invalidPageFilter) {
        this.config = config;
        this.timeoutMs = timeoutMs;
        this.maxItems = maxItems;
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
        return false;
    }

    @Override
    public CrawlResult crawl(SearchIntent intent) {
        long started = System.currentTimeMillis();
        SourceStatus status = new SourceStatus(sourceName(), sourceType(), false);
        List<BidItem> items = new ArrayList<>();
        int filtered = 0;
        try {
            List<String> listUrls = listUrls(intent);
            for (String listUrl : listUrls) {
                if (items.size() >= maxItems) {
                    break;
                }
                Document list;
                try {
                    list = connect(listUrl).get();
                } catch (Exception ignored) {
                    continue;
                }
                for (Element li : list.select("li:has(a[href])")) {
                    if (items.size() >= maxItems) {
                        break;
                    }
                    Element link = li.selectFirst("a[href]");
                    if (link == null) {
                        continue;
                    }
                    String title = clean(link.text());
                    String href = link.absUrl("href");
                    if (href.isBlank() && link.attr("href").startsWith("//")) {
                        href = "http:" + link.attr("href");
                    }
                    if (!titleMatchesIntent(title, intent) && !shouldInspectDetail(title, intent)) {
                        filtered++;
                        continue;
                    }
                    BidItem item = buildItem(intent, title, href, li.text());
                    if (item.getPublishTime() != null && !timeMatches(item.getPublishTime(), intent)) {
                        filtered++;
                        continue;
                    }
                    Detail detail = fetchDetail(href, title);
                    if (!detail.title().isBlank()) {
                        item.setTitle(detail.title());
                    }
                    item.setCoreContent(detail.summary().isBlank() ? item.getCoreContent() : detail.summary());
                    item.getAttachmentLinks().addAll(detail.attachments());
                    if (!titleMatchesIntent(item.getTitle() + " " + item.getCoreContent(), intent)) {
                        filtered++;
                        continue;
                    }
                    if (invalidPageFilter.isInvalid(item, intent)) {
                        filtered++;
                        continue;
                    }
                    items.add(item);
                }
            }
            status.setSuccess(!items.isEmpty());
            status.setWarning(items.isEmpty()
                    ? "北京市政府采购网公开招标列表已检查，未命中当前关键词。"
                    : "已从北京市政府采购网列表页和详情页抽取公告正文。");
        } catch (Exception ex) {
            status.setSuccess(false);
            status.setWarning(readableFailure(ex));
        }
        status.setFetchedCount(items.size() + filtered);
        status.setSelectedCount(items.size());
        status.setElapsedMs(System.currentTimeMillis() - started);
        return new CrawlResult(status, items);
    }

    private List<String> listUrls(SearchIntent intent) {
        int pages = maxPages(intent);
        List<String> urls = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            urls.add(BASE + "/xxgg/sjxxgg/zbgg/A002004001001index_" + page + ".htm");
            urls.add(BASE + "/xxgg/sjxxgg/zhbgg/A002004001002index_" + page + ".htm");
        }
        return urls;
    }

    private int maxPages(SearchIntent intent) {
        if (intent == null || intent.getStartTime() == null) {
            return 5;
        }
        long months = Math.max(1, java.time.temporal.ChronoUnit.MONTHS.between(
                intent.getStartTime().toLocalDate(), LocalDate.now()) + 1);
        return (int) Math.min(18, Math.max(5, months * 3));
    }

    private boolean titleMatchesIntent(String title, SearchIntent intent) {
        String merged = clean(title);
        List<String> terms = invalidPageFilter.keywordTerms(intent);
        if (terms.isEmpty()) {
            return true;
        }
        for (String term : terms) {
            if (!term.isBlank() && merged.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldInspectDetail(String title, SearchIntent intent) {
        String keyword = intent == null ? "" : clean(intent.getKeyword());
        if (!keyword.contains("芯片")) {
            return false;
        }
        String merged = clean(title);
        return merged.contains("集成电路") || merged.contains("半导体") || merged.contains("科研")
                || merged.contains("实验") || merged.contains("平台") || merged.contains("设备")
                || merged.contains("信息化") || merged.contains("智能") || merged.contains("数据");
    }

    private BidItem buildItem(SearchIntent intent, String title, String href, String listText) {
        BidItem item = new BidItem();
        item.setTitle(title);
        item.setSourceName(sourceName());
        item.setSourceType(sourceType());
        item.setSourceUrl(href);
        item.setRegion(intent == null || intent.getProvince() == null ? "北京" : intent.getProvince());
        parseDate(listText).ifPresent(item::setPublishTime);
        item.setCoreContent(title + "，来源于北京市政府采购网公告列表，请打开原文核验正文和附件。");
        item.getMergedSourceLinks().add(sourceName() + "：" + href);
        return item;
    }

    private Detail fetchDetail(String href, String fallbackTitle) {
        if (href == null || href.isBlank()) {
            return new Detail("", "", List.of());
        }
        try {
            Document doc = connect(href).get();
            String title = clean(doc.select("h1, .xl-box-t h1, title").first() == null
                    ? fallbackTitle : doc.select("h1, .xl-box-t h1, title").first().text());
            doc.select("script, style, noscript, nav, footer, header, form").remove();
            String text = clean(doc.select("#BodyLabel, #mainText, .mainTextBox, article, body").text());
            List<String> attachments = new ArrayList<>();
            for (Element a : doc.select("a[href]")) {
                String linkText = clean(a.text());
                String url = a.absUrl("href");
                String merged = (linkText + " " + url).toLowerCase();
                if (merged.matches(".*(\\.pdf|\\.doc|\\.docx|\\.xls|\\.xlsx|\\.zip|download|attach).*")
                        || linkText.contains("附件") || linkText.contains("采购需求") || linkText.contains("招标文件")) {
                    attachments.add(url.isBlank() ? linkText : url);
                }
            }
            return new Detail(title, truncate(text, 700), attachments.stream().distinct().limit(6).toList());
        } catch (Exception ignored) {
            return new Detail(fallbackTitle, "", List.of());
        }
    }

    private java.util.Optional<LocalDateTime> parseDate(String text) {
        Matcher matcher = DATE.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        return java.util.Optional.of(LocalDate.of(year, month, day).atStartOfDay());
    }

    private boolean timeMatches(LocalDateTime publishTime, SearchIntent intent) {
        if (intent == null || intent.getStartTime() == null || intent.getEndTime() == null) {
            return true;
        }
        return !publishTime.isBefore(intent.getStartTime()) && !publishTime.isAfter(intent.getEndTime());
    }

    private org.jsoup.Connection connect(String url) {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                .referrer(BASE + "/")
                .followRedirects(true)
                .timeout(Math.max(3000, Math.min(timeoutMs, 6000)));
    }

    private String clean(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text, int max) {
        String value = clean(text);
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private String readableFailure(Exception ex) {
        Throwable cause = rootCause(ex);
        if (ex instanceof HttpStatusException http) {
            return "北京市政府采购网访问受限或页面返回 " + http.getStatusCode() + "。";
        }
        if (cause instanceof SocketTimeoutException) {
            return "北京市政府采购网响应超时。";
        }
        if (cause instanceof SSLHandshakeException) {
            return "北京市政府采购网 SSL 握手失败。";
        }
        return "北京市政府采购网页面结构变化或暂不可用。";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record Detail(String title, String summary, List<String> attachments) {
    }
}
