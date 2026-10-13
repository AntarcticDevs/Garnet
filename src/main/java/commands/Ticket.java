package commands;

import database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import tickets.TicketPanel;
import tickets.TicketPanelData;
import tickets.TicketPanelEditor;

import java.sql.SQLException;
import java.util.List;

public class Ticket extends ListenerAdapter implements Command {
    @Override
    public CommandData getCommandData() {
        OptionData channel = new OptionData(
                OptionType.CHANNEL,
                "channel",
                "The channel where the panel will be sent",
                true
        ).setChannelTypes(ChannelType.TEXT);
        OptionData category = new OptionData(
                OptionType.CHANNEL,
                "category",
                "The category where new ticket channels will be created",
                true
        ).setChannelTypes(ChannelType.CATEGORY);
        OptionData panelName = new OptionData(
                OptionType.STRING,
                "name",
                "The ticket panel to edit",
                true
        ).setAutoComplete(true);
        OptionData createPanelName = new OptionData(
                OptionType.STRING,
                "name",
                "A name for the new ticket panel",
                true
        );
        OptionData sendPanelName = new OptionData(
                OptionType.STRING,
                "name",
                "The saved panel to send (leave empty for the default panel)",
                false
        ).setAutoComplete(true);
        return Commands.slash("ticket", "Manage the ticket system")
            .addSubcommands(
                new SubcommandData("set-category", "Set the category for new tickets")
                    .addOptions(category)
            )
            .addSubcommandGroups(
                new SubcommandGroupData("panel", "Manage the ticket panel")
                    .addSubcommands(
                        new SubcommandData("create", "Create a saved ticket panel")
                            .addOptions(createPanelName),
                        new SubcommandData("send", "Send a saved ticket panel")
                            .addOptions(channel, sendPanelName),
                        new SubcommandData("edit", "Open the ticket panel editor")
                            .addOptions(panelName)
                    )
            );
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        boolean panelNameOption = "ticket".equals(event.getName())
                && "panel".equals(event.getSubcommandGroup())
                && ("edit".equals(event.getSubcommandName()) || "send".equals(event.getSubcommandName()))
                && "name".equals(event.getFocusedOption().getName());
        if (!panelNameOption) {
            return;
        }

        String guildId = event.getGuild() == null ? null : event.getGuild().getId();
        if (guildId == null) {
            event.replyChoices(List.of()).queue();
            return;
        }

        String search = event.getFocusedOption().getValue();
        String sql = "SELECT name FROM ticket_panels WHERE guild_id = ? AND name LIKE ? COLLATE NOCASE ORDER BY name LIMIT 25";
        try (var connection = Database.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            statement.setString(2, search + "%");
            try (var resultSet = statement.executeQuery()) {
                var choices = new java.util.ArrayList<net.dv8tion.jda.api.interactions.commands.Command.Choice>();
                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    choices.add(new net.dv8tion.jda.api.interactions.commands.Command.Choice(name, name));
                }
                event.replyChoices(choices).queue();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
            event.replyChoices(List.of()).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"ticket".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You don't have permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("panel".equals(event.getSubcommandGroup())
                && "create".equals(event.getSubcommandName())) {
            OptionMapping nameOption = event.getOption("name");
            if (nameOption == null || nameOption.getAsString().isBlank()) {
                event.reply("Please provide a name for the ticket panel.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            String name = nameOption.getAsString().trim();
            try {
                if (Database.getPanelData(event.getGuild().getId(), name) != null) {
                    event.reply("A ticket panel with that name already exists.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }

                TicketPanelData panelData = new TicketPanelData();
                panelData.guildId = event.getGuild().getId();
                panelData.name = name;
                panelData.content = "Use the button below to create a ticket.";
                Database.savePanel(panelData);
                event.reply("Created ticket panel **" + panelData.name + "**. You can now edit or send it.")
                        .setEphemeral(true)
                        .queue();
            } catch (SQLException exception) {
                exception.printStackTrace();
                event.reply("Couldn't create that ticket panel. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        if ("panel".equals(event.getSubcommandGroup())
                && "send".equals(event.getSubcommandName())) {
            OptionMapping channelOption = event.getOption("channel");
            if (channelOption == null || !(channelOption.getAsChannel() instanceof TextChannel channel)) {
                event.reply("Please choose a text channel for the ticket panel.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            OptionMapping panelOption = event.getOption("name");
            TicketPanel sender = new TicketPanel();

            if (panelOption == null) {
                event.deferReply(true).queue(hook ->
                        sender.panel(null, channel, null).queue(
                                message -> hook.sendMessage("Default ticket panel sent to " + channel.getAsMention() + ".").queue(),
                                error -> hook.sendMessage("Couldn't send the ticket panel. Check that I can view and send messages in that channel.")
                                        .queue()
                        )
                );
                return;
            }

            try {
                TicketPanelData panelData = Database.getPanelData(
                        event.getGuild().getId(),
                        panelOption.getAsString()
                );
                if (panelData == null) {
                    event.reply("I couldn't find that saved ticket panel.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }

                event.deferReply(true).queue(hook ->
                        sender.panel(panelData, channel).queue(
                                message -> hook.sendMessage("Ticket panel **" + panelData.name + "** sent to " + channel.getAsMention() + ".").queue(),
                                error -> hook.sendMessage("Couldn't send the ticket panel. Check that I can view and send messages in that channel.")
                                        .queue()
                        )
                );
            } catch (SQLException exception) {
                exception.printStackTrace();
                event.reply("Couldn't load that ticket panel. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        if ("panel".equals(event.getSubcommandGroup())
                && "edit".equals(event.getSubcommandName())) {
            OptionMapping panelNameOption = event.getOption("name");
            if (panelNameOption == null) {
                event.reply("Please choose a ticket panel to edit.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            try {
                TicketPanelData panelData = Database.getPanelData(
                        event.getGuild().getId(),
                        panelNameOption.getAsString()
                );

                if (panelData == null) {
                    event.reply("I couldn't find that ticket panel.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }

                new TicketPanelEditor().open(event, panelData);
            } catch (SQLException exception) {
                exception.printStackTrace();
                event.reply("Couldn't load the ticket panel settings. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        if ("set-category".equals(event.getSubcommandName())) {
            OptionMapping categoryOption = event.getOption("category");
            if (categoryOption == null || !(categoryOption.getAsChannel() instanceof Category category)) {
                event.reply("Please choose a category for new ticket channels.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            try {
                Database.saveTicketCategory(event.getGuild().getId(), category.getId());
                event.reply("New ticket channels will be created in " + category.getAsMention() + ".")
                        .setEphemeral(true)
                        .queue();
            } catch (SQLException exception) {
                exception.printStackTrace();
                event.reply("Couldn't save the ticket category. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }
}