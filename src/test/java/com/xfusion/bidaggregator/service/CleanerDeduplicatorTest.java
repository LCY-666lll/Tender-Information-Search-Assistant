package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CleanerDeduplicatorTest {
    private final CleanerDeduplicator cleaner = new CleanerDeduplicator();

    @Test
    void mergesDuplicateTitleAndDateAndKeepsOfficialSource() {
        SearchIntent intent = intent();
        BidItem search = item("广东软件服务采购公告", "全网搜索", "https://search.example/a");
        BidItem official = item("广东软件服务采购公告", "中国政府采购网", "https://www.ccgp.gov.cn/a");

        List<BidItem> result = cleaner.cleanAndDeduplicate(List.of(search, official), intent);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceName()).isEqualTo("中国政府采购网");
        assertThat(result.get(0).getMergedSourceLinks()).hasSize(2);
    }

    @Test
    void filtersInvalidRedirectPage() {
        SearchIntent intent = intent();
        BidItem invalid = item("Please click here if the page does not redirect automatically", "全网搜索",
                "https://example.com/redirect");
        invalid.setCoreContent("Please click here if the page does not redirect automatically");

        List<BidItem> result = cleaner.cleanAndDeduplicate(List.of(invalid), intent);

        assertThat(result).isEmpty();
    }

    @Test
    void keepsSearchResultWhenCityKeywordAndPublishTimeMatch() {
        SearchIntent intent = new SearchIntent();
        intent.setRawQuestion("最近5个月杭州服务器招标信息有哪些");
        intent.setProvince("浙江");
        intent.setCity("杭州");
        intent.setKeyword("服务器");
        intent.setStartTime(LocalDateTime.now().minusMonths(5));
        intent.setEndTime(LocalDateTime.now().plusDays(1));

        BidItem item = new BidItem();
        item.setTitle("杭州市服务器采购招标公告");
        item.setSourceName("Agent 全网搜索发现");
        item.setSourceType("Agent 搜索发现");
        item.setSourceUrl("https://www.ccgp.gov.cn/cggg/dfgg/gkzb/202605/t20260501_123456.htm");
        item.setRegion("浙江");
        item.setPublishTime(LocalDate.now().minusDays(7).atStartOfDay());
        item.setCoreContent("杭州市服务器采购招标公告，搜索摘要命中城市、关键词和发布时间。");

        List<BidItem> result = cleaner.cleanAndDeduplicate(List.of(item), intent);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRiskWarnings()).anyMatch(text -> text.contains("原文复核"));
    }

    private SearchIntent intent() {
        SearchIntent intent = new SearchIntent();
        intent.setRawQuestion("最近3个月广东软件服务招标信息每天9:00发送");
        intent.setProvince("广东");
        intent.setKeyword("软件服务");
        intent.setStartTime(LocalDateTime.now().minusMonths(3));
        intent.setEndTime(LocalDateTime.now().plusDays(1));
        return intent;
    }

    private BidItem item(String title, String source, String url) {
        BidItem item = new BidItem();
        item.setTitle(title);
        item.setSourceName(source);
        item.setSourceUrl(url);
        item.setRegion("广东");
        item.setPublishTime(LocalDate.now().minusDays(1).atStartOfDay());
        item.setCoreContent("广东软件服务采购招标公告，项目地点广东，包含服务范围、资格条件和投标安排。");
        item.getMergedSourceLinks().add(source + "：" + url);
        return item;
    }
}
