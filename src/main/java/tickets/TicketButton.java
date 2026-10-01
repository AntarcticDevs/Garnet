package tickets;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;



public class TicketButton extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {

        if (event.getGuild() == null) {
            return;
        }

        if (!event.getButton().getCustomId().equals("ticket:support")) {
            return;
        }

        event.deferReply(true).queue();

        TicketManager manager = new TicketManager();

        Member member = event.getMember();

        TicketData ticket = manager.createTicket(
                event.getGuild(),
                member,
                null
        );

        event.getHook().editOriginal(
                "Ticket created: <#" + ticket.channelId + ">"
        ).queue();
    }
}
