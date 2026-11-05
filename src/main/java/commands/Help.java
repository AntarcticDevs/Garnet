package commands;

import config.ConfigManager;
import modules.BotModule;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Help extends ListenerAdapter implements Command {

    private static final String MENU_ID = "help:page";
    private static final String OVERVIEW = "overview";
    private static final Color ACCENT = new Color(0x5865F2);

    private record Page(BotModule module, String key, String emoji, String title, String summary, String body) {
    }

    private static final List<Page> PAGES = List.of(
            new Page(BotModule.INFORMATION, "information", "ℹ️", "Information",
                    "Latency, server, user and avatar info",
                    """
                    `/ping` Shows the bot's latency.
                    `/info` Shows information about this server.
                    `/userinfo [user]` Shows information about a member.
                    `/avatar [user]` Shows a member's avatar."""),
            new Page(BotModule.ACTIVITY, "activity", "🎮", "Activity",
                    "Set or reset the bot's presence",
                    """
                    `/activity set type text [url]` Sets the bot's activity. Streaming requires a Twitch or YouTube URL.
                    `/activity reset` Clears the bot's activity.
                    *Admins only. Presets, rotation, schedules and member-milestone rules can be configured in
                    `data/activity.json`.*"""),
            new Page(BotModule.MODERATION, "moderation", "🛡️", "Moderation",
                    "Ban, kick and clean up messages",
                    """
                    `/purge amount` Deletes up to 100 recent messages. *Needs Manage Messages.*
                    `/kick user [reason]` Kicks a member. *Needs Kick Members.*
                    `/ban user [reason]` Bans a user. *Needs Ban Members.*"""),
            new Page(BotModule.REACTION_ROLES, "reaction-roles", "🎭", "Reaction roles",
                    "Give members a role with a reaction or button",
                    """
                    `/reaction-roles add style role message-id [emoji] [label] [channel]` Adds a role to a message.
                    Emoji style gives the role on reaction and takes it away when the reaction is removed.
                    Button style toggles the role on each click and answers privately; buttons can only go on
                    messages the bot sent (for example with `/embed send`).
                    `/reaction-roles remove message-id [emoji]` Removes one emoji, or everything on the message.
                    `/reaction-roles list` Shows every reaction and button role.
                    *Needs Manage Server; the bot needs Manage Roles.*"""),
            new Page(BotModule.ROLE_PANELS, "role-panels", "🎛️", "Role panels",
                    "A posted message where members pick their own roles",
                    """
                    `/rolepanel create name` Creates a panel and opens its editor.
                    `/rolepanel edit name` Opens the editor: roles, look, style, mode, access.
                    `/rolepanel list` Shows every panel and where it is posted.
                    **Styles:** buttons, a dropdown menu, or reactions.
                    **Modes:** toggle, pick one, add only, remove only. Optional role limit, required role and
                    blocked role. Button and dropdown replies are private; reactions can't reply privately.
                    *Needs Manage Server; the bot needs Manage Roles and its role above the roles it hands out.*"""),
            new Page(BotModule.TICKETS, "tickets", "🎫", "Tickets",
                    "Panels, private ticket channels and transcripts",
                    """
                    **Set up** *(needs Manage Server)*
                    `/ticket panel create name` Creates a panel.
                    `/ticket panel edit name` Opens the panel editor: look, roles, categories, questions, limits.
                    `/ticket panel send name channel` Posts the panel.
                    `/ticket panel disable name [message]` and `enable name` Turns a panel off or on.
                    `/ticket panel staff-roles`, `ping-roles`, `transcript-channel` Quick shortcuts for routing.
                    `/ticket set-category category` Default Discord category for new tickets.

                    **Inside a ticket**
                    `/ticket close`, `reopen` The creator or staff.
                    `/ticket claim`, `unclaim`, `transfer member` Staff.
                    `/ticket transcript` A private transcript, staff only.
                    `/ticket delete` Asks to confirm, then deletes. Admins only.
                    -# When a ticket is closed or deleted, its transcript goes to the panel's transcript channel."""),
            new Page(BotModule.WELCOMER, "welcomer", "👋", "Welcomer",
                    "Greets new members",
                    """
                    `/welcomer setchannel channel` Where welcome messages go.
                    `/welcomer setembed` Choose which saved embed is used.
                    `/welcomer ping on/off` Whether the welcome message mentions the new member.
                    `/welcomer enable`, `disable` Turn welcome messages on or off (settings are kept).
                    `/welcomer test [user]` Sends a test welcome message.
                    *Needs Manage Server.* Embed placeholders: `{user}` `{user_mention}` `{server}` `{member_count}` `{member_count_ordinal}` `{server_icon}` `{member_avatar}`."""),
            new Page(BotModule.EMBEDS, "embeds", "🧩", "Embeds & rules",
                    "Build and send custom embeds, set the rules",
                    """
                    `/embed create`, `edit`, `send`, `delete` Build embeds, save them and post them anywhere.
                    `/rules select` Pick up to five saved embeds as this server's rules."""),
            new Page(BotModule.LOA, "loa", "🏖️", "Leave of absence",
                    "Members request leave, staff approve or deny",
                    """
                    `/loa request reason duration [ign] [rank]` Ask for leave (for example `3d` or `1w 2d`).
                    `/loa cancel` Withdraw your request, or end your leave early.
                    **Reviewers** `/loa list`, `/loa end member`, `/loa stats [reviewer]`, plus the Approve and Deny buttons.
                    `/loa setup` Review channel, LOA role, reviewer and ping roles, rules. *Needs Manage Server.*
                    The LOA role is added on approval and removed when the leave ends, even after a restart."""),
            new Page(BotModule.TAGS, "tags", "🏷️", "Tags",
                    "Custom commands that reply with a message or embed",
                    """
                    `/tag use name` Shows a tag. If the host enabled prefix triggers, members can also type the
                    server's prefix and the name (for example `!ip`).
                    `/tag list` Shows every tag in this server.
                    `/tag set name [embed] [required-role]` Creates or edits a tag: a form asks for the text.
                    `/tag delete name`, `/tag prefix [prefix]` Remove a tag, or choose the trigger prefix.
                    *Managing tags needs Manage Server.* Text placeholders: `{user}` `{user_mention}` `{server}` `{member_count}`."""),
            new Page(BotModule.LOGGING, "logging", "📜", "Logging",
                    "A log channel for tickets, members, moderation and more",
                    """
                    `/logs channel [channel]` Choose where logs are posted (leave empty to turn logging off).
                    `/logs toggle category on/off` Switch tickets, members, moderation, server or voice logs.
                    `/logs status` Shows what is being logged. *Needs Manage Server.*"""));

    @Override
    public CommandData getCommandData() {
        return Commands.slash("help", "Shows what this bot can do");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("help")) {
            return;
        }
        event.reply(new MessageCreateBuilder()
                        .useComponentsV2()
                        .addComponents(build(event.getJDA(), OVERVIEW))
                        .build())
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!MENU_ID.equals(event.getComponentId()) || event.getValues().isEmpty()) {
            return;
        }
        event.editMessage(new MessageEditBuilder()
                        .useComponentsV2()
                        .setComponents(build(event.getJDA(), event.getValues().get(0)))
                        .build())
                .queue();
    }

    private static List<Page> enabledPages() {
        List<Page> pages = new ArrayList<>();
        for (Page page : PAGES) {
            if (page.module().isEnabled()) {
                pages.add(page);
            }
        }
        return pages;
    }

    private Container build(JDA jda, String requestedKey) {
        String botName = ConfigManager.getBotName();
        List<Page> pages = enabledPages();
        Page current = null;
        for (Page page : pages) {
            if (page.key().equals(requestedKey)) {
                current = page;
            }
        }

        String thumbnailUrl = ConfigManager.getString("help-thumbnail-url");
        if (thumbnailUrl == null) {
            thumbnailUrl = jda.getSelfUser().getEffectiveAvatarUrl();
        }
        TextDisplay heading = TextDisplay.of("# " + botName + " Help\n"
                + (current == null
                        ? "Pick a feature from the menu to see its commands."
                        : current.emoji() + " **" + current.title() + "**"));

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(thumbnailUrl == null ? heading : Section.of(Thumbnail.fromUrl(thumbnailUrl), heading));
        children.add(Separator.createDivider(Separator.Spacing.LARGE));

        if (current == null) {
            StringBuilder overview = new StringBuilder("**What I can do**\n");
            for (Page page : pages) {
                overview.append(page.emoji()).append(" **").append(page.title()).append("** · ")
                        .append(page.summary()).append('\n');
            }
            overview.append("\n-# Slash commands: type `/` and pick one. Many are limited to staff.");
            children.add(TextDisplay.of(overview.toString()));
        } else {
            children.add(TextDisplay.of(current.body()));
        }

        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(ActionRow.of(menu(pages, current)));

        List<Button> linkButtons = new ArrayList<>();
        addLinkButton(linkButtons, "support-url", "Support Server");
        addLinkButton(linkButtons, "terms-url", "Terms of Service");
        addLinkButton(linkButtons, "privacy-url", "Privacy");
        addLinkButton(linkButtons, "security-url", "Security");
        if (!linkButtons.isEmpty()) {
            children.add(ActionRow.of(linkButtons));
        }

        children.add(TextDisplay.of("-# " + botName + " · " + pages.size() + " feature"
                + (pages.size() == 1 ? "" : "s") + " enabled"));
        return Container.of(children).withAccentColor(ACCENT);
    }

    private StringSelectMenu menu(List<Page> pages, Page current) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(MENU_ID).setPlaceholder("Browse a feature");
        menu.addOptions(SelectOption.of("Overview", OVERVIEW)
                .withEmoji(Emoji.fromUnicode("🏠"))
                .withDescription("Everything this bot can do"));
        for (Page page : pages) {
            menu.addOptions(SelectOption.of(page.title(), page.key())
                    .withEmoji(Emoji.fromUnicode(page.emoji()))
                    .withDescription(page.summary()));
        }
        menu.setDefaultValues(current == null ? OVERVIEW : current.key());
        return menu.build();
    }

    private void addLinkButton(List<Button> buttons, String configKey, String label) {
        String url = ConfigManager.getString(configKey);
        if (url != null) {
            buttons.add(Button.link(url, label));
        }
    }
}
