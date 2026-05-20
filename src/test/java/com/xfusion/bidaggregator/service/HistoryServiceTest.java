package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void filtersSameAnnouncementOnSecondRun() {
        HistoryService historyService = new HistoryService(new ObjectMapper(), properties());
        SearchIntent intent = new IntentParser().parse("最近3个月广东软件服务招标信息每天9:00发送");

        historyService.markSent(intent, List.of(item("one-id", "广东软件服务招标公告")));
        List<BidItem> secondRun = new ArrayList<>(List.of(item("another-id", "广东 软件服务 招标公告")));

        int skipped = historyService.filterAlreadySent(intent, secondRun);

        assertThat(skipped).isEqualTo(1);
        assertThat(secondRun).isEmpty();
    }

    @Test
    void clearOnlyRemovesCurrentQuestionHistory() {
        HistoryService historyService = new HistoryService(new ObjectMapper(), properties());
        SearchIntent serverIntent = new IntentParser().parse("最近1个月安徽服务器招标信息有哪些");
        SearchIntent softwareIntent = new IntentParser().parse("最近3个月广东软件服务招标信息每天9:00发送");

        historyService.markSent(serverIntent, List.of(item("server-id", "安徽服务器招标公告")));
        historyService.markSent(softwareIntent, List.of(item("software-id", "广东软件服务招标公告")));

        assertThat(historyService.clear(serverIntent)).isTrue();

        List<BidItem> serverItems = new ArrayList<>(List.of(item("server-id-2", "安徽服务器招标公告")));
        List<BidItem> softwareItems = new ArrayList<>(List.of(item("software-id-2", "广东软件服务招标公告")));
        assertThat(historyService.filterAlreadySent(serverIntent, serverItems)).isZero();
        assertThat(historyService.filterAlreadySent(softwareIntent, softwareItems)).isEqualTo(1);
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir.resolve("data").toString());
        return properties;
    }

    private BidItem item(String id, String title) {
        BidItem item = new BidItem();
        item.setId(id);
        item.setTitle(title);
        item.setSourceName("测试来源");
        item.setSourceUrl("https://example.com/a");
        item.setRegion("广东");
        item.setPublishTime(LocalDate.now().minusDays(1).atStartOfDay());
        item.setCoreContent("软件服务采购公告。");
        return item;
    }
}
