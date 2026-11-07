package counting;

import commands.Command;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.RestAction;
import utils.Log;
import utils.MessageContent;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** /counting: a channel where members count upward together, one number per message. */
public class CountingCommand extends ListenerAdapter implements Command {
    private static final Pattern NUMBER = Pattern.compile("^\\d{1,9}$");
    private static final int MILESTONE = 100;
    private static final Object NO_GAME = new Object();

    /** One thread handles every count, so numbers are judged in order and the database stays off the gateway thread. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "garnet-counting");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    public CommandData getCommandData() {
        return Commands.slash("counting", "A game where everyone counts upward together")
                .addSubcommands(
                        new SubcommandData("setup", "Choose the counting channel (Manage Server)")
                                .addOptions(
                                        new OptionData(OptionType.CHANNEL, "channel", "Where people count", true)
                                                .setChannelTypes(ChannelType.TEXT),
                                        new OptionData(OptionType.BOOLEAN, "reset-on-mistake",
                                                "Start again from 1 after a mistake (default: no)", false),
                                        new OptionData(OptionType.BOOLEAN, "allow-double",
                                                "Let the same person count twice in a row (default: no)", false)),
                        new SubcommandData("status", "Show the current number and high score"),
                        new SubcommandData("leaderboard", "Who has counted the most"),
                        new SubcommandData("reset", "Start counting again from 1 (Manage Server)"),
                        new SubcommandData("set-number", "Change the current number (Manage Server)")
                                .addOptions(new OptionData(OptionType.INTEGER, "number", "The last number counted", true)
                                        .setMinValue(0).setMaxValue(999_999_999)),
                        new SubcommandData("disable", "Turn counting off in this server (Manage Server)"));
    }

    // ---------------------------------------------------------------- slash command

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("counting")) {
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null || event.getMember() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        String sub = String.valueOf(event.getSubcommandName());
        boolean admin = !sub.equals("status") && !sub.equals("leaderboard");
        if (admin && !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission for that.").setEphemeral(true).queue();
            return;
        }
        try {
            CountingStore.Settings settings = CountingStore.get(guild.getId());
            if (settings == null && !sub.equals("setup")) {
                event.reply("Counting isn't set up here. An admin can start it with `/counting setup`.")
                        .setEphemeral(true).queue();
                return;
            }
            switch (sub) {
                case "setup" -> {
                    if (!(event.getOption("channel").getAsChannel() instanceof GuildMessageChannel channel)) {
                        event.reply("Choose a text channel.").setEphemeral(true).queue();
                        return;
                    }
                    boolean reset = event.getOption("reset-on-mistake") != null
                            && event.getOption("reset-on-mistake").getAsBoolean();
                    boolean allowDouble = event.getOption("allow-double") != null
                            && event.getOption("allow-double").getAsBoolean();
                    CountingStore.configure(guild.getId(), channel.getId(), reset, allowDouble);
                    cache.remove(guild.getId());
                    String missing = guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL,
                            Permission.MESSAGE_HISTORY, Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_SEND)
                            ? "" : "\n⚠️ I need View Channel, Send Messages, Add Reactions and Read Message History in "
                                    + channel.getAsMention() + ".";
                    event.reply("✅ Counting is on in " + channel.getAsMention() + ". "
                            + (reset ? "A mistake starts again from 1." : "Mistakes are deleted and the count goes on.")
                            + (allowDouble ? "" : " Nobody can count twice in a row.")
                            + missing + MessageContent.warning()).setEphemeral(true).queue();
                }
                case "status" -> event.reply("🔢 Current number: **" + settings.current + "**\nHigh score: **"
                        + settings.high + "**\nChannel: <#" + settings.channelId + ">")
                        .setEphemeral(true).queue();
                case "leaderboard" -> leaderboard(event, guild);
                case "reset" -> {
                    CountingStore.saveProgress(guild.getId(), 0, settings.high, null);
                    cache.remove(guild.getId());
                    event.reply("🔄 Counting was reset. The next number is **1**.").queue();
                }
                case "set-number" -> {
                    int number = event.getOption("number").getAsInt();
                    CountingStore.saveProgress(guild.getId(), number, Math.max(settings.high, number), null);
                    cache.remove(guild.getId());
                    event.reply("✅ The current number is now **" + number + "**. The next one is **"
                            + (number + 1) + "**.").queue();
                }
                case "disable" -> {
                    CountingStore.disable(guild.getId());
                    cache.put(guild.getId(), NO_GAME);
                    event.reply("Counting is turned off. Run `/counting setup` to start again.")
                            .setEphemeral(true).queue();
                }
                default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("CountingCommand failed", exception);
            event.reply("Something went wrong with the database. Please try again.").setEphemeral(true).queue();
        }
    }

    private void leaderboard(SlashCommandInteractionEvent event, Guild guild) throws SQLException {
        List<CountingStore.Entry> top = CountingStore.top(guild.getId(), 10);
        if (top.isEmpty()) {
            event.reply("Nobody has counted yet.").setEphemeral(true).queue();
            return;
        }
        StringBuilder text = new StringBuilder("🏆 **Counting leaderboard**\n");
        int place = 1;
        for (CountingStore.Entry entry : top) {
            text.append(place++).append(". <@").append(entry.userId()).append("> — **").append(entry.correct())
                    .append("** correct, ").append(entry.ruined()).append(" ruined\n");
        }
        event.reply(text.toString()).setAllowedMentions(EnumSet.noneOf(Message.MentionType.class)).queue();
    }

    // ---------------------------------------------------------------- counting

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        String content = event.getMessage().getContentRaw().trim();
        if (!NUMBER.matcher(content).matches()) {
            return;
        }
        String guildId = event.getGuild().getId();
        String channelId = event.getChannel().getId();
        int number = Integer.parseInt(content);
        worker.execute(() -> {
            try {
                CountingStore.Settings settings = settingsFor(guildId);
                if (settings == null || !settings.channelId.equals(channelId)) {
                    return;
                }
                judge(event, settings, number);
            } catch (Exception exception) {
                Log.error("Counting failed", exception);
            }
        });
    }

    private CountingStore.Settings settingsFor(String guildId) throws SQLException {
        Object cached = cache.get(guildId);
        if (cached == null) {
            CountingStore.Settings loaded = CountingStore.get(guildId);
            cache.put(guildId, loaded == null ? NO_GAME : loaded);
            return loaded;
        }
        return cached == NO_GAME ? null : (CountingStore.Settings) cached;
    }

    private void judge(MessageReceivedEvent event, CountingStore.Settings settings, int number) throws SQLException {
        String userId = event.getAuthor().getId();
        Message message = event.getMessage();
        boolean sameUser = !settings.allowDouble && userId.equals(settings.lastUserId) && settings.current > 0;
        boolean right = number == settings.current + 1 && !sameUser;

        if (right) {
            settings.current = number;
            settings.high = Math.max(settings.high, number);
            settings.lastUserId = userId;
            CountingStore.saveProgress(settings.guildId, settings.current, settings.high, userId);
            CountingStore.addResult(settings.guildId, userId, true);
            react(message, number == settings.high && number > 1 && number % MILESTONE == 0 ? "🎉" : "✅");
            if (number % MILESTONE == 0) {
                event.getChannel().sendMessage("🎉 **" + number + "!** Keep it going.").queue(null, ignored -> {
                });
            }
            return;
        }

        if (settings.current == 0) {
            react(message, "❌");
            return;
        }
        CountingStore.addResult(settings.guildId, userId, false);
        String reason = sameUser ? "counted twice in a row" : "got the number wrong";
        if (settings.resetOnMistake) {
            int ruinedAt = settings.current;
            settings.current = 0;
            settings.lastUserId = null;
            CountingStore.saveProgress(settings.guildId, 0, settings.high, null);
            react(message, "❌");
            event.getChannel().sendMessage("<@" + userId + "> " + reason + " at **" + ruinedAt
                            + "**! The next number is **1**.")
                    .setAllowedMentions(EnumSet.of(Message.MentionType.USER)).queue(null, ignored -> {
                    });
        } else {
            message.delete().queue(null, ignored -> {
            });
            event.getChannel().sendMessage("<@" + userId + "> " + reason + ". The next number is **"
                            + (settings.current + 1) + "**.")
                    .setAllowedMentions(EnumSet.of(Message.MentionType.USER))
                    .queue(notice -> notice.delete().queueAfter(5, TimeUnit.SECONDS, null, ignored -> {
                    }), ignored -> {
                    });
        }
    }

    private static void react(Message message, String emoji) {
        RestAction<Void> action = message.addReaction(Emoji.fromUnicode(emoji));
        action.queue(null, ignored -> {
        });
    }
}
