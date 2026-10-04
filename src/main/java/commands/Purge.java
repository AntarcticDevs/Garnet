package commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.time.OffsetDateTime;
import java.util.List;

public class Purge extends ListenerAdapter implements Command {
    @Override
    public CommandData getCommandData() {
        OptionData amount = new OptionData(
                OptionType.INTEGER,
                "amount",
                "Number of recent messages to delete (1-100)",
                true
        ).setRequiredRange(1, 100);

        return Commands.slash("purge", "Delete recent messages from this channel")
                .addOptions(amount)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"purge".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("This command can only be used in a server text channel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("You need the Manage Messages permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!event.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            event.reply("I need the Manage Messages permission in this channel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int amount = event.getOption("amount").getAsInt();
        event.deferReply(true).queue(hook ->
                channel.getHistory().retrievePast(amount).queue(messages -> {
                    if (messages.isEmpty()) {
                        hook.sendMessage("There are no messages to delete.").queue();
                        return;
                    }

                    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);
                    if (messages.size() > 1 && messages.stream()
                            .anyMatch(message -> message.getTimeCreated().isBefore(cutoff))) {
                        hook.sendMessage("Discord only allows bulk deletion of messages from the last 14 days. No messages were deleted.")
                                .queue();
                        return;
                    }

                    deleteMessages(channel, messages, hook);
                }, error -> {
                    error.printStackTrace();
                    hook.sendMessage("I couldn't read the message history. Check that I can view this channel.")
                            .queue();
                })
        );
    }

    private void deleteMessages(
            TextChannel channel,
            List<Message> messages,
            net.dv8tion.jda.api.interactions.InteractionHook hook
    ) {
        if (messages.size() == 1) {
            messages.get(0).delete().queue(
                    ignored -> hook.sendMessage("Deleted 1 message.").queue(),
                    error -> {
                        error.printStackTrace();
                        hook.sendMessage("I couldn't delete that message. Check my channel permissions.")
                                .queue();
                    }
            );
            return;
        }

        channel.deleteMessages(messages).queue(
                ignored -> hook.sendMessage("Deleted " + messages.size() + " messages.").queue(),
                error -> {
                    error.printStackTrace();
                    hook.sendMessage("I couldn't delete those messages. Check my channel permissions and try again.")
                            .queue();
                }
        );
    }
}
