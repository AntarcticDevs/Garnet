package events;

import utils.Log;
import config.ConfigManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;


import database.Database;
import utils.OrdinalUtils;
import utils.EmbedHelpers;
import commands.Command;
import utils.EmbedManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;



public class WelcomeListener extends ListenerAdapter implements Command {
    private static final String DEFAULT_EMBED_VALUE = "__garnet_default__";

    @Override
    public CommandData getCommandData() {
        return Commands.slash("welcomer", "Manage welcome messages")
            .addSubcommands(
                new SubcommandData("test", "Sends a test welcome message")
                    .addOption(OptionType.USER, "user", "The user to welcome", false),
                new SubcommandData("setchannel", "Sets the welcome channel")
                    .addOption(OptionType.CHANNEL, "channel", "The channel for welcome messages", true),
                new SubcommandData("setembed", "Choose the welcome embed")
            );
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("welcomer:setembed:")) {
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null || !componentId.equals("welcomer:setembed:" + guild.getId())) {
            event.reply("This welcome embed menu is not for this server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String selectedValue = event.getValues().get(0);
        try {
            if (DEFAULT_EMBED_VALUE.equals(selectedValue)) {
                saveWelcomeEmbedId(guild, null);
                event.editMessage("Welcome embed set to " + ConfigManager.getBotName() + " Default.")
                        .setComponents(List.of())
                        .queue();
                return;
            }

            long embedId = Long.parseLong(selectedValue);
            String embedName = getEmbedName(guild, embedId);
            if (embedName == null) {
                event.editMessage("That saved embed is no longer available in this server.")
                        .setComponents(List.of())
                        .queue();
                return;
            }

            saveWelcomeEmbedId(guild, embedId);
            event.editMessage("Welcome embed set to `" + embedName + "`!")
                    .setComponents(List.of())
                    .queue();
        } catch (NumberFormatException | SQLException exception) {
            Log.error("WelcomeListener.saveWelcomeEmbedId failed", exception);
            event.reply("Something went wrong while saving the welcome embed.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void showWelcomeEmbedMenu(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        StringSelectMenu.Builder menu = StringSelectMenu.create("welcomer:setembed:" + guild.getId())
                .setPlaceholder("Choose a welcome embed")
                .addOption(ConfigManager.getBotName() + " Default", DEFAULT_EMBED_VALUE);
        String sql = "SELECT id, name FROM embeds WHERE guild_id = ? ORDER BY name LIMIT 24";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guild.getId());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long embedId = resultSet.getLong("id");
                    String name = resultSet.getString("name");
                    menu.addOption(name, Long.toString(embedId));
                }
            }
        } catch (SQLException exception) {
            Log.error("WelcomeListener.showWelcomeEmbedMenu failed", exception);
            event.reply("Could not load this server's saved embeds.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.reply("Select the welcome embed to use:")
                .addComponents(ActionRow.of(menu.build()))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"welcomer".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("setchannel".equals(event.getSubcommandName())) {
            TextChannel channel = event.getOption("channel", option -> option.getAsChannel().asTextChannel());
            saveWelcomeChannel(event.getGuild(), channel);
            event.reply("Welcome messages will be sent in " + channel.getAsMention() + ".")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if ("setembed".equals(event.getSubcommandName())) {
            showWelcomeEmbedMenu(event);
            return;
        }

        if (!"test".equals(event.getSubcommandName())) {
            return;
        }

        Member member = event.getOption("user", event.getMember(), option -> option.getAsMember());
        if (member == null) {
            event.reply("I couldn't resolve that user in this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (sendWelcomeMessage(event.getGuild(), member)) {
            event.reply("Welcome message sent!").setEphemeral(true).queue();
        } else {
            event.reply("No welcome channel has been configured. Use `/welcomer setchannel` first.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        sendWelcomeMessage(event.getGuild(), event.getMember());
    }

    private boolean sendWelcomeMessage(Guild guild, Member member) {
        TextChannel welcomeChannel = getWelcomeChannel(guild);
        if (welcomeChannel == null) {
            
            return false;
        }

        Long embedId = getWelcomeEmbedId(guild);

        EmbedBuilder embed = null;
        if (embedId != null) {
            try {
                embed = EmbedManager.loadEmbedById(guild, member, embedId);
            } catch (SQLException exception) {
                Log.error("WelcomeListener.sendWelcomeMessage failed", exception);
            }
        }

        if (embed == null) {
            embed = new EmbedBuilder();
            String num = OrdinalUtils.ordinal(guild.getMemberCount());
            embed.setAuthor("Joined");
            embed.setTitle("Welcome " + member.getUser().getName() + " to " + guild.getName() + "! You are the " + num + " member!");
            embed.setThumbnail(member.getEffectiveAvatarUrl());
            EmbedHelpers.setDefaultFooter(embed, guild);
        }

        welcomeChannel.sendMessage(member.getUser().getAsMention()).setEmbeds(embed.build()).queue();
        return true;
    }

    private void saveWelcomeEmbedId(Guild guild, Long embedId) throws SQLException {
        String sql = """
                INSERT INTO welcomer (guild_id, embed_id)
                VALUES (?, ?)
                ON CONFLICT(guild_id)
                DO UPDATE SET embed_id = excluded.embed_id
                """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guild.getId());
            if (embedId == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, embedId);
            }
            statement.executeUpdate();
        }
    }

    private String getEmbedName(Guild guild, long embedId) throws SQLException {
        String sql = "SELECT name FROM embeds WHERE id = ? AND guild_id = ?";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, embedId);
            statement.setString(2, guild.getId());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("name") : null;
            }
        }
    }

    private void saveWelcomeChannel(Guild guild, TextChannel channel) {
        String sql = """
                INSERT INTO welcomer (guild_id, channel_id)
                VALUES (?, ?)
                ON CONFLICT(guild_id) DO UPDATE SET channel_id = excluded.channel_id
                """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guild.getId());
            statement.setString(2, channel.getId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save the welcome channel", exception);
        }
    }

    private TextChannel getWelcomeChannel(Guild guild) {
        String sql = "SELECT channel_id FROM welcomer WHERE guild_id = ?";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guild.getId());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return guild.getTextChannelById(resultSet.getString("channel_id"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load the welcome channel", exception);
        }

        return null;}

    private Long getWelcomeEmbedId(Guild guild) {
    String sql = "SELECT embed_id FROM welcomer WHERE guild_id = ?";

    try (Connection connection = Database.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

        statement.setString(1, guild.getId());

        try (ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                long embedId = result.getLong("embed_id");

                if (result.wasNull()) {
                    return null;
                }

                return embedId;
            }
        }

    } catch (SQLException exception) {
        Log.error("WelcomeListener.getWelcomeEmbedId failed", exception);
    }

    return null;
}
    }

