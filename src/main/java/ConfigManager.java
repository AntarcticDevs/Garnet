import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ConfigManager {
    private static final Path CONFIG_PATH = Path.of("config.yml");
    private static final String DEFAULT_CONFIG = """
            #---------------------------------------TOKEN---------------------------------------

            token: ''

            # General settings
            prefix: '!'
            language: en

            # Welcome system
            welcome-enabled: true
            welcome-channel: ''
            """;
    private static Map<String, Object> config;

    public static void createIfMissing() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
    }

    public static void load() throws IOException {
        Yaml yaml = new Yaml();

        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
            config = yaml.load(input);
        }
    }

    public static String getString(String key) {
        return (String) config.get(key);
    }

    public static boolean getBoolean(String key) {
        return (boolean) config.get(key);
    }
}