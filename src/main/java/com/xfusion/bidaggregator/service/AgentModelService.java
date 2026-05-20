package com.xfusion.bidaggregator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.agent.AgentModelResponse;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.io.IOException;
import java.net.URI;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class AgentModelService {
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentModelService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(new NoProxySelector())
                .build();
    }

    public AgentModelResponse analyze(String question, SearchIntent ruleIntent, List<String> sourceNames) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return defaultPlan(question, ruleIntent, sourceNames,
                    "未设置 app.agent-api-key 或 XFUSION_API_KEY，已回退到规则 Agent。");
        }
        try {
            ModelCallResult callResult = callModelWithFallback(apiKey, question, ruleIntent, sourceNames);
            String content = callResult.content();
            AgentModelResponse response = parseModelJson(content);
            response.setModelUsed(true);
            response.setModelName(callResult.modelName());
            fillDefaults(response, question, ruleIntent);
            response.setSearchQueries(normalizeQueries(response.getSearchQueries(), ruleIntent));
            return response;
        } catch (Exception ex) {
            return defaultPlan(question, ruleIntent, sourceNames,
                    "模型 Agent 调用失败，已回退规则 Agent：" + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    public AgentHealth checkHealth() {
        String apiKey = resolveApiKey();
        if (!hasText(apiKey)) {
            return new AgentHealth(false, false, properties.getAgentBaseUrl(), properties.getAgentModel(),
                    properties.getAgentTimeoutSeconds(), "未设置 app.agent-api-key 或 XFUSION_API_KEY。");
        }
        SearchIntent intent = new SearchIntent();
        intent.setKeyword("服务器");
        intent.setProvince("上海");
        try {
            ModelCallResult result = callModelWithFallback(apiKey, "最近1个月上海服务器招标信息有哪些", intent,
                    List.of("中国政府采购网", "全国公共资源交易平台"));
            return new AgentHealth(true, true, properties.getAgentBaseUrl(), result.modelName(),
                    properties.getAgentTimeoutSeconds(), "模型 Agent 调用成功。");
        } catch (Exception ex) {
            return new AgentHealth(true, false, properties.getAgentBaseUrl(), properties.getAgentModel(),
                    properties.getAgentTimeoutSeconds(),
                    ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    public void applyToIntent(AgentModelResponse model, SearchIntent intent) {
        if (model == null || !model.isModelUsed()) {
            return;
        }
        if (hasText(model.getKeyword())) {
            intent.setKeyword(sanitizeKeyword(model.getKeyword(), intent.getKeyword()));
        }
        if (hasText(model.getProvince())) {
            intent.setProvince(model.getProvince().trim());
        }
        if (hasText(model.getCity())) {
            intent.setCity(model.getCity().trim());
        }
    }

    private ModelCallResult callModelWithFallback(String apiKey, String question, SearchIntent ruleIntent,
            List<String> sourceNames) throws Exception {
        List<String> models = candidateModels();
        StringJoiner failures = new StringJoiner("；");
        Exception last = null;
        for (String model : models) {
            try {
                return new ModelCallResult(model, callModel(apiKey, question, ruleIntent, sourceNames, model));
            } catch (Exception ex) {
                last = ex;
                failures.add(model + " -> " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        throw new IllegalStateException("所有候选模型调用失败：" + failures, last);
    }

    private String callModel(String apiKey, String question, SearchIntent ruleIntent, List<String> sourceNames,
            String model)
            throws Exception {
        String uri = trimTrailingSlash(properties.getAgentBaseUrl()) + "/chat/completions";
        String prompt = """
                你是招投标信息聚合工具里的检索规划 Agent。
                你的职责是把用户问题拆成“城市/地区、采购物品或服务、时间范围”三类检索条件，并生成可执行的全网检索计划。
                你不负责编造、补写或确认任何招标事实。
                必须只输出 JSON，不要输出 Markdown。

                JSON 字段：
                userIntentSummary: 中文一句话总结用户需求
                keyword: 采购物品或服务，只保留物品/服务名，例如服务器、充电桩、软件服务；不要带时间、城市、疑问词
                province: 省级地区，没有则为全国；如果用户只给城市，要尽量补出省份
                city: 城市，可为空；例如杭州、上海、北京、深圳
                scheduleText: 定时需求文字，没有则为空
                needAuthenticatedSource: 是否需要优先尝试登录态/鉴权来源
                searchQueries: 8 到 12 个可直接投喂搜索引擎或招标站搜索框的查询词
                planSteps: 4 到 6 个执行步骤
                riskNotes: 2 到 4 个风险说明

                searchQueries 生成规则：
                1. 必须围绕“城市或省份 + 物品/服务关键词或同义词 + 招标/采购/公告/中标/竞争性磋商/公开招标”等组合。
                2. 对服务器类需求，可加入信创服务器、鲲鹏服务器、海光服务器、算力服务器、超频服务器、超融合服务器等行业同义词。
                3. 对软件服务类需求，可加入软件开发、信息化服务、系统建设、系统运维、平台建设、软件采购等同义词。
                4. 可以包含“政府采购网、公共资源交易、招标投标公共服务平台、企业采购门户”等来源类型词。
                5. 不要输出具体公告标题、金额、中标人、采购人等事实；这些必须来自后续 crawler 和来源链接。
                6. 如果用户给了时间范围，搜索词可以包含“最近、2026、月份”等时间辅助词，但 keyword 字段不能包含时间。
                """;
        String user = "用户问题：" + question + "\n"
                + "规则解析关键词：" + nullSafe(ruleIntent.getKeyword()) + "\n"
                + "规则解析地区：" + nullSafe(ruleIntent.getProvince()) + "\n"
                + "可用来源：" + String.join("、", sourceNames);

        var body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);
        body.put("temperature", 0);
        body.put("max_tokens", 900);
        body.set("messages", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("role", "system").put("content", prompt))
                .add(objectMapper.createObjectNode().put("role", "user").put("content", user)));

        String payload = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(properties.getAgentTimeoutSeconds()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return callModelWithCurl(uri, apiKey, payload, model, ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }
        return extractContent(response.body());
    }

    private String callModelWithCurl(String uri, String apiKey, String payload, String model, Exception javaFailure)
            throws Exception {
        Path requestFile = Files.createTempFile("bidradar-agent-", ".json");
        try {
            Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder(
                    "curl.exe",
                    "--noproxy", "*",
                    "-sS",
                    "--connect-timeout", "8",
                    "--max-time", String.valueOf(Math.max(properties.getAgentTimeoutSeconds(), 20)),
                    "-H", "Authorization: Bearer " + apiKey,
                    "-H", "Content-Type: application/json",
                    "--data-binary", "@" + requestFile.toAbsolutePath(),
                    uri);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            byte[] bytes = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(Math.max(properties.getAgentTimeoutSeconds() + 5, 25),
                    TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException(model + " curl fallback timed out after Java failure: "
                        + javaFailure.getClass().getSimpleName() + " - " + javaFailure.getMessage());
            }
            String body = new String(bytes, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException(model + " curl fallback exit " + process.exitValue()
                        + " after Java failure: " + javaFailure.getClass().getSimpleName()
                        + " - " + javaFailure.getMessage() + "；curl output: " + body);
            }
            if (body.contains("\"error\"")) {
                JsonNode root = objectMapper.readTree(firstJsonObject(body));
                throw new IllegalStateException(model + " curl fallback API error after Java failure: "
                        + root.path("error").toString());
            }
            String content = extractContent(body);
            if (!hasText(content)) {
                throw new IllegalStateException(model + " curl fallback empty content after Java failure: "
                        + javaFailure.getClass().getSimpleName() + " - " + javaFailure.getMessage()
                        + "；response: " + body);
            }
            return content;
        } finally {
            try {
                Files.deleteIfExists(requestFile);
            } catch (IOException ignored) {
            }
        }
    }

    private AgentModelResponse parseModelJson(String content) throws Exception {
        String json = content == null ? "" : content.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```json", "").replaceFirst("^```", "");
            int end = json.lastIndexOf("```");
            if (end >= 0) {
                json = json.substring(0, end);
            }
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        return objectMapper.readValue(json, AgentModelResponse.class);
    }

    private String extractContent(String responseBody) throws Exception {
        String body = responseBody == null ? "" : responseBody.trim();
        if (body.isBlank()) {
            return "";
        }
        if (!body.startsWith("data:") && body.startsWith("{")) {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                throw new IllegalStateException(root.path("error").toString());
            }
            String message = root.path("choices").path(0).path("message").path("content").asText();
            if (hasText(message)) {
                return message;
            }
            return root.path("choices").path(0).path("delta").path("content").asText();
        }
        StringBuilder content = new StringBuilder();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String data = trimmed.substring("data:".length()).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                continue;
            }
            JsonNode chunk = objectMapper.readTree(data);
            if (chunk.has("error")) {
                throw new IllegalStateException(chunk.path("error").toString());
            }
            String delta = chunk.path("choices").path(0).path("delta").path("content").asText();
            if (!delta.isBlank()) {
                content.append(delta);
                continue;
            }
            String message = chunk.path("choices").path(0).path("message").path("content").asText();
            if (!message.isBlank()) {
                content.append(message);
            }
        }
        return content.toString();
    }

    private String firstJsonObject(String value) {
        String text = value == null ? "" : value.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    private AgentModelResponse defaultPlan(String question, SearchIntent ruleIntent, List<String> sourceNames,
            String reason) {
        AgentModelResponse response = AgentModelResponse.fallback(reason);
        fillDefaults(response, question, ruleIntent);
        response.setSearchQueries(normalizeQueries(List.of(), ruleIntent));
        response.setPlanSteps(List.of(
                "理解用户问题并抽取关键词、地区、时间范围和频率。",
                "生成面向搜索引擎、公共资源平台、政府采购网和企业采购门户的检索词。",
                "调度固定来源和登录态来源抓取列表与正文。",
                "清洗、去重、过滤无效页面并生成 Word 简报。"));
        response.setRiskNotes(List.of(reason, "单个来源失败只记录 warning，不阻断整体报告。"));
        return response;
    }

    private void fillDefaults(AgentModelResponse response, String question, SearchIntent ruleIntent) {
        if (!hasText(response.getUserIntentSummary())) {
            response.setUserIntentSummary("围绕“" + question + "”检索并汇总可追溯的招投标公告。");
        }
        if (!hasText(response.getKeyword())) {
            response.setKeyword(ruleIntent.getKeyword());
        }
        if (!hasText(response.getProvince())) {
            response.setProvince(ruleIntent.getProvince());
        }
    }

    private List<String> normalizeQueries(List<String> modelQueries, SearchIntent intent) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (modelQueries != null) {
            modelQueries.stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .filter(query -> query.length() <= 80)
                    .forEach(queries::add);
        }
        String region = searchRegion(intent);
        String keyword = nullSafe(intent.getKeyword());
        for (String term : expandedTerms(keyword)) {
            queries.add(region + " " + term + " 招标公告");
            queries.add(region + " " + term + " 采购公告");
            queries.add(region + " " + term + " 公共资源交易");
            queries.add(region + " " + term + " 政府采购网");
        }
        return queries.stream().filter(this::hasText).limit(12).toList();
    }

    private List<String> expandedTerms(String keyword) {
        List<String> terms = new ArrayList<>();
        if (!hasText(keyword)) {
            return terms;
        }
        terms.add(keyword.trim());
        if (keyword.contains("服务器")) {
            terms.addAll(List.of("信创服务器", "鲲鹏服务器", "海光服务器", "算力服务器", "超频服务器",
                    "超融合服务器", "容灾服务器", "数据中心设备"));
        }
        if (keyword.contains("软件服务")) {
            terms.addAll(List.of("软件开发", "信息化服务", "系统建设", "系统运维", "平台建设", "软件采购"));
        }
        if (keyword.contains("充电桩")) {
            terms.addAll(List.of("充电设施", "新能源充电", "充电站", "充电设备"));
        }
        return terms.stream().distinct().limit(8).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveApiKey() {
        if (hasText(properties.getAgentApiKey())) {
            return properties.getAgentApiKey().trim();
        }
        String env = System.getenv("XFUSION_API_KEY");
        return env == null ? "" : env.trim();
    }

    private String sanitizeKeyword(String modelKeyword, String ruleKeyword) {
        String keyword = modelKeyword == null ? "" : modelKeyword.trim();
        keyword = keyword.replaceAll("最近\\s*([0-9一二两三四五六七八九十]+)\\s*(个)?\\s*(月|年|天)", "");
        keyword = keyword.replaceAll("招标信息|采购信息|招标公告|公告|信息|项目|有哪些|哪些|都有哪些|相关", "");
        keyword = keyword.replaceAll("全国|区域内|范围内", "").trim();
        if (!hasText(keyword) || keyword.length() > 12) {
            return ruleKeyword;
        }
        if (hasText(ruleKeyword) && modelKeyword != null && modelKeyword.contains(ruleKeyword)) {
            return ruleKeyword;
        }
        return keyword;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "全国" : value;
    }

    private String searchRegion(SearchIntent intent) {
        if (intent != null && hasText(intent.getCity())) {
            return intent.getCity().trim();
        }
        return intent == null ? "全国" : nullSafe(intent.getProvince());
    }

    private List<String> candidateModels() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        String configured = System.getenv("XFUSION_MODEL_CANDIDATES");
        if (hasText(configured)) {
            for (String model : configured.split("[,，;；\\s]+")) {
                if (hasText(model)) {
                    models.add(model.trim());
                }
            }
        }
        if (hasText(properties.getAgentModel())) {
            models.add(properties.getAgentModel().trim());
        }
        models.add("kimi-k2.6");
        models.add("deepseek-v4-pro");
        models.add("qwen3.6-plus");
        return models.stream().filter(this::hasText).limit(4).toList();
    }

    private String trimTrailingSlash(String value) {
        String result = value == null || value.isBlank() ? "http://218.28.9.108:50053/v1" : value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record ModelCallResult(String modelName, String content) {
    }

    public record AgentHealth(boolean keyConfigured, boolean modelAvailable, String baseUrl, String model,
            int timeoutSeconds, String message) {
    }

    private static class NoProxySelector extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    }
}
