package commands;

import utils.Log;
import database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import utils.EmbedManager;
import net.dv8tion.jda.api.EmbedBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;




public class Embeds extends ListenerAdapter implements Command {

    @Override
    public CommandData getCommandData() {
        return Commands.slash("embed", "Manage embeds")
                .addSubcommands(
                        new SubcommandData("create", "Create an embed")
                                .addOption(OptionType.STRING, "name", "The embed's name", true),
                        new SubcommandData("delete", "Delete a saved embed")
                            .addOptions(embedNameOption()),
                        new SubcommandData("send", "Send an embed")
                            .addOptions(embedNameOption())
                )
                .addSubcommandGroups(
                        new SubcommandGroupData("edit", "Edit an embed")
                                .addSubcommands(
                                        editCommand("title", "Edit the title")
                                                .addOption(OptionType.STRING, "title", "Embed title", true),
                                        editCommand("description", "Edit the description")
                                                .addOption(OptionType.STRING, "description", "Embed description", true),
                                        editCommand("footer", "Edit the footer")
                                                .addOption(OptionType.STRING, "text", "Embed footer text", true)
                                                .addOption(OptionType.STRING, "image", "Embed footer image (link)", false),
                                        editCommand("image", "Edit the image")
                                                .addOption(OptionType.STRING, "image", "Image link", true),
                                        editCommand("author", "Edit embed author")
                                                .addOption(OptionType.STRING, "text", "Embed author text", true)
                                                .addOption(OptionType.STRING, "image", "Embed author image (link)", false),
                                        editCommand("colour", "Edit the colour")
                                                .addOption(OptionType.STRING, "hex", "Edit the embed colour (HEX)", true),
                                        editCommand("thumbnail", "Edit the thumbnail")
                                                .addOption(OptionType.STRING, "thumbnail", "Thumbnail link", true),
                                        editCommand("add-field", "Add embed fields")
                                                .addOption(OptionType.STRING, "field-name", "Embed field name", true)
                                                .addOption(OptionType.STRING, "value", "Embed field value", true)
                                )
                );
    }

    private static SubcommandData editCommand(String name, String description) {
        return new SubcommandData(name, description)
                .addOptions(embedNameOption());
    }

    private static OptionData embedNameOption() {
        return new OptionData(OptionType.STRING, "name", "The embed's name", true)
                .setAutoComplete(true);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"embed".equals(event.getName())) {
            return;
        }

