package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.agent.AgentModelResponse;
import com.xfusion.bidaggregator.agent.AgentOrchestrator;
import com.xfusion.bidaggregator.agent.AgentPlan;
import com.xfusion.bidaggregator.agent.AgentTrace;
import com.xfusion.bidaggregator.crawler.SourceCrawler;
import com.xfusion.bidaggregator.model.AggregationTask;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.CurationResult;
import com.xfusion.bidaggregator.model.HarvestResult;
import com.xfusion.bidaggregator.model.QueryResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.model.SourceStatus;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class BidAggregationService {
    private final IntentParser intentParser;
    private final WordReportService wordReportService;
    private final AgentOrchestrator agentOrchestrator;
    private final AgentModelService agentModelService;
    private final TaskService taskService;
    private final QueryExpansionService queryExpansionService;
    private final SourceHarvestService sourceHarvestService;
    private final ResultCurationService resultCurationService;

    public BidAggregationService(IntentParser intentParser, WordReportService wordReportService,
            AgentOrchestrator agentOrchestrator, AgentModelService agentModelService, TaskService taskService,
            QueryExpansionService queryExpansionService, SourceHarvestService sourceHarvestService,
            ResultCurationService resultCurationService) {
        this.intentParser = intentParser;
        this.wordReportService = wordReportService;
        this.agentOrchestrator = agentOrchestrator;
        this.agentModelService = agentModelService;
        this.taskService = taskService;
        this.queryExpansionService = queryExpansionService;
        this.sourceHarvestService = sourceHarvestService;
        this.resultCurationService = resultCurationService;
    }

    public QueryResult execute(String question) {
        SearchIntent intent = intentParser.parse(question);
        return executeWithIntent(question, intent, true, ignored -> {
        }, () -> false);
    }

    public QueryResult executeStreaming(String question, Consumer<List<BidItem>> itemBatchConsumer,
            BooleanSupplier cancelled) {
        SearchIntent intent = intentParser.parse(question);
        return executeWithIntent(question, intent, true, itemBatchConsumer, cancelled);
    }

    public QueryResult executeScheduledTask(AggregationTask task) {
        SearchIntent intent = task.getIntent();
        if (intent == null) {
            intent = intentParser.parse(task.getQuestion());
        }
        QueryResult result = executeWithIntent(task.getQuestion(), intent, false, ignored -> {
        }, () -> false);
        List<String> selectedIds = new ArrayList<>();
        result.getItems().stream()
                .filter(item -> !isSearchEngineUrl(item.getSourceUrl()))
                .map(BidItem::getId)
                .forEach(selectedIds::add);
        result.getCandidateItems().stream()
                .filter(item -> !isSearchEngineUrl(item.getSourceUrl()))
                .map(BidItem::getId)
                .forEach(selectedIds::add);
        if (!selectedIds.isEmpty()) {
            generateReportForSelection(result, selectedIds);
        }
        return result;
    }

    private QueryResult executeWithIntent(String question, SearchIntent intent, boolean saveTask,
            Consumer<List<BidItem>> itemBatchConsumer, BooleanSupplier cancelled) {
        long started = System.currentTimeMillis();
        AgentTrace trace = new AgentTrace();
        trace.addStep("理解用户问题", "RUNNING", "解析关键词、区域、时间和频率。");

        List<SourceCrawler> crawlers = sourceHarvestService.enabledCrawlers(intent);
        AgentModelResponse modelResponse = agentModelService.analyze(question, intent,
                crawlers.stream().map(SourceCrawler::sourceName).toList());
        agentModelService.applyToIntent(modelResponse, intent);

        trace.addStep("模型 Agent 理解", modelResponse.isModelUsed() ? "SUCCESS" : "WARNING",
                modelResponse.isModelUsed()
                        ? "Agent 已生成需求摘要、搜索词和执行计划。"
                        : modelResponse.getFallbackReason());
        trace.addStep("解析四要素", "SUCCESS", "关键词：" + intent.getKeyword()
                + "，地区：" + displayRegion(intent)
                + "，时间：" + intent.getStartTime().toLocalDate() + " 至 " + intent.getEndTime().toLocalDate()
                + "，频率：" + (intent.getScheduleRule() == null ? "立即执行" : intent.getScheduleRule().getType()));

        AgentPlan plan = agentOrchestrator.createPlan(question, intent, crawlers, modelResponse);
        trace.addStep("生成检索计划", "SUCCESS", "已选择 " + crawlers.size() + " 个固定来源，并生成 "
                + plan.getSearchQueries().size() + " 个全网搜索词。");

        HarvestResult harvest = sourceHarvestService.harvest(intent, plan, trace, itemBatchConsumer, cancelled);
        CurationResult curation = resultCurationService.curate(intent, harvest.getRawItems(), trace);

        QueryResult result = new QueryResult();
        result.setAgentPlan(plan);
        result.setAgentTrace(trace);
        result.setIntent(intent);
        result.setSourceStatuses(harvest.getSourceStatuses());
        result.setRawCount(harvest.getRawItems().size());
        result.setDuplicateCount(curation.getDuplicateCount());
        result.setIncrementalSkipped(curation.getIncrementalSkipped());
        result.setItems(new ArrayList<>(curation.getItems()));
        result.setCandidateItems(new ArrayList<>(curation.getCandidateItems()));
        result.setElapsedMs(System.currentTimeMillis() - started);
        result.setExpandedKeywords(queryExpansionService.expand(intent.getKeyword()));
        reconcileSourceStatusCounts(result);

        if (saveTask && intent.getScheduleRule() != null) {
            AggregationTask task = taskService.saveScheduledTask(question, intent);
            result.setScheduledTask(task);
            trace.addStep("保存定时任务", "SUCCESS", "已写入本地任务配置：" + task.getId());
        }

        return result;
    }

    public Path generateReportForSelection(QueryResult result, List<String> selectedItemIds) {
        if (result == null) {
            throw new IllegalArgumentException("请先完成一次查询。");
        }
        Set<String> selected = new HashSet<>(selectedItemIds == null ? List.of() : selectedItemIds);
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("请先勾选要写入 Word 的信息。");
        }
        List<BidItem> referenceItems = new ArrayList<>();
        referenceItems.addAll(result.getItems());
        referenceItems.addAll(result.getCandidateItems());
        List<BidItem> selectedItems = referenceItems.stream()
                .filter(item -> matchesSelectedItem(item, selected))
                .filter(item -> !isSearchEngineUrl(item.getSourceUrl()))
                .limit(50)
                .toList();
        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("请先勾选要写入 Word 的信息。");
        }
        QueryResult reportResult = copyForReport(result, selectedItems);
        Path path = wordReportService.generate(reportResult);
        result.setReportPath(path);
        resultCurationService.markDelivered(result.getIntent(), selectedItems);
        return path;
    }

    private boolean matchesSelectedItem(BidItem item, Set<String> selected) {
        if (item == null || selected == null || selected.isEmpty()) {
            return false;
        }
        return selected.contains(safe(item.getId()))
                || selected.contains(safe(item.getSourceUrl()))
                || selected.contains(safe(item.getTitle()))
                || selected.contains(stableItemKey(item));
    }

    private String stableItemKey(BidItem item) {
        return hash(safe(item.getTitle()) + "|" + safe(item.getSourceUrl()));
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private QueryResult copyForReport(QueryResult source, List<BidItem> selectedItems) {
        QueryResult copy = new QueryResult();
        copy.setAgentPlan(source.getAgentPlan());
        copy.setAgentTrace(source.getAgentTrace());
        copy.setIntent(source.getIntent());
        copy.setSourceStatuses(source.getSourceStatuses());
        copy.setRawCount(source.getRawCount());
        copy.setDuplicateCount(source.getDuplicateCount());
        copy.setIncrementalSkipped(source.getIncrementalSkipped());
        copy.setScheduledTask(source.getScheduledTask());
        copy.setElapsedMs(source.getElapsedMs());
        copy.setExpandedKeywords(source.getExpandedKeywords());
        copy.setItems(selectedItems);
        copy.setCandidateItems(List.of());
        return copy;
    }

    private void reconcileSourceStatusCounts(QueryResult result) {
        List<BidItem> finalItems = result.getItems();
        List<BidItem> candidateItems = result.getCandidateItems();
        for (SourceStatus status : result.getSourceStatuses()) {
            int count = (int) finalItems.stream().filter(item -> belongsToStatus(item, status)).count();
            int candidateCount = (int) candidateItems.stream().filter(item -> belongsToStatus(item, status)).count();
            if (status.isSuccess()) {
                status.setSelectedCount(count + candidateCount);
                status.setSuccess(count + candidateCount > 0);
                if (count + candidateCount == 0 && (status.getWarning() == null || status.getWarning().isBlank())) {
                    status.setWarning("已处理但无命中：候选已被清洗、去重或增量历史过滤。");
                }
            }
        }
    }

    private boolean belongsToStatus(BidItem item, SourceStatus status) {
        String itemSource = safe(item.getSourceName());
        String itemType = safe(item.getSourceType());
        String statusName = safe(status.getSourceName());
        String statusType = safe(status.getSourceType());
        if (itemType.contains("搜索") || itemType.contains("候选") || itemType.contains("检索入口")) {
            return statusName.contains("全网搜索") || statusType.contains("搜索")
                    || statusType.contains("候选") || statusType.contains("检索入口");
        }
        return !itemSource.isBlank() && (statusName.contains(itemSource) || itemSource.contains(statusName));
    }

    private String displayRegion(SearchIntent intent) {
        return !safe(intent.getCity()).isBlank() ? intent.getCity() : safe(intent.getProvince());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isSearchEngineUrl(String raw) {
        String url = safe(raw).toLowerCase();
        return url.contains("bing.com/search") || url.contains("google.com/search");
    }
}
