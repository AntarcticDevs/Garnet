package loa;

import database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saves the Leave of Absence settings of each server and every request that was made. */
public final class LoaStore {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String DENIED = "DENIED";
    public static final String CANCELLED = "CANCELLED";

    private LoaStore() {
    }

    /** One server's setup. Null ids mean "not chosen yet". */
    public static final class Settings {
        public String guildId;
        public String channelId;
        public String roleId;
        public List<String> reviewerRoleIds = new ArrayList<>();
        public List<String> pingRoleIds = new ArrayList<>();
        public int minReason = 30;
        public int maxDays = 90;      // 0 = no limit
        public boolean blockAi;
        public String displayName;    // shown in titles and footers; null = the server name

        public boolean ready() {
            return channelId != null && roleId != null;
        }
    }

    public static final class Request {
        public int id;
        public String guildId;
        public String userId;
        public String reason;
        public String ign;
        public String rank;
        public long durationMs;
        public String status;
        public String channelId;
        public String messageId;
        public String reviewerId;
        public String denyReason;
        public long createdAt;
        public long reviewedAt;
        public long endsAt;
        public boolean ended;
    }

    public static void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS loa_settings (
                        guild_id TEXT PRIMARY KEY,
                        channel_id TEXT,
                        role_id TEXT,
                        reviewer_roles TEXT,
                        ping_roles TEXT,
                        min_reason INTEGER NOT NULL DEFAULT 30,
                        max_days INTEGER NOT NULL DEFAULT 90,
                        block_ai INTEGER NOT NULL DEFAULT 0,
                        display_name TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS loa_requests (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        ign TEXT,
                        rank TEXT,
                        duration_ms INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        channel_id TEXT,
                        message_id TEXT,
                        reviewer_id TEXT,
                        deny_reason TEXT,
                        created_at INTEGER NOT NULL,
                        reviewed_at INTEGER,
                        ends_at INTEGER,
                        ended INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_loa_requests_user ON loa_requests (guild_id, user_id)");
        }
    }

    // ---------------------------------------------------------------- settings

