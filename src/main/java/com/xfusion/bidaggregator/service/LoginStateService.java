package com.xfusion.bidaggregator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.ApiResponse;
import com.xfusion.bidaggregator.model.LoginDiagnosticEvent;
import com.xfusion.bidaggregator.model.LoginStateInfo;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class LoginStateService {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration PLAYWRIGHT_LAUNCH_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration CAPTURE_START_TIMEOUT = Duration.ofSeconds(45);
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final PlaywrightEnvironmentChecker environmentChecker;
    private final Map<String, CaptureSession> captures = new ConcurrentHashMap<>();
    private final List<LoginDiagnosticEvent> diagnostics = new ArrayList<>();
    private final ExecutorService captureExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "jianyu-login-capture");
        thread.setDaemon(true);
        return thread;
    });

    public LoginStateService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environmentChecker = new PlaywrightEnvironmentChecker(properties);
    }

    public LoginStateInfo status(AppProperties.SourceConfig source) {
        Path path = statePath(source);
        PlaywrightEnvironmentChecker.CheckResult storageState = environmentChecker.checkStorageState(path);
        if (!storageState.available()) {
            record("LOGIN_STATE_MISSING", "MISSING", "尚未保存登录态：" + path);
            return new LoginStateInfo(source.getName(), path.toString(), false, null, storageState.message());
        }
        LoginStateValidation validation = validateStorageState(source, path);
        if (!validation.valid()) {
            record("LOGIN_STATE_INVALID", "WARNING", source.getName() + " 登录态不可用：" + validation.message());
            return new LoginStateInfo(source.getName(), path.toString(), false, savedAt(path),
                    "登录态不可用，请重新登录该来源。");
        }
        try {
            LocalDateTime savedAt = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
            return new LoginStateInfo(source.getName(), path.toString(), true, savedAt, "登录态已保存，可用于登录来源抓取");
        } catch (Exception ex) {
            return new LoginStateInfo(source.getName(), path.toString(), true, null, "登录态已保存，但读取修改时间失败");
        }
    }

    public ApiResponse statusResponse(AppProperties.SourceConfig source) {
        LoginStateInfo info = status(source);
        String status = info.isAvailable() ? "SAVED" : (Files.exists(statePath(source)) ? "INVALID" : "MISSING");
        return ApiResponse.ok(info.getMessage())
                .with("source", source.getName())
                .with("statePath", info.getStateFile())
                .with("exists", info.isAvailable())
                .with("lastModified", info.getSavedAt() == null ? null : DISPLAY_TIME.format(info.getSavedAt()))
                .with("status", status);
    }

    public synchronized ApiResponse startCapture(AppProperties.SourceConfig source) {
        cleanupExpiredCaptures();
        String captureId = UUID.randomUUID().toString();
        AtomicBoolean abandoned = new AtomicBoolean(false);
        LaunchResources resources = new LaunchResources();
        CompletableFuture<ApiResponse> startFuture = CompletableFuture.supplyAsync(
                () -> openCaptureWindow(source, captureId, abandoned, resources), captureExecutor);
        try {
            return startFuture.get(PLAYWRIGHT_LAUNCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            abandoned.set(true);
            startFuture.cancel(true);
            resources.close();
            record("LOGIN_CAPTURE_FAILED", "WARNING",
                    "标准 Playwright launch 8 秒内未完成握手，改用可见 Chrome + CDP 兜底。");
            return openCaptureWindowByCdp(source, captureId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            abandoned.set(true);
            startFuture.cancel(true);
            resources.close();
            record("LOGIN_CAPTURE_FAILED", "FAILED", "启动登录窗口被中断：" + ex.getMessage());
            return ApiResponse.fail("Playwright 登录窗口打开失败：启动过程被中断");
        } catch (ExecutionException ex) {
            abandoned.set(true);
            resources.close();
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            record("LOGIN_CAPTURE_FAILED", "WARNING",
                    "标准 Playwright launch 失败，改用可见 Chrome + CDP 兜底："
                            + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            return openCaptureWindowByCdp(source, captureId);
        }
    }

    private ApiResponse openCaptureWindow(AppProperties.SourceConfig source, String captureId,
            AtomicBoolean abandoned, LaunchResources resources) {
        try {
            record("LOGIN_CAPTURE_START", "RUNNING", "准备启动 Playwright 登录窗口：" + source.getLoginUrl());
            resources.playwright = createPlaywright();
            resources.browser = resources.playwright.chromium().launch(chromeLaunchOptions());
            record("PLAYWRIGHT_BROWSER_OPENED", "SUCCESS", "Playwright 可见浏览器已打开。");
            resources.context = resources.browser.newContext();
            resources.page = resources.context.newPage();
            resources.page.setDefaultNavigationTimeout(30000);
            resources.page.setDefaultTimeout(15000);
            NavigateResult navigateResult = navigateLoginPage(resources.page, source.getLoginUrl());
            if (abandoned.get()) {
                resources.close();
                return ApiResponse.fail("Playwright 登录窗口打开已超时，已关闭迟到的采集窗口");
            }
            captures.put(captureId, new CaptureSession(captureId, resources.playwright, resources.browser,
                    resources.context, resources.page, null, LocalDateTime.now()));
            record("LOGIN_PAGE_NAVIGATED", navigateResult.success() ? "SUCCESS" : "WARNING",
                    navigateResult.message());
            String message = navigateResult.success()
                    ? "Playwright 登录窗口已打开，请在窗口中完成登录"
                    : "登录窗口已打开，但目标站点暂时无法自动加载；请在窗口地址栏手动打开剑鱼标讯或稍后重试";
            return ApiResponse.ok(message)
                    .with("captureId", captureId)
                    .with("loginUrl", navigateResult.url())
                    .with("navigationWarning", navigateResult.success() ? null : navigateResult.message());
        } catch (Exception ex) {
            resources.close();
            record("LOGIN_CAPTURE_FAILED", "FAILED", ex.getClass().getSimpleName() + " - " + ex.getMessage());
            return ApiResponse.fail("Playwright 登录窗口打开失败：" + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    private ApiResponse openCaptureWindowByCdp(AppProperties.SourceConfig source, String captureId) {
        LaunchResources resources = new LaunchResources();
        Path browserPath = findChromiumExecutable();
        if (browserPath == null) {
            String message = environmentChecker.checkBrowserAvailability().message();
            record("LOGIN_CAPTURE_FAILED", "FAILED", message);
            return ApiResponse.fail(message);
        }
        try {
            int port = freePort();
            Path profileDir = Path.of(properties.getDataDir(), "login", "capture-" + captureId);
            Files.createDirectories(profileDir);
            record("LOGIN_CAPTURE_START", "RUNNING",
                    "使用可见 Chrome CDP 兜底启动登录窗口：" + browserPath);
            resources.process = new ProcessBuilder(
                    browserPath.toString(),
                    "--remote-debugging-port=" + port,
                    "--remote-debugging-address=127.0.0.1",
                    "--user-data-dir=" + profileDir.toAbsolutePath(),
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-background-mode",
                    "--new-window",
                    source.getLoginUrl())
                    .redirectErrorStream(true)
                    .start();
            waitForCdp(port, Duration.ofSeconds(20));
            resources.playwright = createPlaywright();
            resources.browser = resources.playwright.chromium().connectOverCDP("http://127.0.0.1:" + port);
            record("PLAYWRIGHT_BROWSER_OPENED", "SUCCESS", "可见 Chrome 已打开，Playwright 已通过 CDP 接管。");
            resources.context = resources.browser.contexts().isEmpty()
                    ? resources.browser.newContext()
                    : resources.browser.contexts().get(0);
            resources.page = resources.context.pages().isEmpty()
                    ? resources.context.newPage()
                    : resources.context.pages().get(0);
            resources.page.setDefaultNavigationTimeout(30000);
            resources.page.setDefaultTimeout(15000);
            if (!resources.page.url().startsWith("http")) {
                navigateLoginPage(resources.page, source.getLoginUrl());
            }
            NavigateResult navigateResult = navigateLoginPage(resources.page, source.getLoginUrl());
            captures.put(captureId, new CaptureSession(captureId, resources.playwright, resources.browser,
                    resources.context, resources.page, resources.process, LocalDateTime.now()));
            record("LOGIN_PAGE_NAVIGATED", navigateResult.success() ? "SUCCESS" : "WARNING",
                    navigateResult.message());
            String message = navigateResult.success()
                    ? "Playwright 登录窗口已打开，请在窗口中完成登录"
                    : "登录窗口已打开，但目标站点暂时无法自动加载；请在窗口地址栏手动打开剑鱼标讯或稍后重试";
            return ApiResponse.ok(message)
                    .with("captureId", captureId)
                    .with("loginUrl", navigateResult.url())
                    .with("navigationWarning", navigateResult.success() ? null : navigateResult.message());
        } catch (Exception ex) {
            resources.close();
            record("LOGIN_CAPTURE_FAILED", "FAILED", ex.getClass().getSimpleName() + " - " + ex.getMessage());
            return ApiResponse.fail("Playwright 登录窗口打开失败：" + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    public synchronized ApiResponse saveCapture(String captureId, AppProperties.SourceConfig source) {
        cleanupExpiredCaptures();
        CaptureSession session = captures.remove(captureId);
        if (session == null) {
            return ApiResponse.fail("登录态保存失败：登录采集窗口不存在或已超时，请重新启动采集窗口");
        }
        Path path = statePath(source);
        try {
            Files.createDirectories(path.getParent());
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            session.context.storageState(new BrowserContext.StorageStateOptions().setPath(tempPath));
            LoginStateValidation validation = validateStorageState(source, tempPath);
            if (!validation.valid()) {
                Files.deleteIfExists(tempPath);
                record("LOGIN_STATE_INVALID", "WARNING",
                        "拒绝保存未登录或不可用的登录态：" + source.getName() + "，" + validation.message());
                return ApiResponse.fail("登录态不可用，请先在打开的窗口完成登录后再保存。");
            }
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            record("STORAGE_STATE_SAVED", "SUCCESS", "登录态已保存：" + path);
            return ApiResponse.ok("登录态已保存")
                    .with("statePath", path.toString())
                    .with("source", source.getName());
        } catch (Exception ex) {
            record("LOGIN_CAPTURE_FAILED", "FAILED", "保存登录态失败：" + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            return ApiResponse.fail("登录态保存失败：" + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        } finally {
            session.close();
        }
    }

    public Map<String, String> cookiesFor(AppProperties.SourceConfig source) {
        Map<String, String> cookies = new LinkedHashMap<>();
        Path path = statePath(source);
        if (!Files.exists(path)) {
            record("LOGIN_STATE_MISSING", "MISSING", "登录态文件不存在，登录来源降级：" + path);
            return cookies;
        }
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            String host = URI.create(source.getUrl()).getHost();
            for (JsonNode cookie : root.path("cookies")) {
                String domain = cookie.path("domain").asText("");
                if (!host.endsWith(domain.replaceFirst("^\\.", ""))) {
                    continue;
                }
                String name = cookie.path("name").asText("");
                String value = cookie.path("value").asText("");
                if (!name.isBlank()) {
                    cookies.put(name, value);
                }
            }
            record("LOGIN_STATE_USED", "SUCCESS", "已读取 storageState cookies：" + source.getName());
        } catch (Exception ex) {
            record("LOGIN_STATE_EXPIRED", "WARNING", "登录态可能过期或损坏：" + ex.getMessage());
            return Map.of();
        }
        return cookies;
    }

    public Path statePath(AppProperties.SourceConfig source) {
        if (source.getStorageState() != null && !source.getStorageState().isBlank()) {
            return Path.of(source.getStorageState());
        }
        return Path.of(properties.getDataDir(), "login", sourceKey(source) + "-state.json");
    }

    private LoginStateValidation validateStorageState(AppProperties.SourceConfig source, Path path) {
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            String key = sourceKey(source).toLowerCase();
            if (key.contains("jianyu")) {
                return validateJianyu(root);
            }
            if (key.contains("bidcenter")) {
                return validateBidcenter(root);
            }
            return hasGenericAuthMarker(root)
                    ? new LoginStateValidation(true, "检测到登录凭证")
                    : new LoginStateValidation(false, "未检测到可用登录凭证");
        } catch (Exception ex) {
            return new LoginStateValidation(false, "storageState 解析失败：" + ex.getClass().getSimpleName());
        }
    }

    private LoginStateValidation validateJianyu(JsonNode root) {
        for (JsonNode origin : root.path("origins")) {
            if (!origin.path("origin").asText("").contains("jianyu360.cn")) {
                continue;
            }
            for (JsonNode item : origin.path("localStorage")) {
                String name = item.path("name").asText("");
                String value = item.path("value").asText("");
                if ("BIGMEMBER_PC".equalsIgnoreCase(name) && meaningful(value)) {
                    return new LoginStateValidation(true, "检测到剑鱼会员登录态");
                }
            }
        }
        if (hasJianyuReusableSession(root)) {
            return new LoginStateValidation(true, "检测到剑鱼浏览器会话态");
        }
        return hasAuthCookie(root, "jianyu360.cn", List.of("member", "user", "token", "auth", "login"))
                ? new LoginStateValidation(true, "检测到剑鱼登录 cookie")
                : new LoginStateValidation(false, "仅检测到访客 cookie，未检测到剑鱼登录态");
    }

    private boolean hasJianyuReusableSession(JsonNode root) {
        boolean hasSession = false;
        boolean hasBrowserSessionToken = false;
        for (JsonNode cookie : root.path("cookies")) {
            String domain = cookie.path("domain").asText("").toLowerCase();
            if (!domain.contains("jianyu360.cn") && !domain.contains("jianyu360.com")) {
                continue;
            }
            String name = cookie.path("name").asText("");
            String value = cookie.path("value").asText("");
            if (!meaningful(value) || "JYGuestUID".equalsIgnoreCase(name) || name.toLowerCase().startsWith("hm_")) {
                continue;
            }
            if ("SESSIONID".equalsIgnoreCase(name)) {
                hasSession = true;
            }
            if ("fid".equalsIgnoreCase(name) || "eid".equalsIgnoreCase(name)) {
                hasBrowserSessionToken = true;
            }
        }
        return hasSession && hasBrowserSessionToken;
    }

    private LoginStateValidation validateBidcenter(JsonNode root) {
        if (hasAuthCookie(root, "bidcenter.com.cn", List.of("user", "member", "token", "auth", "login", "passport"))) {
            return new LoginStateValidation(true, "检测到招标与采购网登录 cookie");
        }
        for (JsonNode origin : root.path("origins")) {
            if (!origin.path("origin").asText("").contains("bidcenter.com.cn")) {
                continue;
            }
            for (JsonNode item : origin.path("localStorage")) {
                String name = item.path("name").asText("").toLowerCase();
                String value = item.path("value").asText("");
                if (meaningful(value) && containsAny(name, List.of("user", "member", "token", "auth", "login"))) {
                    return new LoginStateValidation(true, "检测到招标与采购网登录本地状态");
                }
            }
        }
        return new LoginStateValidation(false, "仅检测到访客状态，未检测到招标与采购网登录态");
    }

    private boolean hasGenericAuthMarker(JsonNode root) {
        return hasAuthCookie(root, "", List.of("user", "member", "token", "auth", "login", "passport"));
    }

    private boolean hasAuthCookie(JsonNode root, String domainHint, List<String> markers) {
        for (JsonNode cookie : root.path("cookies")) {
            String domain = cookie.path("domain").asText("").toLowerCase();
            if (!domainHint.isBlank() && !domain.contains(domainHint)) {
                continue;
            }
            String name = cookie.path("name").asText("").toLowerCase();
            String value = cookie.path("value").asText("");
            if (!meaningful(value)) {
                continue;
            }
            if (containsAny(name, markers) && !name.contains("guest") && !name.startsWith("hm_")
                    && !name.contains("hmaccount")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean meaningful(String value) {
        String normalized = value == null ? "" : value.trim();
        return !normalized.isBlank()
                && !"undefined".equalsIgnoreCase(normalized)
                && !"null".equalsIgnoreCase(normalized)
                && !"0".equals(normalized);
    }

    private LocalDateTime savedAt(Path path) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
        } catch (Exception ignored) {
            return null;
        }
    }

    public String sourceKey(AppProperties.SourceConfig source) {
        if (source.getKey() != null && !source.getKey().isBlank()) {
            return source.getKey().trim();
        }
        String raw = source.getName() == null || source.getName().isBlank() ? "auth-source" : source.getName();
        if ((source.getUrl() != null && source.getUrl().contains("jianyu"))
                || raw.toLowerCase().contains("jianyu")
                || raw.contains("剑鱼")) {
            return "jianyu";
        }
        if ((source.getUrl() != null && source.getUrl().contains("bidcenter"))
                || raw.toLowerCase().contains("bidcenter")
                || raw.contains("招标与采购")
                || raw.contains("采招")) {
            return "bidcenter";
        }
        String normalized = raw.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "auth-source" : normalized;
    }

    public List<LoginDiagnosticEvent> diagnostics() {
        synchronized (diagnostics) {
            return diagnostics.stream().skip(Math.max(0, diagnostics.size() - 50)).toList();
        }
    }

    private BrowserType.LaunchOptions chromeLaunchOptions() {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setSlowMo(300)
                .setArgs(List.of("--disable-gpu"));
        Path chrome = findSystemChromiumExecutable();
        if (chrome != null) {
            options.setExecutablePath(chrome);
        }
        return options;
    }

    private Playwright createPlaywright() {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        return Playwright.create(new Playwright.CreateOptions().setEnv(env));
    }

    private Path findChromiumExecutable() {
        String localAppData = System.getenv("LOCALAPPDATA");
        List<Path> candidates = new ArrayList<>();
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(Path.of(localAppData, "ms-playwright", "chromium-1148", "chrome-win", "chrome.exe"));
        }
        candidates.addAll(List.of(
                Path.of("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"),
                Path.of("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"),
                Path.of("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"),
                Path.of("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe")));
        return candidates.stream().filter(Files::exists).findFirst().orElse(null);
    }

    private NavigateResult navigateLoginPage(Page page, String primaryUrl) {
        List<String> candidates = loginUrlCandidates(primaryUrl);
        Exception last = null;
        String lastFailure = "";
        for (String url : candidates) {
            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout(15000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(8000));
                String failure = browserErrorReason(page);
                if (!failure.isBlank()) {
                    lastFailure = failure;
                    record("LOGIN_PAGE_NAVIGATION_RETRY", "WARNING",
                            "登录页打开为浏览器错误页，继续尝试下一个入口：" + url + "，原因：" + failure);
                    continue;
                }
                return new NavigateResult(true, page.url(), "登录页已打开：" + page.url());
            } catch (Exception ex) {
                last = ex;
                record("LOGIN_PAGE_NAVIGATION_RETRY", "WARNING",
                        "登录页导航失败，尝试下一个入口：" + url + "，原因：" + ex.getClass().getSimpleName());
            }
        }
        return new NavigateResult(false, page.url(),
                "剑鱼登录页暂时无法自动打开：" + (!lastFailure.isBlank() ? lastFailure : last == null ? "未知原因" : last.getClass().getSimpleName()
                        + " - " + last.getMessage()));
    }

    private String browserErrorReason(Page page) {
        try {
            String url = page.url();
            String title = page.title();
            String body = page.textContent("body");
            String merged = (url + "\n" + title + "\n" + body).toLowerCase();
            if (merged.contains("chrome-error://") || merged.contains("无法访问此网站")
                    || merged.contains("err_failed") || merged.contains("err_connection_reset")
                    || merged.contains("err_timed_out") || merged.contains("err_name_not_resolved")) {
                return title == null || title.isBlank() ? "浏览器错误页" : title;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private List<String> loginUrlCandidates(String primaryUrl) {
        List<String> urls = new ArrayList<>();
        if (primaryUrl != null && !primaryUrl.isBlank()) {
            urls.add(primaryUrl);
            if (primaryUrl.startsWith("https://")) {
                urls.add("http://" + primaryUrl.substring("https://".length()));
            }
        }
        if (primaryUrl != null && primaryUrl.toLowerCase().contains("jianyu")) {
            urls.addAll(List.of(
                    "https://www.jianyu360.cn/login",
                    "https://www.jianyu360.cn/login.html",
                    "https://www.jianyu360.cn/#/login",
                    "https://www.jianyu360.cn/",
                    "http://www.jianyu360.cn/login",
                    "http://www.jianyu360.cn/login.html",
                    "http://www.jianyu360.cn/",
                    "https://www.jianyu360.com/login",
                    "https://www.jianyu360.com/login.html",
                    "https://www.jianyu360.com/#/login",
                    "https://www.jianyu360.com/",
                    "http://www.jianyu360.com/login",
                    "http://www.jianyu360.com/"));
        }
        return urls.stream().distinct().toList();
    }

    private Path findSystemChromiumExecutable() {
        List<Path> candidates = List.of(
                Path.of("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"),
                Path.of("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"),
                Path.of("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"),
                Path.of("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"));
        return candidates.stream().filter(Files::exists).findFirst().orElse(null);
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void waitForCdp(int port, Duration timeout) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/json/version");
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
            }
            Thread.sleep(300);
        }
        throw new IOException("Chrome remote debugging 端口未在 " + timeout.toSeconds() + " 秒内就绪");
    }

    @PreDestroy
    public void shutdown() {
        captures.values().forEach(CaptureSession::close);
        captureExecutor.shutdownNow();
    }

    private void cleanupExpiredCaptures() {
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMinutes(10));
        captures.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    private void record(String event, String status, String message) {
        synchronized (diagnostics) {
            diagnostics.add(new LoginDiagnosticEvent(event, status, message));
            if (diagnostics.size() > 100) {
                diagnostics.remove(0);
            }
        }
    }

    private record CaptureSession(String id, Playwright playwright, Browser browser,
            BrowserContext context, Page page, Process process, LocalDateTime createdAt) {
        void close() {
            try {
                if (browser != null) {
                    browser.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (playwright != null) {
                    playwright.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (process != null && process.isAlive()) {
                    process.destroy();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private record NavigateResult(boolean success, String url, String message) {
    }

    private record LoginStateValidation(boolean valid, String message) {
    }

    private static class LaunchResources {
        private volatile Playwright playwright;
        private volatile Browser browser;
        private volatile BrowserContext context;
        private volatile Page page;
        private volatile Process process;

        private synchronized void close() {
            try {
                if (browser != null) {
                    browser.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (playwright != null) {
                    playwright.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (process != null && process.isAlive()) {
                    process.destroy();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
