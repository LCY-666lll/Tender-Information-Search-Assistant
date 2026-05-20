package com.xfusion.bidaggregator.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentModelResponse {
    private boolean modelUsed;
    private String modelName;
    private String fallbackReason;
    private String userIntentSummary;
    private String keyword;
    private String province;
    private String city;
    private String scheduleText;
    private boolean needAuthenticatedSource;
    private List<String> searchQueries = new ArrayList<>();
    private List<String> planSteps = new ArrayList<>();
    private List<String> riskNotes = new ArrayList<>();

    public static AgentModelResponse fallback(String reason) {
        AgentModelResponse response = new AgentModelResponse();
        response.setModelUsed(false);
        response.setFallbackReason(reason);
        return response;
    }

    public boolean isModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(boolean modelUsed) {
        this.modelUsed = modelUsed;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getUserIntentSummary() {
        return userIntentSummary;
    }

    public void setUserIntentSummary(String userIntentSummary) {
        this.userIntentSummary = userIntentSummary;
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

    public String getScheduleText() {
        return scheduleText;
    }

    public void setScheduleText(String scheduleText) {
        this.scheduleText = scheduleText;
    }

    public boolean isNeedAuthenticatedSource() {
        return needAuthenticatedSource;
    }

    public void setNeedAuthenticatedSource(boolean needAuthenticatedSource) {
        this.needAuthenticatedSource = needAuthenticatedSource;
    }

    public List<String> getSearchQueries() {
        return searchQueries;
    }

    public void setSearchQueries(List<String> searchQueries) {
        this.searchQueries = searchQueries == null ? new ArrayList<>() : searchQueries;
    }

    public List<String> getPlanSteps() {
        return planSteps;
    }

    public void setPlanSteps(List<String> planSteps) {
        this.planSteps = planSteps == null ? new ArrayList<>() : planSteps;
    }

    public List<String> getRiskNotes() {
        return riskNotes;
    }

    public void setRiskNotes(List<String> riskNotes) {
        this.riskNotes = riskNotes == null ? new ArrayList<>() : riskNotes;
    }
}
