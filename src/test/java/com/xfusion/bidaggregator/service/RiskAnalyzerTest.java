package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xfusion.bidaggregator.model.BidItem;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RiskAnalyzerTest {
    private final RiskAnalyzer analyzer = new RiskAnalyzer();

    @Test
    void marksRealDeadlineRiskWithoutPaintingEveryRowRed() {
        BidItem item = new BidItem();
        item.setTitle("服务器招标公告");
        item.setCoreContent("响应文件提交截止时间临近。");
        item.setBidDeadline(LocalDateTime.now().plusDays(2));

        analyzer.analyze(item);

        assertThat(item.getRiskWarnings()).anyMatch(warning -> warning.contains("高风险"));
        assertThat(item.getRiskWarnings()).noneMatch(warning -> warning.contains("附件"));
    }
}
