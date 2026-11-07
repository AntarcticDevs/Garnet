package counting;

import database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Saves the counting game: one channel per server, the current number and per-member stats. */
public final class CountingStore {
    private CountingStore() {
    }

    public static final class Settings {
        public String guildId;
        public String channelId;
        public int current;
        public int high;
        public String lastUserId;
        public boolean resetOnMistake = true;
        public boolean allowDouble;
    }

    public record Entry(String userId, int correct, int ruined) {
    }

    public static void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS counting_settings (
                        guild_id TEXT PRIMARY KEY,
                        channel_id TEXT NOT NULL,
                        current INTEGER NOT NULL DEFAULT 0,
                        high INTEGER NOT NULL DEFAULT 0,
                        last_user_id TEXT,
                        reset_on_mistake INTEGER NOT NULL DEFAULT 1,
                        allow_double INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS counting_users (
                        guild_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        correct INTEGER NOT NULL DEFAULT 0,
                        ruined INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (guild_id, user_id)
                    )
                    """);
        }
    }

    public static Settings get(String guildId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM counting_settings WHERE guild_id = ?")) {
            statement.setString(1, guildId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Settings settings = new Settings();
                settings.guildId = guildId;
                settings.channelId = rs.getString("channel_id");
                settings.current = rs.getInt("current");
                settings.high = rs.getInt("high");
                settings.lastUserId = rs.getString("last_user_id");
                settings.resetOnMistake = rs.getInt("reset_on_mistake") == 1;
                settings.allowDouble = rs.getInt("allow_double") == 1;
                return settings;
            }
        }
    }

    /** Sets the channel and options. The count and high score are kept when the channel stays the same. */
    public static void configure(String guildId, String channelId, boolean resetOnMistake, boolean allowDouble)
            throws SQLException {
        Settings old = get(guildId);
        boolean same = old != null && old.channelId.equals(channelId);
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO counting_settings
                            (guild_id, channel_id, current, high, last_user_id, reset_on_mistake, allow_double)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(guild_id) DO UPDATE SET channel_id = excluded.channel_id,
                            current = excluded.current, high = excluded.high, last_user_id = excluded.last_user_id,
                            reset_on_mistake = excluded.reset_on_mistake, allow_double = excluded.allow_double
                        """)) {
            statement.setString(1, guildId);
            statement.setString(2, channelId);
            statement.setInt(3, same ? old.current : 0);
            statement.setInt(4, old == null ? 0 : old.high);
            statement.setString(5, same ? old.lastUserId : null);
            statement.setInt(6, resetOnMistake ? 1 : 0);
            statement.setInt(7, allowDouble ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public static void saveProgress(String guildId, int current, int high, String lastUserId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE counting_settings SET current = ?, high = ?, last_user_id = ? WHERE guild_id = ?")) {
            statement.setInt(1, current);
            statement.setInt(2, high);
            statement.setString(3, lastUserId);
            statement.setString(4, guildId);
            statement.executeUpdate();
        }
    }

    public static void disable(String guildId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM counting_settings WHERE guild_id = ?")) {
            statement.setString(1, guildId);
            statement.executeUpdate();
        }
    }

    public static void addResult(String guildId, String userId, boolean correct) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO counting_users (guild_id, user_id, correct, ruined) VALUES (?, ?, ?, ?)
                        ON CONFLICT(guild_id, user_id) DO UPDATE SET
                            correct = correct + excluded.correct, ruined = ruined + excluded.ruined
                        """)) {
            statement.setString(1, guildId);
            statement.setString(2, userId);
            statement.setInt(3, correct ? 1 : 0);
            statement.setInt(4, correct ? 0 : 1);
            statement.executeUpdate();
        }
    }

    public static List<Entry> top(String guildId, int limit) throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT user_id, correct, ruined FROM counting_users
                        WHERE guild_id = ? AND correct > 0 ORDER BY correct DESC LIMIT ?
                        """)) {
            statement.setString(1, guildId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new Entry(rs.getString(1), rs.getInt(2), rs.getInt(3)));
                }
            }
        }
        return entries;
    }
}
