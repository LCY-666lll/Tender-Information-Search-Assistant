package com.xfusion.bidaggregator.service;

import com.xfusion.bidaggregator.model.ScheduleRule;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class IntentParser {
    private static final Pattern RECENT_MONTH = Pattern.compile("最近\\s*([0-9一二两三四五六七八九十]+)\\s*个?月");
    private static final Pattern RECENT_HALF_YEAR = Pattern.compile("(最近|近)\\s*半\\s*年");
    private static final Pattern ABS_MONTH = Pattern.compile("(20\\d{2})\\s*年\\s*([0-9]{1,2})\\s*月份?");
    private static final Pattern CLOCK = Pattern.compile("([0-9]{1,2})\\s*(?::|：|点)\\s*([0-9]{1,2})?");
    private static final Map<String, String> REGIONS = new LinkedHashMap<>();
    private static final Map<String, String> CITY_TO_PROVINCE = new LinkedHashMap<>();

    static {
        String[] regions = {
                "北京", "上海", "天津", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽",
                "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南", "陕西",
                "甘肃", "青海", "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门", "全国"
        };
        for (String region : regions) {
            REGIONS.put(region, region);
            REGIONS.put(region + "省", region);
            REGIONS.put(region + "市", region);
            REGIONS.put(region + "区域", region);
        }
        CITY_TO_PROVINCE.put("杭州", "浙江");
        CITY_TO_PROVINCE.put("宁波", "浙江");
        CITY_TO_PROVINCE.put("温州", "浙江");
        CITY_TO_PROVINCE.put("南京", "江苏");
        CITY_TO_PROVINCE.put("苏州", "江苏");
        CITY_TO_PROVINCE.put("无锡", "江苏");
        CITY_TO_PROVINCE.put("合肥", "安徽");
        CITY_TO_PROVINCE.put("广州", "广东");
        CITY_TO_PROVINCE.put("深圳", "广东");
        CITY_TO_PROVINCE.put("东莞", "广东");
        CITY_TO_PROVINCE.put("成都", "四川");
        CITY_TO_PROVINCE.put("武汉", "湖北");
        CITY_TO_PROVINCE.put("长沙", "湖南");
        CITY_TO_PROVINCE.put("济南", "山东");
        CITY_TO_PROVINCE.put("青岛", "山东");
        CITY_TO_PROVINCE.put("郑州", "河南");
        CITY_TO_PROVINCE.put("西安", "陕西");
        CITY_TO_PROVINCE.put("福州", "福建");
        CITY_TO_PROVINCE.put("厦门", "福建");
    }

    public SearchIntent parse(String question) {
        String raw = question == null || question.isBlank()
                ? "最近1个月上海服务器招标信息有哪些"
                : question.trim();
        SearchIntent intent = new SearchIntent();
        intent.setRawQuestion(raw);
        parseRegion(raw, intent);
        parseTimeRange(raw, intent);
        intent.setScheduleRule(parseSchedule(raw));
        intent.setKeyword(parseKeyword(raw, intent));
        return intent;
    }

    private void parseRegion(String raw, SearchIntent intent) {
        for (Map.Entry<String, String> entry : CITY_TO_PROVINCE.entrySet()) {
            if (raw.contains(entry.getKey())) {
                intent.setProvince(entry.getValue());
                intent.setCity(entry.getKey());
                return;
            }
        }
        for (Map.Entry<String, String> entry : REGIONS.entrySet()) {
            if (raw.contains(entry.getKey())) {
                intent.setProvince(entry.getValue());
                return;
            }
        }
        intent.setProvince("全国");
    }

    private void parseTimeRange(String raw, SearchIntent intent) {
        Matcher abs = ABS_MONTH.matcher(raw);
        if (abs.find()) {
            YearMonth ym = YearMonth.of(Integer.parseInt(abs.group(1)), Integer.parseInt(abs.group(2)));
            intent.setStartTime(ym.atDay(1).atStartOfDay());
            intent.setEndTime(ym.atEndOfMonth().atTime(23, 59, 59));
            return;
        }

        int months = 1;
        Matcher halfYear = RECENT_HALF_YEAR.matcher(raw);
        if (halfYear.find()) {
            months = 6;
        }
        Matcher recent = RECENT_MONTH.matcher(raw);
        if (recent.find()) {
            months = parseNumber(recent.group(1));
        }
        LocalDate today = LocalDate.now();
        intent.setStartTime(today.minusMonths(Math.max(months, 1)).atStartOfDay());
        intent.setEndTime(today.atTime(23, 59, 59));
    }

    private ScheduleRule parseSchedule(String raw) {
        LocalTime time = parseClock(raw);
        if (raw.contains("每天") || raw.contains("每日")) {
            return new ScheduleRule(ScheduleRule.Type.DAILY, time, null);
        }
        if (raw.contains("每周") || raw.contains("每星期")) {
            return new ScheduleRule(ScheduleRule.Type.WEEKLY, time, parseDayOfWeek(raw));
        }
        if ((raw.contains("今天") || raw.contains("一次")) && (raw.contains("发送") || raw.contains("推送") || raw.contains("提醒"))) {
            return new ScheduleRule(ScheduleRule.Type.ONCE, time, null);
        }
        return null;
    }

    private LocalTime parseClock(String raw) {
        Matcher matcher = CLOCK.matcher(raw);
        if (!matcher.find()) {
            return LocalTime.of(9, 0);
        }
        int hour = Math.min(Integer.parseInt(matcher.group(1)), 23);
        int minute = matcher.group(2) == null || matcher.group(2).isBlank()
                ? 0
                : Math.min(Integer.parseInt(matcher.group(2)), 59);
        if ((raw.contains("下午") || raw.contains("晚上")) && hour < 12) {
            hour += 12;
        }
        return LocalTime.of(hour, minute);
    }

    private DayOfWeek parseDayOfWeek(String raw) {
        if (raw.contains("周二") || raw.contains("星期二")) return DayOfWeek.TUESDAY;
        if (raw.contains("周三") || raw.contains("星期三")) return DayOfWeek.WEDNESDAY;
        if (raw.contains("周四") || raw.contains("星期四")) return DayOfWeek.THURSDAY;
        if (raw.contains("周五") || raw.contains("星期五")) return DayOfWeek.FRIDAY;
        if (raw.contains("周六") || raw.contains("星期六")) return DayOfWeek.SATURDAY;
        if (raw.contains("周日") || raw.contains("周天") || raw.contains("星期日") || raw.contains("星期天")) {
            return DayOfWeek.SUNDAY;
        }
        return DayOfWeek.MONDAY;
    }

    private String parseKeyword(String raw, SearchIntent intent) {
        String keyword = raw;
        keyword = ABS_MONTH.matcher(keyword).replaceAll("");
        keyword = RECENT_MONTH.matcher(keyword).replaceAll("");
        keyword = RECENT_HALF_YEAR.matcher(keyword).replaceAll("");
        if (intent.getCity() != null) {
            keyword = keyword.replace(intent.getCity(), "");
            keyword = keyword.replace(intent.getCity() + "市", "");
        }
        keyword = keyword.replace(intent.getProvince(), "");
        keyword = keyword.replace(intent.getProvince() + "省", "").replace(intent.getProvince() + "市", "").replace(intent.getProvince() + "区域", "");
        keyword = keyword.replaceAll("每周[一二三四五六日天]?|每星期[一二三四五六日天]?|每天|每日|今天|上午|下午|晚上|早上", "");
        keyword = CLOCK.matcher(keyword).replaceAll("");
        keyword = keyword.replaceAll("汇总|发送|推送|提醒|给我|请|相关的|相关|区域内|范围内|最近|近|半年|半年度", "");
        keyword = keyword.replaceAll("招标信息|采购信息|招标公告|公告|信息|项目|有哪些|有哪一些|哪些|哪一些|都有|都|的|，|。|、|,|\\?|？", "");
        keyword = keyword.trim();
        return keyword.isBlank() ? "服务器" : keyword;
    }

    private int parseNumber(String value) {
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> Integer.parseInt(value);
        };
    }
}
