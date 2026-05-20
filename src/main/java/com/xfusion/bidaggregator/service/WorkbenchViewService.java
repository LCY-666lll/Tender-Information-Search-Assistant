package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.AggregationTask;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.LoginStateInfo;
import com.xfusion.bidaggregator.model.QueryResult;
import com.xfusion.bidaggregator.model.SourceStatus;
import com.xfusion.bidaggregator.model.WorkbenchView;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkbenchViewService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public WorkbenchView create(QueryResult result, List<LoginStateInfo> loginStates,
            List<AggregationTask> tasks, int configuredSourceCount) {
        WorkbenchView view = new WorkbenchView();
        boolean loginReady = !loginStates.isEmpty() && loginStates.get(0).isAvailable();
        view.setLoginSummary(loginReady ? "登录态已保存" : "待保存登录态");
        view.setSubscriptionRows(subscriptionRows(tasks));

        if (result == null) {
            view.setSourceSummary(configuredSourceCount + " 个来源待调度");
            view.setWordSummary("待生成");
            view.setStages(defaultStages(false));
            view.setScoreBadges(List.of(
                    new WorkbenchView.WorkbenchBadge("意图解析", "待查询", "muted"),
                    new WorkbenchView.WorkbenchBadge("多来源", configuredSourceCount + " 个配置", "info"),
                    new WorkbenchView.WorkbenchBadge("登录态", loginReady ? "已保存" : "待保存", loginReady ? "ok" : "warn"),
                    new WorkbenchView.WorkbenchBadge("Word", "待生成", "muted")
            ));
            return view;
        }

        view.setHasResult(true);
        view.setHeroCount(result.getItems().size() + " 条");
        view.setHeroCountHint(result.getUserSummary() + candidateHint(result));
        view.setSourceSummary(result.getSourceStatuses().size() + " 个来源已处理");
        view.setWordSummary(result.getReportStatusText());
        view.setWordFilename(result.getReportFilename());
        view.setQuestionSummary(result.getIntent().getKeyword() + " / " + displayRegion(result)
                + " / " + result.getFrequencyText());
        view.setStages(resultStages(result, loginReady));
        view.setScoreBadges(List.of(
                new WorkbenchView.WorkbenchBadge("意图解析", result.getIntent().getKeyword() + " / " + displayRegion(result), "ok"),
                new WorkbenchView.WorkbenchBadge("来源处理", result.getSourceStatuses().size() + " 个", "info"),
                new WorkbenchView.WorkbenchBadge("登录态", loginReady ? "已保存" : "待保存", loginReady ? "ok" : "warn"),
                new WorkbenchView.WorkbenchBadge("清洗去重", result.getIncrementalText(), "info"),
                new WorkbenchView.WorkbenchBadge("Word", result.getReportStatusText(), result.isWordGenerated() ? "ok" : "warn"),
                new WorkbenchView.WorkbenchBadge("耗时", result.getElapsedText(), "info")
        ));
        view.setResultCards(result.getItems().stream().limit(12).map(this::card).toList());
        view.setCandidateCards(result.getCandidateItems().stream().limit(12).map(this::candidateCard).toList());
        view.setSourceRows(result.getSourceStatuses().stream().map(this::sourceRow).toList());
        return view;
    }

    private String candidateHint(QueryResult result) {
        if (result.getCandidateItems() == null || result.getCandidateItems().isEmpty()) {
            return "";
        }
        return " 另发现 " + result.getCandidateItems().size() + " 条候选线索，可在页面下方打开原文核验。";
    }

    private List<WorkbenchView.WorkbenchStage> defaultStages(boolean done) {
        String status = done ? "已完成" : "待执行";
        String tone = done ? "ok" : "muted";
        return List.of(
                new WorkbenchView.WorkbenchStage("理解需求", status, tone),
                new WorkbenchView.WorkbenchStage("规划来源", status, tone),
                new WorkbenchView.WorkbenchStage("采集公告", status, tone),
                new WorkbenchView.WorkbenchStage("清洗去重", status, tone),
                new WorkbenchView.WorkbenchStage("风险识别", status, tone),
                new WorkbenchView.WorkbenchStage("Word 简报", status, tone)
        );
    }

    private List<WorkbenchView.WorkbenchStage> resultStages(QueryResult result, boolean loginReady) {
        return List.of(
                new WorkbenchView.WorkbenchStage("理解需求", "已完成", "ok"),
                new WorkbenchView.WorkbenchStage("规划来源", "已完成", "ok"),
                new WorkbenchView.WorkbenchStage("采集公告", result.getSourceAvailable() > 0 ? "部分成功" : "已处理", "info"),
                new WorkbenchView.WorkbenchStage("清洗去重", "已完成", "ok"),
                new WorkbenchView.WorkbenchStage("登录态", loginReady ? "已保存" : "待登录", loginReady ? "ok" : "warn"),
                new WorkbenchView.WorkbenchStage("Word 简报", result.isWordGenerated() ? "已生成" : "待生成", result.isWordGenerated() ? "ok" : "warn")
        );
    }

    private WorkbenchView.WorkbenchCard card(BidItem item) {
        String time = item.getPublishTime() == null ? "发布时间待核对" : TIME.format(item.getPublishTime());
        return new WorkbenchView.WorkbenchCard(
                item.getTitle(),
                time + " / " + item.getSourceName(),
                item.getDisplaySummary(),
                item.getRiskSummary(),
                item.getAttachmentLinks() == null || item.getAttachmentLinks().isEmpty()
                        ? "附件：原文核对" : "附件：" + item.getAttachmentLinks().size() + " 个",
                item.getSourceUrl()
        );
    }

    private WorkbenchView.WorkbenchCard candidateCard(BidItem item) {
        String time = item.getPublishTime() == null ? "发布时间待核验" : TIME.format(item.getPublishTime());
        return new WorkbenchView.WorkbenchCard(
                item.getTitle(),
                time + " / " + item.getSourceName(),
                item.getDisplaySummary(),
                "候选线索：需打开原文确认发布时间、正文和附件",
                "不计入有效公告，不写入 Word 正文",
                item.getSourceUrl()
        );
    }

    private WorkbenchView.WorkbenchRow sourceRow(SourceStatus status) {
        return new WorkbenchView.WorkbenchRow(
                status.getSourceName(),
                status.getSourceTypeLabel() + " / " + status.getFetchedCount() + " 抓取 / " + status.getSelectedCount() + " 入选",
                status.getStatusLabel(),
                status.getUserMessage()
        );
    }

    private List<WorkbenchView.WorkbenchRow> subscriptionRows(List<AggregationTask> tasks) {
        List<WorkbenchView.WorkbenchRow> rows = new ArrayList<>();
        for (AggregationTask task : tasks) {
            String next = task.getNextRunAt() == null ? "下次检查待定" : "下次检查 " + TIME.format(task.getNextRunAt());
            String status = task.isActive() ? "运行中" : "已暂停";
            rows.add(new WorkbenchView.WorkbenchRow(task.getQuestion(), next, status,
                    "上次新增 " + task.getLastNewCount() + "，跳过 " + task.getLastSkippedCount()));
        }
        return rows;
    }

    private String displayRegion(QueryResult result) {
        if (result.getIntent().getCity() != null && !result.getIntent().getCity().isBlank()) {
            return result.getIntent().getCity();
        }
        return result.getIntent().getProvince();
    }
}
