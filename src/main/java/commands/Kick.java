package commands;

import utils.Log;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class Kick extends ListenerAdapter implements Command {
    @Override
    public CommandData getCommandData() {
        return Commands.slash("kick", "Kick a member from this server")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "The member to kick", true),
                        new OptionData(OptionType.STRING, "reason", "Reason for the kick", false)
                                .setMaxLength(512)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"kick".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need the Kick Members permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!event.getGuild().getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("I need the Kick Members permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply("Please choose a member of this server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (target.equals(event.getMember()) || target.equals(event.getGuild().getSelfMember())
                || !event.getMember().canInteract(target)
                || !event.getGuild().getSelfMember().canInteract(target)) {
            event.reply("I can't kick that member because they are you, the bot, or have a higher role.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String reason = event.getOption("reason") == null
                ? "No reason provided"
                : event.getOption("reason").getAsString();

        event.deferReply(true).queue(hook ->
                target.kick().reason(reason).queue(
                        ignored -> hook.sendMessage("Kicked " + target.getUser().getAsMention() + ".").queue(),
                        error -> {
                            Log.error("Kick.onSlashCommandInteraction failed", error);
                            hook.sendMessage("I couldn't kick that member. Check my permissions and role hierarchy.")
                                    .queue();
                        }
                )
        );
    }
}
