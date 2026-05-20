package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.QueryResult;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class SearchJobService {
    public enum Status {
        RUNNING, STOPPING, COMPLETED, STOPPED, FAILED
    }

    private final BidAggregationService aggregationService;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "bid-search-job");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, SearchJob> jobs = new ConcurrentHashMap<>();

    public SearchJobService(BidAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    public SearchJob start(String question) {
        String id = UUID.randomUUID().toString();
        SearchJob job = new SearchJob(id, question);
        jobs.put(id, job);
        CompletableFuture.runAsync(() -> runJob(job), executor);
        return job;
    }

    public SearchJob get(String id) {
        return jobs.get(id);
    }

    public void stop(String id) {
        SearchJob job = jobs.get(id);
        if (job != null && job.status == Status.RUNNING) {
            job.cancelled.set(true);
            job.status = Status.STOPPING;
        }
    }

    private void runJob(SearchJob job) {
        try {
            QueryResult result = aggregationService.executeStreaming(job.question, job::addBatch,
                    () -> job.cancelled.get());
            job.setResult(result);
            job.status = job.cancelled.get() ? Status.STOPPED : Status.COMPLETED;
        } catch (Exception ex) {
            job.status = Status.FAILED;
            job.error = ex.getMessage();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public static class SearchJob {
        private final String id;
        private final String question;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Map<String, BidItem> partialItems = new LinkedHashMap<>();
        private volatile Status status = Status.RUNNING;
        private volatile QueryResult result;
        private volatile String error;

        SearchJob(String id, String question) {
            this.id = id;
            this.question = question;
        }

        public synchronized void addBatch(List<BidItem> items) {
            for (BidItem item : items) {
                ensureId(item, partialItems.size());
                if (!isSearchEngineUrl(item.getSourceUrl())) {
                    partialItems.putIfAbsent(item.getId(), item);
                }
                if (partialItems.size() >= 50) {
                    break;
                }
            }
        }

        public synchronized List<BidItem> items() {
            if (result != null) {
                List<BidItem> finalItems = new ArrayList<>();
                finalItems.addAll(result.getItems());
                finalItems.addAll(result.getCandidateItems());
                for (int i = 0; i < finalItems.size(); i++) {
                    ensureId(finalItems.get(i), i);
                }
                return finalItems.stream()
                        .filter(item -> !isSearchEngineUrl(item.getSourceUrl()))
                        .limit(50)
                        .toList();
            }
            return partialItems.values().stream()
                    .filter(item -> !isSearchEngineUrl(item.getSourceUrl()))
                    .limit(50)
                    .toList();
        }

        public synchronized void setResult(QueryResult result) {
            this.result = result;
            List<BidItem> finalItems = new ArrayList<>();
            finalItems.addAll(result.getItems());
            finalItems.addAll(result.getCandidateItems());
            for (int i = 0; i < finalItems.size(); i++) {
                ensureId(finalItems.get(i), i);
            }
        }

        public String getId() {
            return id;
        }

        public String getQuestion() {
            return question;
        }

        public Status getStatus() {
            return status;
        }

        public QueryResult getResult() {
            return result;
        }

        public String getError() {
            return error;
        }

        public boolean isDone() {
            return status == Status.COMPLETED || status == Status.STOPPED || status == Status.FAILED;
        }

        private static void ensureId(BidItem item, int index) {
            if (item.getId() == null || item.getId().isBlank()) {
                item.setId("ref-" + hash(safe(item.getTitle()) + "|" + safe(item.getSourceUrl()) + "|" + index));
            }
        }

        private static String hash(String raw) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
            } catch (Exception ex) {
                return Integer.toHexString(raw.hashCode());
            }
        }

        private static String safe(String raw) {
            return raw == null ? "" : raw;
        }

        private static boolean isSearchEngineUrl(String raw) {
            String url = safe(raw).toLowerCase();
            return url.contains("bing.com/search") || url.contains("google.com/search");
        }
    }
}
