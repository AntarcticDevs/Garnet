package utils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class ConsoleLogger {
    private ConsoleLogger() {
    }

    public static void logConsole(String message) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String singleLineMessage = message.replace("\r", "\\r").replace("\n", "\\n");
        System.out.println("[" + timestamp + "] " + singleLineMessage);
    }
}
