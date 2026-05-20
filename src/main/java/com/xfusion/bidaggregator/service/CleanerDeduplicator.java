package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CleanerDeduplicator {
    private final InvalidPageFilter invalidPageFilter;
    private final ValidAnnouncementScorer scorer;

    public CleanerDeduplicator() {
        this(new InvalidPageFilter(), new ValidAnnouncementScorer());
    }

    public CleanerDeduplicator(InvalidPageFilter invalidPageFilter, ValidAnnouncementScorer scorer) {
        this.invalidPageFilter = invalidPageFilter;
        this.scorer = scorer;
    }

    public List<BidItem> cleanAndDeduplicate(List<BidItem> rawItems, SearchIntent intent) {
        Map<String, BidItem> unique = new LinkedHashMap<>();
        for (BidItem item : rawItems) {
            item.setTitle(clean(item.getTitle()));
            item.setCoreContent(clean(item.getCoreContent()));
            if (invalidPageFilter.isInvalid(item, intent) || !matchesIntent(item, intent)) {
                continue;
            }
            if (isSearchCandidate(item) && !hasAcceptableSearchEvidence(item, intent)) {
                continue;
            }
            String key = dedupeKey(item);
            item.setContentHash(sha256(key + item.getCoreContent()));
            item.setId(item.getContentHash());
            BidItem existing = unique.get(key);
            if (existing == null) {
                unique.put(key, item);
            } else {
                BidItem kept = scorer.shouldReplace(existing, item) ? item : existing;
                BidItem merged = kept == item ? existing : item;
                kept.getMergedSourceLinks().add(merged.getSourceName() + "：" + merged.getSourceUrl());
                kept.getAttachmentLinks().addAll(merged.getAttachmentLinks());
                unique.put(key, kept);
            }
        }
        return unique.values().stream()
                .sorted(scorer.comparator())
                .toList();
    }

    private boolean matchesIntent(BidItem item, SearchIntent intent) {
        String joined = (item.getTitle() + " " + item.getCoreContent() + " " + item.getRegion()).toLowerCase();
        boolean keywordMatch = invalidPageFilter.keywordTerms(intent).isEmpty()
                || invalidPageFilter.keywordTerms(intent).stream()
                .anyMatch(term -> joined.contains(term.toLowerCase()));
        boolean bidMatch = invalidPageFilter.bidTerms().stream()
                .anyMatch(term -> joined.contains(term.toLowerCase()))
                || invalidPageFilter.realBidTerms().stream()
                .anyMatch(term -> joined.contains(term.toLowerCase()));
        boolean regionMatch = intent == null || intent.getProvince() == null || "全国".equals(intent.getProvince())
                || joined.contains(regionToken(intent.getProvince()))
                || joined.contains(regionToken(intent.getCity()))
                || "全国".equals(item.getRegion());
        boolean timeMatch = intent == null || item.getPublishTime() == null || intent.getStartTime() == null
                || intent.getEndTime() == null
                || (!item.getPublishTime().isBefore(intent.getStartTime())
                        && !item.getPublishTime().isAfter(intent.getEndTime()));
        return keywordMatch && bidMatch && regionMatch && timeMatch;
    }

    private boolean isSearchCandidate(BidItem item) {
        String name = item.getSourceName() == null ? "" : item.getSourceName();
        String type = item.getSourceType() == null ? "" : item.getSourceType();
        return name.contains("全网搜索") || type.contains("搜索") || type.contains("候选") || type.contains("检索入口")
                || name.contains("鍏ㄧ綉鎼滅储") || type.contains("鎼滅储") || type.contains("鍊欓€?");
    }

    private String regionToken(String value) {
        return value == null ? "" : value.replace("省", "").replace("市", "").toLowerCase();
    }

    private boolean hasAcceptableSearchEvidence(BidItem item, SearchIntent intent) {
        String title = item.getTitle() == null ? "" : item.getTitle();
        if (title.contains("相关公告候选")) {
            return false;
        }
        if (item.getPublishTime() == null) {
            return false;
        }
        String text = (item.getTitle() + " " + item.getCoreContent()).replaceAll("\\s+", "");
        boolean realBusinessEvidence = text.contains("项目") || text.contains("采购人") || text.contains("预算")
                || text.contains("截止") || text.contains("投标") || text.contains("招标文件")
                || text.contains("成交") || text.contains("中标") || text.contains("开标");
        boolean businessEvidence = text.contains("项目") || text.contains("采购人") || text.contains("预算") || text.contains("截止")
                || text.contains("投标") || text.contains("招标文件") || text.contains("成交")
                || text.contains("椤圭洰") || text.contains("閲囪喘浜?") || text.contains("棰勭畻")
                || text.contains("鎴") || text.contains("鎶曟爣") || text.contains("鎷涙爣鏂囦欢")
                || text.contains("鎴愪氦");
        boolean directMatch = invalidPageFilter.keywordTerms(intent).stream().anyMatch(text::contains)
                && (invalidPageFilter.bidTerms().stream().anyMatch(text::contains)
                || invalidPageFilter.realBidTerms().stream().anyMatch(text::contains))
                && containsRequestedRegion(text, intent)
                && looksLikeRealUrl(item.getSourceUrl());
        if (directMatch && !businessEvidence) {
            item.getRiskWarnings().add("搜索结果摘要证据有限，已按城市、关键词和发布时间命中纳入，建议打开原文复核。");
        }
        return businessEvidence || realBusinessEvidence || directMatch;
    }

    private boolean containsRequestedRegion(String text, SearchIntent intent) {
        if (intent == null || intent.getProvince() == null || "全国".equals(intent.getProvince())) {
            return true;
        }
        String province = regionToken(intent.getProvince());
        String city = regionToken(intent.getCity());
        return (!province.isBlank() && text.toLowerCase().contains(province))
                || (!city.isBlank() && text.toLowerCase().contains(city));
    }

    private boolean looksLikeRealUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http") && !lower.contains("bing.com") && !lower.contains("google.com")
                && !lower.contains("/search") && !lower.contains("/list");
    }

    private String dedupeKey(BidItem item) {
        String normalizedTitle = item.getTitle() == null ? "" : item.getTitle()
                .replaceAll("[\\s　，。、”“《》（）()\\[\\]【】]", "")
                .replaceAll("招标公告|采购公告|中标公告|结果公告|成交公告|公示", "");
        String day = item.getPublishTime() == null ? "" : item.getPublishTime().toLocalDate().toString();
        return sha256(normalizedTitle + day);
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("上一篇.*|下一篇.*|分享到.*|打印本页.*|关闭窗口.*", "")
                .replaceAll("Please click here if the page does not redirect automatically", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
