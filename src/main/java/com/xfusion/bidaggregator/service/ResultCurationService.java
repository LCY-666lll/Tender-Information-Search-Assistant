package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.agent.AgentTrace;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.CurationResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ResultCurationService {
    private final CleanerDeduplicator cleanerDeduplicator;
    private final RiskAnalyzer riskAnalyzer;
    private final HistoryService historyService;
    private final InvalidPageFilter invalidPageFilter;

    public ResultCurationService(CleanerDeduplicator cleanerDeduplicator, RiskAnalyzer riskAnalyzer,
            HistoryService historyService, InvalidPageFilter invalidPageFilter) {
        this.cleanerDeduplicator = cleanerDeduplicator;
        this.riskAnalyzer = riskAnalyzer;
        this.historyService = historyService;
        this.invalidPageFilter = invalidPageFilter;
    }

    public CurationResult curate(SearchIntent intent, List<BidItem> rawItems, AgentTrace trace) {
        trace.addStep("清洗过滤与去重", "RUNNING", "过滤跳转页、导航页、弱相关候选，并合并重复公告。");
        List<BidItem> cleaned = new ArrayList<>(cleanerDeduplicator.cleanAndDeduplicate(rawItems, intent));
        List<BidItem> candidates = candidateLeads(intent, rawItems, cleaned);
        int duplicateCount = Math.max(rawItems.size() - cleaned.size(), 0);
        trace.addStep("清洗过滤与去重", "SUCCESS",
                "原始候选 " + rawItems.size() + " 条，有效公告 " + cleaned.size()
                        + " 条，过滤/合并 " + duplicateCount + " 条。");

        trace.addStep("风险识别", "RUNNING", "识别截止临近、附件缺失、变更澄清等业务风险。");
        cleaned.forEach(riskAnalyzer::analyze);
        long riskCount = cleaned.stream().filter(item -> !item.getRiskWarnings().isEmpty()).count();
        trace.addStep("风险识别", cleaned.isEmpty() ? "WARNING" : "SUCCESS",
                cleaned.isEmpty() ? "本次没有有效公告，风险识别跳过。" : "识别到 " + riskCount + " 条风险提示。");

        int skipped = 0;
        if (intent.getScheduleRule() != null) {
            trace.addStep("增量过滤", "RUNNING", "订阅任务只输出未推送过的新公告。");
            skipped = historyService.filterAlreadySent(intent, cleaned);
            trace.addStep("增量过滤", "SUCCESS", "已跳过历史公告 " + skipped + " 条。");
        }
        return new CurationResult(cleaned, candidates, duplicateCount, skipped);
    }

    private List<BidItem> candidateLeads(SearchIntent intent, List<BidItem> rawItems, List<BidItem> finalItems) {
        Map<String, BidItem> leads = new LinkedHashMap<>();
        for (BidItem item : rawItems) {
            if (isAlreadyFinal(item, finalItems) || !invalidPageFilter.isDisplayableCandidate(item, intent)) {
                continue;
            }
            String key = candidateKey(item);
            leads.putIfAbsent(key, item);
            if (leads.size() >= 10) {
                break;
            }
        }
        return leads.values().stream().toList();
    }

    private boolean isAlreadyFinal(BidItem candidate, List<BidItem> finalItems) {
        String candidateUrl = safe(candidate.getSourceUrl());
        String candidateTitle = safe(candidate.getTitle());
        for (BidItem item : finalItems) {
            if (!candidateUrl.isBlank() && candidateUrl.equals(safe(item.getSourceUrl()))) {
                return true;
            }
            if (!candidateTitle.isBlank() && candidateTitle.equals(safe(item.getTitle()))) {
                return true;
            }
        }
        return false;
    }

    private String candidateKey(BidItem item) {
        String url = safe(item.getSourceUrl());
        if (!url.isBlank()) {
            return url;
        }
        return safe(item.getTitle()) + "|" + safe(item.getSourceName());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public void markDelivered(SearchIntent intent, List<BidItem> deliveredItems) {
        if (intent.getScheduleRule() != null) {
            historyService.markSent(intent, deliveredItems);
        }
    }
}
