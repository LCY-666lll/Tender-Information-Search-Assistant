package com.xfusion.bidaggregator.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BidItem {
    private String id;
    private String title;
    private LocalDateTime publishTime;
    private String sourceName;
    private String sourceType;
    private String sourceUrl;
    private String region;
    private String coreContent;
    private List<String> attachmentLinks = new ArrayList<>();
    private LocalDateTime bidDeadline;
    private LocalDateTime registerDeadline;
    private List<String> riskWarnings = new ArrayList<>();
    private List<String> mergedSourceLinks = new ArrayList<>();
    private String contentHash;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCoreContent() {
        return coreContent;
    }

    public void setCoreContent(String coreContent) {
        this.coreContent = coreContent;
    }

    public List<String> getAttachmentLinks() {
        return attachmentLinks;
    }

    public void setAttachmentLinks(List<String> attachmentLinks) {
        this.attachmentLinks = attachmentLinks;
    }

    public LocalDateTime getBidDeadline() {
        return bidDeadline;
    }

    public void setBidDeadline(LocalDateTime bidDeadline) {
        this.bidDeadline = bidDeadline;
    }

    public LocalDateTime getRegisterDeadline() {
        return registerDeadline;
    }

    public void setRegisterDeadline(LocalDateTime registerDeadline) {
        this.registerDeadline = registerDeadline;
    }

    public List<String> getRiskWarnings() {
        return riskWarnings;
    }

    public void setRiskWarnings(List<String> riskWarnings) {
        this.riskWarnings = riskWarnings;
    }

    public List<String> getMergedSourceLinks() {
        return mergedSourceLinks;
    }

    public void setMergedSourceLinks(List<String> mergedSourceLinks) {
        this.mergedSourceLinks = mergedSourceLinks;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getDisplaySummary() {
        String text = coreContent == null || coreContent.isBlank() ? title : coreContent;
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) + "..." : cleaned;
    }

    public String getRiskSummary() {
        return riskWarnings == null || riskWarnings.isEmpty() ? "暂无明显风险" : String.join("；", riskWarnings);
    }

    public String getReferenceValue() {
        if (sourceName != null && sourceName.contains("全网搜索")) {
            return "线索页，建议打开原文确认";
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return "待复核";
        }
        return "可参考，来源可追溯";
    }
}
