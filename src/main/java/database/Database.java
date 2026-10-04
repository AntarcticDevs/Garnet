package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.sql.PreparedStatement;
import tickets.TicketData;
import tickets.TicketPanelData;

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
                        claim_state TEXT
                    )
                    """);

                    ///////////////////////////////////// TICKET PANEL
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_panels (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        content TEXT,
                        total_categories INTEGER,
                        total_staff_roles INTEGER,
                        total_ping_roles INTEGER,
                        ticket_category_id TEXT
                    )
                    """);

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

    private static void addColumnIfMissing(Connection connection, String tableName, String columnDefinition) throws SQLException {
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
                    claim_state
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, ticket.channelId);
            statement.setString(2, ticket.creatorId);
            statement.setString(3, ticket.state);
            statement.setString(4, ticket.claimerId);
            statement.setString(5, ticket.claimState);

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
                    claim_state = ?
                WHERE id = ?
                """;

        try (Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, ticket.channelId);
            statement.setString(2, ticket.creatorId);
            statement.setString(3, ticket.state);
            statement.setString(4, ticket.claimerId);
            statement.setString(5, ticket.claimState);
            statement.setInt(6, ticket.id);

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

    
    public static String getPanelData(String panelID) throws SQLException {
        TicketPanelData panelData = new TicketPanelData();
        String sql = """
                SELECT *
                FROM ticket_panels
                WHERE id = ?

            """;


        try (Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, panelData.idString);
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    panelData.content = "content";
                    panelData.name = "name";
                    panelData.guildId = "guild_id";
                    panelData.ticketCategoryId = "ticket_category_id";
                }
        
        return "e";
       }
    }
}