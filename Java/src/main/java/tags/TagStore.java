package tags;

import database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;


public final class TagStore {
    public static final int MAX_TAGS = 200;

    private TagStore() {
    }

    public static final class Tag {
        public int id;
        public String guildId;
        public String name;
        public String content;
        public Long embedId;
        public String requiredRoleId;
        public int uses;
    }

    public static void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        content TEXT,
                        embed_id INTEGER,
                        required_role_id TEXT,
                        uses INTEGER NOT NULL DEFAULT 0,
                        created_by TEXT,
                        created_at INTEGER NOT NULL,
                        UNIQUE (guild_id, name),
                        FOREIGN KEY (embed_id) REFERENCES embeds(id) ON DELETE SET NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tag_settings (
                        guild_id TEXT PRIMARY KEY,
                        prefix TEXT
                    )
                    """);
        }
    }



    public static Tag get(String guildId, String name) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM tags WHERE guild_id = ? AND name = ?")) {
            statement.setString(1, guildId);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Tag tag = new Tag();
                tag.id = resultSet.getInt("id");
                tag.guildId = guildId;
                tag.name = resultSet.getString("name");
                tag.content = resultSet.getString("content");
                long embed = resultSet.getLong("embed_id");
                tag.embedId = resultSet.wasNull() ? null : embed;
                tag.requiredRoleId = resultSet.getString("required_role_id");
                tag.uses = resultSet.getInt("uses");
                return tag;
            }
        }
    }

    public static int count(String guildId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM tags WHERE guild_id = ?")) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }


    public static void save(Tag tag, String createdBy) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO tags (guild_id, name, content, embed_id, required_role_id, created_by, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(guild_id, name) DO UPDATE SET content = excluded.content,
                            embed_id = excluded.embed_id, required_role_id = excluded.required_role_id
                        """)) {
            statement.setString(1, tag.guildId);
            statement.setString(2, tag.name);
            statement.setString(3, tag.content);
            if (tag.embedId == null) {
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setLong(4, tag.embedId);
            }
            statement.setString(5, tag.requiredRoleId);
            statement.setString(6, createdBy);
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public static boolean delete(String guildId, String name) throws SQLException {
        return update("DELETE FROM tags WHERE guild_id = ? AND name = ?", guildId, name) > 0;
    }

    public static void countUse(int id) throws SQLException {
        update("UPDATE tags SET uses = uses + 1 WHERE id = ?", id);
    }

    public static List<Tag> list(String guildId) throws SQLException {
        List<Tag> tags = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT name, embed_id, required_role_id, uses FROM tags WHERE guild_id = ? ORDER BY name")) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Tag tag = new Tag();
                    tag.guildId = guildId;
                    tag.name = resultSet.getString("name");
                    long embed = resultSet.getLong("embed_id");
                    tag.embedId = resultSet.wasNull() ? null : embed;
                    tag.requiredRoleId = resultSet.getString("required_role_id");
                    tag.uses = resultSet.getInt("uses");
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    public static List<String> names(String guildId, String typed, int limit) throws SQLException {
        return strings("SELECT name FROM tags WHERE guild_id = ? AND name LIKE ? ESCAPE '\\' ORDER BY name LIMIT ?",
                guildId, like(typed), limit);
    }



    public static Long embedIdByName(String guildId, String name) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM embeds WHERE guild_id = ? AND name = ?")) {
            statement.setString(1, guildId);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    public static List<String> embedNames(String guildId, String typed, int limit) throws SQLException {
        return strings("SELECT name FROM embeds WHERE guild_id = ? AND name LIKE ? ESCAPE '\\' ORDER BY name LIMIT ?",
                guildId, like(typed), limit);
    }



    public static String getPrefix(String guildId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT prefix FROM tag_settings WHERE guild_id = ?")) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                String prefix = resultSet.getString(1);
                return prefix == null ? "" : prefix;
            }
        }
    }


    public static void setPrefix(String guildId, String prefix) throws SQLException {
        update("INSERT INTO tag_settings (guild_id, prefix) VALUES (?, ?) "
                + "ON CONFLICT(guild_id) DO UPDATE SET prefix = excluded.prefix", guildId, prefix);
    }



    private static String like(String typed) {
        String text = typed == null ? "" : typed.toLowerCase();
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static List<String> strings(String sql, Object... values) throws SQLException {
        List<String> found = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    found.add(resultSet.getString(1));
                }
            }
        }
        return found;
    }

    private static int update(String sql, Object... values) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement.executeUpdate();
        }
    }
}
