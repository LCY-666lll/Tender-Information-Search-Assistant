package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InvalidPageFilterTest {
    private final InvalidPageFilter filter = new InvalidPageFilter();

    @Test
    void rejectsRedirectAppLoginAndWeakSearchPages() {
        SearchIntent intent = intent();

        assertThat(filter.isInvalid(item("Please click here if the page does not redirect automatically",
                "Please click here if the page does not redirect automatically"), intent)).isTrue();
        assertThat(filter.isInvalid(item("APP下载", "下载APP后查看采购信息"), intent)).isTrue();
        assertThat(filter.isInvalid(item("用户登录", "登录后查看完整公告"), intent)).isTrue();
        assertThat(filter.isInvalid(item("搜索结果", "全国省份分站列表 首页 导航 服务"), intent)).isTrue();
    }

    @Test
    void rejectsListingAndSearchResultUrls() {
        SearchIntent intent = intent();
        BidItem listing = item("软件服务招标信息", "软件服务招标公告项目列表，实时更新招标采购信息。");
        listing.setSourceUrl("https://s.zhaobiao.cn/s?searchtype=zb&queryword=software");
        assertThat(filter.isInvalid(listing, intent)).isTrue();

        BidItem rfpList = item("最新的耳机招标和RFP", "耳机招标市场分析和招标信息列表，注册后查看更多。");
        rfpList.setSourceUrl("https://www.globaltenders.com/rfp-cn/headphones-tenders");
        assertThat(filter.isInvalid(rfpList, intent)).isTrue();
    }

    @Test
    void acceptsRelevantAnnouncement() {
        SearchIntent intent = intent();
        BidItem item = item("广东软件服务公开招标公告",
                "广东软件服务项目公开招标，公告列明采购范围、投标人资格条件、文件获取方式和递交要求。");
        item.setPublishTime(LocalDateTime.now());

        assertThat(filter.isInvalid(item, intent)).isFalse();
    }

    @Test
    void rejectsIndustryRankingAndNewsEvenWhenKeywordAndRegionMatch() {
        SearchIntent intent = new SearchIntent();
        intent.setProvince("上海");
        intent.setKeyword("芯片");

        BidItem baidu = item("上海芯片公司排名一览表",
                "上海芯片公司排名，介绍意法半导体、芯片设计企业和产业链情况。");
        baidu.setSourceUrl("https://zhidao.baidu.com/question/317961180711430884.html");
        assertThat(filter.isInvalid(baidu, intent)).isTrue();

        BidItem news = item("上海半导体产业分布一览！撑起了中国芯片的半边天",
                "上海集成电路产业新闻资讯，介绍芯片设计、晶圆代工、封装测试。");
        news.setSourceUrl("https://news.qq.com/rain/a/20240729A092E100");
        assertThat(filter.isInvalid(news, intent)).isTrue();
    }

    @Test
    void acceptsChineseChipTenderAnnouncement() {
        SearchIntent intent = new SearchIntent();
        intent.setProvince("上海");
        intent.setKeyword("芯片");

        BidItem item = item("上海某单位芯片采购项目公开招标公告",
                "上海某单位芯片采购项目公开招标公告，公告列明采购范围、投标人资格、招标文件获取方式和开标时间。");
        item.setSourceUrl("https://www.ccgp.gov.cn/cggg/dfgg/gkzb/202605/t20260501_123456.htm");
        item.setPublishTime(LocalDateTime.now());

        assertThat(filter.isInvalid(item, intent)).isFalse();
    }

    @Test
    void acceptsServerExpansionTermsButRejectsUnrelatedPurchase() {
        SearchIntent intent = new SearchIntent();
        intent.setProvince("上海");
        intent.setKeyword("服务器");

        BidItem x86 = item("上海证券交易所鲲鹏服务器集中采购项目招标公告",
                "上海证券交易所及下属公司采购鲲鹏服务器和海光服务器，公告列明采购范围、资格条件和投标安排。");
        x86.setPublishTime(LocalDateTime.now());
        assertThat(filter.isInvalid(x86, intent)).isFalse();

        BidItem furniture = item("宿舍楼家具采购公开招标公告",
                "上海建设管理职业技术学院宿舍楼家具采购项目，包含床、桌椅和柜体等家具。");
        furniture.setPublishTime(LocalDateTime.now());
        assertThat(filter.isInvalid(furniture, intent)).isTrue();
    }

    @Test
    void acceptsRealChineseBeijingChipTenderAnnouncement() {
        SearchIntent intent = new SearchIntent();
        intent.setProvince("北京");
        intent.setKeyword("芯片");

        BidItem item = item("北京某单位芯片采购项目公开招标公告",
                "北京某单位芯片采购项目公开招标公告，公告列明采购范围、投标人资格、招标文件获取方式和开标时间。");
        item.setSourceUrl("http://www.ccgp-beijing.gov.cn/xxgg/sjzfcggg/sjzbgg/2026/5/demo.htm");
        item.setPublishTime(LocalDateTime.now());

        assertThat(filter.isInvalid(item, intent)).isFalse();
    }

    private SearchIntent intent() {
        SearchIntent intent = new SearchIntent();
        intent.setProvince("广东");
        intent.setKeyword("软件服务");
        return intent;
    }

    private BidItem item(String title, String content) {
        BidItem item = new BidItem();
        item.setTitle(title);
        item.setCoreContent(content);
        item.setSourceName("全网搜索");
        item.setSourceType("Agent 搜索发现");
        item.setSourceUrl("https://example.com/a");
        return item;
    }
}
