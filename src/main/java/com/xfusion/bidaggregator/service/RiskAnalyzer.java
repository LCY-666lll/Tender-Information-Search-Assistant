package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.BidItem;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class RiskAnalyzer {
    public void analyze(BidItem item) {
        item.getRiskWarnings().clear();
        LocalDateTime now = LocalDateTime.now();
        if (item.getBidDeadline() != null) {
            long days = Duration.between(now, item.getBidDeadline()).toDays();
            if (days < 0) {
                item.getRiskWarnings().add("【高风险】投标截止时间已过，请仅作历史参考。");
            } else if (days <= 3) {
                item.getRiskWarnings().add("【高风险】距投标截止不足 3 天，请优先确认报名和投标准备。");
            } else if (days <= 7) {
                item.getRiskWarnings().add("【中风险】距投标截止不足 7 天，建议优先跟进。");
            }
        }
        if (item.getRegisterDeadline() != null && Duration.between(now, item.getRegisterDeadline()).toDays() <= 2) {
            item.getRiskWarnings().add("【高风险】报名截止时间接近，请尽快确认报名资格。");
        }
        String text = ((item.getTitle() == null ? "" : item.getTitle()) + " "
                + (item.getCoreContent() == null ? "" : item.getCoreContent()));
        if (text.contains("变更公告") || text.contains("澄清公告") || text.contains("二次公告") || text.contains("重新招标")) {
            item.getRiskWarnings().add("【提示】公告包含变更、澄清或二次招标信息，请关注版本变化。");
        }
        if ((item.getAttachmentLinks() == null || item.getAttachmentLinks().isEmpty())
                && (text.contains("附件") || text.contains("招标文件") || text.contains("采购文件"))) {
            item.getRiskWarnings().add("【提示】正文提到附件或招标文件，建议打开原文核对下载入口。");
        }
        if (text.contains("演示兜底")) {
            item.getRiskWarnings().add("【提示】该条目为无网络兜底内容，不可作为真实投标依据。");
        }
    }
}
