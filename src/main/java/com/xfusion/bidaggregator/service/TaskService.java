package com.xfusion.bidaggregator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.AggregationTask;
import com.xfusion.bidaggregator.model.QueryResult;
import com.xfusion.bidaggregator.model.ScheduleRule;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public TaskService(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.properties = properties;
    }

    public synchronized AggregationTask saveScheduledTask(String question, SearchIntent intent) {
        if (intent.getScheduleRule() == null) {
            return null;
        }
        List<AggregationTask> tasks = readTasks();
        String key = taskKey(question, intent);
        Optional<AggregationTask> existing = tasks.stream()
                .filter(task -> taskKey(task.getQuestion(), task.getIntent()).equals(key))
                .findFirst();
        AggregationTask task = existing.orElseGet(() -> {
            AggregationTask created = new AggregationTask();
            created.setId(UUID.randomUUID().toString());
            created.setCreatedAt(LocalDateTime.now());
            tasks.add(created);
            return created;
        });
        task.setQuestion(question);
        task.setIntent(intent);
        task.setActive(true);
        task.setIncrementalOnly(true);
        if (task.getNextRunAt() == null || existing.isEmpty()) {
            task.setNextRunAt(nextRunAfter(LocalDateTime.now(), intent.getScheduleRule()));
        }
        writeTasks(tasks);
        return task;
    }

    public synchronized AggregationTask saveEditableTask(String id, String question, SearchIntent intent,
            boolean incrementalOnly) {
        if (intent.getScheduleRule() == null) {
            return null;
        }
        List<AggregationTask> tasks = readTasks();
        AggregationTask task = tasks.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseGet(() -> {
                    AggregationTask created = new AggregationTask();
                    created.setId(UUID.randomUUID().toString());
                    created.setCreatedAt(LocalDateTime.now());
                    tasks.add(created);
                    return created;
                });
        task.setQuestion(question);
        task.setIntent(intent);
        task.setActive(true);
        task.setIncrementalOnly(incrementalOnly);
        task.setRunning(false);
        task.setLastRunStatus("SAVED");
        task.setLastRunMessage("已保存，等待自动执行。");
        task.setNextRunAt(nextRunAfter(LocalDateTime.now(), intent.getScheduleRule()));
        writeTasks(tasks);
        return task;
    }

    public synchronized List<AggregationTask> listTasks() {
        return readTasks().stream()
                .sorted(Comparator.comparing(AggregationTask::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public synchronized Optional<AggregationTask> findById(String id) {
        return readTasks().stream().filter(task -> task.getId().equals(id)).findFirst();
    }

    public synchronized Optional<AggregationTask> updateActive(String id, boolean active) {
        List<AggregationTask> tasks = readTasks();
        Optional<AggregationTask> updated = Optional.empty();
        for (AggregationTask task : tasks) {
            if (!task.getId().equals(id)) {
                continue;
            }
            task.setActive(active);
            if (active && task.getNextRunAt() == null && task.getIntent() != null
                    && task.getIntent().getScheduleRule() != null) {
                task.setNextRunAt(nextRunAfter(LocalDateTime.now(), task.getIntent().getScheduleRule()));
            }
            updated = Optional.of(task);
            break;
        }
        updated.ifPresent(task -> writeTasks(tasks));
        return updated;
    }

    public synchronized boolean deleteTask(String id) {
        List<AggregationTask> tasks = readTasks();
        boolean removed = tasks.removeIf(task -> task.getId().equals(id));
        if (removed) {
            writeTasks(tasks);
        }
        return removed;
    }

    public synchronized List<AggregationTask> dueTasks(LocalDateTime now) {
        return readTasks().stream()
                .filter(AggregationTask::isActive)
                .filter(task -> !task.isRunning())
                .filter(task -> task.getNextRunAt() != null && !task.getNextRunAt().isAfter(now))
                .toList();
    }

    public synchronized Optional<AggregationTask> markRunning(String id) {
        List<AggregationTask> tasks = readTasks();
        Optional<AggregationTask> updated = Optional.empty();
        for (AggregationTask task : tasks) {
            if (!task.getId().equals(id)) {
                continue;
            }
            task.setRunning(true);
            task.setLastRunStatus("RUNNING");
            task.setLastRunMessage("自动执行中，正在检索并生成报告。");
            updated = Optional.of(task);
            break;
        }
        updated.ifPresent(task -> writeTasks(tasks));
        return updated;
    }

    public synchronized void recordRun(AggregationTask task, QueryResult result) {
        List<AggregationTask> tasks = readTasks();
        LocalDateTime now = LocalDateTime.now();
        for (AggregationTask current : tasks) {
            if (!current.getId().equals(task.getId())) {
                continue;
            }
            current.setRunning(false);
            current.setLastRunAt(now);
            current.setLastNewCount(result.getItems().size());
            current.setLastSkippedCount(result.getIncrementalSkipped());
            current.setLastReportPath(result.getReportPath() == null ? null : result.getReportPath().toString());
            current.setLastRunStatus("COMPLETED");
            current.setLastRunMessage(result.getReportPath() == null
                    ? "自动执行完成，本次没有可写入 Word 的新增信息。"
                    : "自动执行完成，已生成 Word 文件：" + result.getReportFilename());
            if (current.getIntent() != null) {
                ScheduleRule rule = current.getIntent().getScheduleRule();
                if (rule != null && rule.getType() == ScheduleRule.Type.ONCE) {
                    current.setActive(false);
                    current.setNextRunAt(null);
                } else {
                    current.setNextRunAt(nextRunAfter(now, rule));
                }
            }
        }
        writeTasks(tasks);
    }

    public synchronized void recordFailure(String id, String message) {
        List<AggregationTask> tasks = readTasks();
        LocalDateTime now = LocalDateTime.now();
        for (AggregationTask current : tasks) {
            if (!current.getId().equals(id)) {
                continue;
            }
            current.setRunning(false);
            current.setLastRunAt(now);
            current.setLastRunStatus("FAILED");
            current.setLastRunMessage(message == null || message.isBlank() ? "自动执行失败。" : message);
            if (current.getIntent() != null && current.getIntent().getScheduleRule() != null) {
                ScheduleRule rule = current.getIntent().getScheduleRule();
                if (rule.getType() == ScheduleRule.Type.ONCE) {
                    current.setActive(false);
                    current.setNextRunAt(null);
                } else {
                    current.setNextRunAt(nextRunAfter(now, rule));
                }
            }
            break;
        }
        writeTasks(tasks);
    }

    private List<AggregationTask> readTasks() {
        Path path = taskPath();
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<List<AggregationTask>>() {});
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private void writeTasks(List<AggregationTask> tasks) {
        try {
            Path path = taskPath();
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), tasks);
        } catch (Exception ex) {
            throw new IllegalStateException("写入定时任务失败", ex);
        }
    }

    private Path taskPath() {
        return Path.of(properties.getDataDir(), "tasks.json");
    }

    private LocalDateTime nextRunAfter(LocalDateTime now, ScheduleRule rule) {
        if (rule == null) {
            return null;
        }
        LocalDateTime candidate = now.toLocalDate().atTime(rule.getTime());
        if (rule.getType() == ScheduleRule.Type.ONCE) {
            return candidate.isAfter(now) ? candidate : now.plusMinutes(1);
        }
        if (rule.getType() == ScheduleRule.Type.WEEKLY) {
            int target = rule.getDayOfWeek() == null ? 1 : rule.getDayOfWeek().getValue();
            int delta = target - now.getDayOfWeek().getValue();
            if (delta < 0 || (delta == 0 && !candidate.isAfter(now))) {
                delta += 7;
            }
            return now.toLocalDate().plusDays(delta).atTime(rule.getTime());
        }
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private String taskKey(String question, SearchIntent intent) {
        String schedule = intent == null || intent.getScheduleRule() == null
                ? "none"
                : intent.getScheduleRule().getType() + "@" + intent.getScheduleRule().getTime();
        String base = (question == null ? "" : question) + "|" + schedule;
        return base.replaceAll("\\s+", "");
    }
}
