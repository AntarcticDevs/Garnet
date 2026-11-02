package tickets;

import database.Database;
import logging.LogCategory;
import logging.LogService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import utils.Log;

import java.awt.Color;
import java.sql.SQLException;

public final class TicketLog {

    public enum Kind {
        OPENED("Ticket opened", LogService.GREEN),
        CLOSED("Ticket closed", LogService.YELLOW),
        REOPENED("Ticket reopened", LogService.GREEN),
        CLAIMED("Ticket claimed", LogService.BLUE),
        UNCLAIMED("Ticket unclaimed", LogService.GREY),
        TRANSFERRED("Ticket transferred", LogService.BLUE),
        DELETED("Ticket deleted", LogService.RED);

        private final String title;
        private final Color colour;

        Kind(String title, Color colour) {
            this.title = title;
            this.colour = colour;
        }
    }

    private TicketLog() {
    }

    public static void opened(Guild guild, Member member, TicketData ticket) {
        log(guild, Kind.OPENED, ticket, null, "Opened by " + member.getAsMention() + ".");
    }

    public static void fromEmbed(Guild guild, Kind kind, TicketData ticket, TextChannel channel, EmbedBuilder embed) {
        log(guild, kind, ticket, channel == null ? null : channel.getName(), embed.getDescriptionBuilder().toString());
    }

    public static void log(Guild guild, Kind kind, TicketData ticket, String channelName, String detail) {
        try {
            if (!LogService.wants(guild, LogCategory.TICKETS)) {
                return;
            }
            TextChannel channel = ticket.channelId == null ? null : guild.getTextChannelById(ticket.channelId);
            String channelText = channel != null
                    ? channel.getAsMention()
                    : channelName == null ? "(deleted)" : "#" + channelName;

            EmbedBuilder embed = LogService.embed(kind.title, kind.colour)
                    .addField("Ticket", "#" + ticket.id, true)
                    .addField("Channel", channelText, true);
            if (ticket.creatorId != null) {
                embed.addField("Opened by", "<@" + ticket.creatorId + ">", true);
            }
            if (ticket.panelId != null) {
                try {
                    TicketPanelData panel = Database.getPanelDataById(guild.getId(), ticket.panelId);
                    if (panel != null) {
                        embed.addField("Panel", panel.name, true);
                    }
                } catch (SQLException exception) {
                    Log.warn("TicketLog couldn't load the panel name: " + exception.getMessage());
                }
            }
            if (ticket.categoryName != null && !ticket.categoryName.isBlank()) {
                embed.addField("Category", ticket.categoryName, true);
            }
            if (ticket.claimerId != null && kind != Kind.UNCLAIMED) {
                embed.addField("Claimed by", "<@" + ticket.claimerId + ">", true);
            }
            if (detail != null && !detail.isBlank()) {
                embed.setDescription(detail);
            }
            LogService.send(guild, LogCategory.TICKETS, embed);
        } catch (RuntimeException exception) {
            Log.error("TicketLog.log failed", exception);
        }
    }
}
