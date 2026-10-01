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

        TicketManager manager = new TicketManager();

        String id = event.getButton().getCustomId();

        if (id.equals("ticket:support")) {
            Member member = event.getMember();
            manager.createTicket(event.getGuild(), member, null); // Work in progress
        }
    }
}
