package com.xfusion.bidaggregator.agent;

import com.xfusion.bidaggregator.model.SearchIntent;
import java.util.ArrayList;
import java.util.List;

public class AgentPlan {
    private String userQuestion;
    private SearchIntent intent;
    private List<String> selectedSources = new ArrayList<>();
    private List<String> planSteps = new ArrayList<>();
    private List<String> fallbackStrategies = new ArrayList<>();
    private AgentMode agentMode = AgentMode.RULE;
    private String userIntentSummary;
    private List<String> searchQueries = new ArrayList<>();
    private boolean needAuthenticatedSource;
    private String modelFallbackReason;

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public SearchIntent getIntent() {
        return intent;
    }

    public void setIntent(SearchIntent intent) {
        this.intent = intent;
    }

    public List<String> getSelectedSources() {
        return selectedSources;
    }

    public void setSelectedSources(List<String> selectedSources) {
        this.selectedSources = selectedSources;
    }

    public List<String> getPlanSteps() {
        return planSteps;
    }

    public void setPlanSteps(List<String> planSteps) {
        this.planSteps = planSteps;
    }

    public List<String> getFallbackStrategies() {
        return fallbackStrategies;
    }

    public void setFallbackStrategies(List<String> fallbackStrategies) {
        this.fallbackStrategies = fallbackStrategies;
    }

    public AgentMode getAgentMode() {
        return agentMode;
    }

    public void setAgentMode(AgentMode agentMode) {
        this.agentMode = agentMode;
    }

    public String getUserIntentSummary() {
        return userIntentSummary;
    }

    public void setUserIntentSummary(String userIntentSummary) {
        this.userIntentSummary = userIntentSummary;
    }

    public List<String> getSearchQueries() {
        return searchQueries;
    }

    public void setSearchQueries(List<String> searchQueries) {
        this.searchQueries = searchQueries == null ? new ArrayList<>() : searchQueries;
    }

    public boolean isNeedAuthenticatedSource() {
        return needAuthenticatedSource;
    }

    public void setNeedAuthenticatedSource(boolean needAuthenticatedSource) {
        this.needAuthenticatedSource = needAuthenticatedSource;
    }

    public String getModelFallbackReason() {
        return modelFallbackReason;
    }

    public void setModelFallbackReason(String modelFallbackReason) {
        this.modelFallbackReason = modelFallbackReason;
    }

    public String getAgentModeLabel() {
        return agentMode == AgentMode.LLM_ENHANCED ? "智能模型规划已启用" : "规则规划已启用";
    }
}
