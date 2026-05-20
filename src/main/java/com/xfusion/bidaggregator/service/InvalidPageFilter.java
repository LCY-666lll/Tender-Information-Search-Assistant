package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InvalidPageFilter {
    private static final List<String> INVALID_PHRASES = List.of(
            "Please click here if the page does not redirect automatically",
            "app下载", "APP下载", "下载APP", "欢迎访问", "用户登录", "会员登录", "登录后查看",
            "全国省份", "分站列表", "网站首页", "首页导航", "搜索结果", "暂无数据");

    public boolean isInvalid(BidItem item, SearchIntent intent) {
        if (item == null) {
            return true;
        }
        String title = clean(item.getTitle());
        String body = clean(item.getCoreContent());
        String url = clean(item.getSourceUrl()).toLowerCase(Locale.ROOT);
        String merged = title + " " + body + " " + url;
        if (title.isBlank() && body.isBlank()) {
            return true;
        }
        if (containsInvalidPhrase(merged) || looksLikeHomeOrLogin(title, body, url)
                || isUnsupportedCandidateDomain(url)) {
            return true;
        }
        if (isSearchCandidate(item) && looksLikeListingUrl(url) && !isConcreteListingCandidate(merged, url, intent)) {
            return true;
        }
        if (isSearchCandidate(item) && item.getPublishTime() == null && body.length() < 80) {
            return true;
        }
        if (!containsKeyword(merged, intent)) {
            return true;
        }
        return !containsAnyTerm(merged, bidTerms()) && !containsAnyTerm(merged, realBidTerms());
    }

    public boolean isInvalidPageText(String title, String url, String body, SearchIntent intent) {
        BidItem item = new BidItem();
        item.setTitle(title);
        item.setSourceUrl(url);
        item.setCoreContent(body);
        return isInvalid(item, intent);
    }

    public boolean isDisplayableCandidate(BidItem item, SearchIntent intent) {
        if (item == null) {
            return false;
        }
        String title = clean(item.getTitle());
        String body = clean(item.getCoreContent());
        String url = clean(item.getSourceUrl()).toLowerCase(Locale.ROOT);
        String merged = title + " " + body + " " + url + " " + clean(item.getRegion());
        if (clean(item.getSourceType()).contains("检索入口")) {
            return false;
        }
        if (title.isBlank() || url.isBlank()) {
            return false;
        }
        if (containsInvalidPhrase(merged) || looksLikeHomeOrLogin(title, body, url)
                || (looksLikeListingUrl(url) && !isConcreteListingCandidate(merged, url, intent))
                || isUnsupportedCandidateDomain(url)) {
            return false;
        }
        boolean trustedBidDomain = looksLikeBidDetailUrl(url);
        if (!containsKeyword(merged, intent)) {
            return false;
        }
        if (intent != null && intent.getProvince() != null && !"全国".equals(intent.getProvince())) {
            String province = regionToken(intent.getProvince());
            String city = regionToken(intent.getCity());
            boolean regionMatch = merged.contains(intent.getProvince()) || merged.contains(province)
                    || (!city.isBlank() && merged.contains(city)) || "全国".equals(item.getRegion());
            if (!regionMatch && !isSearchCandidate(item)) {
                return false;
            }
        }
        return containsAnyTerm(merged, bidTerms()) || containsAnyTerm(merged, realBidTerms()) || trustedBidDomain;
    }

    public List<String> announcementTerms(SearchIntent intent) {
        List<String> terms = new ArrayList<>();
        terms.addAll(keywordTerms(intent));
        terms.addAll(bidTerms());
        return terms.stream().distinct().toList();
    }

    public List<String> keywordTerms(SearchIntent intent) {
        if (intent == null || intent.getKeyword() == null || intent.getKeyword().isBlank()) {
            return List.of();
        }
        return expandedKeywordTerms(intent.getKeyword());
    }

    public List<String> realBidTerms() {
        return List.of("招标", "采购", "公告", "中标", "成交", "竞争性磋商", "公开招标",
                "询价", "比选", "磋商", "谈判", "单一来源", "更正", "结果公告",
                "招标文件", "投标", "开标", "候选人公示", "采购意向");
    }

    public List<String> bidTerms() {
        return List.of("招标", "采购", "公告", "中标", "成交", "竞争性磋商", "公开招标",
                "询价", "比选", "磋商", "谈判", "单一来源", "更正", "结果公告",
                "招标文件", "投标", "开标", "候选人公示", "采购意向",
                "鎷涙爣", "閲囪喘", "鍏憡", "涓爣", "鎴愪氦", "鍏紑鎷涙爣");
    }

    private boolean containsInvalidPhrase(String merged) {
        for (String phrase : INVALID_PHRASES) {
            if (merged.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeHomeOrLogin(String title, String body, String url) {
        String text = title + " " + body;
        boolean shortBody = body.length() < 80;
        if (shortBody && (title.endsWith("首页") || title.contains("登录") || title.contains("欢迎"))) {
            return true;
        }
        if (url.endsWith("/login") || url.contains("login") || url.contains("passport")) {
            return true;
        }
        int navWords = countMatches(text, List.of("首页", "新闻", "政策", "服务", "办事", "互动", "帮助", "导航"));
        return shortBody && navWords >= 4;
    }

    private boolean looksLikeListingUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (url.matches("https?://[^/]+/?") || url.endsWith("/index.shtml")
                || url.endsWith("/index.html") || url.endsWith("/index.htm") || url.endsWith("/index.jhtml")) {
            return true;
        }
        String path = urlPathAndQuery(url);
        return path.contains("/zhaobiao/zbkeyw-") || path.contains("/industry/")
                || path.contains("/gjxx/") || path.contains("/search") || path.contains("/list")
                || path.contains("/category") || path.contains("/rfp-") || path.contains("/s?");
    }

    private boolean isConcreteListingCandidate(String merged, String url, SearchIntent intent) {
        String lowerUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (!lowerUrl.contains("qianlima.com/gjxx/") && !lowerUrl.contains("qianlima.com/zb/")) {
            return false;
        }
        return containsKeyword(merged, intent)
                && (containsAnyTerm(merged, bidTerms()) || containsAnyTerm(merged, realBidTerms()))
                && (merged.matches(".*20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}.*")
                    || merged.matches(".*20\\d{2}年\\d{1,2}月\\d{1,2}日.*"));
    }

    private boolean looksLikeBidDetailUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.contains("bidcenter.com.cn/newscontent-")
                || url.contains("ccgp.gov.cn/cggg/")
                || url.contains("ccgp-beijing.gov.cn")
                || url.contains("ggzyfw.beijing.gov.cn")
                || url.contains("cebpubservice.com")
                || url.contains("ggzy.gov.cn/information")
                || url.contains("zfcg.czt.zj.gov.cn")
                || url.contains("zcygov.cn")
                || url.contains("hzctc.hangzhou.gov.cn")
                || url.contains("ggzy.hangzhou.gov.cn")
                || url.contains("ccgp-jiangsu.gov.cn")
                || url.contains("jszfcg.jsczt.cn")
                || url.contains("njggzy.nanjing.gov.cn")
                || url.contains("gdgpo.czt.gd.gov.cn")
                || url.contains("ggzy.gz.gov.cn")
                || url.contains("szggzy.com")
                || url.contains("hnzfcg.gov.cn")
                || url.contains("zzggzy.zhengzhou.gov.cn");
    }

    private boolean isUnsupportedCandidateDomain(String href) {
        String host = hostOf(href);
        return host.contains("zhidao.baidu.com") || host.contains("baike.baidu.com")
                || host.contains("baijiahao.baidu.com") || host.contains("zhihu.com")
                || host.contains("xueqiu.com") || host.contains("36kr.com")
                || host.contains("news.qq.com") || host.contains("weixin.qq.com")
                || host.contains("mp.weixin.qq.com") || host.contains("toutiao.com")
                || host.contains("sohu.com") || host.contains("163.com")
                || host.contains("sina.com") || host.contains("csdn.net")
                || host.contains("bilibili.com") || host.contains("ewbang.com");
    }

    private String urlPathAndQuery(String url) {
        try {
            URI uri = URI.create(url);
            return ((uri.getPath() == null ? "" : uri.getPath())
                    + (uri.getQuery() == null ? "" : "?" + uri.getQuery())).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return url.toLowerCase(Locale.ROOT);
        }
    }

    private String hostOf(String href) {
        try {
            String url = href != null && href.startsWith("http") ? href : "http://" + href;
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isSearchCandidate(BidItem item) {
        String name = clean(item.getSourceName());
        String type = clean(item.getSourceType());
        return name.contains("全网搜索") || type.contains("搜索") || type.contains("候选")
                || type.contains("检索入口") || name.contains("鍏ㄧ綉鎼滅储")
                || type.contains("鎼滅储") || type.contains("鍊欓€?") || type.contains("妫€绱㈠叆鍙?");
    }

    private boolean containsAnyTerm(String merged, List<String> terms) {
        String lower = merged.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (term != null && !term.isBlank() && lower.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKeyword(String merged, SearchIntent intent) {
        List<String> terms = keywordTerms(intent);
        if (terms.isEmpty()) {
            return true;
        }
        String lower = merged.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (!term.isBlank() && lower.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> expandedKeywordTerms(String keyword) {
        List<String> terms = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return terms;
        }
        terms.add(keyword.trim());
        for (String part : keyword.split("[\\s,，、+]+")) {
            if (!part.isBlank()) {
                terms.add(part.trim());
            }
        }
        if (keyword.contains("服务器")) {
            terms.addAll(List.of("计算服务器", "机架式服务器", "存储服务器", "信创服务器", "鲲鹏服务器",
                    "海光服务器", "海光", "鲲鹏", "算力服务器", "智能算力", "超频服务器", "低延时服务器",
                    "超融合服务器", "容灾服务器", "IT资源扩容", "数据中心设备"));
        }
        if (keyword.contains("软件服务")) {
            terms.addAll(List.of("软件开发", "信息化服务", "系统建设", "系统运维", "平台建设", "数字化服务",
                    "应用软件", "信息系统", "软件采购", "运维服务"));
        }
        if (keyword.contains("充电桩")) {
            terms.addAll(List.of("充电设施", "新能源充电", "充电站", "充电设备"));
        }
        if (keyword.contains("芯片")) {
            terms.addAll(List.of("集成电路", "半导体", "芯片采购", "芯片服务"));
        }
        return terms.stream().distinct().toList();
    }

    private String regionToken(String value) {
        return value == null ? "" : value.replace("省", "").replace("市", "").toLowerCase(Locale.ROOT);
    }

    private int countMatches(String text, List<String> words) {
        int count = 0;
        for (String word : words) {
            if (text.contains(word)) {
                count++;
            }
        }
        return count;
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
