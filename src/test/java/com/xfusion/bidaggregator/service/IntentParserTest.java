package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xfusion.bidaggregator.model.ScheduleRule;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class IntentParserTest {
    private final IntentParser parser = new IntentParser();

    @Test
    void parsesRecentOneMonthAnhuiServer() {
        SearchIntent intent = parser.parse("最近1个月安徽服务器招标信息有哪些");

        assertThat(intent.getKeyword()).isEqualTo("服务器");
        assertThat(intent.getProvince()).isEqualTo("安徽");
        assertThat(intent.getStartTime().toLocalDate()).isEqualTo(LocalDate.now().minusMonths(1));
        assertThat(intent.getEndTime().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(intent.getScheduleRule()).isNull();
    }

    @Test
    void parsesRecentHalfYearNationwideServer() {
        SearchIntent intent = parser.parse("最近半年全国服务器招标信息都有哪些。");

        assertThat(intent.getKeyword()).isEqualTo("服务器");
        assertThat(intent.getProvince()).isEqualTo("全国");
        assertThat(intent.getStartTime().toLocalDate()).isEqualTo(LocalDate.now().minusMonths(6));
        assertThat(intent.getEndTime().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(intent.getScheduleRule()).isNull();
    }

    @Test
    void parsesCityAndKeepsKeywordClean() {
        SearchIntent intent = parser.parse("最近5个月杭州服务器招标信息有哪些");

        assertThat(intent.getKeyword()).isEqualTo("服务器");
        assertThat(intent.getProvince()).isEqualTo("浙江");
        assertThat(intent.getCity()).isEqualTo("杭州");
        assertThat(intent.getStartTime().toLocalDate()).isEqualTo(LocalDate.now().minusMonths(5));
    }

    @Test
    void parsesShanghaiChargingPileAbsoluteMonth() {
        SearchIntent intent = parser.parse("2026年3月份上海充电桩招标信息有哪些");

        assertThat(intent.getKeyword()).isEqualTo("充电桩");
        assertThat(intent.getProvince()).isEqualTo("上海");
        assertThat(intent.getStartTime().toLocalDate().toString()).isEqualTo("2026-03-01");
        assertThat(intent.getEndTime().toLocalDate().toString()).isEqualTo("2026-03-31");
        assertThat(intent.getScheduleRule()).isNull();
    }

    @Test
    void parsesGuangdongSoftwareServiceDailySchedule() {
        SearchIntent intent = parser.parse("最近3个月广东软件服务招标信息每天9:00发送");

        assertThat(intent.getKeyword()).isEqualTo("软件服务");
        assertThat(intent.getProvince()).isEqualTo("广东");
        assertThat(intent.getStartTime().toLocalDate()).isEqualTo(LocalDate.now().minusMonths(3));
        assertThat(intent.getScheduleRule().getType()).isEqualTo(ScheduleRule.Type.DAILY);
        assertThat(intent.getScheduleRule().getTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void parsesBeijingChargingPileAbsoluteMonth() {
        SearchIntent intent = parser.parse("2026年4月份北京充电桩相关的招标信息有哪些");

        assertThat(intent.getKeyword()).isEqualTo("充电桩");
        assertThat(intent.getProvince()).isEqualTo("北京");
        assertThat(intent.getStartTime().toLocalDate().toString()).isEqualTo("2026-04-01");
        assertThat(intent.getEndTime().toLocalDate().toString()).isEqualTo("2026-04-30");
    }

    @Test
    void parsesWeeklyMondayMorningSchedule() {
        SearchIntent intent = parser.parse("每周一上午9点汇总最近1个月安徽服务器招标信息");

        assertThat(intent.getKeyword()).isEqualTo("服务器");
        assertThat(intent.getProvince()).isEqualTo("安徽");
        assertThat(intent.getScheduleRule().getType()).isEqualTo(ScheduleRule.Type.WEEKLY);
        assertThat(intent.getScheduleRule().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(intent.getScheduleRule().getTime()).isEqualTo(LocalTime.of(9, 0));
    }
}
