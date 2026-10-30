package config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private static final Path CONFIG_PATH = Path.of("config.yml");
    private static final String DEFAULT_CONFIG = """
            # -----------------------------------------------------------------------------
            # Bot configuration. Edit this file, save it, then start the bot again.
            # Tip: keep values inside quotes, for example  token: 'your-token-here'
            # -----------------------------------------------------------------------------

            # Your bot's token (https://discord.com/developers/applications -> your app -> Bot).
            # Never share it. On that same page, turn on "Server Members Intent" under
            # "Privileged Gateway Intents", otherwise the bot cannot start.
            token: ''

            # The name shown in /help, embed footers and the console.
            bot-name: 'Garnet'

            # Turn features on or off (true = on, false = off) and restart the bot afterwards.
            # A feature missing from this list stays on. Turning one off also removes its
            # slash commands from Discord and hides it from /help.
            modules:
              information: true   # /ping, /info, /userinfo, /avatar
              moderation: true    # /ban, /kick, /purge
              tickets: true       # ticket panels, ticket channels and transcripts
              welcomer: true      # welcome messages for new members
              embeds: true        # /embed and /rules

            # Optional links shown as buttons in /help. Leave a link blank to hide its button.
            support-url: ''
            terms-url: ''
            privacy-url: ''
            security-url: ''

            # Optional picture shown in /help. Leave blank to use the bot's own avatar.
            help-thumbnail-url: ''
            """;

    private static Map<String, Object> config = Map.of();

    /** Writes a commented default config.yml when none exists. Returns true if it was just created. */
    public static boolean createIfMissing() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            return false;
        }

        Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
        return true;
    }

    /**
     * Reads config.yml. An empty file is treated as "no settings"; a file that is not valid YAML or
     * is not a list of {@code key: value} settings is reported with a readable message.
     */
    public static void load() throws IOException {
        Object loaded;
        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
            loaded = new Yaml().load(input);
        } catch (YAMLException exception) {
            throw new IOException("config.yml is not valid YAML: " + exception.getMessage(), exception);
        }

        if (loaded == null) {
            config = Map.of();
            return;
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IOException("config.yml must contain settings written as  key: value");
        }

        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            values.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        config = values;
    }

    /**
     * The setting as trimmed text, or null when it is missing or blank. Numbers and booleans are
     * converted, so an ID written without quotes ({@code channel: 123456789012345678}) still works.
     */
    public static String getString(String key) {
        Object value = find(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static String getBotName() {
        String botName = getString("bot-name");
        return botName == null ? "Bot" : botName;
    }

    /** The setting as a boolean; accepts true/false/yes/no/on/off (quoted or not). Missing or unreadable gives the fallback. */
    public static boolean getBoolean(String key, boolean fallback) {
        String text = getString(key);
        if (text == null) {
            return fallback;
        }
        Boolean value = parseBoolean(text);
        return value == null ? fallback : value;
    }

    /** True when the text is something {@link #getBoolean} understands (true/false/yes/no/on/off). */
    public static boolean looksLikeBoolean(String text) {
        return text != null && parseBoolean(text.trim()) != null;
    }

    /** The names of the settings nested under a section such as {@code modules}; empty if it isn't a section. */
    public static Set<String> keysUnder(String path) {
        Set<String> keys = new LinkedHashSet<>();
        if (find(path) instanceof Map<?, ?> section) {
            for (Object key : section.keySet()) {
                keys.add(String.valueOf(key));
            }
        }
        return keys;
    }

    /** True when the setting exists and is a section ({@code name:} followed by indented settings). */
    public static boolean isSection(String path) {
        return find(path) instanceof Map<?, ?>;
    }

    private static Boolean parseBoolean(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> Boolean.TRUE;
            case "false", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** Looks a setting up by name, or by dotted path for nested settings such as {@code modules.tickets}. */
    private static Object find(String path) {
        if (config.containsKey(path)) {
            return config.get(path);
        }
        Object current = config;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            Object next = null;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (String.valueOf(entry.getKey()).equals(part)) {
                    next = entry.getValue();
                    break;
                }
            }
            current = next;
        }
        return current;
    }
}
