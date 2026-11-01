package logging;

import commands.Command;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import utils.Log;

import java.sql.SQLException;

/** /logs: choose the log channel and which kinds of events are posted there. */
public class LogCommand extends ListenerAdapter implements Command {

    @Override
    public CommandData getCommandData() {
        OptionData category = new OptionData(OptionType.STRING, "category", "Which kind of events", true);
        for (LogCategory entry : LogCategory.values()) {
            category.addChoice(entry.key() + " - " + entry.description(), entry.key());
        }
        return Commands.slash("logs", "Set up the server log channel")
                .addSubcommands(
                        new SubcommandData("channel", "Set the log channel (leave empty to turn logging off)")
                                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Where to post logs", false)
                                        .setChannelTypes(ChannelType.TEXT)),
                        new SubcommandData("toggle", "Turn one kind of event on or off")
                                .addOptions(category,
                                        new OptionData(OptionType.BOOLEAN, "enabled", "On or off", true)),
                        new SubcommandData("status", "Show the current log settings"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("logs")) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission to change log settings.")
                    .setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        try {
            switch (String.valueOf(event.getSubcommandName())) {
                case "channel" -> {
                    OptionMapping option = event.getOption("channel");
                    if (option == null) {
                        LogService.setChannel(guildId, null);
                        event.reply("Logging is off. Pick a channel with `/logs channel` to turn it back on.")
                                .setEphemeral(true).queue();
                        return;
                    }
                    if (!(option.getAsChannel() instanceof TextChannel channel)) {
                        event.reply("Please choose a text channel.").setEphemeral(true).queue();
                        return;
                    }
                    LogService.setChannel(guildId, channel.getId());
                    String warning = event.getGuild().getSelfMember().hasPermission(channel,
                            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)
                            ? ""
                            : "\n⚠️ I can't post there yet: I need View Channel, Send Messages and Embed Links.";
                    event.reply("Logs will be posted in " + channel.getAsMention() + "." + warning)
                            .setEphemeral(true).queue();
                }
                case "toggle" -> {
                    LogCategory category = LogCategory.fromKey(event.getOption("category").getAsString());
                    boolean enabled = event.getOption("enabled").getAsBoolean();
                    if (category == null) {
                        event.reply("Unknown category.").setEphemeral(true).queue();
                        return;
                    }
                    LogService.setEnabled(guildId, category, enabled);
                    event.reply("**" + category.key() + "** logs are now " + (enabled ? "on" : "off") + ".")
                            .setEphemeral(true).queue();
                }
                case "status" -> {
                    LogService.Settings settings = LogService.settings(guildId);
                    StringBuilder text = new StringBuilder();
                    text.append("**Channel:** ")
                            .append(settings.channelId() == null ? "not set (logging is off)"
                                    : "<#" + settings.channelId() + ">")
                            .append("\n\n");
                    for (LogCategory category : LogCategory.values()) {
                        text.append(settings.enabled(category) ? "✅ " : "⬜ ")
                                .append("**").append(category.key()).append("** - ")
                                .append(category.description()).append('\n');
                    }
                    event.replyEmbeds(new EmbedBuilder().setTitle("Log settings")
                                    .setDescription(text.toString()).setColor(LogService.BLUE).build())
                            .setEphemeral(true).queue();
                }
                default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("LogCommand.onSlashCommandInteraction failed", exception);
            event.reply("Couldn't save the log settings. Please try again.").setEphemeral(true).queue();
        }
    }
}
