package com.staffplaytime.util;

import com.staffplaytime.Settings;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * A week is saved under the date of its first day, for example "2026-09-28".
 * The first day and the timezone come from config.yml.
 */
public final class WeekUtil {

    private WeekUtil() {
    }

    public static LocalDate currentWeekStart(Settings settings) {
        LocalDate today = LocalDate.now(settings.zone);
        return today.with(TemporalAdjusters.previousOrSame(settings.weekStart));
    }

    public static String currentWeekId(Settings settings) {
        return currentWeekStart(settings).toString();
    }

    public static String lastWeekId(Settings settings) {
        return currentWeekStart(settings).minusWeeks(1).toString();
    }
}
