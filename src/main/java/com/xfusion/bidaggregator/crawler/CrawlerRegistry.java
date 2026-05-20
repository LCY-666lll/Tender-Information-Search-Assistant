package com.xfusion.bidaggregator.crawler;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.service.InvalidPageFilter;
import com.xfusion.bidaggregator.service.LoginStateService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CrawlerRegistry {
    private final AppProperties properties;
    private final LoginStateService loginStateService;
    private final InvalidPageFilter invalidPageFilter;

    public CrawlerRegistry(AppProperties properties, LoginStateService loginStateService,
            InvalidPageFilter invalidPageFilter) {
        this.properties = properties;
        this.loginStateService = loginStateService;
        this.invalidPageFilter = invalidPageFilter;
    }

    public List<SourceCrawler> enabledCrawlers() {
        return properties.getSources().stream()
                .filter(AppProperties.SourceConfig::isEnabled)
                .map(this::crawler)
                .toList();
    }

    public List<SourceCrawler> enabledCrawlers(SearchIntent intent) {
        return properties.getSources().stream()
                .filter(AppProperties.SourceConfig::isEnabled)
                .filter(source -> shouldUseSource(source, intent))
                .map(this::crawler)
                .toList();
    }

    public List<AppProperties.SourceConfig> loginSources() {
        return properties.getSources().stream()
                .filter(AppProperties.SourceConfig::isEnabled)
                .filter(AppProperties.SourceConfig::isNeedLogin)
                .toList();
    }

    public Optional<AppProperties.SourceConfig> jianyuSource() {
        return loginSource("jianyu")
                .or(() -> loginSources().stream()
                        .filter(source -> containsAny(sourceText(source), "剑鱼", "jianyu"))
                        .findFirst())
                .or(() -> loginSources().stream().findFirst());
    }

    public Optional<AppProperties.SourceConfig> loginSource(String sourceKey) {
        return loginSources().stream()
                .filter(source -> loginStateService.sourceKey(source).equalsIgnoreCase(safe(sourceKey)))
                .findFirst();
    }

    public Optional<AppProperties.SourceConfig> sourceByKey(String sourceKey) {
        return properties.getSources().stream()
                .filter(AppProperties.SourceConfig::isEnabled)
                .filter(source -> loginStateService.sourceKey(source).equalsIgnoreCase(safe(sourceKey)))
                .findFirst();
    }

    private SourceCrawler crawler(AppProperties.SourceConfig source) {
        if (!source.isNeedLogin() && containsAny(sourceText(source), "beijing-gov", "ccgp-beijing.gov.cn")) {
            return new BeijingGovernmentProcurementCrawler(source, properties.getRequestTimeoutMs(),
                    properties.getMaxItemsPerSource(), invalidPageFilter);
        }
        return new ConfigurablePublicCrawler(source, properties.getRequestTimeoutMs(),
                properties.getMaxItemsPerSource(), loginStateService, invalidPageFilter);
    }

    private boolean shouldUseSource(AppProperties.SourceConfig source, SearchIntent intent) {
        if (source.isNeedLogin()) {
            return true;
        }
        String sourceText = sourceText(source);
        if (isNationalSource(sourceText)) {
            return true;
        }
        String region = (intent == null ? "" : safe(intent.getProvince()) + " " + safe(intent.getCity()))
                .toLowerCase(Locale.ROOT);
        if (containsAny(region, "北京", "鍖椾含")) {
            return containsAny(sourceText, "北京", "beijing", "ccgp-beijing", "ggzyfw.beijing");
        }
        if (containsAny(region, "上海")) {
            return containsAny(sourceText, "上海", "shggzy");
        }
        if (containsAny(region, "南京", "江苏")) {
            return containsAny(sourceText, "南京", "江苏", "jiangsu", "nanjing", "jszfcg", "ccgp-jiangsu", "njggzy");
        }
        if (containsAny(region, "广东", "广州", "深圳")) {
            return containsAny(sourceText, "广东", "广州", "深圳", "gdgpo", "gzggzy", "szggzy");
        }
        if (containsAny(region, "河南", "郑州")) {
            return containsAny(sourceText, "河南", "郑州", "henan", "zhengzhou", "hnzfcg", "zzggzy");
        }
        if (containsAny(region, "浙江", "杭州")) {
            return containsAny(sourceText, "浙江", "杭州", "zhejiang", "hangzhou", "zfcg.czt.zj", "hzctc");
        }
        return false;
    }

    private boolean isNationalSource(String text) {
        return containsAny(text, "全国", "中国政府采购", "中国招标投标", "ggzy.gov.cn", "ccgp.gov.cn", "cebpubservice");
    }

    private String sourceText(AppProperties.SourceConfig source) {
        return (safe(source.getKey()) + " " + safe(source.getName()) + " " + safe(source.getUrl()))
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... terms) {
        String value = safe(text).toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (!term.isBlank() && value.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
