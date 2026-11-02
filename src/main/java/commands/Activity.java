package commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
                                                .addChoice("Custom Status", "custom"),
                                        new OptionData(OptionType.STRING, "text", "Activity text", true)),
                        new SubcommandData("reset", "Remove Garnet's activity"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("activity")) {
            return;
        }

        if ("set".equals(event.getSubcommandName())) {
            String type = event.getOption("type").getAsString();
            String text = event.getOption("text").getAsString();

            net.dv8tion.jda.api.entities.Activity activity;

            switch (type) {
                case "playing":
                    activity = net.dv8tion.jda.api.entities.Activity.playing(text);
                    break;
                case "watching":
                    activity = net.dv8tion.jda.api.entities.Activity.watching(text);
                    break;
                case "listening":
                    activity = net.dv8tion.jda.api.entities.Activity.listening(text);
                    break;
                case "competing":
                    activity = net.dv8tion.jda.api.entities.Activity.competing(text);
                    break;
                case "custom":
                    activity = net.dv8tion.jda.api.entities.Activity.customStatus(text);
                    break;
                default:
                    event.reply("Invalid activity type.").setEphemeral(true).queue();
                    return;
            }

            event.getJDA().getPresence().setActivity(activity);
            event.reply("Activity updated successfully!").setEphemeral(true).queue();
            return;
        }

        if ("reset".equals(event.getSubcommandName())) {
            event.getJDA().getPresence().setActivity(null);
            event.reply("Activity reset successfully!").setEphemeral(true).queue();
        }
    }
}