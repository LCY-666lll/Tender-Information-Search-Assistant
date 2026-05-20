package com.xfusion.bidaggregator.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String dataDir = "data";
    private String reportsDir = "data/reports";
    private int requestTimeoutMs = 8000;
    private int maxItemsPerSource = 8;
    private String agentBaseUrl = "http://218.28.9.108:50053/v1";
    private String agentModel = "kimi-k2.6";
    private String agentApiKey = "";
    private int agentTimeoutSeconds = 45;
    private int loginWaitSeconds = 120;
    private List<SourceConfig> sources = new ArrayList<>();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getReportsDir() {
        return reportsDir;
    }

    public void setReportsDir(String reportsDir) {
        this.reportsDir = reportsDir;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getMaxItemsPerSource() {
        return maxItemsPerSource;
    }

    public void setMaxItemsPerSource(int maxItemsPerSource) {
        this.maxItemsPerSource = maxItemsPerSource;
    }

    public String getAgentBaseUrl() {
        return agentBaseUrl;
    }

    public void setAgentBaseUrl(String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl;
    }

    public String getAgentModel() {
        return agentModel;
    }

    public void setAgentModel(String agentModel) {
        this.agentModel = agentModel;
    }

    public String getAgentApiKey() {
        return agentApiKey;
    }

    public void setAgentApiKey(String agentApiKey) {
        this.agentApiKey = agentApiKey;
    }

    public int getAgentTimeoutSeconds() {
        return agentTimeoutSeconds;
    }

    public void setAgentTimeoutSeconds(int agentTimeoutSeconds) {
        this.agentTimeoutSeconds = agentTimeoutSeconds;
    }

    public int getLoginWaitSeconds() {
        return loginWaitSeconds;
    }

    public void setLoginWaitSeconds(int loginWaitSeconds) {
        this.loginWaitSeconds = loginWaitSeconds;
    }

    public List<SourceConfig> getSources() {
        return sources;
    }

    public void setSources(List<SourceConfig> sources) {
        this.sources = sources;
    }

    public static class SourceConfig {
        private String key;
        private String name;
        private String type;
        private String url;
        private String loginUrl;
        private String storageState;
        private boolean enabled = true;
        private boolean needLogin;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getLoginUrl() {
            return loginUrl == null || loginUrl.isBlank() ? url : loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        public String getStorageState() {
            return storageState;
        }

        public void setStorageState(String storageState) {
            this.storageState = storageState;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isNeedLogin() {
            return needLogin;
        }

        public void setNeedLogin(boolean needLogin) {
            this.needLogin = needLogin;
        }
    }
}
