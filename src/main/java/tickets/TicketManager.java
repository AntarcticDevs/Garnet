package tickets;

import utils.Log;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import database.Database;

public class TicketManager {

    public void createTicket(
            Guild guild,
            Member member,
            String categoryID,
            String categoryName,
            Integer panelId,
            List<String> staffRoleIds,
            List<String> pingRoleIds,
            Consumer<TicketData> onSuccess,
            Consumer<Throwable> onFailure) {
        TicketData ticket = new TicketData();
        ticket.panelId = panelId;

        Category category = categoryID == null ? null : guild.getCategoryById(categoryID);
        if (categoryID != null && category == null) {
            throw new IllegalArgumentException("Configured ticket category does not exist in this server.");
        }

        String ownerName = member.getUser().getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String topicName = categoryName == null || categoryName.isBlank()
                ? ""
                : "-" + categoryName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String channelName = "ticket-" + ownerName + topicName;
        channelName = channelName.replaceAll("-{2,}", "-");
        channelName = channelName.replaceAll("(^-|-$)", "");
        if (channelName.length() > 90) {
            channelName = channelName.substring(0, 90);
        }
        ChannelAction<TextChannel> channelAction = category == null
                ? guild.createTextChannel(channelName)
                : guild.createTextChannel(channelName, category);

        channelAction.addPermissionOverride(
                guild.getPublicRole(),
                null,
                EnumSet.of(Permission.VIEW_CHANNEL));
        channelAction.addPermissionOverride(
                member,
                EnumSet.of(
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_SEND,
                        Permission.MESSAGE_ATTACH_FILES,
                        Permission.MESSAGE_EMBED_LINKS,
                        Permission.MESSAGE_ADD_REACTION,
                        Permission.MESSAGE_HISTORY),
                null);
        EnumSet<Permission> staffPermissions = EnumSet.of(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_HISTORY);
        for (String roleId : java.util.stream.Stream.concat(
                staffRoleIds.stream(), pingRoleIds.stream()).distinct().toList()) {
            var role = guild.getRoleById(roleId);
            if (role != null && !guild.getPublicRole().getId().equals(roleId)) {
                channelAction.addPermissionOverride(role, staffPermissions, null);
            }
        }

        channelAction.queue(channel -> {
            ticket.channelId = channel.getId();
            ticket.creatorId = member.getId();
            ticket.state = "OPEN";
            ticket.claimState = "UNCLAIMED";
            ticket.categoryName = categoryName;

            try {
                Database.saveTicket(ticket);
            } catch (SQLException | RuntimeException exception) {
                channel.delete().queue(
                        ignored -> {
                        },
                        exception::addSuppressed);
                onFailure.accept(exception);
                return;
            }
            onSuccess.accept(ticket);
        }, onFailure);
    }

    public void deleteTicket(Guild guild, String channelID) {

        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        channel.delete().queue(
                success -> {
                    try {
                        Database.deleteTicketByChannelId(channelID);
                    } catch (SQLException e) {
                        Log.error("TicketManager.deleteTicket failed", e);
                    }
                },
                error -> {
                    Log.error("TicketManager.deleteTicket failed", error);
                });
    }

