package com.xfusion.bidaggregator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QueryExpansionService {
    private static final int MAX_TERMS = 8;
    private static final Map<String, List<String>> EXPANSIONS = new LinkedHashMap<>();

    static {
        EXPANSIONS.put("软件服务", List.of("软件开发", "信息化服务", "系统建设", "系统运维", "平台建设", "数字化服务", "应用软件", "信息系统", "软件采购", "运维服务"));
        EXPANSIONS.put("服务器", List.of("计算服务器", "机架式服务器", "存储服务器", "硬件设备", "数据中心设备"));
        EXPANSIONS.put("充电桩", List.of("充电设施", "新能源充电", "充电站", "充电设备"));
    }

    public List<String> expand(String keyword) {
        List<String> terms = new ArrayList<>();
        addTerm(terms, keyword);
        if (keyword != null) {
            for (Map.Entry<String, List<String>> entry : EXPANSIONS.entrySet()) {
                if (keyword.contains(entry.getKey())) {
                    for (String expansion : entry.getValue()) {
                        addTerm(terms, expansion);
                    }
                }
            }
        }
        return terms.stream().limit(MAX_TERMS).toList();
    }

    private void addTerm(List<String> terms, String term) {
        if (term == null || term.isBlank() || terms.contains(term.trim())) {
            return;
        }
        terms.add(term.trim());
    }
}
