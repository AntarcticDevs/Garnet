package tickets;


import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.EmbedBuilder;

import net.dv8tion.jda.api.Permission;

import java.sql.SQLException;

import database.Database;



public class TicketManager {

    public TicketData createTicket(Guild guild, Member member, String categoryID) {
        TicketData ticket = new TicketData();

        TextChannel channel;

        if (categoryID == null) {
            channel = guild.createTextChannel("ticket-" + member.getUser().getName()).complete();
        } else {
            Category category = guild.getCategoryById(categoryID);

            if (category == null) {
                channel = guild.createTextChannel("ticket-" + member.getUser().getName()).complete();
            } else {
                channel = guild.createTextChannel(
                        member.getUser().getName() + " ticket",
                        category
                ).complete();
            }
        }

        ticket.channelId = channel.getId();
        ticket.creatorId = member.getId();
        ticket.state = "OPEN";
        ticket.claimState = "UNCLAIMED";

        try {
            Database.saveTicket(ticket);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ticket;
    }


    public void deleteTicket(Guild guild, String channelID) {

        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        channel.delete().queue(
            success -> {
                try {
                    Database.deleteTicketByChannelId(channelID);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            },
            error -> {
                error.printStackTrace();
            }
        );
    }


    public void closeTicket(Guild guild, String channelID, String creatorID, EmbedBuilder embed) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        TicketData ticket;
        try {
            ticket = Database.getTicketByChannelId(channelID);
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        if (ticket == null) {
            return;
        }

        String previousName = channel.getName();
        channel.getManager().setName("closed-" + previousName).queue();
        Member creator = guild.getMemberById(creatorID);
        
        ticket.state = "CLOSED";

        try {
            Database.updateTicket(ticket);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        channel.upsertPermissionOverride(creator).deny(Permission.MESSAGE_SEND).queue();

        channel.sendMessageEmbeds(embed.build()).queue();

    }


    public void reopenTicket(Guild guild, String channelID, String creatorID, EmbedBuilder embed) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        TicketData ticket;
        try {
            ticket = Database.getTicketByChannelId(channelID);
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        if (ticket == null) {
            return;
        }

        String currentName = channel.getName();
        if (currentName.startsWith("closed-")) {
            channel.getManager().setName(currentName.substring("closed-".length())).queue();
        }
        Member creator = guild.getMemberById(creatorID);

        ticket.state = "OPEN";

        try {
            Database.updateTicket(ticket);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        channel.upsertPermissionOverride(creator).grant(Permission.MESSAGE_SEND).queue();
        channel.sendMessageEmbeds(embed.build()).queue();
    }


    public String claimTicket(Guild guild, String channelID, String claimerID, EmbedBuilder embed) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);

        TicketData ticket;

        try {
            ticket = Database.getTicketByChannelId(channelID);
        } catch (SQLException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
            return "DATABASE_ERROR";
        }

        channel.sendMessageEmbeds(embed.build()).queue();

        return "SUCCESS";
    }
}
