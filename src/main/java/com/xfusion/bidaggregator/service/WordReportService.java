package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.QueryResult;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

@Service
public class WordReportService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final AppProperties properties;

    public WordReportService(AppProperties properties) {
        this.properties = properties;
    }

    public Path generate(QueryResult result) {
        if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
            throw new IllegalArgumentException("请先勾选要写入 Word 的信息。");
        }
        try {
            Files.createDirectories(Path.of(properties.getReportsDir()));
            String filename = sanitize(result.getIntent().getRawQuestion()) + "_"
                    + FILE_TIME.format(LocalDateTime.now()) + ".docx";
            Path path = Path.of(properties.getReportsDir(), filename);
            try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(path)) {
                title(document, "信息聚合简报");

                section(document, "用户问题");
                paragraph(document, safe(result.getIntent().getRawQuestion()), false);

                section(document, "检索条件");
                table(document, new String[][]{
                        {"关键词", safe(result.getIntent().getKeyword())},
                        {"地区", displayRegion(result)},
                        {"时间", display(result.getIntent().getStartTime()) + " 至 " + display(result.getIntent().getEndTime())},
                        {"频率", result.getFrequencyText()}
                });

                section(document, "用户选择写入的信息");
                int index = 1;
                for (BidItem item : result.getItems()) {
                    selectedItemBlock(document, index++, item);
                }

                section(document, "系统说明");
                paragraph(document, "本报告仅汇总用户选择写入的信息，关键事实以原文链接为准。", false);
                document.write(out);
            }
            return path;
        } catch (Exception ex) {
            throw new IllegalStateException("生成 Word 报告失败", ex);
        }
    }

    private void selectedItemBlock(XWPFDocument document, int index, BidItem item) {
        paragraph(document, index + ". " + displayTitle(item), true, 12);
        field(document, "发布时间", item.getPublishTime() == null ? "待核验" : DISPLAY_TIME.format(item.getPublishTime()));
        field(document, "来源链接", wrapUrl(item.getSourceUrl()));
        field(document, "核心内容摘要", summary(item));
        if (item.getAttachmentLinks() == null || item.getAttachmentLinks().isEmpty()) {
            field(document, "附件链接", "暂无附件");
        } else {
            field(document, "附件链接", "");
            int attachmentIndex = 1;
            for (String link : item.getAttachmentLinks().stream().limit(8).toList()) {
                paragraph(document, "  " + attachmentIndex++ + ". " + wrapUrl(link), false, 9);
            }
        }
        paragraph(document, "", false);
    }

    private void field(XWPFDocument document, String label, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(70);
        XWPFRun labelRun = paragraph.createRun();
        labelRun.setBold(true);
        labelRun.setFontFamily("Microsoft YaHei");
        labelRun.setFontSize(10);
        labelRun.setText(label + "：");
        XWPFRun valueRun = paragraph.createRun();
        valueRun.setFontFamily("Microsoft YaHei");
        valueRun.setFontSize(10);
        valueRun.setText(cleanForReport(value));
    }

    private String summary(BidItem item) {
        String text = firstNonBlank(item.getDisplaySummary(), item.getCoreContent(), "请打开原文核验详情。");
        return trim(cleanForReport(text), 220);
    }

    private String displayTitle(BidItem item) {
        String title = safe(item.getTitle());
        if (title.contains("打开全网检索") || title.contains("鎵撳紑鍏ㄧ綉")) {
            return firstNonBlank(item.getSourceUrl(), "可参考信息");
        }
        return firstNonBlank(title, item.getSourceUrl(), "可参考信息");
    }

    private String displayRegion(QueryResult result) {
        String city = result.getIntent().getCity();
        if (city != null && !city.isBlank()) {
            return city;
        }
        return safe(result.getIntent().getProvince());
    }

    private void title(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(220);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(20);
        run.setText(text);
    }

    private void section(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(180);
        paragraph.setSpacingAfter(90);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(14);
        run.setText(text);
    }

    private void paragraph(XWPFDocument document, String text, boolean bold) {
        paragraph(document, text, bold, 10);
    }

    private void paragraph(XWPFDocument document, String text, boolean bold, int fontSize) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setBold(bold);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(fontSize);
        run.setText(cleanForReport(text));
    }

    private void table(XWPFDocument document, String[][] rows) {
        XWPFTable table = document.createTable();
        fillRow(table.getRow(0), true, rows[0][0], rows[0][1]);
        for (int i = 1; i < rows.length; i++) {
            fillRow(table.createRow(), false, rows[i][0], rows[i][1]);
        }
    }

    private void fillRow(XWPFTableRow row, boolean header, String... values) {
        while (row.getTableCells().size() < values.length) {
            row.addNewTableCell();
        }
        for (int i = 0; i < values.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            if (header) {
                cell.setColor("EEF2F6");
            }
            cell.removeParagraph(0);
            XWPFParagraph paragraph = cell.addParagraph();
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("Microsoft YaHei");
            run.setFontSize(10);
            run.setBold(header || i == 0);
            run.setText(cleanForReport(values[i]));
        }
    }

    private String display(LocalDateTime time) {
        return time == null ? "待核验" : DISPLAY_TIME.format(time);
    }

    private String sanitize(String raw) {
        String cleaned = safe(raw).replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    private String wrapUrl(String raw) {
        return safe(raw)
                .replace("?", "?\n")
                .replace("&", "\n&")
                .replace("%", "%\u200B");
    }

    private String cleanForReport(String text) {
        return safe(text)
                .replaceAll("PlaywrightException|SSLHandshakeException|SocketTimeoutException|LOGIN_STATE_USED", "")
                .replaceAll("(?s)org\\.[A-Za-z0-9_.$: \\n\\r\\t-]+", "")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String trim(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String safe(String raw) {
        return raw == null ? "" : raw;
    }
}
