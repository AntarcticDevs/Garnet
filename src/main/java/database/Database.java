package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import java.sql.PreparedStatement;
import tickets.TicketData;
import tickets.TicketPanelData;
import tickets.TicketQuestion;

public class Database {

    private static final String URL = "jdbc:sqlite:garnet.db";

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static void initialize() throws SQLException {
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS embeds (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        title TEXT,
                        description TEXT,
                        footer_text TEXT,
                        footer_image TEXT,
                        image_url TEXT,
                        author_text TEXT,
                        author_image TEXT,
                        colour TEXT,
                        thumbnail_url TEXT
                    )
                    """);

            addColumnIfMissing(connection, "embeds", "title TEXT");
            addColumnIfMissing(connection, "embeds", "description TEXT");
            addColumnIfMissing(connection, "embeds", "footer_text TEXT");
            addColumnIfMissing(connection, "embeds", "footer_image TEXT");
            addColumnIfMissing(connection, "embeds", "image_url TEXT");
            addColumnIfMissing(connection, "embeds", "author_text TEXT");
            addColumnIfMissing(connection, "embeds", "author_image TEXT");
            addColumnIfMissing(connection, "embeds", "colour TEXT");
            addColumnIfMissing(connection, "embeds", "thumbnail_url TEXT");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS embed_fields (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        embed_id INTEGER NOT NULL,
                        field_name TEXT NOT NULL,
                        field_value TEXT NOT NULL,
                        field_position INTEGER NOT NULL,
                        is_inline INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (embed_id) REFERENCES embeds(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tickets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        channel_id TEXT,
                        creator_id TEXT,
                        state TEXT,
                        claimer_id TEXT,
                        claim_state TEXT,
                        category_name TEXT
                    )
                    """);
            addColumnIfMissing(connection, "tickets", "category_name TEXT");
            addColumnIfMissing(connection, "tickets", "panel_id INTEGER");

