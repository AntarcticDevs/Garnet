package tags;

import commands.Command;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import utils.EmbedManager;
import utils.Log;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** /tag: custom commands that answer with a message or a saved embed, also usable as !name. */
public class TagCommand extends ListenerAdapter implements Command {
    private static final Pattern NAME = Pattern.compile("^[a-z0-9_-]{1,32}$");
    private static final String MODAL = "tag:set:";
    private static final long COOLDOWN_MS = 3000;
    private static final String DEFAULT_PREFIX = "!";

    private static volatile boolean prefixTriggersAvailable;

    /** Set by Main: true when the bot connected with the Message Content Intent. */
    public static void setPrefixTriggersAvailable(boolean available) {
        prefixTriggersAvailable = available;
    }

    /** Prefix per server, so ordinary messages don't each cost a database lookup. "" = off. */
    private final Map<String, String> prefixes = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUse = new ConcurrentHashMap<>();

    @Override
    public CommandData getCommandData() {
        OptionData name = new OptionData(OptionType.STRING, "name", "The tag's name", true)
                .setMaxLength(32).setAutoComplete(true);
        return Commands.slash("tag", "Custom commands that reply with a message or embed")
                .addSubcommands(
                        new SubcommandData("use", "Show a tag").addOptions(new OptionData(OptionType.STRING, "name",
                                "The tag to show", true).setMaxLength(32).setAutoComplete(true)),
                        new SubcommandData("set", "Create a tag or change an existing one")
                                .addOptions(name,
                                        new OptionData(OptionType.STRING, "embed",
                                                "A saved embed to send with it (from /embed create)", false)
                                                .setAutoComplete(true),
                                        new OptionData(OptionType.ROLE, "required-role",
                                                "Only members with this role can use the tag", false),
                                        new OptionData(OptionType.BOOLEAN, "no-embed",
                                                "Remove the embed from this tag", false),
                                        new OptionData(OptionType.BOOLEAN, "no-role",
                                                "Remove the required role from this tag", false)),
                        new SubcommandData("delete", "Delete a tag").addOptions(new OptionData(OptionType.STRING,
                                "name", "The tag to delete", true).setMaxLength(32).setAutoComplete(true)),
                        new SubcommandData("list", "Show every tag in this server"),
                        new SubcommandData("prefix", "Show or change the prefix that triggers tags (default !)")
                                .addOption(OptionType.STRING, "prefix",
                                        "1 to 3 characters, for example ? or $. Type off to turn prefix triggers off", false));
    }

