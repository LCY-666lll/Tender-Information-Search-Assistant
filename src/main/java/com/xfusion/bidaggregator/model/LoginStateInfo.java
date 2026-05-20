package com.xfusion.bidaggregator.model;

import java.time.LocalDateTime;

public class LoginStateInfo {
    private String sourceName;
    private String stateFile;
    private boolean available;
    private LocalDateTime savedAt;
    private String message;

    public LoginStateInfo() {
    }

    public LoginStateInfo(String sourceName, String stateFile, boolean available, LocalDateTime savedAt, String message) {
        this.sourceName = sourceName;
        this.stateFile = stateFile;
        this.available = available;
        this.savedAt = savedAt;
        this.message = message;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getStateFile() {
        return stateFile;
    }

    public void setStateFile(String stateFile) {
        this.stateFile = stateFile;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
