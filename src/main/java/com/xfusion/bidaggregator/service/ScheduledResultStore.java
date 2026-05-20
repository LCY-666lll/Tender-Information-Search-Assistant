package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.QueryResult;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ScheduledResultStore {
    private final Map<String, QueryResult> results = new ConcurrentHashMap<>();
    private volatile String latestTaskId;

    public void put(String taskId, QueryResult result) {
        if (taskId == null || taskId.isBlank() || result == null) {
            return;
        }
        results.put(taskId, result);
        latestTaskId = taskId;
    }

    public Optional<QueryResult> get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(taskId));
    }

    public Optional<QueryResult> latest() {
        return get(latestTaskId);
    }
}
