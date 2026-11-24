package commands;

import utils.Log;
import database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Rules extends ListenerAdapter implements Command {

    @Override
    public CommandData getCommandData() {
        OptionData embedName = new OptionData(OptionType.STRING, "name", "First rules embed", true)
                .setAutoComplete(true);
        OptionData secondEmbed = new OptionData(OptionType.STRING, "name-2", "Second rules embed", false)
            .setAutoComplete(true);
        OptionData thirdEmbed = new OptionData(OptionType.STRING, "name-3", "Third rules embed", false)
            .setAutoComplete(true);
        OptionData fourthEmbed = new OptionData(OptionType.STRING, "name-4", "Fourth rules embed", false)
            .setAutoComplete(true);
        OptionData fifthEmbed = new OptionData(OptionType.STRING, "name-5", "Fifth rules embed", false)
            .setAutoComplete(true);
        return Commands.slash("rules", "Choose up to five embeds for this server's rules")
                .addSubcommands(
                new SubcommandData("select", "Select up to five saved embeds as the server rules")
                    .addOptions(embedName, secondEmbed, thirdEmbed, fourthEmbed, fifthEmbed)
                );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"rules".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        if (!"select".equals(event.getSubcommandName())) {
            return;
        }

        List<String> embedNames = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            String optionName = index == 1 ? "name" : "name-" + index;
            OptionMapping option = event.getOption(optionName);
            if (option != null) {
                embedNames.add(option.getAsString());
            }
        }

        String findEmbedSql = "SELECT id FROM embeds WHERE guild_id = ? AND name = ?";
        String deleteRulesSql = "DELETE FROM guild_rule_embeds WHERE guild_id = ?";
        String saveRulesSql = "INSERT INTO guild_rule_embeds (guild_id, position, embed_id) VALUES (?, ?, ?)";

        try (Connection connection = Database.getConnection();
             PreparedStatement findEmbed = connection.prepareStatement(findEmbedSql);
             PreparedStatement deleteRules = connection.prepareStatement(deleteRulesSql);
             PreparedStatement saveRules = connection.prepareStatement(saveRulesSql)) {
            List<Long> embedIds = new ArrayList<>();
            for (String embedName : embedNames) {
                findEmbed.setString(1, event.getGuild().getId());
                findEmbed.setString(2, embedName);
                try (ResultSet resultSet = findEmbed.executeQuery()) {
                    if (!resultSet.next()) {
                        event.reply("No saved embed named `" + embedName + "` was found.")
                                .setEphemeral(true)
                                .queue();
                        return;
                    }
                    long embedId = resultSet.getLong("id");
                    if (embedIds.contains(embedId)) {
                        event.reply("Each rules embed must be different.").setEphemeral(true).queue();
                        return;
                    }
                    embedIds.add(embedId);
                }
            }

            connection.setAutoCommit(false);
            deleteRules.setString(1, event.getGuild().getId());
            deleteRules.executeUpdate();
            for (int index = 0; index < embedIds.size(); index++) {
                saveRules.setString(1, event.getGuild().getId());
                saveRules.setInt(2, index + 1);
                saveRules.setLong(3, embedIds.get(index));
                saveRules.addBatch();
            }
            saveRules.executeBatch();
            connection.commit();

            event.reply("Selected " + embedNames.size() + " embed(s) as this server's rules.").queue();
        } catch (SQLException exception) {
            Log.error("Rules.onSlashCommandInteraction failed", exception);
            event.reply("Something went wrong while saving the rules embed.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!"rules".equals(event.getName())
                || !"select".equals(event.getSubcommandName())
            || !event.getFocusedOption().getName().matches("name(-[2-5])?")) {
            return;
        }

        if (event.getGuild() == null) {
            event.replyChoices(List.of()).queue();
            return;
        }

        String sql = "SELECT name FROM embeds WHERE guild_id = ? AND name LIKE ? COLLATE NOCASE ORDER BY name LIMIT 25";
        String search = "%" + event.getFocusedOption().getValue() + "%";
        List<net.dv8tion.jda.api.interactions.commands.Command.Choice> choices = new ArrayList<>();

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getGuild().getId());
            statement.setString(2, search);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    choices.add(new net.dv8tion.jda.api.interactions.commands.Command.Choice(name, name));
                }
            }

            event.replyChoices(choices).queue();
        } catch (SQLException exception) {
            Log.error("Rules.onCommandAutoCompleteInteraction failed", exception);
            event.replyChoices(List.of()).queue();
        }
    }
}