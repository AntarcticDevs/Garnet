package tickets;

import utils.Log;
import java.sql.SQLException;

import database.Database;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import utils.ConsoleLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TicketButton extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {

        if (event.getGuild() == null) {
            return;
        }

        String customId = event.getButton().getCustomId();
        if ("ticket:close".equals(customId)) {
            closeTicket(event);
            return;
        }
        if ("ticket:deletecancel".equals(customId)) {
            event.editMessage("Cancelled. The ticket was not deleted.")
                    .setComponents(java.util.Collections.emptyList())
                    .queue();
            return;
        }
        if ("ticket:reopen".equals(customId)) {
            reopenTicket(event);
            return;
        }
        if (customId.startsWith("ticket:staff:")) {
            handleStaffButton(event, customId.substring("ticket:staff:".length()));
            return;
        }
        if ("ticket:support".equals(customId)) {
            createTicket(event, null, null);
            return;
        }
        if (customId.startsWith("ticket:create:")) {
            try {
                int panelId = Integer.parseInt(customId.substring("ticket:create:".length()));
                createTicket(event, panelId, null);
            } catch (NumberFormatException exception) {
                event.reply("This ticket panel button is invalid. Please contact a server moderator.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!"ticket:transfer".equals(event.getComponentId())) {
            return;
        }
        if (!(event.getChannel() instanceof TextChannel channel)
                || event.getGuild() == null
                || event.getMember() == null
                || event.getValues().isEmpty()) {
            event.reply("Choose a staff member from inside an active ticket.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            TicketData ticket = Database.getTicketByChannelId(channel.getId());
            if (ticket == null) {
                event.reply("This channel isn't an active ticket.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!TicketPermissions.can(event.getMember(), ticket, TicketPermissions.Action.TRANSFER)) {
                event.reply(TicketPermissions.denialMessage(TicketPermissions.Action.TRANSFER))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            Member target = event.getGuild().getMemberById(event.getValues().get(0).getId());
            if (target == null) {
                event.reply("That member isn't in this server.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!TicketPermissions.isStaff(target, ticket)) {
                event.reply("The selected member must have a staff role configured for this ticket's panel.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            String result = new TicketManager().transferTicket(
                    event.getGuild(),
                    channel.getId(),
                    target.getId(),
                    new EmbedBuilder()
                            .setTitle("Ticket transferred")
                            .setDescription("Transferred to " + target.getAsMention()
                                    + " by " + event.getUser().getAsMention() + "."));
            replyStaffResult(event, result, "Ticket transferred to " + target.getAsMention() + ".");
        } catch (SQLException exception) {
            Log.error("TicketButton.replyStaffResult failed", exception);
            event.reply("Couldn't load the ticket record or staff settings.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("ticket:category:")) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null || event.getValues().isEmpty()) {
            event.reply("This ticket panel can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        try {
            int panelId = Integer.parseInt(event.getComponentId().substring("ticket:category:".length()));
            TicketPanelData panelData = Database.getPanelDataById(event.getGuild().getId(), panelId);
            if (panelData == null || !panelData.categories.contains(event.getValues().get(0))) {
                event.reply("That ticket category is no longer available.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (panelData.disabled) {
                event.reply(disabledText(panelData)).setEphemeral(true).queue();
                return;
            }
            String limit = limitMessage(event.getMember(), panelData);
            if (limit != null) {
                event.reply(limit).setEphemeral(true).queue();
                return;
            }
            String categoryName = event.getValues().get(0);
            event.getMessage().editMessageComponents(ActionRow.of(TicketPanel.categoryMenu(panelData))).queue();
            List<TicketQuestion> questions = Database.getPanelQuestions(panelId, categoryName);
            if (questions.isEmpty()) {
                event.deferReply(true)
                        .queue(hook -> createTicket(event.getGuild(), event.getMember(), panelData, categoryName,
                                List.of(),
                                List.of(),
                                message -> hook.editOriginal(message).queue()));
                return;
            }
            List<Label> fields = new ArrayList<>();
            for (int index = 0; index < questions.size(); index++) {
                TicketQuestion question = questions.get(index);
                TextInput.Builder answer = TextInput.create(
                                "answer" + index,
                                question.paragraph() ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT)
                        .setRequired(question.required())
                        .setMaxLength(question.effectiveMaxLength());
                if (question.placeholder() != null && !question.placeholder().isBlank()) {
                    answer.setPlaceholder(question.placeholder());
                }
                fields.add(Label.of(question.prompt(), answer.build()));
            }
            Modal modal = Modal.create("ticket:form:" + panelId + ":" + panelData.categories.indexOf(categoryName),
                    "New Ticket Request")
                    .addComponents(fields)
                    .build();
            event.replyModal(modal).queue();
        } catch (NumberFormatException exception) {
            event.reply("This ticket panel is invalid. Please contact a server moderator.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            Log.error("TicketButton.onStringSelectInteraction failed", exception);
            event.reply("Couldn't load the ticket settings. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private static String limitMessage(Member member, TicketPanelData panelData) throws SQLException {
        if (panelData == null || panelData.ticketLimit <= 0) {
            return null;
        }
        int open = Database.countOpenTickets(member.getId(), panelData.id);
        if (open < panelData.ticketLimit) {
            return null;
        }
        return "You already have " + open + " open ticket" + (open == 1 ? "" : "s") + " from this panel (the limit is "
                + panelData.ticketLimit + "). Please close one before opening another.";
    }

    private static String welcomeText(TicketPanelData panelData, Member member, String categoryName) {
        String template = panelData == null || panelData.welcomeMessage == null || panelData.welcomeMessage.isBlank()
                ? "Opened by {user}. Please describe your request here."
                : panelData.welcomeMessage;
        String text = template
                .replace("{user}", member.getAsMention())
                .replace("{username}", member.getUser().getName())
                .replace("{category}", categoryName == null ? "Support" : categoryName)
                .replace("{panel}", panelData == null ? "Support" : panelData.name);
        return text.length() > 4000 ? text.substring(0, 3997) + "..." : text;
    }

    public static final String DELETE_PROMPT =
            "⚠️ **Delete this ticket?** The channel and its record are removed for good. "
                    + "If the panel has a transcript channel, the transcript is sent there first.";

    public static ActionRow deleteConfirmRow() {
        return ActionRow.of(
                Button.danger("ticket:staff:deleteconfirm", "Yes, delete"),
                Button.secondary("ticket:deletecancel", "Cancel"));
    }

    private static String disabledText(TicketPanelData panelData) {
        return panelData.disabledMessage == null || panelData.disabledMessage.isBlank()
                ? TicketPanel.DEFAULT_DISABLED_MESSAGE
                : panelData.disabledMessage;
    }

    private void createTicket(ButtonInteractionEvent event, Integer panelId, String categoryName) {
        event.deferReply(true).queue(hook -> {
            try {
                TicketPanelData panelData = panelId == null
                        ? null
                        : Database.getPanelDataById(event.getGuild().getId(), panelId);
                if (panelId != null && panelData == null) {
                    hook.editOriginal("That ticket panel no longer exists.").queue();
                    return;
                }
                if (panelData != null && panelData.disabled) {
                    hook.editOriginal(disabledText(panelData)).queue();
                    return;
                }
                createTicket(event.getGuild(), event.getMember(), panelData, categoryName, List.of(),
                        List.of(),
                        message -> hook.editOriginal(message).queue());
            } catch (SQLException | RuntimeException exception) {
                Log.error("TicketButton.createTicket failed", exception);
                hook.editOriginal("Couldn't create your ticket. Please try again or contact a server moderator.")
                        .queue();
            }
        });
    }

    private void createTicket(
            net.dv8tion.jda.api.entities.Guild guild,
            Member member,
            TicketPanelData panelData,
            String categoryName,
            List<String> questions,
            List<String> answers,
            Consumer<String> completion) {
        try {
            String categoryId = panelData != null && panelData.ticketCategoryId != null
                    ? panelData.ticketCategoryId
                    : Database.getTicketCategory(guild.getId());
            String limit = limitMessage(member, panelData);
            if (limit != null) {
                completion.accept(limit);
                return;
            }
            List<String> staffRoles = panelData == null ? List.of() : panelData.staffRoles;
            List<String> pingRoles = panelData == null ? List.of() : panelData.pingRoles;
            new TicketManager().createTicket(
                    guild,
                    member,
                    categoryId,
                    categoryName,
                    panelData == null ? null : panelData.id,
                    staffRoles,
                    pingRoles,
                    ticket -> {
                        resetCategorySelection(guild, panelData);
                        try {
                            postTicketMessage(
                                    guild, member, panelData, categoryName, questions, answers, pingRoles, ticket,
                                    completion);
                        } catch (RuntimeException exception) {
                            reportTicketCreationFailure(exception, completion);
                        }
                    },
                    exception -> reportTicketCreationFailure(exception, completion));
        } catch (SQLException | RuntimeException exception) {
            reportTicketCreationFailure(exception, completion);
        }
    }

    private void resetCategorySelection(
            net.dv8tion.jda.api.entities.Guild guild,
            TicketPanelData panelData) {
        if (panelData == null || panelData.categories.isEmpty()) {
            return;
        }
        try {
            TicketPanelData currentPanelData = Database.getPanelDataById(guild.getId(), panelData.id);
            if (currentPanelData == null || currentPanelData.categories.isEmpty()) {
                return;
            }
            var update = new TicketPanel().editPublished(guild, currentPanelData);
            if (update != null) {
                update.queue(
                        ignored -> {
                        },
                        error -> Log.error("TicketButton.resetCategorySelection failed", error));
            }
        } catch (SQLException exception) {
            Log.error("TicketButton.resetCategorySelection failed", exception);
        }
    }

    private void postTicketMessage(
            net.dv8tion.jda.api.entities.Guild guild,
            Member member,
            TicketPanelData panelData,
            String categoryName,
            List<String> questions,
            List<String> answers,
            List<String> pingRoles,
            TicketData ticket,
            Consumer<String> completion) {
        TextChannel channel = guild.getTextChannelById(ticket.channelId);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(categoryName == null ? "Support ticket" : categoryName + " ticket")
                .setAuthor(member.getUser().getName(), null, member.getUser().getAvatarUrl())
                .setDescription(welcomeText(panelData, member, categoryName))
                .setFooter("Ticket ID: " + ticket.id);
        for (int i = 0; i < Math.min(answers.size(), 5); i++) {
            String answer = answers.get(i);
            if (answer.isBlank()) {

                answer = "*Not answered*";
            }
            embed.addField(questions.get(i), answer.length() > 1024 ? answer.substring(0, 1021) + "..." : answer,
                    false);
        }
        String roleMentions = pingRoles.stream()
                .map(roleId -> "<@&" + roleId + ">")
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        channel.sendMessage((roleMentions.isBlank() ? "" : roleMentions + " ") + member.getAsMention())
                .setEmbeds(embed.build())
                .setAllowedMentions(java.util.EnumSet.of(Message.MentionType.USER, Message.MentionType.ROLE))
                .addComponents(
                        ActionRow.of(
                                Button.danger("ticket:close", "Close"),
                                Button.primary("ticket:staff:claim", "Claim"),
                                Button.secondary("ticket:staff:unclaim", "Unclaim"),
                                Button.secondary("ticket:staff:transcript", "Transcript"),
                                Button.secondary("ticket:staff:transfer", "Transfer")),
                        ActionRow.of(Button.danger("ticket:staff:delete", "Delete ticket")))
                .queue(
                        ignored -> {
                            TicketLog.opened(guild, member, ticket);
                            ConsoleLogger.logConsole("TICKET_CREATED ticketId=" + ticket.id
                                    + " channelId=" + ticket.channelId
                                    + " user=\"" + member.getUser().getName() + "\" userId=" + member.getId()
                                    + " guild=\"" + guild.getName() + "\" guildId=" + guild.getId());
                            completion.accept("Ticket created: <#" + ticket.channelId + ">");
                        },
                        error -> completion.accept(
                                "Ticket channel created, but I couldn't post its embed or controls. Check my channel permissions."));
    }

    private void reportTicketCreationFailure(Throwable exception, Consumer<String> completion) {
        Log.error("TicketButton.reportTicketCreationFailure failed", exception);
        if (exception instanceof IllegalArgumentException) {
            completion.accept(exception.getMessage());
        } else if (exception instanceof InsufficientPermissionException) {
            completion.accept(
                    "I don't have permission to create ticket channels. Please ask a moderator to grant me Manage Channels and Manage Roles.");
        } else if (exception instanceof SQLException) {
            completion.accept("Couldn't save your ticket. Please try again or contact a server moderator.");
        } else {
            completion.accept(
                    "Couldn't create the ticket channel. Check that I have permission to manage channels and access the configured category.");
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String[] parts = event.getModalId().split(":");
        if (parts.length == 4 && "ticket".equals(parts[0]) && "form".equals(parts[1])) {
            submitTicketForm(event, parts);
        }
    }

    private void submitTicketForm(ModalInteractionEvent event, String[] parts) {
        try {
            int panelId = Integer.parseInt(parts[2]);
            int categoryIndex = Integer.parseInt(parts[3]);
            TicketPanelData panelData = Database.getPanelDataById(event.getGuild().getId(), panelId);
            if (panelData == null || categoryIndex < 0 || categoryIndex >= panelData.categories.size()) {
                event.reply("This ticket panel or category is no longer available.").setEphemeral(true).queue();
                return;
            }
            if (panelData.disabled) {
                event.reply(disabledText(panelData)).setEphemeral(true).queue();
                return;
            }
            String categoryName = panelData.categories.get(categoryIndex);
            List<TicketQuestion> questions = Database.getPanelQuestions(panelId, categoryName);
            List<String> prompts = new ArrayList<>();
            List<String> answers = new ArrayList<>();
            for (int index = 0; index < questions.size(); index++) {
                TicketQuestion question = questions.get(index);
                var field = event.getValue("answer" + index);
                String answer = field == null ? "" : field.getAsString().trim();
                if (question.required() && answer.isEmpty()) {
                    event.reply("Please answer: " + question.prompt()).setEphemeral(true).queue();
                    return;
                }
                prompts.add(question.prompt());
                answers.add(answer);
            }
            event.deferReply(true).queue(hook -> createTicket(
                    event.getGuild(), event.getMember(), panelData, categoryName, prompts, answers,
                    message -> hook.editOriginal(message).queue()));
        } catch (NumberFormatException | SQLException exception) {
            Log.error("TicketButton.submitTicketForm failed", exception);
            event.reply("Couldn't load the ticket form settings. Please try again.").setEphemeral(true).queue();
        }
    }

    private void handleStaffButton(ButtonInteractionEvent event, String action) {
        if (!(event.getChannel() instanceof TextChannel channel) || event.getMember() == null) {
            event.reply("This control can only be used inside a ticket channel.").setEphemeral(true).queue();
            return;
        }
        try {
            TicketData ticket = Database.getTicketByChannelId(channel.getId());
            if (ticket == null) {
                event.reply("This channel isn't an active ticket.").setEphemeral(true).queue();
                return;
            }
            TicketPermissions.Action required = TicketPermissions.Action.fromName(
                    "deleteconfirm".equals(action) ? "delete" : action);
            if (required == null) {
                event.reply("Unknown ticket control.").setEphemeral(true).queue();
                return;
            }
            if (!TicketPermissions.can(event.getMember(), ticket, required)) {
                event.reply(TicketPermissions.denialMessage(required))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if ("transfer".equals(action)) {
                EntitySelectMenu staffMember = EntitySelectMenu.create(
                                "ticket:transfer", EntitySelectMenu.SelectTarget.USER)
                        .setPlaceholder("Select a staff member")
                        .setRequiredRange(1, 1)
                        .build();
                event.reply("Choose a staff member to transfer this ticket to.")
                        .addComponents(ActionRow.of(staffMember))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if ("delete".equals(action)) {
                event.reply(DELETE_PROMPT)
                        .addComponents(deleteConfirmRow())
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if ("deleteconfirm".equals(action)) {
                event.deferEdit().queue(hook -> new TranscriptManager().sendThenDelete(
                        channel, ticket, hook, () -> {

                            try {
                                Database.deleteTicketByChannelId(channel.getId());
                                Log.info("Ticket channel " + channel.getId() + " deleted.");
                            } catch (SQLException exception) {
                                Log.error("Channel deleted, but ticket record removal failed.", exception);
                            }
                        }));
                return;
            }

            if ("transcript".equals(action)) {

                event.deferReply(true).queue(hook -> {
                    try {
                        new TranscriptManager().sendTranscript(channel, hook)
                                .whenComplete((ignored, error) -> {
                                    if (error != null) {
                                        Log.error("Transcript generation failed", error);
                                        hook.editOriginal("Couldn't create or upload the transcript.")
                                                .queue(null, editError ->
                                                        Log.warn("Failed to send transcript error message: "
                                                                + editError));
                                    }
                                });
                    } catch (RuntimeException exception) {
                        Log.error("Transcript generation failed", exception);
                        hook.editOriginal("Couldn't create the transcript.")
                                .queue(null, error ->
                                        Log.warn("Failed to send transcript error message: " + error));
                    }
                });
                return;
            }

            if ("claim".equals(action)) {
                String result = new TicketManager().claimTicket(event.getGuild(), channel.getId(),
                        event.getMember().getId(), new EmbedBuilder().setTitle("Ticket claimed")
                                .setDescription("Claimed by " + event.getUser().getAsMention() + "."));
                replyStaffResult(event, result, "Ticket claimed.");
            } else if ("unclaim".equals(action)) {
                String result = new TicketManager().unclaimTicket(event.getGuild(), channel.getId(),
                        new EmbedBuilder().setTitle("Ticket unclaimed")
                                .setDescription("Released by " + event.getUser().getAsMention() + "."));
                replyStaffResult(event, result, "Ticket unclaimed.");
            } else {
                event.reply("Unknown ticket control.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("TicketButton.replyStaffResult failed", exception);
            event.reply("Couldn't load the ticket record.").setEphemeral(true).queue();
        }
    }

    private void replyStaffResult(EntitySelectInteractionEvent event, String result, String successMessage) {
        String message = switch (result) {
            case "SUCCESS" -> successMessage;
            case "TICKET_NOT_FOUND" -> "This channel isn't an active ticket.";
            case "TICKET_CLOSED" -> "This ticket is closed.";
            case "ALREADY_CLAIMED" -> "This ticket has already been claimed.";
            case "NOT_CLAIMED" -> "This ticket isn't currently claimed.";
            case "SAME_CLAIMER" -> "This ticket is already assigned to that member.";
            default -> "Couldn't update this ticket.";
        };
        event.reply(message).setEphemeral(true).queue();
    }
    private void replyStaffResult(ButtonInteractionEvent event, String result, String successMessage) {
        String message = switch (result) {
            case "SUCCESS" -> successMessage;
            case "TICKET_NOT_FOUND" -> "This channel isn't an active ticket.";
            case "TICKET_CLOSED" -> "This ticket is closed.";
            case "ALREADY_CLAIMED" -> "This ticket has already been claimed.";
            case "NOT_CLAIMED" -> "This ticket isn't currently claimed.";
            case "SAME_CLAIMER" -> "This ticket is already assigned to that member.";
            default -> "Couldn't update this ticket.";
        };
        event.reply(message).setEphemeral(true).queue();
    }

    private void closeTicket(ButtonInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel) || event.getMember() == null) {
            event.reply("This button can only be used inside a ticket channel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            TicketData ticket = Database.getTicketByChannelId(channel.getId());
            if (ticket == null) {
                event.reply("This channel isn't an active ticket.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!TicketPermissions.can(event.getMember(), ticket, TicketPermissions.Action.CLOSE)) {
                event.reply(TicketPermissions.denialMessage(TicketPermissions.Action.CLOSE))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if ("CLOSED".equals(ticket.state)) {
                event.reply("This ticket is already closed.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            new TicketManager().closeTicket(
                    event.getGuild(),
                    channel.getId(),
                    ticket.creatorId,
                    new EmbedBuilder().setTitle("Ticket closed")
                            .setDescription("Closed by " + event.getUser().getAsMention() + "."));
            new TranscriptManager().sendOnClose(channel, ticket);
            event.reply("Ticket closed.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            Log.error("TicketButton.closeTicket failed", exception);
            event.reply("Couldn't close this ticket because its data couldn't be saved.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void reopenTicket(ButtonInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel) || event.getMember() == null) {
            event.reply("This button can only be used inside a ticket channel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            TicketData ticket = Database.getTicketByChannelId(channel.getId());
            if (ticket == null) {
                event.reply("This channel isn't an active ticket.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!TicketPermissions.can(event.getMember(), ticket, TicketPermissions.Action.REOPEN)) {
                event.reply(TicketPermissions.denialMessage(TicketPermissions.Action.REOPEN))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!"CLOSED".equals(ticket.state)) {
                event.reply("This ticket is already open.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            new TicketManager().reopenTicket(
                    event.getGuild(),
                    channel.getId(),
                    ticket.creatorId,
                    new EmbedBuilder().setTitle("Ticket reopened")
                            .setDescription("Reopened by " + event.getUser().getAsMention() + "."));
            event.reply("Ticket reopened.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            Log.error("TicketButton.reopenTicket failed", exception);
            event.reply("Couldn't reopen this ticket because its data couldn't be saved.")
                    .setEphemeral(true)
                    .queue();
        }
    }
}