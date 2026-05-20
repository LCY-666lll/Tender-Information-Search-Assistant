package com.xfusion.bidaggregator.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class ScheduleRule {
    public enum Type {
        ONCE, DAILY, WEEKLY
    }

    private Type type;
    private LocalTime time;
    private DayOfWeek dayOfWeek;

    public ScheduleRule() {
    }

    public ScheduleRule(Type type, LocalTime time, DayOfWeek dayOfWeek) {
        this.type = type;
        this.time = time;
        this.dayOfWeek = dayOfWeek;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getDisplayText() {
        String timeText = time == null ? "09:00" : time.toString();
        if (type == Type.DAILY) {
            return "每天 " + timeText;
        }
        if (type == Type.WEEKLY) {
            return "每周 " + (dayOfWeek == null ? "" : dayOfWeek) + " " + timeText;
        }
        return "一次 " + timeText;
    }
}