    public static Settings getSettings(String guildId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM loa_settings WHERE guild_id = ?")) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Settings settings = new Settings();
                settings.guildId = guildId;
                settings.channelId = resultSet.getString("channel_id");
                settings.roleId = resultSet.getString("role_id");
                settings.reviewerRoleIds = split(resultSet.getString("reviewer_roles"));
                settings.pingRoleIds = split(resultSet.getString("ping_roles"));
                settings.minReason = resultSet.getInt("min_reason");
                settings.maxDays = resultSet.getInt("max_days");
                settings.blockAi = resultSet.getInt("block_ai") != 0;
                settings.displayName = resultSet.getString("display_name");
                return settings;
            }
        }
    }

    public static void saveSettings(Settings settings) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO loa_settings (guild_id, channel_id, role_id, reviewer_roles, ping_roles,
                            min_reason, max_days, block_ai, display_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(guild_id) DO UPDATE SET channel_id = excluded.channel_id,
                            role_id = excluded.role_id, reviewer_roles = excluded.reviewer_roles,
                            ping_roles = excluded.ping_roles, min_reason = excluded.min_reason,
                            max_days = excluded.max_days, block_ai = excluded.block_ai,
                            display_name = excluded.display_name
                        """)) {
            statement.setString(1, settings.guildId);
            statement.setString(2, settings.channelId);
            statement.setString(3, settings.roleId);
            statement.setString(4, String.join(",", settings.reviewerRoleIds));
            statement.setString(5, String.join(",", settings.pingRoleIds));
            statement.setInt(6, settings.minReason);
            statement.setInt(7, settings.maxDays);
            statement.setInt(8, settings.blockAi ? 1 : 0);
            statement.setString(9, settings.displayName);
            statement.executeUpdate();
        }
    }

    private static List<String> split(String value) {
        List<String> ids = new ArrayList<>();
        if (value != null) {
            for (String id : value.split(",")) {
                if (!id.isBlank()) {
                    ids.add(id.trim());
                }
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- requests

    public static int createRequest(String guildId, String userId, String reason, String ign, String rank,
            long durationMs) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO loa_requests (guild_id, user_id, reason, ign, rank, duration_ms, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, guildId);
            statement.setString(2, userId);
            statement.setString(3, reason);
            statement.setString(4, ign);
            statement.setString(5, rank);
            statement.setLong(6, durationMs);
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The new LOA request has no id.");
                }
                return keys.getInt(1);
            }
        }
    }

    public static Request getRequest(String guildId, int id) throws SQLException {
        return queryOne("SELECT * FROM loa_requests WHERE guild_id = ? AND id = ?", guildId, id);
    }

    /** The member's pending request or running LOA, if they have one. */
    public static Request findOpen(String guildId, String userId) throws SQLException {
        return queryOne("SELECT * FROM loa_requests WHERE guild_id = ? AND user_id = ? "
                + "AND (status = 'PENDING' OR (status = 'APPROVED' AND ended = 0)) "
                + "ORDER BY id DESC LIMIT 1", guildId, userId);
    }

    public static void saveMessage(int id, String channelId, String messageId) throws SQLException {
        update("UPDATE loa_requests SET channel_id = ?, message_id = ? WHERE id = ?", channelId, messageId, id);
    }

    /** Approves a pending request. False if somebody else already handled it. */
    public static boolean approve(int id, String reviewerId, long endsAt) throws SQLException {
        return update("UPDATE loa_requests SET status = 'APPROVED', reviewer_id = ?, reviewed_at = ?, "
                + "ends_at = ? WHERE id = ? AND status = 'PENDING'",
                reviewerId, System.currentTimeMillis(), endsAt, id) > 0;
    }

    public static boolean deny(int id, String reviewerId, String reason) throws SQLException {
        return update("UPDATE loa_requests SET status = 'DENIED', reviewer_id = ?, reviewed_at = ?, "
                + "deny_reason = ? WHERE id = ? AND status = 'PENDING'",
                reviewerId, System.currentTimeMillis(), reason, id) > 0;
    }

    public static boolean cancel(int id) throws SQLException {
        return update("UPDATE loa_requests SET status = 'CANCELLED', reviewed_at = ? "
                + "WHERE id = ? AND status = 'PENDING'", System.currentTimeMillis(), id) > 0;
    }

    /** Marks a running LOA as over. False if it was already over. */
    public static boolean markEnded(int id) throws SQLException {
        return update("UPDATE loa_requests SET ended = 1 WHERE id = ? AND status = 'APPROVED' AND ended = 0", id) > 0;
    }

    /** Approved LOAs whose time has run out, in every server. */
    public static List<Request> due(long now) throws SQLException {
        return queryMany("SELECT * FROM loa_requests WHERE status = 'APPROVED' AND ended = 0 AND ends_at <= ?",
                now);
    }

    public static List<Request> active(String guildId) throws SQLException {
        return queryMany("SELECT * FROM loa_requests WHERE guild_id = ? AND status = 'APPROVED' AND ended = 0 "
                + "ORDER BY ends_at", guildId);
    }

    public static List<Request> pending(String guildId) throws SQLException {
        return queryMany("SELECT * FROM loa_requests WHERE guild_id = ? AND status = 'PENDING' ORDER BY id",
                guildId);
    }

    /** Reviewer id to {approved, denied}; most active first. */
    public static Map<String, int[]> reviewerStats(String guildId) throws SQLException {
        Map<String, int[]> stats = new LinkedHashMap<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT reviewer_id,
                               SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved,
                               SUM(CASE WHEN status = 'DENIED' THEN 1 ELSE 0 END) AS denied
                        FROM loa_requests
                        WHERE guild_id = ? AND reviewer_id IS NOT NULL AND status IN ('APPROVED', 'DENIED')
                        GROUP BY reviewer_id ORDER BY (approved + denied) DESC
                        """)) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stats.put(resultSet.getString(1), new int[] {resultSet.getInt(2), resultSet.getInt(3)});
                }
            }
        }
        return stats;
    }

    // ---------------------------------------------------------------- helpers

    private static int update(String sql, Object... values) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            return statement.executeUpdate();
        }
    }

    private static Request queryOne(String sql, Object... values) throws SQLException {
        List<Request> found = queryMany(sql, values);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<Request> queryMany(String sql, Object... values) throws SQLException {
        List<Request> requests = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Request request = new Request();
                    request.id = resultSet.getInt("id");
                    request.guildId = resultSet.getString("guild_id");
                    request.userId = resultSet.getString("user_id");
                    request.reason = resultSet.getString("reason");
                    request.ign = resultSet.getString("ign");
                    request.rank = resultSet.getString("rank");
                    request.durationMs = resultSet.getLong("duration_ms");
                    request.status = resultSet.getString("status");
                    request.channelId = resultSet.getString("channel_id");
                    request.messageId = resultSet.getString("message_id");
                    request.reviewerId = resultSet.getString("reviewer_id");
                    request.denyReason = resultSet.getString("deny_reason");
                    request.createdAt = resultSet.getLong("created_at");
                    request.reviewedAt = resultSet.getLong("reviewed_at");
                    request.endsAt = resultSet.getLong("ends_at");
                    request.ended = resultSet.getInt("ended") != 0;
                    requests.add(request);
                }
            }
        }
        return requests;
    }

    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }
}
