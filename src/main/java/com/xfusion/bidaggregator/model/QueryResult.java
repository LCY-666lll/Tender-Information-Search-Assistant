package com.xfusion.bidaggregator.model;

import com.xfusion.bidaggregator.agent.AgentPlan;
import com.xfusion.bidaggregator.agent.AgentTrace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    private AgentPlan agentPlan;
    private AgentTrace agentTrace;
    private SearchIntent intent;
    private List<SourceStatus> sourceStatuses = new ArrayList<>();
    private List<BidItem> items = new ArrayList<>();
    private List<BidItem> candidateItems = new ArrayList<>();
    private int rawCount;
    private int duplicateCount;
    private int incrementalSkipped;
    private AggregationTask scheduledTask;
    private Path reportPath;
    private long elapsedMs;
    private List<String> expandedKeywords = new ArrayList<>();

    public AgentPlan getAgentPlan() {
        return agentPlan;
    }

    public void setAgentPlan(AgentPlan agentPlan) {
        this.agentPlan = agentPlan;
    }

    public AgentTrace getAgentTrace() {
        return agentTrace;
    }

    public void setAgentTrace(AgentTrace agentTrace) {
        this.agentTrace = agentTrace;
    }

    public SearchIntent getIntent() {
        return intent;
    }

    public void setIntent(SearchIntent intent) {
        this.intent = intent;
    }

    public List<SourceStatus> getSourceStatuses() {
        return sourceStatuses;
    }

    public void setSourceStatuses(List<SourceStatus> sourceStatuses) {
        this.sourceStatuses = sourceStatuses;
    }

    public List<BidItem> getItems() {
        return items;
    }

    public void setItems(List<BidItem> items) {
        this.items = items;
    }

    public List<BidItem> getCandidateItems() {
        return candidateItems;
    }

    public void setCandidateItems(List<BidItem> candidateItems) {
        this.candidateItems = candidateItems == null ? new ArrayList<>() : candidateItems;
    }

    public int getRawCount() {
        return rawCount;
    }

    public void setRawCount(int rawCount) {
        this.rawCount = rawCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getIncrementalSkipped() {
        return incrementalSkipped;
    }

    public void setIncrementalSkipped(int incrementalSkipped) {
        this.incrementalSkipped = incrementalSkipped;
    }

    public AggregationTask getScheduledTask() {
        return scheduledTask;
    }

    public void setScheduledTask(AggregationTask scheduledTask) {
        this.scheduledTask = scheduledTask;
    }

    public Path getReportPath() {
        return reportPath;
    }

    public void setReportPath(Path reportPath) {
        this.reportPath = reportPath;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public List<String> getExpandedKeywords() {
        return expandedKeywords;
    }

    public void setExpandedKeywords(List<String> expandedKeywords) {
        this.expandedKeywords = expandedKeywords == null ? new ArrayList<>() : expandedKeywords;
    }

    public String getReportFilename() {
        return reportPath == null ? null : reportPath.getFileName().toString();
    }

    public long getSuccessfulSourceCount() {
        return sourceStatuses.stream().filter(SourceStatus::isSuccess).count();
    }

    public boolean isIntentParsed() {
        return intent != null && intent.getKeyword() != null && !intent.getKeyword().isBlank()
                && intent.getStartTime() != null && intent.getEndTime() != null;
    }

    public int getSourceTotal() {
        return sourceStatuses == null ? 0 : sourceStatuses.size();
    }

    public long getSourceAvailable() {
        return getSuccessfulSourceCount();
    }

    public String getLoginSourceStatus() {
        if (sourceStatuses == null || sourceStatuses.stream().noneMatch(SourceStatus::isNeedLogin)) {
            return "未配置登录来源";
        }
        boolean available = sourceStatuses.stream().anyMatch(status -> status.isNeedLogin() && status.isSuccess());
        return available ? "登录来源可用" : "登录来源待登录或不可用";
    }

    public int getValidAnnouncementCount() {
        return items == null ? 0 : items.size();
    }

    public int getCandidateLeadCount() {
        return candidateItems == null ? 0 : candidateItems.size();
    }

    public int getFilteredDuplicateCount() {
        return duplicateCount + incrementalSkipped;
    }

    public boolean isWordGenerated() {
        return getReportFilename() != null;
    }

    public boolean isScheduled() {
        return scheduledTask != null || (intent != null && intent.getScheduleRule() != null);
    }

    public boolean isIncrementalOnly() {
        return intent != null && intent.getScheduleRule() != null;
    }

    public long getWarningSourceCount() {
        return sourceStatuses.stream().filter(status -> !status.isSuccess() || status.getWarning() != null).count();
    }

    public boolean isNoNewItems() {
        return items.isEmpty() && incrementalSkipped > 0;
    }

    public String getUserSummary() {
        if (items.isEmpty() && incrementalSkipped > 0) {
            return "本次没有新增公告，系统已过滤 " + incrementalSkipped + " 条历史已见公告。";
        }
        if (items.isEmpty() && getCandidateLeadCount() > 0) {
            return "本次没有通过严格有效公告校验，但保留 " + getCandidateLeadCount()
                    + " 条可点击核验的候选线索。";
        }
        if (items.isEmpty()) {
            return "本次没有筛选出可展示公告，请换关键词、地区或先检查来源登录状态。";
        }
        return "本次筛选出 " + items.size() + " 条可查看公告。";
    }

    public String getSourceAvailabilityText() {
        return getSuccessfulSourceCount() + "/" + sourceStatuses.size();
    }

    public String getReportStatusText() {
        return getReportFilename() == null ? "未生成" : "已生成";
    }

    public String getScheduleStatusText() {
        if (scheduledTask == null) {
            return intent != null && intent.getScheduleRule() != null ? "已识别提醒，待保存" : "立即查询";
        }
        return "已创建订阅";
    }

    public String getIncrementalText() {
        if (incrementalSkipped > 0) {
            return "已跳过历史 " + incrementalSkipped + " 条";
        }
        if (scheduledTask != null || (intent != null && intent.getScheduleRule() != null)) {
            return "首次执行会写入历史";
        }
        return "本次即时查询";
    }

    public String getElapsedText() {
        if (elapsedMs < 1000) {
            return elapsedMs + " ms";
        }
        return String.format("%.1f 秒", elapsedMs / 1000.0);
    }

    public String getIntentTimeText() {
        if (intent == null || intent.getStartTime() == null || intent.getEndTime() == null) {
            return "-";
        }
        return intent.getStartTime().toLocalDate() + " 至 " + intent.getEndTime().toLocalDate();
    }

    public String getFrequencyText() {
        if (intent == null || intent.getScheduleRule() == null) {
            return "立即执行";
        }
        return intent.getScheduleRule().getDisplayText();
    }
}
