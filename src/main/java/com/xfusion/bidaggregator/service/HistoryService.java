package com.xfusion.bidaggregator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public HistoryService(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public int filterAlreadySent(SearchIntent intent, List<BidItem> items) {
        Set<String> sent = readHistory(intent);
        int before = items.size();
        items.removeIf(item -> sent.contains(itemFingerprint(item)));
        return before - items.size();
    }

    public void markSent(SearchIntent intent, List<BidItem> items) {
        try {
            Path path = historyPath(intent);
            Files.createDirectories(path.getParent());
            Set<String> sent = readHistory(intent);
            for (BidItem item : items) {
                sent.add(itemFingerprint(item));
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), sent);
        } catch (Exception ex) {
            throw new IllegalStateException("写入增量历史失败", ex);
        }
    }

    public boolean clear(SearchIntent intent) {
        try {
            return Files.deleteIfExists(historyPath(intent));
        } catch (Exception ex) {
            throw new IllegalStateException("清空增量历史失败", ex);
        }
    }

    Set<String> readHistory(SearchIntent intent) {
        try {
            Path path = historyPath(intent);
            if (!Files.exists(path)) {
                return new HashSet<>();
            }
            return objectMapper.readValue(path.toFile(), new TypeReference<Set<String>>() {});
        } catch (Exception ex) {
            return new HashSet<>();
        }
    }

    Path historyPath(SearchIntent intent) {
        String rawQuestion = intent.getRawQuestion() == null ? "" : intent.getRawQuestion();
        String schedule = intent.getScheduleRule() == null
                ? "once"
                : intent.getScheduleRule().getType() + "_" + intent.getScheduleRule().getTime();
        String taskKey = (intent.getProvince() + "_" + intent.getKeyword() + "_" + schedule + "_" + shortHash(rawQuestion))
                .replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        return Path.of(properties.getDataDir(), "history", taskKey + ".json");
    }

    String itemFingerprint(BidItem item) {
        if (item == null) {
            return shortHash("empty");
        }
        String title = normalizeTitle(item.getTitle());
        LocalDate publishDate = item.getPublishTime() == null ? null : item.getPublishTime().toLocalDate();
        String domain = sourceDomain(item.getSourceUrl());
        String sourceUrl = item.getSourceUrl() == null ? "" : item.getSourceUrl().trim();
        String base = title + "|" + (publishDate == null ? "" : publishDate) + "|" + domain + "|" + sourceUrl;
        if (title.isBlank() && sourceUrl.isBlank() && item.getId() != null) {
            base = item.getId();
        }
        return shortHash(base);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceAll("\\s+", "")
                .replaceAll("[《》【】\\[\\]（）()，,。.!！?？:：;；\"'“”‘’]", "")
                .toLowerCase();
    }

    private String sourceDomain(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(sourceUrl).getHost();
            return host == null ? "" : host.replaceFirst("^www\\.", "");
        } catch (Exception ex) {
            return "";
        }
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(bytes.length, 12); i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }
}
