package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueryExpansionServiceTest {
    private final QueryExpansionService service = new QueryExpansionService();

    @Test
    void expandsSoftwareServiceAndKeepsOriginalWithinLimit() {
        assertThat(service.expand("软件服务"))
                .containsExactly("软件服务", "软件开发", "信息化服务", "系统建设", "系统运维", "平台建设", "数字化服务", "应用软件")
                .hasSize(8);
    }

    @Test
    void expandsServerAndChargingPile() {
        assertThat(service.expand("服务器")).contains("服务器", "计算服务器", "机架式服务器", "存储服务器");
        assertThat(service.expand("充电桩")).contains("充电桩", "充电设施", "新能源充电", "充电站", "充电设备");
    }
}
