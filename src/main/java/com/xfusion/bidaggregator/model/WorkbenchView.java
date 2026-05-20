package com.xfusion.bidaggregator.model;

import java.util.ArrayList;
import java.util.List;

public class WorkbenchView {
    private boolean hasResult;
    private String heroCount = "待查询";
    private String heroCountHint = "提交问题后展示有效公告";
    private String sourceSummary = "来源待调度";
    private String loginSummary = "待检查";
    private String wordSummary = "待生成";
    private String wordFilename;
    private String questionSummary = "等待输入自然语言需求";
    private List<WorkbenchBadge> scoreBadges = new ArrayList<>();
    private List<WorkbenchStage> stages = new ArrayList<>();
    private List<WorkbenchCard> resultCards = new ArrayList<>();
    private List<WorkbenchCard> candidateCards = new ArrayList<>();
    private List<WorkbenchRow> sourceRows = new ArrayList<>();
    private List<WorkbenchRow> subscriptionRows = new ArrayList<>();

    public boolean isHasResult() {
        return hasResult;
    }

    public void setHasResult(boolean hasResult) {
        this.hasResult = hasResult;
    }

    public String getHeroCount() {
        return heroCount;
    }

    public void setHeroCount(String heroCount) {
        this.heroCount = heroCount;
    }

    public String getHeroCountHint() {
        return heroCountHint;
    }

    public void setHeroCountHint(String heroCountHint) {
        this.heroCountHint = heroCountHint;
    }

    public String getSourceSummary() {
        return sourceSummary;
    }

    public void setSourceSummary(String sourceSummary) {
        this.sourceSummary = sourceSummary;
    }

    public String getLoginSummary() {
        return loginSummary;
    }

    public void setLoginSummary(String loginSummary) {
        this.loginSummary = loginSummary;
    }

    public String getWordSummary() {
        return wordSummary;
    }

    public void setWordSummary(String wordSummary) {
        this.wordSummary = wordSummary;
    }

    public String getWordFilename() {
        return wordFilename;
    }

    public void setWordFilename(String wordFilename) {
        this.wordFilename = wordFilename;
    }

    public String getQuestionSummary() {
        return questionSummary;
    }

    public void setQuestionSummary(String questionSummary) {
        this.questionSummary = questionSummary;
    }

    public List<WorkbenchBadge> getScoreBadges() {
        return scoreBadges;
    }

    public void setScoreBadges(List<WorkbenchBadge> scoreBadges) {
        this.scoreBadges = scoreBadges;
    }

    public List<WorkbenchStage> getStages() {
        return stages;
    }

    public void setStages(List<WorkbenchStage> stages) {
        this.stages = stages;
    }

    public List<WorkbenchCard> getResultCards() {
        return resultCards;
    }

    public void setResultCards(List<WorkbenchCard> resultCards) {
        this.resultCards = resultCards;
    }

    public List<WorkbenchCard> getCandidateCards() {
        return candidateCards;
    }

    public void setCandidateCards(List<WorkbenchCard> candidateCards) {
        this.candidateCards = candidateCards;
    }

    public List<WorkbenchRow> getSourceRows() {
        return sourceRows;
    }

    public void setSourceRows(List<WorkbenchRow> sourceRows) {
        this.sourceRows = sourceRows;
    }

    public List<WorkbenchRow> getSubscriptionRows() {
        return subscriptionRows;
    }

    public void setSubscriptionRows(List<WorkbenchRow> subscriptionRows) {
        this.subscriptionRows = subscriptionRows;
    }

    public record WorkbenchBadge(String label, String value, String tone) {
    }

    public record WorkbenchStage(String name, String status, String tone) {
    }

    public record WorkbenchCard(String title, String meta, String summary, String risk, String attachmentStatus,
            String link) {
    }

    public record WorkbenchRow(String title, String meta, String status, String note) {
    }
}
