package commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import tickets.TicketPanel;

public class Ticket extends ListenerAdapter implements Command {
    @Override
    public CommandData getCommandData() {
        OptionData channel = new OptionData(
                OptionType.CHANNEL,
                "channel",
                "The channel where the panel will be sent",
                true
        ).setChannelTypes(ChannelType.TEXT);
        return Commands.slash("ticket", "Manage the ticket system")
            .addSubcommands(
                new SubcommandData("send-panel", "Sends the ticket panel")
                    .addOptions(channel)
            );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"ticket".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You don't have permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!"send-panel".equals(event.getSubcommandName())) {
            return;
        }

        OptionMapping channelOption = event.getOption("channel");
        TextChannel channel;
        if (channelOption != null) {
            if (!(channelOption.getAsChannel() instanceof TextChannel selectedChannel)) {
                event.reply("Please choose a text channel for the ticket panel.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            channel = selectedChannel;
        } else if (event.getChannel() instanceof TextChannel currentChannel) {
            channel = currentChannel;
        } else {
            event.reply("Please choose a text channel for the ticket panel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue(hook ->
                new TicketPanel().panel(null, channel, null).queue(
                        message -> hook.sendMessage("Ticket panel sent to " + channel.getAsMention() + ".").queue(),
                        error -> hook.sendMessage("Couldn't send the ticket panel. Check that I can view and send messages in that channel.")
                                .queue()
                )
        );
    }
}