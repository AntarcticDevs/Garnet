package utils;

/** Kept for existing callers; messages now go through {@link Log} so they also reach logs/latest.log. */
public final class ConsoleLogger {
    private ConsoleLogger() {
    }

    public static void logConsole(String message) {
        Log.info(message.replace("\r", "\\r").replace("\n", "\\n"));
    }
}
