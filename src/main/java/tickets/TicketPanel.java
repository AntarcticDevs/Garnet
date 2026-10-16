package tickets;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.requests.RestAction;

import utils.EmbedHelpers;


public class TicketPanel {
    public RestAction<Message> panel(TicketPanelData panelData, TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(panelData.name)
                .setDescription(panelData.content == null || panelData.content.isBlank()
                        ? "Use the button below to create a ticket."
                        : panelData.content);

        EmbedHelpers.setDefaultFooter(embed, channel.getGuild());
        String customId = panelData.id > 0 ? "ticket:create:" + panelData.id : "ticket:support";
        if (!panelData.categories.isEmpty()) {
            StringSelectMenu.Builder categories = StringSelectMenu.create("ticket:category:" + panelData.id)
                    .setPlaceholder("Choose a ticket category");
            panelData.categories.stream().limit(25).forEach(category ->
                    categories.addOptions(SelectOption.of(category, category)));
            return channel.sendMessageEmbeds(embed.build())
                    .addComponents(ActionRow.of(categories.build()));
        }
        return channel.sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(Button.primary(customId, "Create ticket")));
    }


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
