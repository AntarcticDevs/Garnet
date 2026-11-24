package autoreact;

import database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public final class AutoReactStore {
    public static final int MAX_RULES = 25;

    private AutoReactStore() {
    }

    public enum Kind {
        ALL("every message"),
        ATTACHMENTS("messages with an image"),
        LINKS("messages with a link"),
        KEYWORD("messages containing a word");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        static Kind parse(String value) {
            for (Kind kind : values()) {
                if (kind.name().equalsIgnoreCase(value)) {
                    return kind;
                }
            }
            return ALL;
        }
    }

    public static final class Rule {
        public int id;
        public String guildId;
        public String channelId;
        public List<String> emojis = new ArrayList<>();
        public Kind kind = Kind.ALL;
        public String keyword;
        public boolean includeBots;
    }

    public static void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS autoreact_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        channel_id TEXT,
                        emojis TEXT NOT NULL,
                        kind TEXT NOT NULL DEFAULT 'ALL',
                        keyword TEXT,
                        include_bots INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_autoreact_guild ON autoreact_rules (guild_id)");
        }
    }

    public static int add(Rule rule) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO autoreact_rules (guild_id, channel_id, emojis, kind, keyword, include_bots)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, rule.guildId);
            if (rule.channelId == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, rule.channelId);
            }
            statement.setString(3, String.join(" ", rule.emojis));
            statement.setString(4, rule.kind.name());
            statement.setString(5, rule.keyword);
            statement.setInt(6, rule.includeBots ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public static boolean remove(String guildId, int id) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM autoreact_rules WHERE guild_id = ? AND id = ?")) {
            statement.setString(1, guildId);
            statement.setInt(2, id);
            return statement.executeUpdate() > 0;
        }
    }

    public static int count(String guildId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM autoreact_rules WHERE guild_id = ?")) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public static List<Rule> list(String guildId) throws SQLException {
        List<Rule> rules = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM autoreact_rules WHERE guild_id = ? ORDER BY id")) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Rule rule = new Rule();
                    rule.id = resultSet.getInt("id");
                    rule.guildId = guildId;
                    rule.channelId = resultSet.getString("channel_id");
                    rule.emojis = new ArrayList<>(Arrays.asList(resultSet.getString("emojis").split(" ")));
                    rule.kind = Kind.parse(resultSet.getString("kind"));
                    rule.keyword = resultSet.getString("keyword");
                    rule.includeBots = resultSet.getInt("include_bots") == 1;
                    rules.add(rule);
                }
            }
        }
        return rules;
    }
}
