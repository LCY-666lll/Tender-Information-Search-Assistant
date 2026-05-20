package com.xfusion.bidaggregator.model;

import java.time.LocalDateTime;

public class SearchIntent {
    private String rawQuestion;
    private String keyword;
    private String province;
    private String city;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ScheduleRule scheduleRule;

    public String getRawQuestion() {
        return rawQuestion;
    }

    public void setRawQuestion(String rawQuestion) {
        this.rawQuestion = rawQuestion;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public ScheduleRule getScheduleRule() {
        return scheduleRule;
    }

    public void setScheduleRule(ScheduleRule scheduleRule) {
        this.scheduleRule = scheduleRule;
    }
}
