package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xfusion.bidaggregator.model.BidItem;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidAnnouncementScorerTest {
    private final ValidAnnouncementScorer scorer = new ValidAnnouncementScorer();

    @Test
    void ranksOfficialAnnouncementBeforeSearchCandidate() {
        BidItem search = item("全网搜索", "https://example.com/a");
        BidItem official = item("中国政府采购网", "https://www.ccgp.gov.cn/a");
        official.getAttachmentLinks().add("https://www.ccgp.gov.cn/a.docx");

        List<BidItem> sorted = List.of(search, official).stream().sorted(scorer.comparator()).toList();

        assertThat(sorted.get(0).getSourceName()).isEqualTo("中国政府采购网");
    }

    private BidItem item(String source, String url) {
        BidItem item = new BidItem();
        item.setTitle("广东软件服务公开招标公告");
        item.setSourceName(source);
        item.setSourceUrl(url);
        item.setPublishTime(LocalDateTime.now());
        item.setBidDeadline(LocalDateTime.now().plusDays(2));
        item.setCoreContent("广东软件服务公开招标公告，包含采购范围、资格条件、报名安排、投标截止时间和联系方式。");
        return item;
    }
}
