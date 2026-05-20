package com.xfusion.bidaggregator.controller;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.crawler.CrawlerRegistry;
import com.xfusion.bidaggregator.model.AggregationTask;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.LoginStateInfo;
import com.xfusion.bidaggregator.model.QueryResult;
import com.xfusion.bidaggregator.model.ScheduleRule;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.service.AgentModelService;
import com.xfusion.bidaggregator.service.BidAggregationService;
import com.xfusion.bidaggregator.service.HistoryService;
import com.xfusion.bidaggregator.service.IntentParser;
import com.xfusion.bidaggregator.service.LoginStateService;
import com.xfusion.bidaggregator.service.ScheduledResultStore;
import com.xfusion.bidaggregator.service.SearchJobService;
import com.xfusion.bidaggregator.service.TaskService;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {
    private static final String LAST_RESULT = "lastQueryResult";
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BidAggregationService aggregationService;
    private final AgentModelService agentModelService;
    private final AppProperties properties;
    private final CrawlerRegistry crawlerRegistry;
    private final LoginStateService loginStateService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final SearchJobService searchJobService;
    private final IntentParser intentParser;
    private final ScheduledResultStore scheduledResultStore;

    public HomeController(BidAggregationService aggregationService, AgentModelService agentModelService,
            AppProperties properties, CrawlerRegistry crawlerRegistry, LoginStateService loginStateService,
            TaskService taskService, HistoryService historyService, SearchJobService searchJobService,
            IntentParser intentParser, ScheduledResultStore scheduledResultStore) {
        this.aggregationService = aggregationService;
        this.agentModelService = agentModelService;
        this.properties = properties;
        this.crawlerRegistry = crawlerRegistry;
        this.loginStateService = loginStateService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.searchJobService = searchJobService;
        this.intentParser = intentParser;
        this.scheduledResultStore = scheduledResultStore;
    }

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        QueryResult result = (QueryResult) session.getAttribute(LAST_RESULT);
        model.addAttribute("question", result == null ? "" : result.getIntent().getRawQuestion());
        addDashboardState(model, result, null);
        return "index";
    }

    @PostMapping(value = {"/api/query", "/api/agent/run"}, produces = MediaType.TEXT_HTML_VALUE)
    public String query(@RequestParam String question, Model model, HttpSession session) {
        QueryResult result = aggregationService.execute(question);
        prepareForDisplay(result);
        session.setAttribute(LAST_RESULT, result);
        model.addAttribute("question", question);
        addDashboardState(model, result, null);
        return "index";
    }

    @ResponseBody
    @PostMapping(value = "/api/search/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> startSearch(@RequestParam String question) {
        SearchJobService.SearchJob job = searchJobService.start(question);
        return Map.of("success", true, "jobId", job.getId(), "status", job.getStatus().name());
    }

    @ResponseBody
    @GetMapping(value = "/api/search/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> searchStatus(@PathVariable String jobId) {
        SearchJobService.SearchJob job = searchJobService.get(jobId);
        if (job == null) {
            return Map.of("success", false, "message", "搜索任务不存在");
        }
        QueryResult result = job.getResult();
        if (result != null) {
            prepareForDisplay(result);
        }
        List<BidItem> items = job.items();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("jobId", jobId);
        body.put("status", job.getStatus().name());
        body.put("done", job.isDone());
        body.put("error", job.getError());
        body.put("items", items);
        body.put("referenceCount", items.size());
        body.put("result", result == null ? null : summary(result));
        return body;
    }

    @ResponseBody
    @PostMapping(value = "/api/search/{jobId}/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> stopSearch(@PathVariable String jobId) {
        searchJobService.stop(jobId);
        return Map.of("success", true, "status", "STOPPING");
    }

    @PostMapping(value = "/api/reports/generate", produces = MediaType.TEXT_HTML_VALUE)
    public String generateReport(@RequestParam(value = "selectedItemIds", required = false) List<String> selectedItemIds,
            @RequestParam(value = "jobId", required = false) String jobId, Model model, HttpSession session) {
        QueryResult result = resolveReportResult(jobId, session);
        if (result == null) {
            model.addAttribute("wordError", "请先完成一次查询。");
            addDashboardState(model, null, currentTask());
            return "index";
        }
        prepareForDisplay(result);
        model.addAttribute("question", result.getIntent().getRawQuestion());
        if (selectedItemIds == null || selectedItemIds.isEmpty()) {
            model.addAttribute("wordError", "请先勾选要写入 Word 的信息。");
            addDashboardState(model, result, currentTask());
            return "index";
        }
        try {
            aggregationService.generateReportForSelection(result, selectedItemIds);
            model.addAttribute("wordMessage", "已生成 Word 文件：" + result.getReportFilename());
            session.setAttribute(LAST_RESULT, result);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("wordError", "请先勾选要写入 Word 的信息。");
        }
        addDashboardState(model, result, currentTask());
        return "index";
    }

    @PostMapping(value = "/api/current-task/update", produces = MediaType.TEXT_HTML_VALUE)
    public String updateCurrentTask(@RequestParam(required = false) String taskId,
            @RequestParam String taskQuestion,
            @RequestParam(defaultValue = "DAILY") ScheduleRule.Type frequency,
            @RequestParam(defaultValue = "09:00") String nextTime,
            @RequestParam(required = false) String incrementalOnly,
            Model model, HttpSession session) {
        SearchIntent intent = intentParser.parse(taskQuestion);
        intent.setScheduleRule(new ScheduleRule(frequency, parseTime(nextTime), null));
        AggregationTask task = taskService.saveEditableTask(taskId, taskQuestion, intent, incrementalOnly != null);
        model.addAttribute("taskMessage", "已保存定时执行与推送配置。");
        QueryResult result = (QueryResult) session.getAttribute(LAST_RESULT);
        addDashboardState(model, result, task);
        model.addAttribute("question", result == null ? taskQuestion : result.getIntent().getRawQuestion());
        return "index";
    }

    @ResponseBody
    @GetMapping(value = "/api/tasks/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> taskStatus() {
        AggregationTask task = currentTask();
        QueryResult latestResult = task == null
                ? scheduledResultStore.latest().orElse(null)
                : scheduledResultStore.get(task.getId()).orElseGet(() -> scheduledResultStore.latest().orElse(null));
        if (latestResult != null) {
            prepareForDisplay(latestResult);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("currentTask", toTaskView(task));
        body.put("taskPlans", taskService.listTasks().stream().map(this::toTaskView).toList());
        body.put("latestResult", latestResult == null ? null : summary(latestResult));
        body.put("latestItems", referenceItems(latestResult));
        return body;
    }

    @ResponseBody
    @GetMapping(value = "/api/agent/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> agentHealth() {
        AgentModelService.AgentHealth health = agentModelService.checkHealth();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyConfigured", health.keyConfigured());
        body.put("modelAvailable", health.modelAvailable());
        body.put("baseUrl", health.baseUrl());
        body.put("model", health.model());
        body.put("timeoutSeconds", health.timeoutSeconds());
        body.put("message", health.message());
        return body;
    }

    @ResponseBody
    @PostMapping(value = "/api/agent/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> queryJson(@RequestParam String question) {
        QueryResult result = aggregationService.execute(question);
        prepareForDisplay(result);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("userQuery", question);
        body.put("parsedIntent", Map.of(
                "keyword", safe(result.getIntent().getKeyword()),
                "region", displayRegion(result),
                "timeRange", result.getIntentTimeText(),
                "frequency", result.getFrequencyText()));
        body.put("summary", summary(result));
        body.put("items", referenceItems(result));
        return body;
    }

    @PostMapping("/api/tasks/{id}/run")
    public String runTask(@PathVariable String id, Model model, HttpSession session) {
        AggregationTask task = taskService.findById(id).orElse(null);
        if (task == null) {
            model.addAttribute("taskMessage", "未找到任务：" + id);
            addDashboardState(model, null, currentTask());
            return "index";
        }
        QueryResult result = aggregationService.executeScheduledTask(task);
        prepareForDisplay(result);
        taskService.recordRun(task, result);
        session.setAttribute(LAST_RESULT, result);
        model.addAttribute("question", task.getQuestion());
        model.addAttribute("taskMessage", "任务已执行。");
        addDashboardState(model, result, task);
        return "index";
    }

    @PostMapping("/api/tasks/{id}/history/clear")
    public String clearTaskHistory(@PathVariable String id, Model model) {
        AggregationTask task = taskService.findById(id).orElse(null);
        if (task == null || task.getIntent() == null) {
            model.addAttribute("taskMessage", "未找到可清空历史的任务：" + id);
        } else {
            model.addAttribute("taskMessage", historyService.clear(task.getIntent()) ? "已清空当前任务历史。" : "当前任务暂无历史。");
            model.addAttribute("question", task.getQuestion());
        }
        addDashboardState(model, null, currentTask());
        return "index";
    }

    @PostMapping("/api/tasks/{id}/pause")
    public String pauseTask(@PathVariable String id, Model model) {
        AggregationTask task = taskService.updateActive(id, false).orElse(null);
        model.addAttribute("taskMessage", task == null ? "未找到任务：" + id : "已暂停任务：" + task.getQuestion());
        addDashboardState(model, null, task == null ? currentTask() : task);
        return "index";
    }

    @PostMapping("/api/tasks/{id}/resume")
    public String resumeTask(@PathVariable String id, Model model) {
        AggregationTask task = taskService.updateActive(id, true).orElse(null);
        model.addAttribute("taskMessage", task == null ? "未找到任务：" + id : "已恢复任务：" + task.getQuestion());
        addDashboardState(model, null, task == null ? currentTask() : task);
        return "index";
    }

    @PostMapping("/api/tasks/{id}/delete")
    public String deleteTask(@PathVariable String id, Model model) {
        model.addAttribute("taskMessage", taskService.deleteTask(id) ? "已删除任务。" : "未找到任务：" + id);
        addDashboardState(model, null, currentTask());
        return "index";
    }

    @GetMapping({"/reports/{filename}", "/api/reports/{filename}/download"})
    public ResponseEntity<InputStreamResource> download(@PathVariable String filename) throws Exception {
        Path path = Path.of(properties.getReportsDir(), filename).normalize();
        Path reportRoot = Path.of(properties.getReportsDir()).toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(reportRoot) || !Files.exists(absolute)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + absolute.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(new InputStreamResource(Files.newInputStream(absolute)));
    }

    private QueryResult resolveReportResult(String jobId, HttpSession session) {
        if (jobId != null && !jobId.isBlank()) {
            SearchJobService.SearchJob job = searchJobService.get(jobId);
            if (job != null && job.getResult() != null) {
                return job.getResult();
            }
        }
        return (QueryResult) session.getAttribute(LAST_RESULT);
    }

    private void addDashboardState(Model model, QueryResult result, AggregationTask preferredTask) {
        QueryResult visibleResult = result;
        if (visibleResult != null) {
            prepareForDisplay(visibleResult);
        }
        List<LoginSourceView> loginSources = crawlerRegistry.loginSources().stream()
                .map(source -> {
                    LoginStateInfo info = loginStateService.status(source);
                    return new LoginSourceView(loginStateService.sourceKey(source), source.getName(), source.getLoginUrl(),
                            info.isAvailable() ? "已保存" : "未登录", info.isAvailable());
                })
                .toList();
        AggregationTask task = preferredTask == null ? currentTask() : preferredTask;
        model.addAttribute("result", visibleResult);
        model.addAttribute("referenceItems", referenceItems(visibleResult));
        model.addAttribute("referenceCount", referenceItems(visibleResult).size());
        model.addAttribute("loginSources", loginSources);
        model.addAttribute("currentTask", toTaskView(task));
        model.addAttribute("taskPlans", taskService.listTasks().stream().map(this::toTaskView).toList());
        model.addAttribute("configuredSourceCount", visibleResult == null ? crawlerRegistry.enabledCrawlers().size() : visibleResult.getSourceTotal());
        model.addAttribute("intentStatus", visibleResult != null && visibleResult.getAgentPlan() != null
                && visibleResult.getAgentPlan().getModelFallbackReason() == null ? "Agent 解析成功" : "规则兜底");
    }

    private AggregationTask currentTask() {
        return taskService.listTasks().stream()
                .filter(AggregationTask::isActive)
                .reduce((first, second) -> second)
                .or(() -> taskService.listTasks().stream().reduce((first, second) -> second))
                .orElse(null);
    }

    private TaskView toTaskView(AggregationTask task) {
        if (task == null) {
            return new TaskView("", "每天十点发河南芯片招标信息", "DAILY", "每日", "09:00", "暂无计划", true,
                    false, "", "暂无自动执行记录", null, true);
        }
        ScheduleRule rule = task.getIntent() == null ? null : task.getIntent().getScheduleRule();
        String frequency = rule == null || rule.getType() == null ? "DAILY" : rule.getType().name();
        String time = rule == null || rule.getTime() == null ? "09:00" : rule.getTime().toString();
        String next = task.getNextRunAt() == null ? "暂无计划" : DISPLAY_TIME.format(task.getNextRunAt());
        return new TaskView(task.getId(), task.getQuestion(), frequency, frequencyText(frequency), time, next,
                task.isIncrementalOnly(), task.isRunning(), safe(task.getLastRunStatus()),
                task.getLastRunMessage() == null || task.getLastRunMessage().isBlank() ? "暂无自动执行记录" : task.getLastRunMessage(),
                task.getLastReportFilename(), task.isActive());
    }

    private String frequencyText(String frequency) {
        if ("WEEKLY".equals(frequency)) {
            return "每周";
        }
        if ("ONCE".equals(frequency)) {
            return "一次";
        }
        return "每日";
    }

    private Map<String, Object> summary(QueryResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("keyword", safe(result.getIntent().getKeyword()));
        summary.put("region", displayRegion(result));
        summary.put("time", result.getIntentTimeText());
        summary.put("frequency", result.getFrequencyText());
        summary.put("sourceTotal", result.getSourceTotal());
        summary.put("referenceCount", referenceItems(result).size());
        summary.put("intentStatus", result.getAgentPlan() != null && result.getAgentPlan().getModelFallbackReason() == null
                ? "Agent 解析成功" : "规则兜底");
        summary.put("elapsedText", result.getElapsedText());
        return summary;
    }

    private void prepareForDisplay(QueryResult result) {
        if (result == null) {
            return;
        }
        List<BidItem> items = referenceItems(result);
        for (int i = 0; i < items.size(); i++) {
            BidItem item = items.get(i);
            if (item.getId() == null || item.getId().isBlank()) {
                item.setId("ref-" + hash(safe(item.getTitle()) + "|" + safe(item.getSourceUrl()) + "|" + i));
            }
        }
    }

    private List<BidItem> referenceItems(QueryResult result) {
        if (result == null) {
            return List.of();
        }
        List<BidItem> items = new ArrayList<>();
        items.addAll(result.getItems());
        items.addAll(result.getCandidateItems());
        return items.stream()
                .filter(item -> !isSearchEngineUrl(item.getSourceUrl()))
                .limit(50)
                .toList();
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (Exception ex) {
            return LocalTime.of(9, 0);
        }
    }

    private String displayRegion(QueryResult result) {
        if (result.getIntent().getCity() != null && !result.getIntent().getCity().isBlank()) {
            return result.getIntent().getCity();
        }
        return safe(result.getIntent().getProvince());
    }

    private String safe(String raw) {
        return raw == null ? "" : raw;
    }

    private boolean isSearchEngineUrl(String raw) {
        String url = safe(raw).toLowerCase();
        return url.contains("bing.com/search") || url.contains("google.com/search");
    }

    public record LoginSourceView(String sourceKey, String sourceName, String loginUrl, String status,
            boolean available) {
    }

    public record TaskView(String id, String question, String frequency, String frequencyText, String time,
            String nextRunAt, boolean incrementalOnly, boolean running, String lastRunStatus, String lastRunMessage,
            String lastReportFilename, boolean active) {
    }
}
