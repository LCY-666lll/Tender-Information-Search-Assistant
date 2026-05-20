package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.BidItem;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ValidAnnouncementScorer {
    public int score(BidItem item) {
        if (item == null) {
            return Integer.MIN_VALUE;
        }
        int score = sourceTier(item) * 1000;
        if (item.getBidDeadline() != null) {
            long days = Duration.between(LocalDateTime.now(), item.getBidDeadline()).toDays();
            if (days >= 0 && days <= 3) {
                score += 180;
            } else if (days > 3 && days <= 10) {
                score += 100;
            }
        }
        if (item.getPublishTime() != null) {
            long ageDays = Math.max(0, Duration.between(item.getPublishTime(), LocalDateTime.now()).toDays());
            score += Math.max(0, 120 - (int) Math.min(ageDays, 120));
        }
        if (item.getAttachmentLinks() != null && !item.getAttachmentLinks().isEmpty()) {
            score += 80;
        }
        String content = item.getCoreContent() == null ? "" : item.getCoreContent().trim();
        if (content.length() >= 120) {
            score += 80;
        } else if (content.length() >= 50) {
            score += 40;
        }
        if (isSearchCandidate(item)) {
            score -= 300;
        }
        if (content.contains("演示") || content.contains("兜底")) {
            score -= 500;
        }
        return score;
    }

    public Comparator<BidItem> comparator() {
        return Comparator.comparingInt(this::score).reversed()
                .thenComparing((BidItem item) -> item.getPublishTime() == null ? LocalDateTime.MIN : item.getPublishTime(),
                        Comparator.reverseOrder())
                .thenComparing(item -> item.getTitle() == null ? "" : item.getTitle());
    }

    public boolean shouldReplace(BidItem existing, BidItem candidate) {
        return score(candidate) > score(existing);
    }

    public int sourceTier(BidItem item) {
        String merged = ((item.getSourceName() == null ? "" : item.getSourceName()) + " "
                + (item.getSourceType() == null ? "" : item.getSourceType()) + " "
                + (item.getSourceUrl() == null ? "" : item.getSourceUrl())).toLowerCase(Locale.ROOT);
        if (merged.contains("ggzy.gov.cn") || merged.contains("ccgp.gov.cn") || merged.contains("cebpubservice")
                || merged.contains("shggzy") || merged.contains("公共资源") || merged.contains("政府采购")
                || merged.contains("招标投标") || merged.contains("省级公共资源")) {
            return 5;
        }
        if (merged.contains("登录") || merged.contains("jianyu") || merged.contains("会员")) {
            return 4;
        }
        if (merged.contains("企业采购") || merged.contains("10086") || merged.contains("b2b")) {
            return 3;
        }
        if (isSearchCandidate(item)) {
            return 1;
        }
        return 2;
    }

    public boolean isSearchCandidate(BidItem item) {
        String merged = ((item.getSourceName() == null ? "" : item.getSourceName()) + " "
                + (item.getSourceType() == null ? "" : item.getSourceType())).toLowerCase(Locale.ROOT);
        return merged.contains("全网搜索") || merged.contains("搜索") || merged.contains("候选");
    }
}
