package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.QueryResult;
import com.xfusion.bidaggregator.model.SearchIntent;
import com.xfusion.bidaggregator.model.SourceStatus;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordReportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void refusesToGenerateWhenNoSelectedItems() {
        WordReportService service = service();
        QueryResult result = result(false);

        assertThatThrownBy(() -> service.generate(result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请先勾选");
    }

    @Test
    void keepsUserQuestionCoreInSafeDocxFilename() {
        WordReportService service = service();
        QueryResult result = result(true);

        Path report = service.generate(result);

        assertThat(Files.exists(report)).isTrue();
        assertThat(report.getFileName().toString()).startsWith("最近3个月广东软件服务招标信息每天9_00发送_");
        assertThat(report.getFileName().toString()).endsWith(".docx");
    }

    @Test
    void writesOnlySelectedUserInformationAndScrubsTechnicalDiagnostics() throws Exception {
        WordReportService service = service();
        QueryResult result = result(true);
        SourceStatus status = new SourceStatus("中国政府采购网", "政府采购", false);
        status.setSuccess(false);
        status.setWarning("网络波动已跳过：SSLHandshakeException org.example.StackTrace");
        result.getSourceStatuses().add(status);

        Path report = service.generate(result);
        String text = docText(report);

        assertThat(text).contains("信息聚合简报");
        assertThat(text).contains("用户选择写入的信息");
        assertThat(text).contains("广东软件服务公开招标公告");
        assertThat(text).contains("本报告仅汇总用户选择写入的信息，关键事实以原文链接为准。");
        assertThat(text).doesNotContain("SSLHandshakeException");
        assertThat(text).doesNotContain("PlaywrightException");
        assertThat(text).doesNotContain("来源健康");
        assertThat(text).doesNotContain("评分点检查");
    }

    private WordReportService service() {
        AppProperties properties = new AppProperties();
        properties.setReportsDir(tempDir.resolve("reports").toString());
        return new WordReportService(properties);
    }

    private QueryResult result(boolean withItem) {
        QueryResult result = new QueryResult();
        result.setIntent(intent());
        result.setIncrementalSkipped(2);
        if (withItem) {
            BidItem item = new BidItem();
            item.setTitle("广东软件服务公开招标公告");
            item.setSourceName("中国政府采购网");
            item.setSourceType("政府采购");
            item.setSourceUrl("https://www.ccgp.gov.cn/a");
            item.setPublishTime(LocalDateTime.now().minusDays(1));
            item.setCoreContent("广东软件服务项目公开招标，采购内容包括系统建设、运维服务和现场支持，公告列明资格条件、文件获取方式、递交地点和截止时间。");
            item.getAttachmentLinks().add("https://www.ccgp.gov.cn/a.docx");
            result.getItems().add(item);
        }
        return result;
    }

    private SearchIntent intent() {
        SearchIntent intent = new SearchIntent();
        intent.setRawQuestion("最近3个月广东软件服务招标信息每天9:00发送");
        intent.setProvince("广东");
        intent.setKeyword("软件服务");
        intent.setStartTime(LocalDateTime.now().minusMonths(3));
        intent.setEndTime(LocalDateTime.now());
        return intent;
    }

    private String docText(Path report) throws Exception {
        try (InputStream input = Files.newInputStream(report); XWPFDocument document = new XWPFDocument(input)) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            return text.toString();
        }
    }
}
