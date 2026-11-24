package commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public class Activity extends ListenerAdapter implements Command {

    @Override
    public CommandData getCommandData() {
        return Commands.slash("activity", "Manage the bot's activity")
                .addSubcommands(
                        new SubcommandData("set", "Set the bot's activity")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "type", "Activity type", true)
                                                .addChoice("Playing", "playing")
                                                .addChoice("Watching", "watching")
                                                .addChoice("Listening", "listening")
                                                .addChoice("Competing", "competing")
                                                .addChoice("Custom Status", "custom")
                                                .addChoice("Streaming", "streaming"),
                                        new OptionData(OptionType.STRING, "text", "Activity text", true)
                                                .setMaxLength(128),
                                        new OptionData(OptionType.STRING, "url", "Twitch or YouTube URL for streaming",
                                                false)),
                        new SubcommandData("reset", "Remove the bot's activity"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"activity".equals(event.getName())) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need the Administrator permission to change the bot's activity.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("set".equals(event.getSubcommandName())) {
            String type = event.getOption("type").getAsString();
            String text = event.getOption("text").getAsString();
            String url = event.getOption("url") == null ? null : event.getOption("url").getAsString();
            String error = ActivityManager.get().set(
                    new ActivityManager.Spec(type, text, url),
                    event.getGuild(),
                    event.getUser().getId());

            event.reply(error == null ? "Activity updated successfully!" : error)
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("reset".equals(event.getSubcommandName())) {
            ActivityManager.get().reset();
            event.reply("Activity reset successfully!")
                    .setEphemeral(true)
                    .queue();
        }
    }
}
