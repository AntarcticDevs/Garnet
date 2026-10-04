package commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class Ban extends ListenerAdapter implements Command {
    @Override
    public CommandData getCommandData() {
        return Commands.slash("ban", "Ban a user from this server")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "The user to ban", true),
                        new OptionData(OptionType.STRING, "reason", "Reason for the ban", false)
                                .setMaxLength(512)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"ban".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need the Ban Members permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("I need the Ban Members permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        User user = event.getOption("user").getAsUser();
        Member targetMember = event.getGuild().getMember(user);
        if (user.equals(event.getJDA().getSelfUser())
                || user.equals(event.getUser())
                || (targetMember != null && !event.getMember().canInteract(targetMember))
                || (targetMember != null && !event.getGuild().getSelfMember().canInteract(targetMember))) {
            event.reply("I can't ban that user because they are you, the bot, or have a higher role.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String reason = event.getOption("reason") == null
                ? "No reason provided"
                : event.getOption("reason").getAsString();

        event.deferReply(true).queue(hook ->
                event.getGuild().ban(user, 0, java.util.concurrent.TimeUnit.SECONDS)
                        .reason(reason)
                        .queue(
                                ignored -> hook.sendMessage("Banned " + user.getAsMention() + ".").queue(),
                                error -> {
                                    error.printStackTrace();
                                    hook.sendMessage("I couldn't ban that user. Check my permissions and role hierarchy.")
                                            .queue();
                                }
                        )
        );
    }
}
