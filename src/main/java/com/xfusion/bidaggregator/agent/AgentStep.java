package com.xfusion.bidaggregator.agent;

import java.time.LocalDateTime;

public class AgentStep {
    private String name;
    private String status;
    private String message;
    private LocalDateTime createdAt;

    public AgentStep() {
    }

    public AgentStep(String name, String status, String message, LocalDateTime createdAt) {
        this.name = name;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
