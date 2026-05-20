package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightEnvironmentChecker {
    public static final String INSTALL_MESSAGE =
            "Playwright 浏览器未安装，请执行 mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args=\"install\"。";

    private final AppProperties properties;

    public PlaywrightEnvironmentChecker(AppProperties properties) {
        this.properties = properties;
    }

    public CheckResult checkStorageState(Path path) {
        if (path == null || !Files.exists(path)) {
            return new CheckResult(false, "登录态文件不存在");
        }
        if (!Files.isReadable(path)) {
            return new CheckResult(false, "登录态文件不可读");
        }
        return new CheckResult(true, "登录态文件可读");
    }

    public CheckResult checkBrowserAvailability() {
        return browserCandidates().stream().anyMatch(Files::exists)
                ? new CheckResult(true, "Playwright/Chrome 浏览器可用")
                : new CheckResult(false, INSTALL_MESSAGE);
    }

    List<Path> browserCandidates() {
        List<Path> candidates = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(Path.of(localAppData, "ms-playwright", "chromium-1148", "chrome-win", "chrome.exe"));
            candidates.add(Path.of(localAppData, "ms-playwright", "chromium-1148", "chrome-win", "chrome.exe"));
        }
        candidates.add(Path.of(properties.getDataDir(), "login", "playwright-browser-marker"));
        candidates.addAll(List.of(
                Path.of("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"),
                Path.of("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"),
                Path.of("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"),
                Path.of("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe")));
        return candidates;
    }

    public record CheckResult(boolean available, String message) {
    }
}
