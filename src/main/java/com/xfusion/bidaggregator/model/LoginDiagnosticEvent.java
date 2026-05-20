package com.xfusion.bidaggregator.model;

import java.time.LocalDateTime;

public class LoginDiagnosticEvent {
    private LocalDateTime time = LocalDateTime.now();
    private String event;
    private String status;
    private String message;

    public LoginDiagnosticEvent() {
    }

    public LoginDiagnosticEvent(String event, String status, String message) {
        this.event = event;
        this.status = status;
        this.message = message;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
