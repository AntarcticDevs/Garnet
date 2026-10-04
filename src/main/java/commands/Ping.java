package commands;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class Ping extends ListenerAdapter implements Command {

    @Override
    public CommandData getCommandData() {
        return Commands.slash("ping", "Replies with Pong!");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        if (!event.getName().equals("ping")) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Latency");
        embed.setDescription("**Ping: " + event.getJDA().getGatewayPing() + "ms**");

        event.replyEmbeds(embed.build()).queue();
    }
}
