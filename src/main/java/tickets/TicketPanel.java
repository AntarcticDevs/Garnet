package tickets;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.requests.RestAction;

import utils.EmbedHelpers;


public class TicketPanel {
    public RestAction<Message> panel(EmbedBuilder embed, TextChannel channel, Button button) {
        if (embed == null) {
            embed = new EmbedBuilder();
            embed.setTitle("Create a ticket")
                 .setDescription("Use the button below to create a ticket");
        }

        EmbedHelpers.setDefaultFooter(embed, channel.getGuild());

        Button finalButton;
        if (button == null) {
            finalButton = Button.primary("ticket:support", "Support");
        } else {
            finalButton = button;
        }

        return channel.sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(finalButton));
    }
}
