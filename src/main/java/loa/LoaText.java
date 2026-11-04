package loa;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LoaText {
    private static final Pattern WHOLE = Pattern.compile("^(\\s*\\d{1,6}\\s*[mhdw]\\s*)+$");
    private static final Pattern PART = Pattern.compile("(\\d{1,6})\\s*([mhdw])");

    private LoaText() {
    }

    static long parseDuration(String input) {
        String text = input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
        if (!WHOLE.matcher(text).matches()) {
            return -1;
        }
        long total = 0;
        Matcher matcher = PART.matcher(text);
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            long unit = switch (matcher.group(2)) {
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
            total += value * unit;
        }
        return total;
    }

    static String formatDuration(long ms) {
        long minutes = ms / 60_000L;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append(days == 1 ? " day " : " days ");
        }
        if (hours > 0) {
            text.append(hours).append(hours == 1 ? " hour " : " hours ");
        }
        if (mins > 0 && days == 0) {
            text.append(mins).append(mins == 1 ? " minute" : " minutes");
        }
        return text.toString().trim();
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
