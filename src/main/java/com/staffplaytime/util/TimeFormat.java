package com.staffplaytime.util;

/** Turns a number of seconds into text like "2d 5h 10m". */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String format(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0m";
        }
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + seconds + "s";
    }
}
