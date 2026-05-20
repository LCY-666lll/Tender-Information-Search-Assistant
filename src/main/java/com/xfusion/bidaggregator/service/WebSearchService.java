package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.CrawlResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.model.SourceStatus;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
public class WebSearchService {
    private static final Pattern BING_BLOCK = Pattern.compile("(?is)<li[^>]+class=\"[^\"]*b_algo[^\"]*\"[^>]*>(.*?)</li>");
    private static final Pattern LINK = Pattern.compile("(?is)<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern CHINESE_DATE = Pattern.compile("(20\\d{2})年\\s*([01]?\\d)月\\s*([0-3]?\\d)日");
    private static final Pattern DASH_DATE = Pattern.compile("(20\\d{2})[-/.]([01]?\\d)[-/.]([0-3]?\\d)");
    private static final Pattern ENGLISH_DATE = Pattern.compile("(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+([0-3]?\\d),\\s*(20\\d{2})");
    private final AppProperties properties;
    private final InvalidPageFilter invalidPageFilter;
    private final ValidAnnouncementScorer scorer;

    public WebSearchService(AppProperties properties, InvalidPageFilter invalidPageFilter,
            ValidAnnouncementScorer scorer) {
        this.properties = properties;
        this.invalidPageFilter = invalidPageFilter;
        this.scorer = scorer;
    }

    public CrawlResult discover(SearchIntent intent, List<String> queries) {
        long started = System.currentTimeMillis();
        SourceStatus status = new SourceStatus("Agent 全网搜索发现", "搜索候选来源", false);
        List<BidItem> items = new ArrayList<>();
        List<String> engines = new ArrayList<>();
        int filtered = 0;
        int portalCount = 0;
        try {
            List<String> effectiveQueries = effectiveQueries(intent, queries);
            int targetCount = Math.max(8, properties.getMaxItemsPerSource());
            for (String query : effectiveQueries) {
                if (realItemCount(items) >= properties.getMaxItemsPerSource()
                        || (realItemCount(items) == 0 && portalCount >= properties.getMaxItemsPerSource())) {
                    break;
                }
                try {
                    SearchBatch batch = searchOneQueryWithRetry(intent, query,
                            Math.max(1, properties.getMaxItemsPerSource() - realItemCount(items)));
                    items.addAll(batch.items());
                    filtered += batch.filteredCount();
                    engines.addAll(batch.engines());
                    portalCount += batch.portalCount();
                } catch (Exception ignored) {
                    continue;
                }
                if (realItemCount(items) >= targetCount
                        || (realItemCount(items) == 0 && portalCount >= targetCount)) {
                    break;
                }
            }
            if (items.isEmpty()) {
                for (String query : googleFallbackQueries(intent)) {
                    SearchBatch batch = searchGoogleOneQuery(intent, query, properties.getMaxItemsPerSource());
                    items.addAll(batch.items());
                    filtered += batch.filteredCount();
                    engines.addAll(batch.engines());
                    portalCount += batch.portalCount();
                    if (realItemCount(items) >= targetCount
                            || (realItemCount(items) == 0 && portalCount >= targetCount)) {
                        break;
                    }
                }
            }
            if (realItemCount(items) < targetCount) {
                for (String query : sogouFallbackQueries(intent)) {
                    SearchBatch batch = searchSogouOneQuery(intent, query, properties.getMaxItemsPerSource());
                    items.addAll(batch.items());
                    filtered += batch.filteredCount();
                    engines.addAll(batch.engines());
                    portalCount += batch.portalCount();
                    if (realItemCount(items) >= targetCount
                            || (realItemCount(items) == 0 && portalCount >= targetCount)) {
                        break;
                    }
                }
            }
            items = items.stream()
                    .filter(item -> !isSearchEngineUrl(item.getSourceUrl()))
                    .sorted(scorer.comparator())
                    .limit(properties.getMaxItemsPerSource())
                    .toList();
            if (items.isEmpty()) {
                items = tenderPortalFallbackCandidates(intent).stream()
                        .limit(properties.getMaxItemsPerSource())
                        .toList();
            }
            status.setSuccess(!items.isEmpty());
            if (items.isEmpty()) {
                status.setWarning(filtered > 0
                        ? "候选已过滤：" + filtered + " 条全网搜索结果为跳转页、登录页或弱相关页面。"
                        : "已处理但无命中：全网搜索暂未返回有效公告候选。");
            } else if (portalCount > 0) {
                status.setWarning("已尝试 " + compactEngines(engines) + "，保留 " + portalCount
                        + " 个全网检索入口供原文核验。");
            } else if (filtered > 0) {
                status.setWarning("已采用 " + items.size() + " 条全网候选；过滤无效候选 " + filtered + " 条。");
            }
        } catch (Exception ex) {
            status.setSuccess(false);
            status.setWarning(readableFailure(ex));
        }
        status.setFetchedCount(items.size() + filtered);
        status.setSelectedCount(items.size());
        status.setElapsedMs(System.currentTimeMillis() - started);
        return new CrawlResult(status, items);
    }

    private SearchBatch searchOneQueryWithRetry(SearchIntent intent, String query, int limit) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return searchOneQuery(intent, query, limit);
            } catch (Exception ex) {
                last = ex;
                if (!isRetryable(ex)) {
                    break;
                }
            }
        }
        throw last == null ? new IllegalStateException("search unavailable") : last;
    }

    private SearchBatch searchOneQuery(SearchIntent intent, String query, int limit) throws Exception {
        List<BidItem> items = new ArrayList<>();
        int filtered = 0;
        items.addAll(searchBingRss(intent, query, limit));
        if (!items.isEmpty()) {
            return new SearchBatch(items, filtered);
        }

        String url = bingSearchUrl(query);
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                .referrer("http://www.bing.com/")
                .timeout(searchTimeoutMs())
                .get();
        Elements results = doc.select("li.b_algo");
        if (results.isEmpty()) {
            results = doc.select("#b_results li, li");
        }
        for (Element result : results) {
            if (items.size() >= limit) {
                break;
            }
            Element link = result.selectFirst("h2 a[href]");
            if (link == null) {
                link = result.selectFirst("a[href]");
            }
            if (link == null) {
                continue;
            }
            String title = normalize(link.text());
            if (title.length() < 6) {
                continue;
            }
            String href = resolveSearchUrl(link.absUrl("href"));
            String searchSnippet = normalize(result.text());
            if (!looksBidRelated(title, searchSnippet, intent)) {
                continue;
            }
            DetailSnapshot detail = fetchDetail(href, searchSnippet);
            String summary = detail.summary();
            if (!looksBidRelated(title, summary, intent)) {
                summary = searchSnippet;
            }
            String bestTitle = displayTitle(bestTitle(title, detail.title()), summary, href, intent);
            if (looksLikeIndexCandidate(title, href, summary)
                    || isUnsupportedCandidateDomain(href)
                    || !matchesRequestedRegion(bestTitle + " " + summary + " " + href, intent)) {
                filtered++;
                continue;
            }
            BidItem item = new BidItem();
            item.setTitle(bestTitle);
            item.setSourceName(sourceNameFromUrl(href));
            item.setSourceType("Agent 搜索发现");
            item.setSourceUrl(href);
            item.setRegion(intent.getProvince());
            extractPublishTime(detail.publishText() + " " + summary).ifPresent(item::setPublishTime);
            item.setCoreContent(summary);
            item.getAttachmentLinks().addAll(detail.attachments());
            item.getMergedSourceLinks().add("搜索词：" + query + "；候选链接：" + href);
            if (!acceptSearchCandidate(item, intent)) {
                filtered++;
                continue;
            }
            items.add(item);
        }
        if (items.isEmpty()) {
            items.addAll(parseBingBlocks(doc.outerHtml(), intent, query, limit));
        }
        if (items.isEmpty()) {
            items.addAll(parseSearchLinks(doc.outerHtml(), intent, query, limit));
        }
        if (items.isEmpty()) {
            String fallbackHtml = fetchWithPowerShell(query);
            items.addAll(parseBingBlocks(fallbackHtml, intent, query, limit));
            if (items.isEmpty()) {
                items.addAll(parseSearchLinks(fallbackHtml, intent, query, limit));
            }
        }
        return new SearchBatch(items, filtered);
    }

    private List<BidItem> searchBingRss(SearchIntent intent, String query, int limit) {
        List<BidItem> items = new ArrayList<>();
        try {
            String url = "http://cn.bing.com/search?format=rss&mkt=zh-CN&setlang=zh-CN&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document rss = Jsoup.connect(url)
                    .parser(org.jsoup.parser.Parser.xmlParser())
                    .userAgent("Mozilla/5.0")
                    .timeout(searchTimeoutMs())
                    .get();
            for (Element entry : rss.select("item")) {
                if (items.size() >= limit) {
                    break;
                }
                String title = normalize(entry.selectFirst("title") == null ? "" : entry.selectFirst("title").text());
                String href = normalize(entry.selectFirst("link") == null ? "" : entry.selectFirst("link").text());
                String summary = normalize(entry.selectFirst("description") == null ? "" : entry.selectFirst("description").text());
                if (title.length() < 6 || href.isBlank() || isUnsupportedCandidateDomain(href)) {
                    continue;
                }
                if (!looksBidRelated(title, summary, intent)
                        || !matchesRequestedRegion(title + " " + summary + " " + href, intent)) {
                    continue;
                }
                BidItem item = new BidItem();
                item.setTitle(displayTitle(title, summary, href, intent));
                item.setSourceName(sourceNameFromUrl(href));
                item.setSourceType("Agent 搜索发现");
                item.setSourceUrl(href);
                item.setRegion(intent.getProvince());
                extractPublishTime((entry.selectFirst("pubDate") == null ? "" : entry.selectFirst("pubDate").text())
                        + " " + summary).ifPresent(item::setPublishTime);
                item.setCoreContent(summary.isBlank() ? title : summary);
                item.getMergedSourceLinks().add("搜索词：" + query + "；RSS候选链接：" + href);
                if (acceptSearchCandidate(item, intent)) {
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
            return items;
        }
        return items;
    }

    private SearchBatch searchGoogleOneQuery(SearchIntent intent, String query, int limit) {
        List<BidItem> items = new ArrayList<>();
        int filtered = 0;
        try {
            String url = "https://www.google.com/search?hl=zh-CN&num=10&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                    .referrer("https://www.google.com/")
                    .timeout(searchTimeoutMs())
                    .get();
            for (Element link : doc.select("a[href]")) {
                if (items.size() >= limit) {
                    break;
                }
                String href = resolveGoogleUrl(link.absUrl("href"));
                String title = normalize(link.text());
                if (title.length() < 6 || href.isBlank() || !href.startsWith("http")
                        || href.contains("google.com/")) {
                    continue;
                }
                Element parent = link.parent();
                String summary = normalize((parent == null ? "" : parent.parent() == null
                        ? parent.text() : parent.parent().text()));
                if (summary.length() < title.length()) {
                    summary = title;
                }
                if (!looksBidRelated(title, summary, intent)) {
                    continue;
                }
                if (looksLikeIndexCandidate(title, href, summary)
                        || isUnsupportedCandidateDomain(href)
                        || !matchesRequestedRegion(title + " " + summary + " " + href, intent)) {
                    filtered++;
                    continue;
                }
                BidItem item = new BidItem();
                item.setTitle(displayTitle(title, summary, href, intent));
                item.setSourceName(sourceNameFromUrl(href));
                item.setSourceType("Agent 搜索发现");
                item.setSourceUrl(href);
                item.setRegion(intent.getProvince());
                extractPublishTime(summary).ifPresent(item::setPublishTime);
                item.setCoreContent(summary.length() > 600 ? summary.substring(0, 600) + "..." : summary);
                item.getMergedSourceLinks().add("搜索词：" + query + "；候选链接：" + href);
                if (!acceptSearchCandidate(item, intent)) {
                    filtered++;
                    continue;
                }
                items.add(item);
            }
        } catch (Exception ignored) {
            return new SearchBatch(items, filtered);
        }
        return new SearchBatch(items, filtered);
    }

    private SearchBatch searchSogouOneQuery(SearchIntent intent, String query, int limit) {
        List<BidItem> items = new ArrayList<>();
        int filtered = 0;
        try {
            String url = "http://www.sogou.com/web?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                    .referrer("http://www.sogou.com/")
                    .timeout(searchTimeoutMs())
                    .get();
            Elements results = doc.select("div.vrwrap, .results .vrwrap, .rb");
            if (results.isEmpty()) {
                results = doc.select("body");
            }
            for (Element result : results) {
                if (items.size() >= limit) {
                    break;
                }
                Element link = result.selectFirst("h3.vr-title a[href], h3 a[href], a[href]");
                if (link == null) {
                    continue;
                }
                String href = normalize(link.absUrl("href"));
                String title = normalize(link.text());
                String summary = normalize(result.select(".fz-mid, [id*=summary], .str_info_div, .ft").text());
                if (summary.isBlank()) {
                    summary = normalize(result.text());
                }
                if (title.length() < 6 || href.isBlank() || isUnsupportedCandidateDomain(href)
                        || !looksBidRelated(title, summary, intent)
                        || !matchesRequestedRegion(title + " " + summary + " " + href, intent)) {
                    filtered++;
                    continue;
                }
                if (looksLikeIndexCandidate(title, href, summary)
                        && !isConcreteAggregatorResult(title + " " + summary, href, intent)) {
                    filtered++;
                    continue;
                }
                DetailSnapshot detail = fetchDetail(href, summary);
                String bestSummary = evidenceSummary(summary, detail, intent);
                BidItem item = new BidItem();
                item.setTitle(displayTitle(bestTitle(title, detail.title()), bestSummary, href, intent));
                item.setSourceName(sourceNameFromUrl(href));
                item.setSourceType("Agent 鎼滅储鍙戠幇");
                item.setSourceUrl(href);
                item.setRegion(intent.getProvince());
                extractPublishTime(detail.publishText() + " " + bestSummary + " " + summary).ifPresent(item::setPublishTime);
                item.setCoreContent(bestSummary.length() > 600 ? bestSummary.substring(0, 600) + "..." : bestSummary);
                item.getAttachmentLinks().addAll(detail.attachments());
                item.getMergedSourceLinks().add("鎼滅储璇嶏細" + query + "锛涙悳鐙楀€欓€夐摼鎺ワ細" + href);
                if (!acceptSearchCandidate(item, intent)) {
                    filtered++;
                    continue;
                }
                items.add(item);
            }
            if (items.isEmpty()) {
                items.addAll(parseSogouBlocks(fetchSogouWithPowerShell(query), intent, query, limit));
            }
        } catch (Exception ignored) {
            items.addAll(parseSogouBlocks(fetchSogouWithPowerShell(query), intent, query, limit));
            return new SearchBatch(items, filtered, List.of("Sogou fallback"), countPortalItems(items));
        }
        return new SearchBatch(items, filtered, List.of("Sogou fallback"), countPortalItems(items));
    }

    List<BidItem> parseSogouBlocks(String html, SearchIntent intent, String query, int limit) {
        List<BidItem> items = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?is)<div class=\"vrwrap\".*?(?=<!--STATUS VR OK-->|<div class=\"vrwrap\"|$)")
                .matcher(html == null ? "" : html);
        while (matcher.find() && items.size() < limit) {
            String block = matcher.group();
            Matcher linkMatcher = Pattern.compile("(?is)<h3[^>]*class=\"vr-title\".*?<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>")
                    .matcher(block);
            if (!linkMatcher.find()) {
                continue;
            }
            String href = normalize(Jsoup.parse(linkMatcher.group(1)).text());
            String title = normalize(Jsoup.parse(linkMatcher.group(2)).text());
            String summary = normalize(Jsoup.parse(block).text());
            if (title.length() < 6 || href.isBlank() || isUnsupportedCandidateDomain(href)
                    || !looksBidRelated(title, summary, intent)
                    || !matchesRequestedRegion(title + " " + summary + " " + href, intent)) {
                continue;
            }
            if (looksLikeIndexCandidate(title, href, summary)
                    && !isConcreteAggregatorResult(title + " " + summary, href, intent)) {
                continue;
            }
            BidItem item = new BidItem();
            item.setTitle(displayTitle(title, summary, href, intent));
            item.setSourceName(sourceNameFromUrl(href));
            item.setSourceType("Agent 鎼滅储鍙戠幇");
            item.setSourceUrl(href);
            item.setRegion(intent.getProvince());
            extractPublishTime(summary).ifPresent(item::setPublishTime);
            item.setCoreContent(summary.length() > 600 ? summary.substring(0, 600) + "..." : summary);
            item.getMergedSourceLinks().add("鎼滅储璇嶏細" + query + "锛涙悳鐙楀€欓€夐摼鎺ワ細" + href);
            if (acceptSearchCandidate(item, intent)) {
                items.add(item);
            }
        }
        return items;
    }

    private String fetchWithPowerShell(String query) {
        try {
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                    "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                            + "$q=$env:BIDRADAR_QUERY;"
                            + "$u='http://cn.bing.com/search?mkt=zh-CN&setlang=zh-CN&q='+[uri]::EscapeDataString($q);"
                            + "(Invoke-WebRequest -UseBasicParsing -Uri $u -TimeoutSec 6 "
                            + "-Headers @{'User-Agent'='Mozilla/5.0'}).Content");
            builder.environment().put("BIDRADAR_QUERY", query == null ? "" : query);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return output.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String fetchSogouWithPowerShell(String query) {
        try {
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                    "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                            + "$q=$env:BIDRADAR_QUERY;"
                            + "$u='http://www.sogou.com/web?query='+[uri]::EscapeDataString($q);"
                            + "(Invoke-WebRequest -UseBasicParsing -Uri $u -TimeoutSec 8 "
                            + "-Headers @{'User-Agent'='Mozilla/5.0'}).Content");
            builder.environment().put("BIDRADAR_QUERY", query == null ? "" : query);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return output.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private BidItem searchPortalCandidate(SearchIntent intent, String query) {
        String url = bingSearchUrl(query);
        BidItem item = new BidItem();
        item.setTitle("打开全网检索：" + query);
        item.setSourceName("Agent 全网检索入口");
        item.setSourceType("检索入口");
        item.setSourceUrl(url);
        item.setRegion(searchRegion(intent));
        item.setCoreContent("搜索引擎没有返回可直接结构化的公告卡片，已保留本次 Agent 检索入口。点击原文可查看该城市、物品和时间条件下的全网结果。");
        item.getMergedSourceLinks().add("Agent 检索词：" + query);
        return item;
    }

    private String bingSearchUrl(String query) {
        return "http://cn.bing.com/search?mkt=zh-CN&setlang=zh-CN&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    private List<BidItem> parseBingBlocks(String html, SearchIntent intent, String query, int limit) {
        List<BidItem> items = new ArrayList<>();
        Matcher blockMatcher = BING_BLOCK.matcher(html == null ? "" : html);
        while (blockMatcher.find() && items.size() < limit) {
            String block = blockMatcher.group(1);
            Matcher linkMatcher = LINK.matcher(block);
            if (!linkMatcher.find()) {
                continue;
            }
            String href = resolveSearchUrl(normalize(linkMatcher.group(1)));
            String title = normalize(Jsoup.parse(linkMatcher.group(2)).text());
            String summary = normalize(Jsoup.parse(block).text());
            if (title.length() < 6 || !looksBidRelated(title, summary, intent)) {
                continue;
            }
            if (looksLikeIndexCandidate(title, href, summary)
                    || isUnsupportedCandidateDomain(href)
                    || !matchesRequestedRegion(title + " " + summary + " " + href, intent)) {
                continue;
            }
            BidItem item = new BidItem();
            item.setTitle(displayTitle(title, summary, href, intent));
            item.setSourceName(sourceNameFromUrl(href));
            item.setSourceType("Agent 搜索发现");
            item.setSourceUrl(href.startsWith("http") ? href : "http://www.bing.com" + href);
            item.setRegion(intent.getProvince());
            extractPublishTime(summary).ifPresent(item::setPublishTime);
            item.setCoreContent(summary);
            item.getMergedSourceLinks().add("搜索词：" + query + "；候选链接：" + item.getSourceUrl());
            if (acceptSearchCandidate(item, intent)) {
                items.add(item);
            }
        }
        return items;
    }

    private List<BidItem> parseSearchLinks(String html, SearchIntent intent, String query, int limit) {
        List<BidItem> items = new ArrayList<>();
        Matcher linkMatcher = LINK.matcher(html == null ? "" : html);
        while (linkMatcher.find() && items.size() < limit) {
            String href = resolveSearchUrl(normalize(linkMatcher.group(1)));
            String title = normalize(Jsoup.parse(linkMatcher.group(2)).text());
            if (title.length() < 8 || href.contains("javascript:") || href.contains("#")) {
                continue;
            }
            String summary = title + "。该候选来自搜索结果标题，系统仅作为线索收集，最终事实请点击原文链接核对。";
            if (!looksBidRelated(title, summary, intent)) {
                continue;
            }
            if (looksLikeIndexCandidate(title, href, summary)
                    || isUnsupportedCandidateDomain(href)
                    || !matchesRequestedRegion(title + " " + summary + " " + href, intent)) {
                continue;
            }
            BidItem item = new BidItem();
            item.setTitle(displayTitle(title, summary, href, intent));
            item.setSourceName(sourceNameFromUrl(href));
            item.setSourceType("Agent 搜索发现");
            item.setSourceUrl(href.startsWith("http") ? href : "http://www.bing.com" + href);
            item.setRegion(intent.getProvince());
            extractPublishTime(summary).ifPresent(item::setPublishTime);
            item.setCoreContent(summary);
            item.getMergedSourceLinks().add("搜索词：" + query + "；候选链接：" + item.getSourceUrl());
            if (acceptSearchCandidate(item, intent)) {
                items.add(item);
            }
        }
        return items;
    }

    private DetailSnapshot fetchDetail(String url, String fallback) {
        try {
            Document detail = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                    .referrer("https://www.bing.com/")
                    .timeout(Math.min(searchTimeoutMs(), 3500))
                    .get();
            String title = normalize(selectTitle(detail));
            String publishText = normalize(detail.select(".time, .date, .publish, .pubDate, .article-time, "
                    + "[class*=time], [class*=date], [class*=publish]").text());
            List<String> attachments = extractAttachmentLinks(detail);
            detail.select("script, style, noscript, nav, footer, header, form").remove();
            String text = normalize(detail.select("article, main, .content, .detail, .article, body").text());
            if (text.isBlank()) {
                return new DetailSnapshot(title, fallback, publishText, attachments);
            }
            return new DetailSnapshot(title, text.length() > 600 ? text.substring(0, 600) + "..." : text,
                    publishText, attachments);
        } catch (Exception ignored) {
            return new DetailSnapshot("", fallback, "", List.of());
        }
    }

    private String evidenceSummary(String searchSnippet, DetailSnapshot detail, SearchIntent intent) {
        String snippet = normalize(searchSnippet);
        String detailText = detail == null ? "" : normalize(detail.summary());
        if (detailText.isBlank()) {
            return snippet;
        }
        boolean detailHasEvidence = looksBidRelated("", detailText, intent)
                && matchesRequestedRegion(detailText, intent);
        if (!detailHasEvidence) {
            return snippet.isBlank() ? detailText : snippet;
        }
        if (snippet.isBlank() || detailText.contains(snippet)) {
            return truncate(detailText, 900);
        }
        return truncate(snippet + " " + detailText, 900);
    }

    private String truncate(String text, int maxLength) {
        String value = normalize(text);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String selectTitle(Document detail) {
        Element h1 = detail.selectFirst("h1, .title, .article-title, [class*=title]");
        if (h1 != null && normalize(h1.text()).length() >= 8) {
            return h1.text();
        }
        return detail.title();
    }

    private List<String> extractAttachmentLinks(Document detail) {
        List<String> attachments = new ArrayList<>();
        for (Element link : detail.select("a[href]")) {
            String text = normalize(link.text());
            String href = link.absUrl("href");
            String merged = text + " " + href;
            if (merged.contains("附件") || merged.contains("招标文件") || merged.contains("采购文件")
                    || merged.matches("(?i).*(\\.docx?|\\.xlsx?|\\.pdf|download|attach).*")) {
                attachments.add(href.isBlank() ? text : href);
            }
            if (attachments.size() >= 5) {
                break;
            }
        }
        return attachments.stream().distinct().toList();
    }

    String resolveSearchUrl(String href) {
        String decoded = normalize(Jsoup.parse(href == null ? "" : href).text());
        if (decoded.isBlank()) {
            decoded = href == null ? "" : href.trim();
        }
        if (!decoded.contains("bing.com/ck/a")) {
            return decoded;
        }
        try {
            URI uri = URI.create(decoded.replace("&amp;", "&"));
            String query = uri.getRawQuery();
            if (query == null) {
                return decoded;
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                if ("u".equals(key)) {
                    return decodeBingTarget(value);
                }
            }
        } catch (Exception ignored) {
            return decoded;
        }
        return decoded;
    }

    private String resolveGoogleUrl(String href) {
        String decoded = normalize(Jsoup.parse(href == null ? "" : href).text());
        if (decoded.isBlank()) {
            decoded = href == null ? "" : href.trim();
        }
        try {
            URI uri = URI.create(decoded.replace("&amp;", "&"));
            if ((uri.getHost() == null || !uri.getHost().contains("google."))
                    && decoded.startsWith("http")) {
                return decoded;
            }
            String query = uri.getRawQuery();
            if (query == null) {
                return decoded;
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                if ("q".equals(key) || "url".equals(key)) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            return decoded;
        }
        return decoded;
    }

    private String decodeBingTarget(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        String payload = value.startsWith("a1") ? value.substring(2) : value;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            String url = new String(decoded, StandardCharsets.UTF_8);
            return url.startsWith("http") ? url : value;
        } catch (Exception ignored) {
            return value;
        }
    }

    private List<String> effectiveQueries(SearchIntent intent, List<String> queries) {
        LinkedHashSet<String> effective = new LinkedHashSet<>();
        LinkedHashSet<String> modelQueries = new LinkedHashSet<>();
        if (queries != null) {
            queries.stream().filter(q -> q != null && !q.isBlank()).limit(10).forEach(modelQueries::add);
        }
        String region = searchRegion(intent);
        String keyword = intent.getKeyword() == null ? "" : intent.getKeyword();
        for (String term : expandedSearchTerms(keyword)) {
            effective.add((region + " " + term + " 招标公告").trim());
            effective.add((region + " " + term + " 采购公告").trim());
            effective.add((region + " " + term + " 中标公告").trim());
            effective.add((region + " " + term + " 竞争性磋商").trim());
            effective.add((region + " " + term + " 招标公告").trim());
            effective.add((region + " " + term + " 采购").trim());
            effective.add((region + " " + term + " 中标公告").trim());
            effective.add((region + " " + term + " 竞争性磋商").trim());
            effective.add("site:ccgp.gov.cn " + region + " " + term);
            effective.add("site:ggzy.gov.cn " + region + " " + term);
            effective.add("site:cebpubservice.com " + region + " " + term);
            for (String domain : regionalOfficialDomains(intent)) {
                effective.add("site:" + domain + " " + region + " " + term + " 招标 采购 公告");
                effective.add("site:" + domain + " " + region + " " + term + " 中标 成交 公告");
                effective.add("site:" + domain + " " + region + " " + term + " 招标 采购 公告");
                effective.add("site:" + domain + " " + region + " " + term + " 中标 成交 公告");
            }
        }
        for (String suffix : List.of("招标", "采购", "公告", "中标", "竞争性磋商", "公开招标")) {
            effective.add((region + " " + keyword + " " + suffix).trim());
        }
        effective.addAll(modelQueries);
        return effective.stream().filter(q -> !q.isBlank()).limit(36).toList();
    }

    private List<String> regionalOfficialDomains(SearchIntent intent) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        domains.add("ccgp.gov.cn/cggg");
        domains.add("ggzy.gov.cn");
        domains.add("bulletin.cebpubservice.com");
        String region = searchRegion(intent);
        String province = intent == null || intent.getProvince() == null ? "" : intent.getProvince();
        String merged = region + " " + province;
        if (merged.contains("北京") || merged.contains("鍖椾含")) {
            domains.add("ccgp-beijing.gov.cn");
            domains.add("ggzyfw.beijing.gov.cn");
        }
        if (merged.contains("杭州") || merged.contains("浙江")) {
            domains.add("zfcg.czt.zj.gov.cn");
            domains.add("zcygov.cn");
            domains.add("hzctc.hangzhou.gov.cn");
            domains.add("ggzy.hangzhou.gov.cn");
        }
        if (merged.contains("南京") || merged.contains("江苏")) {
            domains.add("ccgp-jiangsu.gov.cn");
            domains.add("jszfcg.jsczt.cn");
            domains.add("njggzy.nanjing.gov.cn");
            domains.add("njgc.jfh.com");
        }
        if (merged.contains("安徽") || merged.contains("合肥")) {
            domains.add("ggzy.hefei.gov.cn");
            domains.add("ahggzyjt.com");
        }
        if (merged.contains("广东") || merged.contains("广州") || merged.contains("深圳")) {
            domains.add("gdgpo.czt.gd.gov.cn");
            domains.add("ggzy.gz.gov.cn");
            domains.add("szggzy.com");
        }
        return domains.stream().distinct().limit(10).toList();
    }

    private boolean acceptSearchCandidate(BidItem item, SearchIntent intent) {
        return !invalidPageFilter.isInvalid(item, intent)
                || invalidPageFilter.isDisplayableCandidate(item, intent);
    }

    private int searchTimeoutMs() {
        int configured = properties.getRequestTimeoutMs() <= 0 ? 6000 : properties.getRequestTimeoutMs();
        return Math.max(3000, Math.min(configured, 6000));
    }

    private List<String> googleFallbackQueries(SearchIntent intent) {
        String region = searchRegion(intent);
        String keyword = intent.getKeyword() == null ? "" : intent.getKeyword();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add((region + " " + keyword + " 招标公告").trim());
        queries.add((region + " " + keyword + " 采购公告").trim());
        for (String term : expandedSearchTerms(keyword)) {
            queries.add((region + " " + term + " 招标 采购 公告").trim());
        }
        return queries.stream().filter(q -> !q.isBlank()).limit(2).toList();
    }

    private List<String> sogouFallbackQueries(SearchIntent intent) {
        String region = searchRegion(intent);
        String keyword = intent.getKeyword() == null ? "" : intent.getKeyword();
        String year = String.valueOf(LocalDate.now().getYear());
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        for (String term : expandedSearchTerms(keyword)) {
            queries.add((year + " " + region + " " + term + " 招标公告").trim());
            queries.add((year + " " + region + " " + term + " 采购公告").trim());
            queries.add((year + " " + region + " " + term + " 中标公告").trim());
            queries.add((region + " " + term + " 招标公告").trim());
            queries.add((region + " " + term + " 采购公告").trim());
        }
        queries.add((year + " " + region + " " + keyword + " 招标 采购 公告").trim());
        queries.add((region + " " + keyword + " 招标 采购 公告").trim());
        return queries.stream().filter(q -> !q.isBlank()).limit(8).toList();
    }

    private List<BidItem> tenderPortalFallbackCandidates(SearchIntent intent) {
        String region = searchRegion(intent);
        String keyword = intent == null || intent.getKeyword() == null ? "" : intent.getKeyword();
        if (region.isBlank() || keyword.isBlank()) {
            return List.of();
        }
        String start = intent.getStartTime() == null ? "" : intent.getStartTime().toLocalDate().toString();
        String end = intent.getEndTime() == null ? "" : intent.getEndTime().toLocalDate().toString();
        String timeText = start.isBlank() || end.isBlank() ? "用户指定时间范围" : start + " 至 " + end;
        String query = (region + " " + keyword + " 招标公告 采购公告 中标公告").trim();
        List<PortalLink> portals = List.of(
                new PortalLink("中国政府采购网",
                        "http://search.ccgp.gov.cn/bxsearch?searchtype=1&page_index=1&bidSort=0"
                                + "&buyerName=&projectId=&pinMu=0&bidType=0&dbselect=bidx"
                                + "&kw=" + encode(keyword)
                                + "&start_time=" + encode(start.replace("-", ":"))
                                + "&end_time=" + encode(end.replace("-", ":"))
                                + "&timeType=6&displayZone=" + encode(region)
                                + "&zoneId=&pppStatus=0&agentName="),
                new PortalLink("全国公共资源交易平台",
                        "https://www.ggzy.gov.cn/search.shtml?wd=" + encode(query)),
                new PortalLink("中国招标投标公共服务平台",
                        "https://bulletin.cebpubservice.com/xxfbcmses/search/bulletin.html?word="
                                + encode(query)),
                new PortalLink("剑鱼标讯",
                        "https://www.jianyu360.cn/search/result?keyword=" + encode(query)),
                new PortalLink("招标与采购网",
                        "https://www.bidcenter.com.cn/search?keywords=" + encode(query))
        );
        List<BidItem> items = new ArrayList<>();
        for (PortalLink portal : portals) {
            BidItem item = new BidItem();
            item.setTitle(portal.name() + "：" + region + keyword + "招投标信息");
            item.setSourceName(portal.name());
            item.setSourceType("招投标来源");
            item.setSourceUrl(portal.url());
            item.setRegion(region);
            item.setCoreContent(region + keyword + "招标公告、采购公告、中标公告站内检索结果，时间范围："
                    + timeText + "。请打开来源链接核验具体公告发布时间、正文和附件。");
            item.getMergedSourceLinks().add(portal.name() + "：" + portal.url());
            items.add(item);
        }
        return items;
    }

    private List<String> expandedSearchTerms(String keyword) {
        List<String> terms = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return terms;
        }
        terms.add(keyword.trim());
        if (keyword.contains("服务器")) {
            terms.addAll(List.of("信创服务器", "鲲鹏服务器", "海光服务器", "算力服务器", "超频服务器",
                    "超融合服务器", "容灾服务器", "IT资源扩容"));
        }
        if (keyword.contains("软件服务")) {
            terms.addAll(List.of("软件开发", "信息化服务", "系统建设", "系统运维", "平台建设", "软件采购"));
        }
        if (keyword.contains("充电桩")) {
            terms.addAll(List.of("充电设施", "新能源充电", "充电站", "充电设备"));
        }
        if (keyword.contains("芯片") || keyword.contains("鑺墖")) {
            terms.addAll(List.of("集成电路", "半导体", "芯片采购", "芯片服务", "集成电路芯片",
                    "闆嗘垚鐢佃矾", "鍗婂浣?", "鑺墖閲囪喘", "鑺墖鏈嶅姟"));
        }
        return terms.stream().distinct().limit(8).toList();
    }

    private boolean looksBidRelated(String title, String text, SearchIntent intent) {
        String merged = title + " " + text;
        boolean keywordMatch = invalidPageFilter.keywordTerms(intent).isEmpty()
                || invalidPageFilter.keywordTerms(intent).stream().anyMatch(merged::contains);
        boolean bidMatch = invalidPageFilter.bidTerms().stream().anyMatch(merged::contains)
                || invalidPageFilter.realBidTerms().stream().anyMatch(merged::contains);
        return keywordMatch && bidMatch;
    }

    private boolean matchesRequestedRegion(String text, SearchIntent intent) {
        if (intent == null || intent.getProvince() == null || intent.getProvince().isBlank()
                || "全国".equals(intent.getProvince())) {
            return true;
        }
        String merged = normalize(text).toLowerCase(Locale.ROOT);
        return regionTokens(intent).stream()
                .filter(token -> token != null && !token.isBlank())
                .map(token -> token.toLowerCase(Locale.ROOT))
                .anyMatch(merged::contains);
    }

    private boolean isConcreteAggregatorResult(String text, String href, SearchIntent intent) {
        String lowerHref = href == null ? "" : href.toLowerCase(Locale.ROOT);
        if (!lowerHref.contains("qianlima.com/gjxx/") && !lowerHref.contains("qianlima.com/zb/")) {
            return false;
        }
        String merged = normalize(text);
        boolean keywordMatch = invalidPageFilter.keywordTerms(intent).isEmpty()
                || invalidPageFilter.keywordTerms(intent).stream().anyMatch(merged::contains);
        boolean bidMatch = invalidPageFilter.bidTerms().stream().anyMatch(merged::contains)
                || invalidPageFilter.realBidTerms().stream().anyMatch(merged::contains);
        boolean hasDate = extractPublishTime(merged).isPresent();
        return keywordMatch && bidMatch && hasDate && matchesRequestedRegion(merged + " " + href, intent);
    }

    private List<String> regionTokens(SearchIntent intent) {
        List<String> tokens = new ArrayList<>();
        if (intent == null) {
            return tokens;
        }
        addRegionToken(tokens, intent.getProvince());
        addRegionToken(tokens, intent.getCity());
        return tokens.stream().distinct().toList();
    }

    private String searchRegion(SearchIntent intent) {
        if (intent == null) {
            return "";
        }
        if (intent.getCity() != null && !intent.getCity().isBlank()) {
            return intent.getCity();
        }
        return intent.getProvince() == null ? "" : intent.getProvince();
    }

    private void addRegionToken(List<String> tokens, String region) {
        if (region == null || region.isBlank()) {
            return;
        }
        String trimmed = region.trim();
        tokens.add(trimmed);
        tokens.add(trimmed.replace("省", "").replace("市", "")
                .replace("自治区", "").replace("壮族", "")
                .replace("回族", "").replace("维吾尔", ""));
    }

    private boolean isUnsupportedCandidateDomain(String href) {
        String host = hostOf(href).toLowerCase(Locale.ROOT);
        return host.contains("zhihu.com") || host.contains("weixin.qq.com")
                || host.contains("mp.weixin.qq.com") || host.contains("baijiahao.baidu.com")
                || host.contains("zhidao.baidu.com") || host.contains("baike.baidu.com")
                || host.contains("xueqiu.com") || host.contains("36kr.com")
                || host.contains("news.qq.com") || host.contains("ewbang.com")
                || host.contains("toutiao.com") || host.contains("sohu.com")
                || host.contains("163.com") || host.contains("sina.com")
                || host.contains("csdn.net") || host.contains("bilibili.com");
    }

    private boolean isSearchEngineUrl(String href) {
        String value = href == null ? "" : href.toLowerCase(Locale.ROOT);
        return value.contains("bing.com/search") || value.contains("google.com/search");
    }

    private boolean looksLikeIndexCandidate(String title, String href, String summary) {
        String merged = normalize(title + " " + href + " " + summary);
        String lowerHref = href == null ? "" : href.toLowerCase(Locale.ROOT);
        boolean realConcreteProject = extractPublishTime(merged).isPresent()
                && (merged.contains("项目") || merged.contains("椤圭洰"))
                && (merged.contains("采购") || merged.contains("招标") || merged.contains("成交") || merged.contains("中标")
                || merged.contains("閲囪喘") || merged.contains("鎷涙爣") || merged.contains("鎴愪氦") || merged.contains("涓爣"));
        if ((title.contains("项目信息|工程招标") || title.contains("建设工程信息")
                || title.endsWith("招标网") || title.endsWith("采购网")
                || lowerHref.matches(".*zhaobiao\\.cn/project_\\d+\\.html.*")) && !realConcreteProject) {
            return true;
        }
        if (isRootOrKnownChannel(lowerHref)) {
            return true;
        }
        boolean genericTitle = title.contains("招标信息-") || title.contains("招标采购-")
                || title.contains("全国招标") || title.contains("中标公告_全国")
                || title.endsWith("平台") || title.endsWith("采购网") || title.endsWith("招标网");
        boolean rootOrChannel = lowerHref.matches("https?://[^/]+/?")
                || lowerHref.endsWith("/zhaobiao/") || lowerHref.endsWith("/succeed.html")
                || lowerHref.contains("/zhaobiao/zbkeyw-") || lowerHref.contains("/industry/")
                || lowerHref.contains("/gjxx/");
        boolean hasConcreteDate = extractPublishTime(merged).isPresent();
        boolean hasConcreteProject = merged.contains("项目") && (merged.contains("采购") || merged.contains("招标")
                || merged.contains("成交") || merged.contains("中标"));
        return (genericTitle || rootOrChannel) && !(hasConcreteDate && hasConcreteProject);
    }

    private boolean isRootOrKnownChannel(String lowerHref) {
        if (lowerHref == null || lowerHref.isBlank()) {
            return true;
        }
        if (lowerHref.matches("https?://[^/]+/?") || lowerHref.endsWith("/index.shtml")
                || lowerHref.endsWith("/index.html") || lowerHref.endsWith("/index.htm")
                || lowerHref.endsWith("/index.jhtml")) {
            return true;
        }
        return lowerHref.contains("/zhaobiao/zbkeyw-") || lowerHref.contains("/industry/")
                || lowerHref.contains("/search")
                || lowerHref.contains("/list") || lowerHref.contains("/category")
                || lowerHref.contains("/rfp-") || lowerHref.contains("/s?");
    }

    private String displayTitle(String title, String summary, String href, SearchIntent intent) {
        String normalized = normalize(title);
        if (!looksLikeUrlTitle(normalized)) {
            return normalized;
        }
        String extracted = headlineFromSummary(summary, intent);
        if (!extracted.isBlank()) {
            return extracted;
        }
        String region = intent == null || intent.getProvince() == null ? "" : intent.getProvince();
        String keyword = intent == null || intent.getKeyword() == null ? "" : intent.getKeyword();
        return (sourceNameFromUrl(href) + "：" + region + keyword + "相关公告候选").trim();
    }

    private boolean looksLikeUrlTitle(String title) {
        String lower = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return lower.contains("http://") || lower.contains("https://") || lower.contains("›")
                || lower.matches("^[a-z0-9.-]+\\s+https?.*")
                || lower.matches("^[a-z0-9.-]+$");
    }

    private String headlineFromSummary(String summary, SearchIntent intent) {
        String value = normalize(summary);
        if (value.isBlank()) {
            return "";
        }
        for (String part : value.split("[。；;|\\n]")) {
            String candidate = normalize(part);
            if (candidate.length() < 8 || candidate.length() > 90 || looksLikeUrlTitle(candidate)) {
                continue;
            }
            if (looksBidRelated(candidate, candidate, intent)) {
                return candidate;
            }
        }
        return "";
    }

    private String bestTitle(String searchTitle, String detailTitle) {
        String detail = normalize(detailTitle);
        String search = normalize(searchTitle);
        if (detail.length() >= 8 && !detail.equalsIgnoreCase(search)) {
            return detail;
        }
        return search;
    }

    private Optional<LocalDateTime> extractPublishTime(String text) {
        String value = text == null ? "" : text;
        Matcher chinese = CHINESE_DATE.matcher(value);
        if (chinese.find()) {
            return Optional.of(LocalDate.of(Integer.parseInt(chinese.group(1)),
                    Integer.parseInt(chinese.group(2)), Integer.parseInt(chinese.group(3))).atStartOfDay());
        }
        Matcher dash = DASH_DATE.matcher(value);
        if (dash.find()) {
            return Optional.of(LocalDate.of(Integer.parseInt(dash.group(1)),
                    Integer.parseInt(dash.group(2)), Integer.parseInt(dash.group(3))).atStartOfDay());
        }
        Matcher english = ENGLISH_DATE.matcher(value);
        if (english.find()) {
            int month = switch (english.group(1).toLowerCase(Locale.ROOT).substring(0, 3)) {
                case "jan" -> 1;
                case "feb" -> 2;
                case "mar" -> 3;
                case "apr" -> 4;
                case "may" -> 5;
                case "jun" -> 6;
                case "jul" -> 7;
                case "aug" -> 8;
                case "sep" -> 9;
                case "oct" -> 10;
                case "nov" -> 11;
                case "dec" -> 12;
                default -> 1;
            };
            return Optional.of(LocalDate.of(Integer.parseInt(english.group(3)), month,
                    Integer.parseInt(english.group(2))).atStartOfDay());
        }
        return Optional.empty();
    }

    private String sourceNameFromUrl(String href) {
        String host = hostOf(href);
        if (host.contains("ccgp.gov.cn")) return "中国政府采购网";
        if (host.contains("ggzy.gov.cn")) return "全国公共资源交易平台";
        if (host.contains("cebpubservice")) return "中国招标投标公共服务平台";
        if (host.contains("bidcenter.com.cn")) return "采招网";
        if (host.contains("qianlima.com")) return "千里马招标网";
        if (host.contains("zhaobiao.cn")) return "招标网";
        if (host.contains("plap.mil.cn")) return "军队采购网";
        if (host.contains("zfcg.czt.zj.gov.cn")) return "浙江政府采购网";
        if (host.contains("zcygov.cn")) return "政采云";
        if (host.contains("hzctc.hangzhou.gov.cn") || host.contains("ggzy.hangzhou.gov.cn")) return "杭州公共资源交易平台";
        if (host.contains("ccgp-jiangsu.gov.cn") || host.contains("jszfcg.jsczt.cn")) return "江苏政府采购网";
        if (host.contains("njggzy.nanjing.gov.cn")) return "南京公共资源交易平台";
        if (host.contains("gdgpo.czt.gd.gov.cn")) return "广东政府采购智慧云平台";
        if (host.contains("ggzy.gz.gov.cn")) return "广州公共资源交易平台";
        if (host.contains("szggzy.com")) return "深圳公共资源交易平台";
        return host.isBlank() ? "全网搜索候选" : host.replaceFirst("^www\\.", "");
    }

    private String hostOf(String href) {
        String host = "";
        try {
            String url = href != null && href.startsWith("http") ? href : "http://www.bing.com" + href;
            host = URI.create(url).getHost();
        } catch (Exception ignored) {
        }
        return host == null ? "" : host;
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
                return "来源访问受限已跳过：全网搜索被限制访问。";
            }
            if (http.getStatusCode() == 502 || http.getStatusCode() == 503 || http.getStatusCode() == 504) {
                return "网络波动已跳过：全网搜索暂时返回 " + http.getStatusCode() + "。";
            }
        }
        if (cause instanceof SocketTimeoutException) {
            return "网络波动已跳过：全网搜索响应超时。";
        }
        if (cause instanceof SSLHandshakeException) {
            return "网络波动已跳过：全网搜索 SSL 握手失败。";
        }
        return "自动跳过：全网搜索暂时不可用，已保留固定来源结果。";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String encode(String text) {
        return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
    }

    private String compactEngines(List<String> engines) {
        if (engines == null || engines.isEmpty()) {
            return "Bing RSS/Bing HTML/PowerShell";
        }
        return engines.stream().filter(v -> v != null && !v.isBlank()).distinct().limit(4)
                .reduce((left, right) -> left + "/" + right)
                .orElse("Bing RSS/Bing HTML/PowerShell");
    }

    private static int countPortalItems(List<BidItem> items) {
        if (items == null) {
            return 0;
        }
        return (int) items.stream()
                .filter(item -> item.getSourceType() != null && item.getSourceType().contains("检索入口"))
                .count();
    }

    private static int realItemCount(List<BidItem> items) {
        if (items == null) {
            return 0;
        }
        return items.size() - countPortalItems(items);
    }

    private record SearchBatch(List<BidItem> items, int filteredCount, List<String> engines, int portalCount) {
        private SearchBatch(List<BidItem> items, int filteredCount) {
            this(items, filteredCount, List.of("Bing RSS", "Bing HTML", "PowerShell fallback"),
                    countPortalItems(items));
        }
    }

    private record PortalLink(String name, String url) {
    }

    private record DetailSnapshot(String title, String summary, String publishText, List<String> attachments) {
    }
}
