package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xfusion.bidaggregator.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaywrightEnvironmentCheckerTest {
    @TempDir
    Path tempDir;

    @Test
    void checksStorageStateExistsAndReadable() throws Exception {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir.toString());
        PlaywrightEnvironmentChecker checker = new PlaywrightEnvironmentChecker(properties);
        Path state = tempDir.resolve("state.json");

        assertThat(checker.checkStorageState(state).available()).isFalse();

        Files.writeString(state, "{}");
        assertThat(checker.checkStorageState(state).available()).isTrue();
    }

    @Test
    void returnsInstallMessageWhenBrowserIsMissing() {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir.toString());
        PlaywrightEnvironmentChecker checker = new PlaywrightEnvironmentChecker(properties) {
            @Override
            List<Path> browserCandidates() {
                return List.of(tempDir.resolve("missing-browser.exe"));
            }
        };

        PlaywrightEnvironmentChecker.CheckResult result = checker.checkBrowserAvailability();

        assertThat(result.available()).isFalse();
        assertThat(result.message()).isEqualTo(PlaywrightEnvironmentChecker.INSTALL_MESSAGE);
    }
}