            ///////////////////////////////////// TICKET PANEL
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_panels (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        content TEXT,
                        total_categories INTEGER NOT NULL DEFAULT 0,
                        total_staff_roles INTEGER NOT NULL DEFAULT 0,
                        total_ping_roles INTEGER NOT NULL DEFAULT 0,
                        ticket_category_id TEXT,
                        UNIQUE (guild_id, name)
                    )
                    """);

            // Migrate databases created before newer ticket panel columns were added.
            addColumnIfMissing(connection, "ticket_panels", "total_categories INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "ticket_panels", "total_staff_roles INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "ticket_panels", "total_ping_roles INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "ticket_panels", "ticket_category_id TEXT");
            addColumnIfMissing(connection, "ticket_panels", "transcript_channel_id TEXT");

            addColumnIfMissing(connection, "ticket_panels", "embed_title TEXT");
            addColumnIfMissing(connection, "ticket_panels", "embed_colour TEXT");
            addColumnIfMissing(connection, "ticket_panels", "image_url TEXT");
            addColumnIfMissing(connection, "ticket_panels", "thumbnail_url TEXT");
            addColumnIfMissing(connection, "ticket_panels", "footer_text TEXT");
            addColumnIfMissing(connection, "ticket_panels", "button_label TEXT");
            addColumnIfMissing(connection, "ticket_panels", "button_emoji TEXT");
            addColumnIfMissing(connection, "ticket_panels", "button_style TEXT");
            addColumnIfMissing(connection, "ticket_panels", "channel_id TEXT");
            addColumnIfMissing(connection, "ticket_panels", "message_id TEXT");
            addColumnIfMissing(connection, "ticket_panels", "disabled INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "ticket_panels", "disabled_message TEXT");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_panel_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        panel_id INTEGER NOT NULL,
                        category_name TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        UNIQUE (panel_id, category_name),
                        FOREIGN KEY (panel_id) REFERENCES ticket_panels(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_panel_staff_roles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        panel_id INTEGER NOT NULL,
                        role_id TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        UNIQUE (panel_id, role_id),
                        FOREIGN KEY (panel_id) REFERENCES ticket_panels(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_panel_ping_roles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        panel_id INTEGER NOT NULL,
                        role_id TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        UNIQUE (panel_id, role_id),
                        FOREIGN KEY (panel_id) REFERENCES ticket_panels(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_panel_questions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        panel_id INTEGER NOT NULL,
                        category_name TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        prompt TEXT NOT NULL,
                        UNIQUE (panel_id, category_name, position),
                        FOREIGN KEY (panel_id) REFERENCES ticket_panels(id) ON DELETE CASCADE
                    )
                    """);

            // Per-question settings. Existing questions keep the old behaviour: paragraph and required.
            addColumnIfMissing(connection, "ticket_panel_questions", "paragraph INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, "ticket_panel_questions", "required INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, "ticket_panel_questions", "placeholder TEXT");
            addColumnIfMissing(connection, "ticket_panel_questions", "max_length INTEGER");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_rules (
                        guild_id TEXT PRIMARY KEY,
                        embed_id INTEGER NOT NULL,
                        FOREIGN KEY (embed_id) REFERENCES embeds(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_rule_embeds (
                    guild_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    embed_id INTEGER NOT NULL,
                    PRIMARY KEY (guild_id, position),
                    UNIQUE (guild_id, embed_id),
                    FOREIGN KEY (embed_id) REFERENCES embeds(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_config (
                        guild_id TEXT PRIMARY KEY,
                        category_id TEXT
                    )
                        """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS welcomer (
                        guild_id TEXT PRIMARY KEY,
                        channel_id TEXT,
                        embed_id INTEGER
                    )
                        """);

            addColumnIfMissing(connection, "welcomer", "embed_id INTEGER");

            statement.execute("""
                    INSERT OR IGNORE INTO guild_rule_embeds (guild_id, position, embed_id)
                    SELECT guild_id, 1, embed_id FROM guild_rules
                    """);
        }
    }

    private static void addColumnIfMissing(Connection connection, String tableName, String columnDefinition)
            throws SQLException {
        String columnName = columnDefinition.substring(0, columnDefinition.indexOf(' '));
        String tableInfoSql = "PRAGMA table_info(" + tableName + ")";
        boolean columnExists = false;

        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(tableInfoSql)) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    columnExists = true;
                    break;
                }
            }
        }

        if (!columnExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnDefinition);
            }
        }
    }

    public static void saveTicket(TicketData ticket) throws SQLException {

        String sql = """
            INSERT INTO tickets (
                channel_id,
                creator_id,
                state,
                claimer_id,
                claim_state,
                category_name,
                panel_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, ticket.channelId);
            statement.setString(2, ticket.creatorId);
            statement.setString(3, ticket.state);
            statement.setString(4, ticket.claimerId);
            statement.setString(5, ticket.claimState);
            statement.setString(6, ticket.categoryName);

            if (ticket.panelId == null) {
                statement.setNull(7, java.sql.Types.INTEGER);
            } else {
                statement.setInt(7, ticket.panelId);
            }

            statement.executeUpdate();

            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    ticket.id = resultSet.getInt(1);
                }
            }
        }
    }

    public static void updateTicket(TicketData ticket) throws SQLException {

        String sql = """
                UPDATE tickets
                    SET
                        channel_id = ?,
                        creator_id = ?,
                        state = ?,
                        claimer_id = ?,
                        claim_state = ?,
                        category_name = ?,
                        panel_id = ?
                    WHERE id = ?
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, ticket.channelId);
            statement.setString(2, ticket.creatorId);
            statement.setString(3, ticket.state);
            statement.setString(4, ticket.claimerId);
            statement.setString(5, ticket.claimState);
            statement.setString(6, ticket.categoryName);

            if (ticket.panelId == null) {
                statement.setNull(7, java.sql.Types.INTEGER);
            } else {
                statement.setInt(7, ticket.panelId);
            }

            statement.setInt(8, ticket.id);

            statement.executeUpdate();
        }
    }

    public static TicketData getTicketByChannelId(String ticketChannelId) throws SQLException {
        TicketData ticket = new TicketData();
        String sql = """
                SELECT *
                FROM tickets
                WHERE channel_id = ?
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ticketChannelId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                ticket.channelId = resultSet.getString("channel_id");
                ticket.id = resultSet.getInt("id");
                ticket.creatorId = resultSet.getString("creator_id");
                ticket.state = resultSet.getString("state");
                ticket.claimerId = resultSet.getString("claimer_id");
                ticket.claimState = resultSet.getString("claim_state");
                ticket.categoryName = resultSet.getString("category_name");
                int panelId = resultSet.getInt("panel_id");
                ticket.panelId = resultSet.wasNull() ? null : panelId;

                return ticket;
            }

            return null;

        }
    }

    public static void deleteTicketByChannelId(String channelId) throws SQLException {

        String sql = """
                DELETE FROM tickets
                WHERE channel_id = ?
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, channelId);
            statement.executeUpdate();
        }
    }

    public static void deleteTicketById(int ticketId) throws SQLException {

        String sql = """
                DELETE FROM tickets
                WHERE id = ?
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, ticketId);
            statement.executeUpdate();
        }
    }

    public static void saveTicketCategory(String guildId, String categoryId) throws SQLException {
        String sql = """
                INSERT INTO ticket_config (guild_id, category_id)
                VALUES (?, ?)
                ON CONFLICT(guild_id) DO UPDATE SET category_id = excluded.category_id
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildId);
            statement.setString(2, categoryId);

            statement.executeUpdate();
        }
    }

    public static String getTicketCategory(String guildId) throws SQLException {
        String sql = """
                SELECT category_id
                FROM ticket_config
                WHERE guild_id = ?
                """;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getString("category_id");
                }
            }
        }

        return null;
    }

    public static void savePanel(TicketPanelData panel) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Use an explicit lookup/update-or-insert so older databases without the
                // (guild_id, name) unique constraint can still save panels safely.
                boolean panelExists = false;
                try (PreparedStatement lookup = connection.prepareStatement(
                        "SELECT id FROM ticket_panels WHERE guild_id = ? AND name = ? ORDER BY id LIMIT 1")) {
                    lookup.setString(1, panel.guildId);
                    lookup.setString(2, panel.name);
                    try (ResultSet resultSet = lookup.executeQuery()) {
                        if (resultSet.next()) {
                            panel.id = resultSet.getInt("id");
                            panelExists = true;
                        }
                    }
                }

                String sql = panelExists
                        ? "UPDATE ticket_panels SET content = ?, ticket_category_id = ?, embed_title = ?, "
                                + "embed_colour = ?, image_url = ?, thumbnail_url = ?, footer_text = ?, "
                                + "button_label = ?, button_emoji = ?, button_style = ?, channel_id = ?, "
                                + "transcript_channel_id = ?, "
                                + "message_id = ?, disabled = ?, disabled_message = ? WHERE id = ?"
                        : "INSERT INTO ticket_panels (guild_id, name, content, ticket_category_id, embed_title, "
                                + "embed_colour, image_url, thumbnail_url, footer_text, button_label, "
                                + "button_emoji, button_style, channel_id, transcript_channel_id, message_id, "
                                + "disabled, disabled_message) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    int next = 1;
                    if (!panelExists) {
                        statement.setString(next++, panel.guildId);
                        statement.setString(next++, panel.name);
                    }
                    statement.setString(next++, panel.content);
                    statement.setString(next++, panel.ticketCategoryId);
                    statement.setString(next++, panel.embedTitle);
                    statement.setString(next++, panel.embedColour);
                    statement.setString(next++, panel.imageUrl);
                    statement.setString(next++, panel.thumbnailUrl);
                    statement.setString(next++, panel.footerText);
                    statement.setString(next++, panel.buttonLabel);
                    statement.setString(next++, panel.buttonEmoji);
                    statement.setString(next++, panel.buttonStyle);
                    statement.setString(next++, panel.channelId);
                    statement.setString(next++, panel.transcriptChannelId);
                    statement.setString(next++, panel.messageId);
                    statement.setInt(next++, panel.disabled ? 1 : 0);
                    statement.setString(next++, panel.disabledMessage);
                    if (panelExists) {
                        statement.setInt(next, panel.id);
                    }
                    statement.executeUpdate();

                    if (!panelExists) {
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (keys.next()) {
                                panel.id = keys.getInt(1);
                            }
                        }
                    }
                }

                if (panel.id <= 0) {
                    try (PreparedStatement lookup = connection.prepareStatement(
                            "SELECT id FROM ticket_panels WHERE guild_id = ? AND name = ? ORDER BY id LIMIT 1")) {
                        lookup.setString(1, panel.guildId);
                        lookup.setString(2, panel.name);
                        try (ResultSet resultSet = lookup.executeQuery()) {
                            if (!resultSet.next()) {
                                throw new SQLException("Saved ticket panel could not be reloaded.");
                            }
                            panel.id = resultSet.getInt("id");
                        }
                    }
                }

                replacePanelValues(connection, panel.id, "ticket_panel_categories", "category_name", panel.categories);
                replacePanelValues(connection, panel.id, "ticket_panel_staff_roles", "role_id", panel.staffRoles);
                replacePanelValues(connection, panel.id, "ticket_panel_ping_roles", "role_id", panel.pingRoles);
                try (PreparedStatement cleanup = connection.prepareStatement(
                        "DELETE FROM ticket_panel_questions WHERE panel_id = ? AND category_name NOT IN "
                                + "(SELECT category_name FROM ticket_panel_categories WHERE panel_id = ?)")) {
                    cleanup.setInt(1, panel.id);
                    cleanup.setInt(2, panel.id);
                    cleanup.executeUpdate();
                }

                panel.totalCategories = panel.categories.size();
                panel.totalStaffRoles = panel.staffRoles.size();
                panel.totalPingRoles = panel.pingRoles.size();

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE ticket_panels
                        SET total_categories = ?, total_staff_roles = ?, total_ping_roles = ?
                        WHERE id = ?
                        """)) {
                    statement.setInt(1, panel.totalCategories);
                    statement.setInt(2, panel.totalStaffRoles);
                    statement.setInt(3, panel.totalPingRoles);
                    statement.setInt(4, panel.id);
                    statement.executeUpdate();
                }

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void replacePanelValues(
            Connection connection,
            int panelId,
            String tableName,
            String valueColumn,
            List<String> values) throws SQLException {
        // Table and column names are internal constants selected by callers.
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + tableName + " WHERE panel_id = ?")) {
            delete.setInt(1, panelId);
            delete.executeUpdate();
        }

        if (values == null || values.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO " + tableName + " (panel_id, " + valueColumn + ", position) VALUES (?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            int position = 0;
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                insert.setInt(1, panelId);
                insert.setString(2, value);
                insert.setInt(3, position++);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<String> loadPanelValues(
            Connection connection,
            int panelId,
            String tableName,
            String valueColumn) throws SQLException {
        List<String> values = new java.util.ArrayList<>();
        String sql = "SELECT " + valueColumn + " FROM " + tableName
                + " WHERE panel_id = ? ORDER BY position, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, panelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString(valueColumn));
                }
            }
        }
        return values;
    }

    private static TicketPanelData readPanelData(Connection connection, ResultSet resultSet) throws SQLException {
        TicketPanelData panelData = new TicketPanelData();
        panelData.id = resultSet.getInt("id");
        panelData.guildId = resultSet.getString("guild_id");
        panelData.name = resultSet.getString("name");
        panelData.content = resultSet.getString("content");
        panelData.ticketCategoryId = resultSet.getString("ticket_category_id");
        panelData.transcriptChannelId = resultSet.getString("transcript_channel_id");
        panelData.embedTitle = resultSet.getString("embed_title");
        panelData.embedColour = resultSet.getString("embed_colour");
        panelData.imageUrl = resultSet.getString("image_url");
        panelData.thumbnailUrl = resultSet.getString("thumbnail_url");
        panelData.footerText = resultSet.getString("footer_text");
        panelData.buttonLabel = resultSet.getString("button_label");
        panelData.buttonEmoji = resultSet.getString("button_emoji");
        panelData.buttonStyle = resultSet.getString("button_style");
        panelData.channelId = resultSet.getString("channel_id");
        panelData.messageId = resultSet.getString("message_id");
        panelData.disabled = resultSet.getInt("disabled") != 0;
        panelData.disabledMessage = resultSet.getString("disabled_message");

        panelData.categories = loadPanelValues(connection, panelData.id, "ticket_panel_categories", "category_name");
        panelData.staffRoles = loadPanelValues(connection, panelData.id, "ticket_panel_staff_roles", "role_id");
        panelData.pingRoles = loadPanelValues(connection, panelData.id, "ticket_panel_ping_roles", "role_id");

        // Derive counts from the loaded lists so duplicate state cannot drift.
        panelData.totalCategories = panelData.categories.size();
        panelData.totalStaffRoles = panelData.staffRoles.size();
        panelData.totalPingRoles = panelData.pingRoles.size();
        return panelData;
    }

    public static TicketPanelData getPanelData(String guildId, String panelName) throws SQLException {
        String sql = "SELECT * FROM ticket_panels WHERE guild_id = ? AND name = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            statement.setString(2, panelName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readPanelData(connection, resultSet) : null;
            }
        }
    }

    public static TicketPanelData getPanelDataById(String guildId, int panelId) throws SQLException {
        String sql = "SELECT * FROM ticket_panels WHERE guild_id = ? AND id = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            statement.setInt(2, panelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readPanelData(connection, resultSet) : null;
            }
        }
    }

    /** Remembers where a panel was published so later edits can update that message. */
    public static void savePanelMessage(int panelId, String channelId, String messageId) throws SQLException {
        String sql = "UPDATE ticket_panels SET channel_id = ?, message_id = ? WHERE id = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channelId);
            statement.setString(2, messageId);
            statement.setInt(3, panelId);
            statement.executeUpdate();
        }
    }

    public static void savePanelTranscriptChannel(int panelId, String guildId, String channelId)
            throws SQLException {
        String sql = "UPDATE ticket_panels SET transcript_channel_id = ? WHERE id = ? AND guild_id = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channelId);
            statement.setInt(2, panelId);
            statement.setString(3, guildId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Ticket panel was not found while saving its transcript channel.");
            }
        }
    }

    /** Deletes a panel and (through ON DELETE CASCADE) its categories, roles and questions. */
    public static boolean deletePanel(String guildId, int panelId) throws SQLException {
        String sql = "DELETE FROM ticket_panels WHERE guild_id = ? AND id = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            statement.setInt(2, panelId);
            return statement.executeUpdate() == 1;
        }
    }

    public static boolean updatePanelContent(String guildId, int panelId, String content) throws SQLException {
        String sql = "UPDATE ticket_panels SET content = ? WHERE guild_id = ? AND id = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, content);
            statement.setString(2, guildId);
            statement.setInt(3, panelId);
            return statement.executeUpdate() == 1;
        }
    }

    public static List<TicketQuestion> getPanelQuestions(int panelId, String categoryName) throws SQLException {
        List<TicketQuestion> questions = new java.util.ArrayList<>();
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT prompt, paragraph, required, placeholder, max_length
                        FROM ticket_panel_questions
                        WHERE panel_id = ? AND category_name = ?
                        ORDER BY position
                        """)) {
            statement.setInt(1, panelId);
            statement.setString(2, categoryName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String prompt = resultSet.getString("prompt");
                    boolean paragraph = resultSet.getInt("paragraph") != 0;
                    boolean required = resultSet.getInt("required") != 0;
                    String placeholder = resultSet.getString("placeholder");
                    // wasNull() refers to the most recent column read, so check it right after this one.
                    int maxLengthValue = resultSet.getInt("max_length");
                    Integer maxLength = resultSet.wasNull() ? null : maxLengthValue;
                    questions.add(new TicketQuestion(prompt, paragraph, required, placeholder, maxLength));
                }
            }
        }
        return questions;
    }

    /** Replaces all questions of one category, keeping the given order. */
    public static void savePanelQuestions(int panelId, String categoryName, List<TicketQuestion> questions)
            throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM ticket_panel_questions WHERE panel_id = ? AND category_name = ?")) {
                    delete.setInt(1, panelId);
                    delete.setString(2, categoryName);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO ticket_panel_questions
                            (panel_id, category_name, position, prompt, paragraph, required, placeholder, max_length)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (int i = 0; i < questions.size(); i++) {
                        TicketQuestion question = questions.get(i);
                        insert.setInt(1, panelId);
                        insert.setString(2, categoryName);
                        insert.setInt(3, i);
                        insert.setString(4, question.prompt());
                        insert.setInt(5, question.paragraph() ? 1 : 0);
                        insert.setInt(6, question.required() ? 1 : 0);
                        insert.setString(7, question.placeholder());
                        if (question.maxLength() == null) {
                            insert.setNull(8, java.sql.Types.INTEGER);
                        } else {
                            insert.setInt(8, question.maxLength());
                        }
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
