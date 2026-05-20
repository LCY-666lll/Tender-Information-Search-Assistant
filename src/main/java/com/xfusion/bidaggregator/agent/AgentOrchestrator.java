package com.xfusion.bidaggregator.agent;

import com.xfusion.bidaggregator.crawler.SourceCrawler;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {
    public AgentPlan createPlan(String question, SearchIntent intent, List<SourceCrawler> crawlers,
            AgentModelResponse modelResponse) {
        AgentPlan plan = new AgentPlan();
        plan.setUserQuestion(question);
        plan.setIntent(intent);
        plan.setAgentMode(modelResponse != null && modelResponse.isModelUsed() ? AgentMode.LLM_ENHANCED : AgentMode.RULE);
        plan.setSelectedSources(crawlers.stream().map(SourceCrawler::sourceName).toList());
        plan.setUserIntentSummary(modelResponse == null ? null : modelResponse.getUserIntentSummary());
        plan.setSearchQueries(modelResponse == null ? List.of() : modelResponse.getSearchQueries());
        plan.setNeedAuthenticatedSource(modelResponse != null && modelResponse.isNeedAuthenticatedSource());
        plan.setModelFallbackReason(modelResponse == null ? null : modelResponse.getFallbackReason());
        plan.setPlanSteps(modelResponse != null && !modelResponse.getPlanSteps().isEmpty()
                ? modelResponse.getPlanSteps()
                : List.of(
                        "理解用户自然语言任务，抽取关键词、地区、时间范围和频率。",
                        "生成全网搜索查询词，并选择固定来源与登录态来源。",
                        "调度各来源抓取列表与详情，单来源失败只记录 warning。",
                        "清洗、过滤、去重并做 history 增量检查。",
                        "生成 Word 情报报告，所有事实以来源链接原文为准。"));
        plan.setFallbackStrategies(List.of(
                "模型 Agent 未配置或失败时，自动回退规则解析和固定来源抓取。",
                "任一来源失败不影响整体报告生成。",
                "登录态来源不可用时，提示重新登录并降级为公开来源报告。",
                "全网搜索不稳定时，仅保留固定来源 crawler 结果。"));
        return plan;
    }
}
