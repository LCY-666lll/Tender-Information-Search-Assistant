package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.ApiResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoginStateServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsMissingAndRejectsGuestOnlyJianyuState() throws Exception {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir.resolve("data").toString());
        LoginStateService service = new LoginStateService(properties, new ObjectMapper());
        AppProperties.SourceConfig source = new AppProperties.SourceConfig();
        source.setName("剑鱼标讯");
        source.setUrl("https://www.jianyu360.cn/");
        source.setNeedLogin(true);

        ApiResponse missing = service.statusResponse(source);
        assertThat(missing.getData().get("status")).isEqualTo("MISSING");

        Path statePath = service.statePath(source);
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath, """
                {"cookies":[{"name":"JYGuestUID","value":"guest","domain":"www.jianyu360.cn"}],
                "origins":[{"origin":"https://www.jianyu360.cn","localStorage":[{"name":"BIGMEMBER_PC","value":"undefined"}]}]}
                """);

        ApiResponse invalid = service.statusResponse(source);
        assertThat(invalid.getData().get("status")).isEqualTo("INVALID");
        assertThat(invalid.getData().get("exists")).isEqualTo(false);
        assertThat(invalid.getData().get("statePath").toString()).endsWith("jianyu-state.json");
    }

    @Test
    void reportsSavedWhenJianyuMemberStateExists() throws Exception {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir.resolve("data").toString());
        LoginStateService service = new LoginStateService(properties, new ObjectMapper());
        AppProperties.SourceConfig source = new AppProperties.SourceConfig();
        source.setName("剑鱼标讯");
        source.setUrl("https://www.jianyu360.cn/");
        source.setNeedLogin(true);

        Path statePath = service.statePath(source);
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath, """
                {"cookies":[],"origins":[{"origin":"https://www.jianyu360.cn",
                "localStorage":[{"name":"BIGMEMBER_PC","value":"{\\"memberId\\":\\"10001\\"}"}]}]}
                """);

        ApiResponse saved = service.statusResponse(source);
        assertThat(saved.getData().get("status")).isEqualTo("SAVED");
        assertThat(saved.getData().get("exists")).isEqualTo(true);
    }
}
