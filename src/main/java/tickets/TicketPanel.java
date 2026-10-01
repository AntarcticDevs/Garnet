package tickets;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;

import utils.EmbedHelpers;



public class TicketPanel {
    public TicketData panel(EmbedBuilder embed, Guild guild, String channelID, Button button) {
        TicketData ticket = new TicketData();
        TextChannel channel = guild.getTextChannelById(channelID);

        if (embed == null) {
            embed = new EmbedBuilder();
            embed.setTitle("Create a ticket")
                 .setDescription("Use the button below to create a ticket");
        }

        EmbedHelpers.setDefaultFooter(embed, guild);

        Button finalButton;
        if (button == null) {
            finalButton = Button.primary("ticket:support", "Support");
        } else {
            finalButton = button;
        }

        channel.sendMessageEmbeds(embed.build())
        .addComponents(ActionRow.of(finalButton))
        .queue();

        return ticket;
    }
}
