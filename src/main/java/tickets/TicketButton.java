package tickets;

import java.sql.SQLException;

import database.Database;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;



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
        if ("ticket:reopen".equals(customId)) {
            reopenTicket(event);
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
            createTicket(event, panelData, event.getValues().get(0));
        } catch (NumberFormatException exception) {
            event.reply("This ticket panel is invalid. Please contact a server moderator.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            exception.printStackTrace();
            event.reply("Couldn't load the ticket settings. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void createTicket(ButtonInteractionEvent event, Integer panelId, String categoryName) {
        event.deferReply(true).queue();
        Member member = event.getMember();
        if (member == null) {
            event.getHook().editOriginal("Couldn't identify your server membership. Please try again.").queue();
            return;
        }

        try {
            TicketPanelData panelData = panelId == null
                    ? null
                    : Database.getPanelDataById(event.getGuild().getId(), panelId);
            if (panelId != null && panelData == null) {
                event.getHook().editOriginal("That ticket panel no longer exists.").queue();
                return;
            }
            String categoryId = panelData != null && panelData.ticketCategoryId != null
                    ? panelData.ticketCategoryId
                    : Database.getTicketCategory(event.getGuild().getId());
            TicketManager manager = new TicketManager();
            TicketData ticket = manager.createTicket(
                    event.getGuild(),
                    member,
                    categoryId,
                    categoryName,
                    panelData == null ? java.util.List.of() : panelData.staffRoles,
                    panelData == null ? java.util.List.of() : panelData.pingRoles
            );
            TextChannel channel = event.getGuild().getTextChannelById(ticket.channelId);
            String roleMentions = panelData == null ? "" : panelData.pingRoles.stream()
                    .map(roleId -> "<@&" + roleId + ">")
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            String message = (roleMentions.isBlank() ? "" : roleMentions + " ")
                    + member.getAsMention() + ", your ticket is open.";
            if (categoryName != null) {
                message += " Category: **" + categoryName.replace("@", "@\u200B") + "**.";
            }
            channel.sendMessage(message)
                    .setAllowedMentions(java.util.EnumSet.of(Message.MentionType.USER, Message.MentionType.ROLE))
                    .addComponents(ActionRow.of(Button.danger("ticket:close", "Close ticket")))
                    .queue(
                            ignored -> event.getHook().editOriginal("Ticket created: <#" + ticket.channelId + ">").queue(),
                            error -> event.getHook().editOriginal(
                                    "Ticket channel created, but I couldn't post its controls. Check my channel permissions."
                            ).queue()
                    );
        } catch (SQLException | RuntimeException exception) {
            exception.printStackTrace();
            event.getHook().editOriginal("Couldn't create your ticket. Please try again or contact a server moderator.")
                    .queue();
        }
    }

    private void createTicket(StringSelectInteractionEvent event, TicketPanelData panelData, String categoryName) {
        event.deferReply(true).queue();
        Member member = event.getMember();
        try {
            String categoryId = panelData.ticketCategoryId != null
                    ? panelData.ticketCategoryId
                    : Database.getTicketCategory(event.getGuild().getId());
            TicketData ticket = new TicketManager().createTicket(
                    event.getGuild(),
                    member,
                    categoryId,
                    categoryName,
                    panelData.staffRoles,
                    panelData.pingRoles
            );
            TextChannel channel = event.getGuild().getTextChannelById(ticket.channelId);
            String roleMentions = panelData.pingRoles.stream()
                    .map(roleId -> "<@&" + roleId + ">")
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            String message = (roleMentions.isBlank() ? "" : roleMentions + " ")
                    + member.getAsMention() + ", your ticket is open. Category: **"
                    + categoryName.replace("@", "@\u200B") + "**.";
            channel.sendMessage(message)
                    .setAllowedMentions(java.util.EnumSet.of(Message.MentionType.USER, Message.MentionType.ROLE))
                    .addComponents(ActionRow.of(Button.danger("ticket:close", "Close ticket")))
                    .queue(
                            ignored -> event.getHook().editOriginal("Ticket created: <#" + ticket.channelId + ">").queue(),
                            error -> event.getHook().editOriginal(
                                    "Ticket channel created, but I couldn't post its controls. Check my channel permissions."
                            ).queue()
                    );
        } catch (SQLException | RuntimeException exception) {
            exception.printStackTrace();
            event.getHook().editOriginal("Couldn't create your ticket. Please try again or contact a server moderator.")
                    .queue();
        }
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
            if (!ticket.creatorId.equals(event.getMember().getId())
                    && !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                event.reply("Only the ticket creator or a channel manager can close this ticket.")
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
                            .setDescription("Closed by " + event.getUser().getAsMention() + ".")
            );
            event.reply("Ticket closed.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            exception.printStackTrace();
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
            if (!ticket.creatorId.equals(event.getMember().getId())
                    && !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                event.reply("Only the ticket creator or a channel manager can reopen this ticket.")
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
                            .setDescription("Reopened by " + event.getUser().getAsMention() + ".")
            );
            event.reply("Ticket reopened.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            exception.printStackTrace();
            event.reply("Couldn't reopen this ticket because its data couldn't be saved.")
                    .setEphemeral(true)
                    .queue();
        }
    }
}