package utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    private static final Path LOG_DIR = Path.of("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("latest.log");
    private static final Path OLD_LOG_FILE = LOG_DIR.resolve("previous.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static boolean fileUsable = true;
    private static boolean started;

    private Log() {
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message) {
        write("WARN", message, null);
    }

    public static void warn(String message, Throwable error) {
        write("WARN", message, error);
    }

    public static void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    private static synchronized void write(String level, String message, Throwable error) {
        StringBuilder line = new StringBuilder()
                .append('[').append(OffsetDateTime.now().format(TIME)).append("] [").append(level).append("] ")
                .append(message);
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            line.append('\n').append(trace.toString().stripTrailing());
        }

        boolean problem = !"INFO".equals(level);
        (problem ? System.err : System.out).println(line);
        appendToFile(line.append('\n').toString());
    }

    private static void appendToFile(String text) {
        if (!fileUsable) {
            return;
        }
        try {
            if (!started) {
                started = true;
                Files.createDirectories(LOG_DIR);

                if (Files.exists(LOG_FILE)) {
                    Files.move(LOG_FILE, OLD_LOG_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(LOG_FILE, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException exception) {
            fileUsable = false;
            System.err.println("Couldn't write logs/latest.log (" + exception.getMessage()
                    + "); logging to the console only.");
        }
    }
}
