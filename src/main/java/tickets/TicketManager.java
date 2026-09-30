package tickets;


import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.EmbedBuilder;

import net.dv8tion.jda.api.Permission;


public class TicketManager {

    public TicketData createTicket(Guild guild, Member member, String categoryID) {
        TicketData ticket = new TicketData();
        Category category = guild.getCategoryById(categoryID);

        TextChannel channel = guild.createTextChannel(member.getUser().getName() + " ticket", category).complete();

        ticket.channelId = channel.getId();
        ticket.creatorId = member.getId();
        return  ticket;
    }


    public void deleteTicket(Guild guild, String channelID) {

        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        channel.delete();
    }


    public void closeTicket(Guild guild, String channelID, String creatorID, EmbedBuilder embed, TicketData ticket) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        String previousName = channel.getName();
        channel.getManager().setName("closed-" + previousName).queue();
        Member creator = guild.getMemberById(creatorID);
        
        ticket.state = "CLOSED";

        channel.upsertPermissionOverride(creator).deny(Permission.MESSAGE_SEND).queue();

        channel.sendMessageEmbeds(embed.build()).queue();

    }


    public void reopenTicket(Guild guild, String channelID, String creatorID, EmbedBuilder embed, TicketData ticket) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);
        String currentName = channel.getName();
        if (currentName.startsWith("closed-")) {
            channel.getManager().setName(currentName.substring("closed-".length())).queue();
        }
        Member creator = guild.getMemberById(creatorID);

        ticket.state = "OPEN";

        channel.upsertPermissionOverride(creator).grant(Permission.MESSAGE_SEND).queue();
        channel.sendMessageEmbeds(embed.build()).queue();
    }


    public String claimTicket(Guild guild, String channelID, String claimerID, EmbedBuilder embed, TicketData ticket) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);

        if ("CLOSED".equals(ticket.state)) {
            return "TICKET_CLOSED";
        }

        if ("CLAIMED".equals(ticket.state)) {
            return "ALREADY_CLAIMED";
        }

        ticket.claimerId = claimerID;
        ticket.claimState = "CLAIMED";

        channel.sendMessageEmbeds(embed.build()).queue();

        return "SUCCESS";
    }


    public String unclaimTicket(Guild guild, String channelID, EmbedBuilder embed, TicketData ticket) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);

        if ("CLOSED".equals(ticket.state)) {
            return "TICKET_CLOSED";
        }

        if (!"CLAIMED".equals(ticket.claimState)) {
            return "NOT_CLAIMED";
        }

        ticket.claimerId = null;
        ticket.claimState = "UNCLAIMED";

        channel.sendMessageEmbeds(embed.build()).queue();

        return "SUCCESS";
    }

    public String transferTicket(Guild guild, String channelID, String claimerID, EmbedBuilder embed, TicketData ticket) {
        TextChannel channel = guild.getChannelById(TextChannel.class, channelID);

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
        channel.sendMessageEmbeds(embed.build()).queue();

        return "SUCCESS";
    }
}