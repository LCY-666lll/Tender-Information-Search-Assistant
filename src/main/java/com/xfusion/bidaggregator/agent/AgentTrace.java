package com.xfusion.bidaggregator.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgentTrace {
    private final List<AgentStep> steps = new ArrayList<>();

    public void addStep(String name, String status, String message) {
        steps.add(new AgentStep(name, status, message, LocalDateTime.now()));
    }

    public List<AgentStep> getSteps() {
        return steps;
    }
}
