package com.xfusion.bidaggregator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class AggregationTask {
    private String id;
    private String question;
    private SearchIntent intent;
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private String lastReportPath;
    private int lastNewCount;
    private int lastSkippedCount;
    private boolean incrementalOnly = true;
    private boolean running;
    private String lastRunStatus;
    private String lastRunMessage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public SearchIntent getIntent() {
        return intent;
    }

    public void setIntent(SearchIntent intent) {
        this.intent = intent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getLastReportPath() {
        return lastReportPath;
    }

    public void setLastReportPath(String lastReportPath) {
        this.lastReportPath = lastReportPath;
    }

    public int getLastNewCount() {
        return lastNewCount;
    }

    public void setLastNewCount(int lastNewCount) {
        this.lastNewCount = lastNewCount;
    }

    public int getLastSkippedCount() {
        return lastSkippedCount;
    }

    public void setLastSkippedCount(int lastSkippedCount) {
        this.lastSkippedCount = lastSkippedCount;
    }

    public boolean isIncrementalOnly() {
        return incrementalOnly;
    }

    public void setIncrementalOnly(boolean incrementalOnly) {
        this.incrementalOnly = incrementalOnly;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public void setLastRunStatus(String lastRunStatus) {
        this.lastRunStatus = lastRunStatus;
    }

    public String getLastRunMessage() {
        return lastRunMessage;
    }

    public void setLastRunMessage(String lastRunMessage) {
        this.lastRunMessage = lastRunMessage;
    }

    @JsonIgnore
    public String getLastReportFilename() {
        if (lastReportPath == null || lastReportPath.isBlank()) {
            return null;
        }
        return Path.of(lastReportPath).getFileName().toString();
    }
}
