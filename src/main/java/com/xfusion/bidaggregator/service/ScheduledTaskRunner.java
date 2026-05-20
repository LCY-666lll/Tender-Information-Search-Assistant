package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.AggregationTask;
import com.xfusion.bidaggregator.model.QueryResult;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTaskRunner {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunner.class);

    private final TaskService taskService;
    private final BidAggregationService aggregationService;
    private final ScheduledResultStore scheduledResultStore;

    public ScheduledTaskRunner(TaskService taskService, BidAggregationService aggregationService,
            ScheduledResultStore scheduledResultStore) {
        this.taskService = taskService;
        this.aggregationService = aggregationService;
        this.scheduledResultStore = scheduledResultStore;
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    public void runDueTasks() {
        for (AggregationTask task : taskService.dueTasks(LocalDateTime.now())) {
            taskService.markRunning(task.getId());
            try {
                log.info("Running scheduled bid aggregation task: {}", task.getQuestion());
                QueryResult result = aggregationService.executeScheduledTask(task);
                scheduledResultStore.put(task.getId(), result);
                taskService.recordRun(task, result);
            } catch (Exception ignored) {
                log.warn("Scheduled bid aggregation task failed: {}", task.getQuestion(), ignored);
                taskService.recordFailure(task.getId(), "自动执行失败：" + ignored.getClass().getSimpleName());
            }
        }
    }
}
