package rolepanels;

import database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes role panels. The tables are created by {@link #createTables(Connection)} at startup. */
public final class RolePanelStore {

    private RolePanelStore() {
    }

    public static void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS role_panels (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        title TEXT,
                        description TEXT,
                        colour TEXT,
                        image_url TEXT,
                        thumbnail_url TEXT,
                        footer_text TEXT,
                        style TEXT NOT NULL DEFAULT 'BUTTONS',
                        mode TEXT NOT NULL DEFAULT 'TOGGLE',
                        max_roles INTEGER NOT NULL DEFAULT 0,
                        required_role_id TEXT,
                        blocked_role_id TEXT,
                        disabled INTEGER NOT NULL DEFAULT 0,
                        disabled_message TEXT,
                        channel_id TEXT,
                        message_id TEXT,
                        UNIQUE (guild_id, name)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS role_panel_roles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        panel_id INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        role_id TEXT NOT NULL,
                        label TEXT,
                        emoji TEXT,
                        description TEXT,
                        button_style TEXT,
                        UNIQUE (panel_id, role_id),
                        FOREIGN KEY (panel_id) REFERENCES role_panels(id) ON DELETE CASCADE
                    )
                    """);
        }
    }

    /** Creates or updates the panel (matched by server and name) together with its roles, in order. */
    public static void save(RolePanelData panel) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer existing = null;
                try (PreparedStatement lookup = connection.prepareStatement(
                        "SELECT id FROM role_panels WHERE guild_id = ? AND name = ?")) {
                    lookup.setString(1, panel.guildId);
                    lookup.setString(2, panel.name);
                    try (ResultSet resultSet = lookup.executeQuery()) {
                        if (resultSet.next()) {
                            existing = resultSet.getInt(1);
                        }
                    }
                }

                String sql = existing != null
                        ? "UPDATE role_panels SET title = ?, description = ?, colour = ?, image_url = ?, "
                                + "thumbnail_url = ?, footer_text = ?, style = ?, mode = ?, max_roles = ?, "
                                + "required_role_id = ?, blocked_role_id = ?, disabled = ?, disabled_message = ?, "
                                + "channel_id = ?, message_id = ? WHERE id = ?"
                        : "INSERT INTO role_panels (title, description, colour, image_url, thumbnail_url, "
                                + "footer_text, style, mode, max_roles, required_role_id, blocked_role_id, "
                                + "disabled, disabled_message, channel_id, message_id, guild_id, name) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, panel.title);
                    statement.setString(2, panel.description);
                    statement.setString(3, panel.colour);
                    statement.setString(4, panel.imageUrl);
                    statement.setString(5, panel.thumbnailUrl);
                    statement.setString(6, panel.footerText);
                    statement.setString(7, panel.style.name());
                    statement.setString(8, panel.mode.name());
                    statement.setInt(9, Math.max(0, panel.maxRoles));
                    statement.setString(10, panel.requiredRoleId);
                    statement.setString(11, panel.blockedRoleId);
                    statement.setInt(12, panel.disabled ? 1 : 0);
                    statement.setString(13, panel.disabledMessage);
                    statement.setString(14, panel.channelId);
                    statement.setString(15, panel.messageId);
                    if (existing != null) {
                        statement.setInt(16, existing);
                    } else {
                        statement.setString(16, panel.guildId);
                        statement.setString(17, panel.name);
                    }
                    statement.executeUpdate();
                    if (existing != null) {
                        panel.id = existing;
                    } else {
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException("The new role panel has no id.");
                            }
                            panel.id = keys.getInt(1);
                        }
                    }
                }

                replaceRoles(connection, panel);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Rewrites the panel's roles in order. Existing rows keep their ids, because buttons already posted
     * in Discord refer to them.
     */
    private static void replaceRoles(Connection connection, RolePanelData panel) throws SQLException {
        List<Integer> keep = new ArrayList<>();
        for (RolePanelData.PanelRole role : panel.roles) {
            if (role.id > 0) {
                keep.add(role.id);
            }
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM role_panel_roles WHERE panel_id = ?")) {
            select.setInt(1, panel.id);
            try (ResultSet resultSet = select.executeQuery();
                    PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM role_panel_roles WHERE id = ?")) {
                while (resultSet.next()) {
                    int rowId = resultSet.getInt(1);
                    if (!keep.contains(rowId)) {
                        delete.setInt(1, rowId);
                        delete.addBatch();
                    }
                }
                delete.executeBatch();
            }
        }
        // Positions are shifted out of the way first so reordering never trips the unique constraint.
        try (PreparedStatement shift = connection.prepareStatement(
                "UPDATE role_panel_roles SET position = position + 1000 WHERE panel_id = ?")) {
            shift.setInt(1, panel.id);
            shift.executeUpdate();
        }
        int position = 0;
        for (RolePanelData.PanelRole role : panel.roles) {
            if (role.id > 0) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE role_panel_roles SET position = ?, role_id = ?, label = ?, emoji = ?, "
                                + "description = ?, button_style = ? WHERE id = ?")) {
                    update.setInt(1, position);
                    update.setString(2, role.roleId);
                    update.setString(3, role.label);
                    update.setString(4, role.emoji);
                    update.setString(5, role.description);
                    update.setString(6, role.buttonStyle);
                    update.setInt(7, role.id);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO role_panel_roles (panel_id, position, role_id, label, emoji, description, "
                                + "button_style) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setInt(1, panel.id);
                    insert.setInt(2, position);
                    insert.setString(3, role.roleId);
                    insert.setString(4, role.label);
                    insert.setString(5, role.emoji);
                    insert.setString(6, role.description);
                    insert.setString(7, role.buttonStyle);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (keys.next()) {
                            role.id = keys.getInt(1);
                        }
                    }
                }
            }
            position++;
        }
    }

    public static RolePanelData getByName(String guildId, String name) throws SQLException {
        return query("SELECT * FROM role_panels WHERE guild_id = ? AND name = ? COLLATE NOCASE", guildId, name);
    }

    public static RolePanelData getById(String guildId, int panelId) throws SQLException {
        return query("SELECT * FROM role_panels WHERE guild_id = ? AND id = ?", guildId, panelId);
    }

    public static RolePanelData getByMessage(String guildId, String messageId) throws SQLException {
        return query("SELECT * FROM role_panels WHERE guild_id = ? AND message_id = ?", guildId, messageId);
    }

    private static RolePanelData query(String sql, String guildId, Object value) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            if (value instanceof Integer number) {
                statement.setInt(2, number);
            } else {
                statement.setString(2, (String) value);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? read(connection, resultSet) : null;
            }
        }
    }

    public static List<RolePanelData> list(String guildId) throws SQLException {
        List<RolePanelData> panels = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM role_panels WHERE guild_id = ? ORDER BY name COLLATE NOCASE")) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    panels.add(read(connection, resultSet));
                }
            }
        }
        return panels;
    }

    public static List<String> names(String guildId, String prefix, int limit) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT name FROM role_panels WHERE guild_id = ? AND name LIKE ? COLLATE NOCASE "
                                + "ORDER BY name COLLATE NOCASE LIMIT ?")) {
            statement.setString(1, guildId);
            statement.setString(2, prefix + "%");
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString(1));
                }
            }
        }
        return names;
    }

    public static boolean delete(String guildId, int panelId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM role_panels WHERE guild_id = ? AND id = ?")) {
            statement.setString(1, guildId);
            statement.setInt(2, panelId);
            return statement.executeUpdate() > 0;
        }
    }

    /** Remembers where the panel is posted. Pass null for both after the message was deleted. */
    public static void saveMessage(int panelId, String channelId, String messageId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE role_panels SET channel_id = ?, message_id = ? WHERE id = ?")) {
            statement.setString(1, channelId);
            statement.setString(2, messageId);
            statement.setInt(3, panelId);
            statement.executeUpdate();
        }
    }

    /** Forgets the posted message of whichever panel used it (the message was deleted). */
    public static void clearMessage(String guildId, String messageId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE role_panels SET message_id = NULL WHERE guild_id = ? AND message_id = ?")) {
            statement.setString(1, guildId);
            statement.setString(2, messageId);
            statement.executeUpdate();
        }
    }

    private static RolePanelData read(Connection connection, ResultSet resultSet) throws SQLException {
        RolePanelData panel = new RolePanelData();
        panel.id = resultSet.getInt("id");
        panel.guildId = resultSet.getString("guild_id");
        panel.name = resultSet.getString("name");
        panel.title = resultSet.getString("title");
        panel.description = resultSet.getString("description");
        panel.colour = resultSet.getString("colour");
        panel.imageUrl = resultSet.getString("image_url");
        panel.thumbnailUrl = resultSet.getString("thumbnail_url");
        panel.footerText = resultSet.getString("footer_text");
        panel.style = RolePanelData.Style.parse(resultSet.getString("style"));
        panel.mode = RolePanelData.Mode.parse(resultSet.getString("mode"));
        panel.maxRoles = resultSet.getInt("max_roles");
        panel.requiredRoleId = resultSet.getString("required_role_id");
        panel.blockedRoleId = resultSet.getString("blocked_role_id");
        panel.disabled = resultSet.getInt("disabled") != 0;
        panel.disabledMessage = resultSet.getString("disabled_message");
        panel.channelId = resultSet.getString("channel_id");
        panel.messageId = resultSet.getString("message_id");

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM role_panel_roles WHERE panel_id = ? ORDER BY position")) {
            statement.setInt(1, panel.id);
            try (ResultSet roles = statement.executeQuery()) {
                while (roles.next()) {
                    RolePanelData.PanelRole role = new RolePanelData.PanelRole();
                    role.id = roles.getInt("id");
                    role.roleId = roles.getString("role_id");
                    role.label = roles.getString("label");
                    role.emoji = roles.getString("emoji");
                    role.description = roles.getString("description");
                    role.buttonStyle = roles.getString("button_style");
                    panel.roles.add(role);
                }
            }
        }
        return panel;
    }
}
