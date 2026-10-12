package tickets;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.components.separator.Separator;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;



public class TicketPanelEditor extends ListenerAdapter {

    public void open(SlashCommandInteractionEvent event, TicketPanelData panelData) {
        TextDisplay title = TextDisplay.of("# TicketPanelEditor");
        TextDisplay subTitle = TextDisplay.of("## Editing: " + panelData.name);
        TextDisplay description = TextDisplay.of("**Configure how this ticket panel works.**");

        TextDisplay categoryTotal = TextDisplay.of("**Categories: " + panelData.categories.size() + "**");
        TextDisplay staffRolesTotal = TextDisplay.of("**Staff Roles: " + panelData.staffRoles.size() + "**");
        TextDisplay pingRolesTotal = TextDisplay.of("**Ping Roles: " + panelData.pingRoles.size() + "**");
        TextDisplay ticketCategory = TextDisplay.of(panelData.ticketCategoryId == null
            ? "**Ticket Category: Not set**"
            : "**Ticket Category: <#" + panelData.ticketCategoryId + ">" + "**");

        Button button1 = Button.primary("panel_edit:content:" + panelData.id, "Content");
        Button button2 = Button.primary("panel_edit:categories:" + panelData.id, "Categories");
        Button button3 = Button.primary("panel_edit:staff_roles:" + panelData.id, "Staff Roles");
        Button button4 = Button.primary("panel_edit:ping_roles:" + panelData.id, "Ping Roles");
        Button button5 = Button.primary("panel_edit:ticket_category:" + panelData.id, "Ticket Category");
        Button button6 = Button.primary("panel_edit:ticket_messages:" + panelData.id, "Ticket Messages");

        Container container = Container.of(
            title,
            subTitle,
            description,
            Separator.createDivider(Separator.Spacing.LARGE),
            categoryTotal,
            staffRolesTotal,
            pingRolesTotal,
            ticketCategory,
            Separator.createDivider(Separator.Spacing.LARGE),
            TextDisplay.of("## Edit:"),
            ActionRow.of(button1, button2, button3),
            ActionRow.of(button4, button5, button6),
            Separator.createDivider(Separator.Spacing.LARGE),
            TextDisplay.of("-# © 2026 Garnet • All rights reserved.")
        );

        MessageCreateBuilder message = new MessageCreateBuilder()
            .useComponentsV2()
            .addComponents(container);

        event.reply(message.build()).setEphemeral(true).queue();
    }


    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getButton().getCustomId().startsWith("panel_edit:")) {
            return;
        }

        String[] parts = event.getButton().getCustomId().split(":");

        if (parts.length != 3) {
            return;
        }

        String action = parts[1];
        int panelId = Integer.parseInt(parts[2]);

        if ("content".equals(action)) {

            TextInput content = TextInput.create("content", TextInputStyle.PARAGRAPH).setRequired(false).build();

            Label contentLabel = Label.of(
                "Panel Content", content
            );

            Modal modal = Modal.create(
                    "panel_edit:content:" + panelId,
                    "Edit Panel Content"
            )
                    .addComponents(contentLabel)
                    .build();

            event.replyModal(modal).queue();
        }
    }
}