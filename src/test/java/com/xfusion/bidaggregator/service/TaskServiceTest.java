package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.AggregationTask;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.QueryResult;
import com.xfusion.bidaggregator.model.ScheduleRule;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void canPauseResumeAndDeleteSubscriptionTask() {
        TaskService service = new TaskService(new ObjectMapper().findAndRegisterModules(), properties());
        AggregationTask task = service.saveScheduledTask("最近3个月广东软件服务招标信息每天9:00发送", intent());

        assertThat(task.isActive()).isTrue();
        assertThat(service.dueTasks(LocalDateTime.now().plusYears(1))).hasSize(1);

        AggregationTask paused = service.updateActive(task.getId(), false).orElseThrow();
        assertThat(paused.isActive()).isFalse();
        assertThat(service.dueTasks(LocalDateTime.now().plusYears(1))).isEmpty();

        AggregationTask resumed = service.updateActive(task.getId(), true).orElseThrow();
        assertThat(resumed.isActive()).isTrue();
        assertThat(service.dueTasks(LocalDateTime.now().plusYears(1))).hasSize(1);

        assertThat(service.deleteTask(task.getId())).isTrue();
        assertThat(service.listTasks()).isEmpty();
    }

    @Test
    void recordRunKeepsLastReportAndIncrementalCounts() {
        TaskService service = new TaskService(new ObjectMapper().findAndRegisterModules(), properties());
        AggregationTask task = service.saveScheduledTask("最近3个月广东软件服务招标信息每天9:00发送", intent());
        LocalDateTime originalNextRunAt = task.getNextRunAt();

        QueryResult result = new QueryResult();
        BidItem item = new BidItem();
        item.setTitle("广东软件服务招标公告");
        result.setItems(List.of(item));
        result.setIncrementalSkipped(3);
        result.setReportPath(tempDir.resolve("reports").resolve("daily-report.docx"));

        service.recordRun(task, result);

        AggregationTask saved = service.findById(task.getId()).orElseThrow();
        assertThat(saved.getLastRunAt()).isNotNull();
        assertThat(saved.getLastNewCount()).isEqualTo(1);
        assertThat(saved.getLastSkippedCount()).isEqualTo(3);
        assertThat(saved.getLastReportFilename()).isEqualTo("daily-report.docx");
        assertThat(saved.getNextRunAt()).isAfterOrEqualTo(originalNextRunAt);
    }

    @Test
    void readsLegacyTaskFileWithComputedFields() throws Exception {
        AppProperties properties = properties();
        Path taskFile = Path.of(properties.getDataDir(), "tasks.json");
        Files.createDirectories(taskFile.getParent());
        Files.writeString(taskFile, """
                [{
                  "id": "legacy",
                  "question": "最近3个月广东软件服务招标信息每天9:00发送",
                  "intent": {
                    "rawQuestion": "最近3个月广东软件服务招标信息每天9:00发送",
                    "keyword": "软件服务",
                    "province": "广东",
                    "startTime": "2026-02-17T00:00:00",
                    "endTime": "2026-05-17T23:59:59",
                    "scheduleRule": {"type": "DAILY", "time": "09:00:00", "displayText": "每天 09:00"}
                  },
                  "active": true,
                  "createdAt": "2026-05-17T09:00:00",
                  "nextRunAt": "2026-05-17T09:00:00",
                  "lastReportFilename": "legacy.docx"
                }]
                """);
        TaskService service = new TaskService(new ObjectMapper().findAndRegisterModules(), properties);

        assertThat(service.dueTasks(LocalDateTime.of(2026, 5, 17, 10, 0))).hasSize(1);
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir.resolve("data").toString());
        return properties;
    }

    private SearchIntent intent() {
        SearchIntent intent = new IntentParser().parse("最近3个月广东软件服务招标信息每天9:00发送");
        intent.setScheduleRule(new ScheduleRule(ScheduleRule.Type.DAILY, LocalTime.of(9, 0), null));
        return intent;
    }
}
