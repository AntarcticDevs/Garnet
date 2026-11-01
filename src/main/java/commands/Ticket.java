package commands;

import utils.Log;
import database.Database;
import net.dv8tion.jda.api.EmbedBuilder;
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
import tickets.TicketData;
import tickets.TicketManager;
import tickets.TicketPermissions;
import tickets.TranscriptManager;

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
        OptionData transcriptChannel = new OptionData(
                OptionType.CHANNEL,
                "channel",
                "Where a transcript is posted when a ticket is closed or deleted; omit to turn it off",
                false
        ).setChannelTypes(ChannelType.TEXT);
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
        OptionData disabledPanelName = new OptionData(
                OptionType.STRING,
                "name",
                "The ticket panel",
                true
        ).setAutoComplete(true);
        OptionData disabledMessage = new OptionData(
                OptionType.STRING,
                "message",
                "What the panel says while disabled (leave empty for the default message)",
                false
        ).setMaxLength(1000);
        OptionData transferMember = new OptionData(
                OptionType.USER,
                "member",
                "The staff member to transfer this ticket to",
                true
        );
        return Commands.slash("ticket", "Manage the ticket system")
            .addSubcommands(
                new SubcommandData("set-category", "Set the category for new tickets")
                    .addOptions(category),
                new SubcommandData("close", "Close the ticket in this channel"),
                new SubcommandData("reopen", "Reopen the ticket in this channel"),
                new SubcommandData("claim", "Claim the ticket in this channel"),
                new SubcommandData("unclaim", "Release your claim on this ticket"),
                new SubcommandData("transfer", "Transfer this ticket to another staff member")
                    .addOptions(transferMember),
                new SubcommandData("delete", "Delete this ticket channel and its data"),
                new SubcommandData("transcript", "Post a text transcript of this ticket")
            )
            .addSubcommandGroups(
                new SubcommandGroupData("panel", "Manage the ticket panel")
                    .addSubcommands(
                        new SubcommandData("create", "Create a saved ticket panel")
                            .addOptions(createPanelName),
                        new SubcommandData("send", "Send a saved ticket panel")
                            .addOptions(channel, sendPanelName),
                        new SubcommandData("edit", "Open the ticket panel editor")
                            .addOptions(panelName),
                        new SubcommandData("staff-roles", "Select staff roles for a ticket panel")
                            .addOptions(panelName),
                        new SubcommandData("ping-roles", "Select ping roles for a ticket panel")
                            .addOptions(panelName),
                        new SubcommandData("disable", "Replace a panel's button or menu with a disabled notice")
                            .addOptions(disabledPanelName, disabledMessage),
                        new SubcommandData("enable", "Turn a disabled ticket panel back on")
                            .addOptions(disabledPanelName),
                        new SubcommandData("transcript-channel", "Set a transcript channel or clear it")
                            .addOptions(panelName, transcriptChannel)
                    )
            );
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        boolean panelNameOption = "ticket".equals(event.getName())
                && "panel".equals(event.getSubcommandGroup())
                && ("edit".equals(event.getSubcommandName())
                    || "send".equals(event.getSubcommandName())
                    || "staff-roles".equals(event.getSubcommandName())
                    || "ping-roles".equals(event.getSubcommandName())
                    || "disable".equals(event.getSubcommandName())
                    || "enable".equals(event.getSubcommandName())
                    || "transcript-channel".equals(event.getSubcommandName()))
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
            Log.error("Ticket.onCommandAutoCompleteInteraction failed", exception);
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

        boolean panelCommand = "panel".equals(event.getSubcommandGroup());
        boolean configurationCommand = "set-category".equals(event.getSubcommandName());
        if ((panelCommand || configurationCommand)
                && (event.getMember() == null || !TicketPermissions.canManagePanels(event.getMember()))) {
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
                Log.error("Ticket.onSlashCommandInteraction failed", exception);
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
                TicketPanelData defaultPanel = new TicketPanelData();
                defaultPanel.name = "Create a ticket";
                event.deferReply(true).queue(hook ->
                        sender.panel(defaultPanel, channel).queue(
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
                        sender.publish(panelData, channel).queue(
                                message -> {
                                    try {
                                        Database.savePanelMessage(panelData.id, channel.getId(), message.getId());
                                    } catch (SQLException exception) {
                                        Log.error("Ticket.onSlashCommandInteraction failed", exception);
                                    }
                                    hook.sendMessage("Ticket panel **" + panelData.name + "** sent to " + channel.getAsMention() + ".").queue();
                                },
                                error -> hook.sendMessage("Couldn't send the ticket panel. Check that I can view and send messages in that channel.")
                                        .queue()
                        )
                );
            } catch (SQLException exception) {
                Log.error("Ticket.onSlashCommandInteraction failed", exception);
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
                Log.error("Ticket.onSlashCommandInteraction failed", exception);
                event.reply("Couldn't load the ticket panel settings. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        if ("panel".equals(event.getSubcommandGroup())
                && ("staff-roles".equals(event.getSubcommandName())
                    || "ping-roles".equals(event.getSubcommandName()))) {
            OptionMapping rolePanelName = event.getOption("name");
            if (rolePanelName == null) {
                event.reply("Please choose a ticket panel.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            try {
                TicketPanelData panelData = Database.getPanelData(
                        event.getGuild().getId(),
                        rolePanelName.getAsString()
                );
                if (panelData == null) {
                    event.reply("I couldn't find that ticket panel.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                new TicketPanelEditor().open(event, panelData, TicketPanelEditor.Page.ROUTING);
            } catch (SQLException exception) {
                Log.error("Ticket.onSlashCommandInteraction failed", exception);
                event.reply("Couldn't load the ticket panel settings. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        if ("panel".equals(event.getSubcommandGroup())
                && ("disable".equals(event.getSubcommandName())
                    || "enable".equals(event.getSubcommandName()))) {
            boolean disabling = "disable".equals(event.getSubcommandName());
            OptionMapping toggleName = event.getOption("name");
            if (toggleName == null) {
                event.reply("Please choose a ticket panel.").setEphemeral(true).queue();
                return;
            }
            try {
                TicketPanelData panelData = Database.getPanelData(
                        event.getGuild().getId(), toggleName.getAsString());
                if (panelData == null) {
                    event.reply("I couldn't find that ticket panel.").setEphemeral(true).queue();
                    return;
                }
                OptionMapping messageOption = disabling ? event.getOption("message") : null;
                panelData.disabled = disabling;
                panelData.disabledMessage = messageOption == null || messageOption.getAsString().isBlank()
                        ? null
                        : messageOption.getAsString().trim();
                Database.savePanel(panelData);

                String state = disabling ? "disabled" : "enabled";
                TextChannel published = panelData.channelId == null || panelData.messageId == null
                        ? null
                        : event.getGuild().getTextChannelById(panelData.channelId);
                if (published == null) {
                    event.reply("Ticket panel **" + panelData.name + "** is now " + state
                                    + ". It isn't posted in a channel yet; it will look like this when you publish it.")
                            .setEphemeral(true).queue();
                    return;
                }
                TicketPanel renderer = new TicketPanel();
                event.deferReply(true).queue(hook -> published
                        .editMessageEmbedsById(panelData.messageId,
                                renderer.buildEmbed(panelData, event.getGuild()))
                        .setComponents(renderer.buildActionRow(panelData))
                        .queue(
                                message -> hook.editOriginal("Ticket panel **" + panelData.name + "** is now "
                                        + state + " in " + published.getAsMention() + ".").queue(),
                                error -> hook.editOriginal("Ticket panel **" + panelData.name + "** is now "
                                        + state + ", but I couldn't update the message in "
                                        + published.getAsMention() + ". It may have been deleted, or I can't "
                                        + "edit it; publish the panel again from the editor.").queue()));
            } catch (SQLException exception) {
                Log.error("Ticket.onSlashCommandInteraction failed", exception);
                event.reply("Couldn't update that ticket panel. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        if ("panel".equals(event.getSubcommandGroup())
                && "transcript-channel".equals(event.getSubcommandName())) {
            OptionMapping panelNameOption = event.getOption("name");
            OptionMapping channelOption = event.getOption("channel");
            if (panelNameOption == null) {
                event.reply("Please choose a ticket panel.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            TextChannel transcriptChannel = channelOption == null
                    ? null
                    : channelOption.getAsChannel() instanceof TextChannel textChannel ? textChannel : null;
            if (channelOption != null && transcriptChannel == null) {
                event.reply("Please choose a text channel for ticket transcripts.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            String channelId = transcriptChannel == null ? null : transcriptChannel.getId();
            try {
                TicketPanelData panelData = Database.getPanelData(
                        event.getGuild().getId(), panelNameOption.getAsString());
                if (panelData == null) {
                    event.reply("I couldn't find that ticket panel.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                Database.savePanelTranscriptChannel(panelData.id, event.getGuild().getId(), channelId);
                String result = channelOption == null
                        ? "Ticket transcripts for **" + panelData.name
                                + "** will no longer be posted when a ticket is closed or deleted."
                        : "When a ticket from **" + panelData.name + "** is closed or deleted, its transcript will be posted in "
                                + transcriptChannel.getAsMention() + ".";
                event.reply(result).setEphemeral(true).queue();
            } catch (SQLException exception) {
                Log.error("Ticket.onSlashCommandInteraction failed", exception);
                event.reply("Couldn't save the transcript channel. Please try again.")
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
                Log.error("Ticket.onSlashCommandInteraction failed", exception);
                event.reply("Couldn't save the ticket category. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        handleTicketAction(event);
    }

    private void handleTicketAction(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel) || event.getMember() == null) {
            event.reply("Ticket actions can only be used in a server ticket channel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TicketData ticket;
        try {
            ticket = Database.getTicketByChannelId(channel.getId());
        } catch (SQLException exception) {
            Log.error("Ticket.handleTicketAction failed", exception);
            event.reply("Couldn't load this ticket. Please try again.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (ticket == null) {
            event.reply("This channel isn't an active ticket.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String action = event.getSubcommandName();
        TicketPermissions.Action required = TicketPermissions.Action.fromName(action);
        if (required == null) {
            event.reply("Unknown ticket action.").setEphemeral(true).queue();
            return;
        }
        try {
            if (!TicketPermissions.can(event.getMember(), ticket, required)) {
                replyDenied(event, TicketPermissions.denialMessage(required));
                return;
            }
        } catch (SQLException exception) {
            Log.error("Ticket.replyDenied failed", exception);
            event.reply("Couldn't load this ticket's staff settings. Please try again.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        TicketManager manager = new TicketManager();

        switch (action) {
            case "close" -> {
                if ("CLOSED".equals(ticket.state)) {
                    event.reply("This ticket is already closed.").setEphemeral(true).queue();
                    return;
                }
                try {
                    manager.closeTicket(event.getGuild(), channel.getId(), ticket.creatorId,
                            new EmbedBuilder().setTitle("Ticket closed")
                                    .setDescription("Closed by " + event.getUser().getAsMention() + "."));
                    new TranscriptManager().sendOnClose(channel, ticket);
                    event.reply("Ticket closed.").setEphemeral(true).queue();
                } catch (SQLException exception) {
                    Log.error("Ticket.replyDenied failed", exception);
                    replyDatabaseError(event);
                }
            }
            case "reopen" -> {
                if (!"CLOSED".equals(ticket.state)) {
                    event.reply("This ticket is already open.").setEphemeral(true).queue();
                    return;
                }
                try {
                    manager.reopenTicket(event.getGuild(), channel.getId(), ticket.creatorId,
                            new EmbedBuilder().setTitle("Ticket reopened")
                                    .setDescription("Reopened by " + event.getUser().getAsMention() + "."));
                    event.reply("Ticket reopened.").setEphemeral(true).queue();
                } catch (SQLException exception) {
                    Log.error("Ticket.replyDatabaseError failed", exception);
                    replyDatabaseError(event);
                }
            }
            case "claim" -> {
                String result = manager.claimTicket(event.getGuild(), channel.getId(), event.getMember().getId(),
                        new EmbedBuilder().setTitle("Ticket claimed")
                                .setDescription("Claimed by " + event.getUser().getAsMention() + "."));
                replyTicketResult(event, result, "Ticket claimed.");
            }
            case "unclaim" -> {
                String result = manager.unclaimTicket(event.getGuild(), channel.getId(),
                        new EmbedBuilder().setTitle("Ticket unclaimed")
                                .setDescription("Released by " + event.getUser().getAsMention() + "."));
                replyTicketResult(event, result, "Ticket unclaimed.");
            }
            case "transfer" -> {
                OptionMapping memberOption = event.getOption("member");
                if (memberOption == null || memberOption.getAsMember() == null) {
                    event.reply("Choose a member of this server to receive the ticket.")
                            .setEphemeral(true).queue();
                    return;
                }
                var assignee = memberOption.getAsMember();
                try {
                    if (!TicketPermissions.isStaff(assignee, ticket)) {
                        event.reply("The selected member must be staff for this ticket (a staff role on its panel, or Manage Channels).")
                                .setEphemeral(true)
                                .queue();
                        return;
                    }
                } catch (SQLException exception) {
                    Log.error("Ticket.replyTicketResult failed", exception);
                    event.reply("Couldn't load this ticket's staff settings. Please try again.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                String result = manager.transferTicket(event.getGuild(), channel.getId(), assignee.getId(),
                        new EmbedBuilder().setTitle("Ticket transferred")
                                .setDescription("Transferred to " + assignee.getAsMention() + " by "
                                        + event.getUser().getAsMention() + "."));
                replyTicketResult(event, result, "Ticket transferred to " + assignee.getAsMention() + ".");
            }
            case "delete" -> event.reply(TicketButton.DELETE_PROMPT)
                    .addComponents(TicketButton.deleteConfirmRow())
                    .setEphemeral(true)
                    .queue();
            case "transcript" -> {
                // A requested transcript is private: posted ephemerally here. Close and delete
                // are what send it to the panel's transcript channel.
                event.deferReply(true).queue(hook -> {
                    try {
                        new TranscriptManager().sendTranscript(channel, hook)
                                .whenComplete((ignored, error) -> {
                                    if (error != null) {
                                        Log.error("Transcript generation failed", error);
                                        hook.editOriginal(
                                                        "Couldn't create or upload the transcript. Please try again.")
                                                .queue();
                                    }
                                });
                    } catch (RuntimeException exception) {
                        Log.error("Ticket.replyTicketResult failed", exception);
                        hook.editOriginal("Couldn't create the transcript. Please try again.").queue();
                    }
                });
            }
            default -> event.reply("Unknown ticket action.").setEphemeral(true).queue();
        }
    }

    private void replyTicketResult(SlashCommandInteractionEvent event, String result, String successMessage) {
        String message = switch (result) {
            case "SUCCESS" -> successMessage;
            case "TICKET_NOT_FOUND" -> "This channel isn't an active ticket.";
            case "TICKET_CLOSED" -> "This ticket is closed.";
            case "ALREADY_CLAIMED" -> "This ticket has already been claimed.";
            case "NOT_CLAIMED" -> "This ticket isn't currently claimed.";
            case "SAME_CLAIMER" -> "This ticket is already assigned to that member.";
            default -> "Couldn't update this ticket. Please try again.";
        };
        event.reply(message).setEphemeral(true).queue();
    }

    private void replyDenied(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private void replyDatabaseError(SlashCommandInteractionEvent event) {
        event.reply("Couldn't save this ticket update. Please try again.")
                .setEphemeral(true)
                .queue();
    }
}