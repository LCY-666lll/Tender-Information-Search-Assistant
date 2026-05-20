package com.xfusion.bidaggregator.model;

public class SourceStatus {
    private String sourceName;
    private String sourceType;
    private boolean needLogin;
    private boolean success;
    private int fetchedCount;
    private int selectedCount;
    private long elapsedMs;
    private String warning;

    public SourceStatus() {
    }

    public SourceStatus(String sourceName, String sourceType, boolean needLogin) {
        this.sourceName = sourceName;
        this.sourceType = sourceType;
        this.needLogin = needLogin;
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

    public boolean isNeedLogin() {
        return needLogin;
    }

    public void setNeedLogin(boolean needLogin) {
        this.needLogin = needLogin;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getFetchedCount() {
        return fetchedCount;
    }

    public void setFetchedCount(int fetchedCount) {
        this.fetchedCount = fetchedCount;
    }

    public int getSelectedCount() {
        return selectedCount;
    }

    public void setSelectedCount(int selectedCount) {
        this.selectedCount = selectedCount;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getUserMessage() {
        if (warning == null || warning.isBlank()) {
            if (success && selectedCount > 0) {
                return "来源可用，已纳入本次汇总。";
            }
            if (success) {
                return "来源可访问，本次没有符合关键词、地区和时间范围的有效公告。";
            }
            if (fetchedCount > 0) {
                return "候选线索已处理，但被清洗规则过滤为跳转页、首页或弱相关内容。";
            }
            return "来源暂不可用，已自动跳过，不影响其他来源和 Word 生成。";
        }
        if (warning.contains("未保存合法登录态")) {
            return "登录态不可用，公开来源继续执行。";
        }
        if (warning.contains("storageState")) {
            return "已使用本地登录态读取。";
        }
        if (warning.contains("全网搜索")) {
            return "全网搜索暂时不稳定，已保留固定来源结果。";
        }
        if (warning.contains("演示样例") || warning.contains("兜底")) {
            return "该来源本次没有命中足够结果，已降级处理。";
        }
        if (warning.contains("SSL") || warning.contains("SocketTimeout") || warning.contains("HTTP")) {
            return "该来源网络访问不稳定，已自动跳过。";
        }
        return warning;
    }

    public String getStatusLabel() {
        if (success && selectedCount > 0) {
            return "已采用 " + selectedCount + " 条";
        }
        if (success) {
            return "已处理无命中";
        }
        if (warning != null && warning.contains("已处理但无命中")) {
            return "已处理无命中";
        }
        if (fetchedCount > 0) {
            return "候选已过滤";
        }
        if (warning != null && warning.contains("未保存合法登录态")) {
            return "登录态不可用";
        }
        if (warning != null && (warning.contains("SSL") || warning.contains("SocketTimeout")
                || warning.contains("HTTP"))) {
            return "网络波动";
        }
        return "已跳过";
    }

    public String getStatusClass() {
        if (success) {
            return "status ok";
        }
        if (warning != null && warning.contains("未保存合法登录态")) {
            return "status login";
        }
        return "status warn";
    }

    public String getSourceTypeLabel() {
        return needLogin ? "登录来源" : "公开来源";
    }
}
