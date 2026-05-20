package com.xfusion.bidaggregator.controller;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.crawler.CrawlerRegistry;
import com.xfusion.bidaggregator.model.ApiResponse;
import com.xfusion.bidaggregator.service.LoginStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {
    private final CrawlerRegistry crawlerRegistry;
    private final LoginStateService loginStateService;

    public LoginController(CrawlerRegistry crawlerRegistry, LoginStateService loginStateService) {
        this.crawlerRegistry = crawlerRegistry;
        this.loginStateService = loginStateService;
    }

    @GetMapping("/api/login/{sourceKey}/status")
    public ApiResponse status(@PathVariable String sourceKey) {
        return loginStateService.statusResponse(loginSource(sourceKey));
    }

    @PostMapping("/api/login/{sourceKey}/capture/start")
    public ApiResponse startCapture(@PathVariable String sourceKey) {
        return loginStateService.startCapture(loginSource(sourceKey));
    }

    @PostMapping("/api/login/{sourceKey}/capture/{captureId}/save")
    public ApiResponse saveCapture(@PathVariable String sourceKey, @PathVariable String captureId) {
        return loginStateService.saveCapture(captureId, loginSource(sourceKey));
    }

    private AppProperties.SourceConfig loginSource(String sourceKey) {
        return crawlerRegistry.loginSource(sourceKey)
                .orElseThrow(() -> new IllegalStateException("未配置登录来源：" + sourceKey));
    }
}
