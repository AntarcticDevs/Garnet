package logging;

import database.Database;
import modules.BotModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import utils.Log;

import java.awt.Color;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class LogService {
    public static final Color GREEN = new Color(0x57F287);
    public static final Color RED = new Color(0xED4245);
    public static final Color YELLOW = new Color(0xFEE75C);
    public static final Color BLUE = new Color(0x5865F2);
    public static final Color GREY = new Color(0x99AAB5);

    public record Settings(String channelId, Set<LogCategory> disabled) {
        public boolean enabled(LogCategory category) {
            return !disabled.contains(category);
        }
    }

    private static final Map<String, Settings> CACHE = new ConcurrentHashMap<>();

    private LogService() {
    }

    public static Settings settings(String guildId) {
        Settings cached = CACHE.get(guildId);
        if (cached != null) {
            return cached;
        }
        Settings loaded;
        try {
            String[] row = Database.getLogSettings(guildId);
            loaded = row == null ? defaults(null) : new Settings(row[0], parse(row[1]));
        } catch (SQLException exception) {
            Log.error("LogService.settings failed", exception);
            return defaults(null);
        }
        CACHE.put(guildId, loaded);
        return loaded;
    }

    public static void setChannel(String guildId, String channelId) throws SQLException {
        Settings current = settings(guildId);
        save(guildId, new Settings(channelId, current.disabled()));
    }

    public static void setEnabled(String guildId, LogCategory category, boolean enabled) throws SQLException {
        Settings current = settings(guildId);
        Set<LogCategory> disabled = EnumSet.noneOf(LogCategory.class);
        disabled.addAll(current.disabled());
        if (enabled) {
            disabled.remove(category);
        } else {
            disabled.add(category);
        }
        save(guildId, new Settings(current.channelId(), disabled));
    }

    private static void save(String guildId, Settings settings) throws SQLException {
        String disabled = settings.disabled().stream().map(LogCategory::key).collect(Collectors.joining(","));
        Database.saveLogSettings(guildId, settings.channelId(), disabled);
        CACHE.put(guildId, settings);
    }

    private static Settings defaults(String channelId) {
        Set<LogCategory> disabled = EnumSet.noneOf(LogCategory.class);
        for (LogCategory category : LogCategory.values()) {
            if (!category.onByDefault()) {
                disabled.add(category);
            }
        }
        return new Settings(channelId, disabled);
    }

    private static Set<LogCategory> parse(String text) {
        Set<LogCategory> disabled = EnumSet.noneOf(LogCategory.class);
        if (text != null) {
            for (String part : text.split(",")) {
                LogCategory category = LogCategory.fromKey(part.trim());
                if (category != null) {
                    disabled.add(category);
                }
            }
        }
        return disabled;
    }

    public static EmbedBuilder embed(String title, Color colour) {
        return new EmbedBuilder().setTitle(title).setColor(colour).setTimestamp(Instant.now());
    }

    public static boolean wants(Guild guild, LogCategory category) {
        return BotModule.LOGGING.isEnabled() && guild != null
                && settings(guild.getId()).channelId() != null
                && settings(guild.getId()).enabled(category);
    }

    public static void send(Guild guild, LogCategory category, EmbedBuilder embed) {
        try {
            if (!wants(guild, category)) {
                return;
            }
            TextChannel channel = guild.getTextChannelById(settings(guild.getId()).channelId());
            if (channel == null || !guild.getSelfMember().hasPermission(channel,
                    Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)) {
                return;
            }
            channel.sendMessageEmbeds(embed.build()).queue(null,
                    error -> Log.warn("Couldn't post to the log channel of guild " + guild.getId() + ": " + error));
        } catch (RuntimeException exception) {
            Log.error("LogService.send failed", exception);
        }
    }
}
