package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.agent.AgentPlan;
import com.xfusion.bidaggregator.agent.AgentTrace;
import com.xfusion.bidaggregator.crawler.CrawlerRegistry;
import com.xfusion.bidaggregator.crawler.SourceCrawler;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.CrawlResult;
import com.xfusion.bidaggregator.model.HarvestResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.model.SourceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class SourceHarvestService {
    private final CrawlerRegistry crawlerRegistry;
    private final WebSearchService webSearchService;

    public SourceHarvestService(CrawlerRegistry crawlerRegistry, WebSearchService webSearchService) {
        this.crawlerRegistry = crawlerRegistry;
        this.webSearchService = webSearchService;
    }

    public List<SourceCrawler> enabledCrawlers() {
        return crawlerRegistry.enabledCrawlers();
    }

    public List<SourceCrawler> enabledCrawlers(SearchIntent intent) {
        return crawlerRegistry.enabledCrawlers(intent);
    }

    public HarvestResult harvest(SearchIntent intent, AgentPlan plan, AgentTrace trace) {
        return harvest(intent, plan, trace, ignored -> {
        }, () -> false);
    }

    public HarvestResult harvest(SearchIntent intent, AgentPlan plan, AgentTrace trace,
            Consumer<List<BidItem>> itemBatchConsumer, BooleanSupplier cancelled) {
        List<BidItem> rawItems = new ArrayList<>();
        List<SourceStatus> statuses = new ArrayList<>();

        trace.addStep("全网搜索发现", "RUNNING", "根据解析条件发现可参考网页。");
        CrawlResult searchResult = webSearchService.discover(intent, plan.getSearchQueries());
        statuses.add(searchResult.getStatus());
        rawItems.addAll(searchResult.getItems());
        publish(itemBatchConsumer, searchResult.getItems());
        trace.addStep("全网搜索发现", searchResult.getStatus().isSuccess() ? "SUCCESS" : "WARNING",
                sourceTraceMessage(searchResult));

        for (SourceCrawler crawler : enabledCrawlers(intent)) {
            if (cancelled.getAsBoolean()) {
                trace.addStep("用户停止搜寻", "WARNING", "已停止后续网页分析，保留已经返回的结果。");
                break;
            }
            trace.addStep("来源采集：" + crawler.sourceName(), "RUNNING",
                    "访问" + crawler.sourceType() + "，抽取列表、详情页正文和附件链接。");
            CrawlResult result = crawler.crawl(intent);
            statuses.add(result.getStatus());
            rawItems.addAll(result.getItems());
            publish(itemBatchConsumer, result.getItems());
            trace.addStep("来源采集：" + crawler.sourceName(),
                    result.getStatus().isSuccess() ? "SUCCESS" : "WARNING",
                    sourceTraceMessage(result));
        }
        return new HarvestResult(rawItems, statuses);
    }

    private void publish(Consumer<List<BidItem>> itemBatchConsumer, List<BidItem> items) {
        if (items != null && !items.isEmpty()) {
            itemBatchConsumer.accept(items);
        }
    }

    private String sourceTraceMessage(CrawlResult result) {
        SourceStatus status = result.getStatus();
        String base = "抓取 " + status.getFetchedCount() + " 条，入选 " + status.getSelectedCount() + " 条。";
        return status.getWarning() == null || status.getWarning().isBlank() ? base : base + status.getUserMessage();
    }
}
