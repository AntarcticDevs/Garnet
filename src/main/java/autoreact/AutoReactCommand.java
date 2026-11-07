package autoreact;

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
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import utils.Log;
import utils.MessageContent;
import utils.ReactionEmojiParser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** /autoreact: the bot reacts with emoji to messages that match a rule. */
public class AutoReactCommand extends ListenerAdapter implements Command {
    private static final Pattern LINK = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final int MAX_EMOJIS_PER_RULE = 5;
    private static final int MAX_REACTIONS_PER_MESSAGE = 20;

    /** Rules per server, so ordinary messages don't each cost a database lookup. */
    private final Map<String, List<AutoReactStore.Rule>> cache = new ConcurrentHashMap<>();

    @Override
    public CommandData getCommandData() {
        return Commands.slash("autoreact", "Make the bot react to messages automatically")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("add", "Add a rule")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "emojis",
                                                "Up to 5 emoji separated by spaces, such as 👍 👎", true)
                                                .setMaxLength(300),
                                        new OptionData(OptionType.CHANNEL, "channel",
                                                "Only this channel (leave empty for every channel)", false)
                                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                                        new OptionData(OptionType.STRING, "when", "Which messages (default: every message)",
                                                false)
                                                .addChoice("Every message", "ALL")
                                                .addChoice("Messages with an image", "ATTACHMENTS")
                                                .addChoice("Messages with a link", "LINKS")
                                                .addChoice("Messages containing a word", "KEYWORD"),
                                        new OptionData(OptionType.STRING, "keyword",
                                                "The word or phrase, when you choose 'containing a word'", false)
                                                .setMaxLength(50),
                                        new OptionData(OptionType.BOOLEAN, "include-bots",
                                                "Also react to messages from bots (default: no)", false)),
                        new SubcommandData("list", "Show the rules of this server"),
                        new SubcommandData("remove", "Delete a rule")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "The rule's number from /autoreact list",
                                        true).setMinValue(1)));
    }

    // ---------------------------------------------------------------- slash command

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("autoreact")) {
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null || event.getMember() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission to manage auto-reactions.").setEphemeral(true).queue();
            return;
        }
        try {
            switch (String.valueOf(event.getSubcommandName())) {
                case "add" -> add(event, guild);
                case "list" -> list(event, guild);
                case "remove" -> {
                    boolean removed = AutoReactStore.remove(guild.getId(), event.getOption("id").getAsInt());
                    cache.remove(guild.getId());
                    event.reply(removed ? "🗑️ Rule removed." : "There is no rule with that number. See `/autoreact list`.")
                            .setEphemeral(true).queue();
                }
                default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("AutoReactCommand failed", exception);
            event.reply("Something went wrong with the database. Please try again.").setEphemeral(true).queue();
        }
    }

    private void add(SlashCommandInteractionEvent event, Guild guild) throws SQLException {
        if (AutoReactStore.count(guild.getId()) >= AutoReactStore.MAX_RULES) {
            event.reply("A server can have at most " + AutoReactStore.MAX_RULES + " rules. Remove one first.")
                    .setEphemeral(true).queue();
            return;
        }
        List<String> emojis = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String token : event.getOption("emojis").getAsString().trim().split("\\s+")) {
            var parsed = ReactionEmojiParser.parse(token);
            if (parsed.isPresent()) {
                String formatted = parsed.get().getFormatted();
                if (!emojis.contains(formatted)) {
                    emojis.add(formatted);
                }
            } else if (!token.isEmpty()) {
                invalid.add(token);
            }
        }
        if (!invalid.isEmpty() || emojis.isEmpty() || emojis.size() > MAX_EMOJIS_PER_RULE) {
            event.reply(!invalid.isEmpty()
                    ? "I couldn't read these as emoji: " + String.join(" ", invalid)
                            + ". Use normal emoji or server emoji like `<:name:id>`."
                    : "Give between 1 and " + MAX_EMOJIS_PER_RULE + " emoji.").setEphemeral(true).queue();
            return;
        }

        AutoReactStore.Rule rule = new AutoReactStore.Rule();
        rule.guildId = guild.getId();
        rule.emojis = emojis;
        OptionMapping when = event.getOption("when");
        rule.kind = when == null ? AutoReactStore.Kind.ALL : AutoReactStore.Kind.valueOf(when.getAsString());
        OptionMapping keyword = event.getOption("keyword");
        if (rule.kind == AutoReactStore.Kind.KEYWORD) {
            if (keyword == null || keyword.getAsString().isBlank()) {
                event.reply("Add a `keyword` for the rule to look for.").setEphemeral(true).queue();
                return;
            }
            rule.keyword = keyword.getAsString().trim().toLowerCase(Locale.ROOT);
        }
        OptionMapping channel = event.getOption("channel");
        if (channel != null) {
            if (!(channel.getAsChannel() instanceof GuildMessageChannel messageChannel)) {
                event.reply("Choose a text channel.").setEphemeral(true).queue();
                return;
            }
            rule.channelId = messageChannel.getId();
        }
        rule.includeBots = event.getOption("include-bots") != null && event.getOption("include-bots").getAsBoolean();

        int id = AutoReactStore.add(rule);
        cache.remove(guild.getId());
        String warning = rule.kind == AutoReactStore.Kind.ALL ? "" : MessageContent.warning();
        event.reply("✅ Rule **#" + id + "** added: " + describe(rule) + warning).setEphemeral(true).queue();
    }

    private void list(SlashCommandInteractionEvent event, Guild guild) throws SQLException {
        List<AutoReactStore.Rule> rules = AutoReactStore.list(guild.getId());
        if (rules.isEmpty()) {
            event.reply("There are no auto-reaction rules yet. Add one with `/autoreact add`.")
                    .setEphemeral(true).queue();
            return;
        }
        StringBuilder text = new StringBuilder("**Auto-reactions**\n");
        for (AutoReactStore.Rule rule : rules) {
            text.append("`#").append(rule.id).append("` ").append(describe(rule)).append('\n');
        }
        event.reply(text.toString()).setEphemeral(true).queue();
    }

    private static String describe(AutoReactStore.Rule rule) {
        return String.join(" ", rule.emojis) + " on " + rule.kind.label
                + (rule.kind == AutoReactStore.Kind.KEYWORD ? " `" + rule.keyword + "`" : "")
                + (rule.channelId == null ? " in every channel" : " in <#" + rule.channelId + ">")
                + (rule.includeBots ? " (bots included)" : "");
    }

    // ---------------------------------------------------------------- reacting

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.isWebhookMessage()
                || event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())
                || !(event.getChannel() instanceof GuildMessageChannel channel)) {
            return;
        }
        Guild guild = event.getGuild();
        List<AutoReactStore.Rule> rules;
        try {
            rules = cache.get(guild.getId());
            if (rules == null) {
                rules = AutoReactStore.list(guild.getId());
                cache.put(guild.getId(), rules);
            }
        } catch (SQLException exception) {
            Log.error("AutoReactCommand.onMessageReceived failed", exception);
            return;
        }
        if (rules.isEmpty()) {
            return;
        }

        Message message = event.getMessage();
        Map<String, Emoji> toAdd = new LinkedHashMap<>();
        for (AutoReactStore.Rule rule : rules) {
            if ((rule.channelId != null && !rule.channelId.equals(channel.getId()))
                    || (event.getAuthor().isBot() && !rule.includeBots) || !matches(rule, message)) {
                continue;
            }
            for (String text : rule.emojis) {
                ReactionEmojiParser.parse(text).ifPresent(emoji -> toAdd.putIfAbsent(emoji.getAsReactionCode(), emoji));
            }
        }
        if (toAdd.isEmpty() || !guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL,
                Permission.MESSAGE_HISTORY, Permission.MESSAGE_ADD_REACTION)) {
            return;
        }
        react(message, new ArrayList<>(toAdd.values()), 0);
    }

    private static boolean matches(AutoReactStore.Rule rule, Message message) {
        return switch (rule.kind) {
            case ALL -> true;
            case ATTACHMENTS -> message.getAttachments().stream().anyMatch(Message.Attachment::isImage);
            case LINKS -> LINK.matcher(message.getContentRaw()).find();
            case KEYWORD -> rule.keyword != null
                    && message.getContentRaw().toLowerCase(Locale.ROOT).contains(rule.keyword);
        };
    }

    /** One reaction after the other, in the order of the rule; a failed one doesn't stop the rest. */
    private static void react(Message message, List<Emoji> emojis, int index) {
        if (index >= emojis.size() || index >= MAX_REACTIONS_PER_MESSAGE) {
            return;
        }
        message.addReaction(emojis.get(index)).queue(
                ignored -> react(message, emojis, index + 1),
                error -> react(message, emojis, index + 1));
    }
}