    // ---------------------------------------------------------------- slash commands

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("tag")) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        String sub = String.valueOf(event.getSubcommandName());
        try {
            switch (sub) {
                case "use" -> use(event, guild, member);
                case "list" -> list(event, guild);
                default -> {
                    if (!member.hasPermission(Permission.MANAGE_SERVER)) {
                        event.reply("You need the Manage Server permission to manage tags.")
                                .setEphemeral(true).queue();
                        return;
                    }
                    switch (sub) {
                        case "set" -> set(event, guild);
                        case "delete" -> delete(event, guild);
                        case "prefix" -> prefix(event, guild);
                        default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
                    }
                }
            }
        } catch (SQLException exception) {
            Log.error("TagCommand failed", exception);
            event.reply("Something went wrong with the database. Please try again.").setEphemeral(true).queue();
        }
    }

    private void use(SlashCommandInteractionEvent event, Guild guild, Member member) throws SQLException {
        TagStore.Tag tag = TagStore.get(guild.getId(), event.getOption("name").getAsString().toLowerCase(Locale.ROOT));
        if (tag == null) {
            event.reply("There is no tag with that name. See `/tag list`.").setEphemeral(true).queue();
            return;
        }
        if (!mayUse(member, tag)) {
            event.reply("You need a specific role to use this tag.").setEphemeral(true).queue();
            return;
        }
        MessageCreateData message = render(guild, member, event.getChannel() instanceof GuildMessageChannel channel
                ? channel : null, tag);
        TagStore.countUse(tag.id);
        event.reply(message).queue();
    }

    private void set(SlashCommandInteractionEvent event, Guild guild) throws SQLException {
        String name = event.getOption("name").getAsString().trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            event.reply("Tag names can use lowercase letters, numbers, `-` and `_` (up to 32 characters).")
                    .setEphemeral(true).queue();
            return;
        }
        TagStore.Tag existing = TagStore.get(guild.getId(), name);
        if (existing == null && TagStore.count(guild.getId()) >= TagStore.MAX_TAGS) {
            event.reply("A server can have at most " + TagStore.MAX_TAGS + " tags.").setEphemeral(true).queue();
            return;
        }

        Long embedId = existing == null ? null : existing.embedId;
        OptionMapping embedOption = event.getOption("embed");
        if (embedOption != null) {
            embedId = TagStore.embedIdByName(guild.getId(), embedOption.getAsString());
            if (embedId == null) {
                event.reply("I couldn't find a saved embed called **" + embedOption.getAsString()
                        + "**. Create it first with `/embed create`.").setEphemeral(true).queue();
                return;
            }
        } else if (event.getOption("no-embed") != null && event.getOption("no-embed").getAsBoolean()) {
            embedId = null;
        }

        String roleId = existing == null ? null : existing.requiredRoleId;
        OptionMapping roleOption = event.getOption("required-role");
        if (roleOption != null) {
            Role role = roleOption.getAsRole();
            roleId = role.isPublicRole() ? null : role.getId();
        } else if (event.getOption("no-role") != null && event.getOption("no-role").getAsBoolean()) {
            roleId = null;
        }

        TextInput.Builder text = TextInput.create("content", TextInputStyle.PARAGRAPH)
                .setRequired(embedId == null).setMaxLength(2000)
                .setPlaceholder(embedId == null ? "What the tag says" : "Optional text shown above the embed");
        if (existing != null && existing.content != null && !existing.content.isBlank()) {
            text.setValue(existing.content);
        }
        event.replyModal(Modal.create(MODAL + name + ":" + (embedId == null ? "-" : embedId) + ":"
                        + (roleId == null ? "-" : roleId), (existing == null ? "New tag: " : "Edit tag: ") + name)
                .addComponents(Label.of("Text (you can use {user}, {user_mention}, {server}, {member_count})",
                        text.build())).build()).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(MODAL)) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission to manage tags.").setEphemeral(true).queue();
            return;
        }
        String[] parts = event.getModalId().substring(MODAL.length()).split(":");
        if (parts.length != 3) {
            event.reply("That form is no longer valid. Run `/tag set` again.").setEphemeral(true).queue();
            return;
        }
        try {
            TagStore.Tag tag = new TagStore.Tag();
            tag.guildId = guild.getId();
            tag.name = parts[0];
            tag.embedId = parts[1].equals("-") ? null : Long.parseLong(parts[1]);
            tag.requiredRoleId = parts[2].equals("-") ? null : parts[2];
            String content = event.getValue("content") == null ? "" : event.getValue("content").getAsString().trim();
            tag.content = content.isEmpty() ? null : content;
            if (tag.content == null && tag.embedId == null) {
                event.reply("A tag needs some text, an embed, or both.").setEphemeral(true).queue();
                return;
            }
            boolean existed = TagStore.get(guild.getId(), tag.name) != null;
            if (!existed && TagStore.count(guild.getId()) >= TagStore.MAX_TAGS) {
                event.reply("A server can have at most " + TagStore.MAX_TAGS + " tags.").setEphemeral(true).queue();
                return;
            }
            TagStore.save(tag, member.getId());
            event.reply("✅ Tag **" + tag.name + "** " + (existed ? "updated" : "created") + ". Use it with `/tag use "
                    + tag.name + "`" + prefixHint(guild) + ".").setEphemeral(true).queue();
        } catch (NumberFormatException | SQLException exception) {
            Log.error("TagCommand.onModalInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    private void delete(SlashCommandInteractionEvent event, Guild guild) throws SQLException {
        String name = event.getOption("name").getAsString().toLowerCase(Locale.ROOT);
        event.reply(TagStore.delete(guild.getId(), name)
                ? "🗑️ Deleted the tag **" + name + "**."
                : "There is no tag with that name.").setEphemeral(true).queue();
    }

    private void list(SlashCommandInteractionEvent event, Guild guild) throws SQLException {
        List<TagStore.Tag> tags = TagStore.list(guild.getId());
        if (tags.isEmpty()) {
            event.reply("There are no tags yet. Admins can create one with `/tag set`.").setEphemeral(true).queue();
            return;
        }
        StringBuilder text = new StringBuilder("**Tags** (" + tags.size() + ")\n");
        for (TagStore.Tag tag : tags) {
            String line = "`" + tag.name + "`" + (tag.embedId != null ? " 📎" : "")
                    + (tag.requiredRoleId != null ? " 🔒" : "") + " · " + tag.uses
                    + (tag.uses == 1 ? " use" : " uses") + "\n";
            if (text.length() + line.length() > 1850) {
                text.append("…and more");
                break;
            }
            text.append(line);
        }
        text.append("\n-# 📎 has an embed · 🔒 needs a role").append(prefixHint(guild));
        event.reply(text.toString()).setEphemeral(true).queue();
    }

    private void prefix(SlashCommandInteractionEvent event, Guild guild) throws SQLException {
        OptionMapping option = event.getOption("prefix");
        if (option == null) {
            String current = prefixOf(guild.getId());
            event.reply((current.isEmpty()
                    ? "Prefix triggers are **off** in this server. Turn them on with `/tag prefix !`."
                    : "Members can type `" + current + "tagname` or use `/tag use tagname`.")
                    + availabilityNote()).setEphemeral(true).queue();
            return;
        }
        String value = option.getAsString().trim();
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("none")) {
            TagStore.setPrefix(guild.getId(), "");
            prefixes.put(guild.getId(), "");
            event.reply("Prefix triggers are off for this server. Tags still work with `/tag use`.")
                    .setEphemeral(true).queue();
            return;
        }
        if (value.isEmpty() || value.length() > 3 || value.chars().anyMatch(Character::isWhitespace)
                || value.startsWith("/")) {
            event.reply("The prefix must be 1 to 3 characters, with no spaces, and can't start with `/`. "
                    + "Type `off` to turn it off.").setEphemeral(true).queue();
            return;
        }
        TagStore.setPrefix(guild.getId(), value);
        prefixes.put(guild.getId(), value);
        event.reply("✅ Members can now type `" + value + "tagname`." + availabilityNote())
                .setEphemeral(true).queue();
    }

    private static String availabilityNote() {
        return prefixTriggersAvailable ? "" : "\n⚠️ This bot isn't allowed to read message text, so only `/tag use` "
                + "works right now. Whoever runs the bot has to turn on **Message Content Intent** under "
                + "Privileged Gateway Intents on the Bot tab of the application, then restart it.";
    }

    /** The server's prefix: its own choice, "" when turned off, or the default ! when never set. */
    private String prefixOf(String guildId) throws SQLException {
        String prefix = prefixes.get(guildId);
        if (prefix == null) {
            String stored = TagStore.getPrefix(guildId);
            prefix = stored == null ? DEFAULT_PREFIX : stored;
            prefixes.put(guildId, prefix);
        }
        return prefix;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("tag") || event.getGuild() == null) {
            return;
        }
        try {
            String option = event.getFocusedOption().getName();
            String typed = event.getFocusedOption().getValue();
            List<String> names = option.equals("embed")
                    ? TagStore.embedNames(event.getGuild().getId(), typed, 25)
                    : TagStore.names(event.getGuild().getId(), typed, 25);
            event.replyChoiceStrings(names).queue();
        } catch (SQLException exception) {
            Log.error("TagCommand autocomplete failed", exception);
        }
    }

    // ---------------------------------------------------------------- prefix trigger (!name)

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()
                || event.getMember() == null) {
            return;
        }
        String content = event.getMessage().getContentRaw();
        if (content.isEmpty()) {
            return;
        }
        Guild guild = event.getGuild();
        try {
            String prefix = prefixOf(guild.getId());
            if (prefix.isEmpty() || !content.startsWith(prefix) || content.length() == prefix.length()) {
                return;
            }
            String name = content.substring(prefix.length()).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            if (!NAME.matcher(name).matches()) {
                return;
            }
            TagStore.Tag tag = TagStore.get(guild.getId(), name);
            if (tag == null || !mayUse(event.getMember(), tag)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = lastUse.put(guild.getId() + ":" + event.getAuthor().getId() + ":" + name, now);
            if (last != null && now - last < COOLDOWN_MS) {
                return;
            }
            if (lastUse.size() > 5000) {
                lastUse.values().removeIf(time -> now - time > COOLDOWN_MS);
            }
            if (!(event.getChannel() instanceof GuildMessageChannel channel)
                    || !guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL,
                            Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                    || (tag.embedId != null
                            && !guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS))) {
                return;
            }
            MessageCreateData message = render(guild, event.getMember(), channel, tag);
            TagStore.countUse(tag.id);
            event.getMessage().reply(message).mentionRepliedUser(false).queue(null, error -> { });
        } catch (SQLException exception) {
            Log.error("TagCommand.onMessageReceived failed", exception);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static boolean mayUse(Member member, TagStore.Tag tag) {
        if (tag.requiredRoleId == null || member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        return member.getRoles().stream().anyMatch(role -> role.getId().equals(tag.requiredRoleId));
    }

    private MessageCreateData render(Guild guild, Member member, GuildMessageChannel channel, TagStore.Tag tag)
            throws SQLException {
        MessageCreateBuilder builder = new MessageCreateBuilder()
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class));
        if (tag.content != null && !tag.content.isBlank()) {
            String text = tag.content
                    .replace("{user_mention}", member.getAsMention())
                    .replace("{username}", member.getUser().getName())
                    .replace("{user}", member.getEffectiveName())
                    .replace("{server}", guild.getName())
                    .replace("{member_count}", String.valueOf(guild.getMemberCount()));
            builder.setContent(text.length() > 2000 ? text.substring(0, 1999) + "…" : text);
        }
        if (tag.embedId != null) {
            var embed = EmbedManager.loadEmbedById(guild, member, tag.embedId);
            if (embed != null && !embed.isEmpty()) {
                MessageEmbed built = embed.build();
                builder.setEmbeds(built);
            }
        }
        if (builder.isEmpty()) {
            builder.setContent("*This tag has no content any more. Its embed was deleted.*");
        }
        return builder.build();
    }

    private String prefixHint(Guild guild) {
        if (!prefixTriggersAvailable) {
            return "";
        }
        try {
            String prefix = prefixOf(guild.getId());
            return prefix.isEmpty() ? "" : " or `" + prefix + "name`";
        } catch (SQLException exception) {
            return "";
        }
    }
}
