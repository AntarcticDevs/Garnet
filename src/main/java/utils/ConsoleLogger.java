package utils;

public final class ConsoleLogger {
    private ConsoleLogger() {
    }

    public static void logConsole(String message) {
        Log.info(message.replace("\r", "\\r").replace("\n", "\\n"));
    }
}
