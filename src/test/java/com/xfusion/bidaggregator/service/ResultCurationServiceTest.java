package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.agent.AgentTrace;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.CurationResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultCurationServiceTest {
    private final ResultCurationService service = new ResultCurationService(
            new CleanerDeduplicator(),
            new RiskAnalyzer(),
            new HistoryService(new ObjectMapper(), new AppProperties()),
            new InvalidPageFilter());

    @Test
    void keepsDisplayableCandidateLeadsWhenStrictAnnouncementChecksDropThem() {
        SearchIntent intent = intent();
        BidItem lead = item("北京服务器采购公告线索",
                "北京服务器采购公告线索，摘要命中关键词，但发布时间和完整正文需要打开原文核验。",
                "https://www.bidcenter.com.cn/newscontent-1-1.html");
        lead.setSourceName("Agent 全网搜索发现");
        lead.setSourceType("Agent 搜索候选");
        lead.setPublishTime(LocalDateTime.now().minusYears(2));

        CurationResult result = service.curate(intent, List.of(lead), new AgentTrace());

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getCandidateItems()).hasSize(1);
        assertThat(result.getCandidateItems().get(0).getSourceUrl()).contains("bidcenter.com.cn");
    }

    private SearchIntent intent() {
        SearchIntent intent = new SearchIntent();
        intent.setRawQuestion("最近5个月北京服务器招标信息有哪些");
        intent.setProvince("北京");
        intent.setKeyword("服务器");
        intent.setStartTime(LocalDateTime.now().minusMonths(5));
        intent.setEndTime(LocalDateTime.now().plusDays(1));
        return intent;
    }

    private BidItem item(String title, String content, String url) {
        BidItem item = new BidItem();
        item.setTitle(title);
        item.setCoreContent(content);
        item.setSourceName("测试来源");
        item.setSourceType("公开来源");
        item.setSourceUrl(url);
        item.setRegion("北京");
        return item;
    }
}
