package com.xfusion.bidaggregator.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.service.InvalidPageFilter;
import com.xfusion.bidaggregator.service.LoginStateService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrawlerRegistryTest {

    @Test
    void beijingQueryUsesBeijingAndNationalSourcesButNotShanghai() {
        AppProperties properties = new AppProperties();
        properties.setSources(List.of(
                source("national-ggzy", "全国公共资源交易平台", "https://www.ggzy.gov.cn/", false),
                source("ccgp", "中国政府采购网", "https://www.ccgp.gov.cn/", false),
                source("shggzy", "上海公共资源交易平台", "https://www.shggzy.com/", false),
                source("beijing-gov", "北京市政府采购网", "http://www.ccgp-beijing.gov.cn/", false),
                source("beijing-ggzy", "北京市公共资源交易服务平台", "https://ggzyfw.beijing.gov.cn/", false),
                source("jianyu", "剑鱼标讯", "https://www.jianyu360.cn/", true)));

        CrawlerRegistry registry = new CrawlerRegistry(properties,
                new LoginStateService(properties, new ObjectMapper()), new InvalidPageFilter());
        SearchIntent intent = new SearchIntent();
        intent.setProvince("北京");
        intent.setCity("北京");
        intent.setKeyword("芯片");

        List<String> keys = registry.enabledCrawlers(intent).stream()
                .map(SourceCrawler::sourceName)
                .toList();

        assertThat(keys).contains("全国公共资源交易平台", "中国政府采购网",
                "北京市政府采购网", "北京市公共资源交易服务平台", "剑鱼标讯");
        assertThat(keys).doesNotContain("上海公共资源交易平台");
    }

    private AppProperties.SourceConfig source(String key, String name, String url, boolean needLogin) {
        AppProperties.SourceConfig source = new AppProperties.SourceConfig();
        source.setKey(key);
        source.setName(name);
        source.setType(needLogin ? "登录源" : "公开源");
        source.setUrl(url);
        source.setEnabled(true);
        source.setNeedLogin(needLogin);
        if (needLogin) {
            source.setStorageState("data/login/" + key + "-state.json");
        }
        return source;
    }
}