        if (event.getGuild() != null
                && (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER))) {
            event.reply("You don't have permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("create".equals(event.getSubcommandName())) {
            createEmbed(event);
        } else if ("delete".equals(event.getSubcommandName())) {
            deleteEmbed(event);
        } else if ("edit".equals(event.getSubcommandGroup())) {
            editEmbed(event);
        } else if ("send".equals(event.getSubcommandName())) {
            sendEmbed(event);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!"embed".equals(event.getName())
                || !"name".equals(event.getFocusedOption().getName())
                || !isEmbedNameAutocomplete(event)) {
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
            Log.error("Embeds.onCommandAutoCompleteInteraction failed", exception);
            event.replyChoices(List.of()).queue();
        }
    }

    private static boolean isEmbedNameAutocomplete(CommandAutoCompleteInteractionEvent event) {
        return "send".equals(event.getSubcommandName())
                || "delete".equals(event.getSubcommandName())
                || "edit".equals(event.getSubcommandGroup());
    }

    private void createEmbed(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        String sql = "INSERT INTO embeds (guild_id, name) VALUES (?, ?)";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getGuild().getId());
            statement.setString(2, name);
            statement.executeUpdate();
            event.reply("Created embed `" + name + "`!").queue();
        } catch (SQLException exception) {
            Log.error("Embeds.createEmbed failed", exception);
            event.reply("Something went wrong while creating the embed.").setEphemeral(true).queue();
        }
    }

    private void deleteEmbed(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        String embedName = event.getOption("name").getAsString();
        String sql = "DELETE FROM embeds WHERE guild_id = ? AND name = ?";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getGuild().getId());
            statement.setString(2, embedName);

            if (statement.executeUpdate() == 0) {
                event.reply("No embed named `" + embedName + "` was found.").setEphemeral(true).queue();
                return;
            }

            event.reply("Deleted embed `" + embedName + "`.").queue();
        } catch (SQLException exception) {
            Log.error("Embeds.deleteEmbed failed", exception);
            event.reply("Something went wrong while deleting the embed.").setEphemeral(true).queue();
        }
    }

    private void editEmbed(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        String embedName = event.getOption("name").getAsString();
        String guildId = event.getGuild().getId();
        String sql;

        switch (subcommand) {
            case "title" -> sql = "UPDATE embeds SET title = ? WHERE guild_id = ? AND name = ?";
            case "description" -> sql = "UPDATE embeds SET description = ? WHERE guild_id = ? AND name = ?";
            case "image" -> sql = "UPDATE embeds SET image_url = ? WHERE guild_id = ? AND name = ?";
            case "colour" -> sql = "UPDATE embeds SET colour = ? WHERE guild_id = ? AND name = ?";
            case "thumbnail" -> sql = "UPDATE embeds SET thumbnail_url = ? WHERE guild_id = ? AND name = ?";
            case "footer" -> sql = "UPDATE embeds SET footer_text = ?, footer_image = ? WHERE guild_id = ? AND name = ?";
            case "author" -> sql = "UPDATE embeds SET author_text = ?, author_image = ? WHERE guild_id = ? AND name = ?";
            case "add-field" -> {
                addField(event, guildId, embedName);
                return;
            }
            default -> {
                return;
            }
        }

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if ("footer".equals(subcommand) || "author".equals(subcommand)) {
                statement.setString(1, event.getOption("text").getAsString());
                statement.setString(2, optionalValue(event, "image"));
                statement.setString(3, guildId);
                statement.setString(4, embedName);
            } else {
                statement.setString(1, valueFor(event, subcommand));
                statement.setString(2, guildId);
                statement.setString(3, embedName);
            }

            if (statement.executeUpdate() == 0) {
                event.reply("No embed named `" + embedName + "` was found.").setEphemeral(true).queue();
                return;
            }

            event.reply("Updated embed `" + embedName + "`.").queue();
        } catch (SQLException exception) {
            Log.error("Embeds.addField failed", exception);
            event.reply("Something went wrong while updating the embed.").setEphemeral(true).queue();
        }
    }



    private void sendEmbed(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String embedName = event.getOption("name").getAsString();

        try {
            EmbedBuilder embed = EmbedManager.loadEmbed(
                    event.getGuild(),
                    event.getMember(),
                    embedName
            );

            if (embed == null) {
                event.reply("No embed named `" + embedName + "` was found.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if (embed.isEmpty()) {
                event.reply("This embed is empty. Edit it to add content before sending.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (SQLException exception) {
            Log.error("Embeds.sendEmbed failed", exception);

            event.reply("Something went wrong while loading the embed.")
                    .setEphemeral(true)
                    .queue();

        } catch (NumberFormatException exception) {
            event.reply("The embed has an invalid colour value.")
                    .setEphemeral(true)
                    .queue();
        }
    }



    private void addField(SlashCommandInteractionEvent event, String guildId, String embedName) {
        String findEmbedSql = "SELECT id FROM embeds WHERE guild_id = ? AND name = ?";
        String findPositionSql = "SELECT COALESCE(MAX(field_position), -1) + 1 FROM embed_fields WHERE embed_id = ?";
        String insertFieldSql = "INSERT INTO embed_fields (embed_id, field_name, field_value, field_position, is_inline) VALUES (?, ?, ?, ?, 0)";

        try (Connection connection = Database.getConnection();
             PreparedStatement findEmbed = connection.prepareStatement(findEmbedSql);
             PreparedStatement findPosition = connection.prepareStatement(findPositionSql);
             PreparedStatement insertField = connection.prepareStatement(insertFieldSql)) {
            findEmbed.setString(1, guildId);
            findEmbed.setString(2, embedName);

            try (var embedResult = findEmbed.executeQuery()) {
                if (!embedResult.next()) {
                    event.reply("No embed named `" + embedName + "` was found.").setEphemeral(true).queue();
                    return;
                }

                long embedId = embedResult.getLong("id");
                findPosition.setLong(1, embedId);

                try (var positionResult = findPosition.executeQuery()) {
                    positionResult.next();
                    int fieldPosition = positionResult.getInt(1);

                    insertField.setLong(1, embedId);
                    insertField.setString(2, event.getOption("field-name").getAsString());
                    insertField.setString(3, event.getOption("value").getAsString());
                    insertField.setInt(4, fieldPosition);
                    insertField.executeUpdate();
                }
            }

            event.reply("Added a field to embed `" + embedName + "`.").queue();
        } catch (SQLException exception) {
            Log.error("Embeds.addField failed", exception);
            event.reply("Something went wrong while adding the embed field.").setEphemeral(true).queue();
        }
    }

    private static String valueFor(SlashCommandInteractionEvent event, String subcommand) {
        String option = switch (subcommand) {
            case "title" -> "title";
            case "description" -> "description";
            case "image" -> "image";
            case "colour" -> "hex";
            case "thumbnail" -> "thumbnail";
            default -> throw new IllegalArgumentException("Unsupported edit: " + subcommand);
        };
        return event.getOption(option).getAsString();
    }

    private static String optionalValue(SlashCommandInteractionEvent event, String option) {
        OptionMapping mapping = event.getOption(option);
        return mapping == null ? null : mapping.getAsString();
    }
}