    public void closeTicket(Guild guild, String channelID, String creatorID, EmbedBuilder embed) throws SQLException {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        if (channel == null) {
            return;
        }
        TicketData ticket = Database.getTicketByChannelId(channelID);
        if (ticket == null) {
            return;
        }
        if ("CLOSED".equals(ticket.state)) {
            return;
        }

        String previousName = channel.getName();
        channel.getManager().setName("closed-" + previousName).queue();
        Member creator = guild.getMemberById(creatorID);

        ticket.state = "CLOSED";
        Database.updateTicket(ticket);

        if (creator != null) {
            channel.upsertPermissionOverride(creator).deny(Permission.MESSAGE_SEND).queue();
        }

        channel.sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(Button.success("ticket:reopen", "Reopen ticket")))
                .queue();

    }

    public void reopenTicket(Guild guild, String channelID, String creatorID, EmbedBuilder embed) throws SQLException {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        if (channel == null) {
            return;
        }
        TicketData ticket = Database.getTicketByChannelId(channelID);
        if (ticket == null) {
            return;
        }
        if (!"CLOSED".equals(ticket.state)) {
            return;
        }

        String currentName = channel.getName();
        if (currentName.startsWith("closed-")) {
            channel.getManager().setName(currentName.substring("closed-".length())).queue();
        }
        Member creator = guild.getMemberById(creatorID);

        ticket.state = "OPEN";
        Database.updateTicket(ticket);

        if (creator != null) {
            channel.upsertPermissionOverride(creator).grant(Permission.MESSAGE_SEND).queue();
        }
        channel.sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(Button.danger("ticket:close", "Close ticket")))
                .queue();
    }

    public String claimTicket(Guild guild, String channelID, String claimerID, EmbedBuilder embed) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);

        TicketData ticket;

        try {
            ticket = Database.getTicketByChannelId(channelID);
        } catch (SQLException e) {
            Log.error("TicketManager.claimTicket failed", e);
            return "DATABASE_ERROR";
        }

        if (ticket == null) {
            return "TICKET_NOT_FOUND";
        }

        if ("CLOSED".equals(ticket.state)) {
            return "TICKET_CLOSED";
        }

        if ("CLAIMED".equals(ticket.claimState)) {
            return "ALREADY_CLAIMED";
        }

        ticket.claimerId = claimerID;
        ticket.claimState = "CLAIMED";

        try {
            Database.updateTicket(ticket);
        } catch (SQLException e) {
            Log.error("TicketManager.claimTicket failed", e);
            return "DATABASE_ERROR";
        }

        channel.sendMessageEmbeds(embed.build()).queue();

        return "SUCCESS";
    }

    public String unclaimTicket(Guild guild, String channelID, EmbedBuilder embed) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);

        TicketData ticket;
        try {
            ticket = Database.getTicketByChannelId(channelID);
        } catch (SQLException e) {
            Log.error("TicketManager.unclaimTicket failed", e);
            return "DATABASE_ERROR";
        }
        if (ticket == null) {
            return "TICKET_NOT_FOUND";
        }

        if ("CLOSED".equals(ticket.state)) {
            return "TICKET_CLOSED";
        }

        if (!"CLAIMED".equals(ticket.claimState)) {
            return "NOT_CLAIMED";
        }

        ticket.claimerId = null;
        ticket.claimState = "UNCLAIMED";

        try {
            Database.updateTicket(ticket);
        } catch (SQLException e) {
            Log.error("TicketManager.unclaimTicket failed", e);
            return "DATABASE_ERROR";
        }

        channel.sendMessageEmbeds(embed.build()).queue();

        return "SUCCESS";
    }

    public String transferTicket(Guild guild, String channelID, String claimerID, EmbedBuilder embed) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);

        TicketData ticket;
        try {
            ticket = Database.getTicketByChannelId(channelID);
        } catch (SQLException e) {
            Log.error("TicketManager.transferTicket failed", e);
            return "DATABASE_ERROR";
        }
        if (ticket == null) {
            return "TICKET_NOT_FOUND";
        }

        if ("CLOSED".equals(ticket.state)) {
            return "TICKET_CLOSED";
        }

        if (!"CLAIMED".equals(ticket.claimState)) {
            return "NOT_CLAIMED";
        }

        if (claimerID.equals(ticket.claimerId)) {
            return "SAME_CLAIMER";
        }

        ticket.claimerId = claimerID;

        try {
            Database.updateTicket(ticket);
        } catch (SQLException e) {
            Log.error("TicketManager.transferTicket failed", e);
            return "DATABASE_ERROR";
        }

        channel.sendMessageEmbeds(embed.build()).queue();

        return "SUCCESS";
    }
}