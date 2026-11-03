package rolepanels;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import utils.Log;
import utils.ReactionEmojiParser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** The in-Discord editor of a role panel (/rolepanel create and /rolepanel edit). */
public class RolePanelEditor extends ListenerAdapter {
    private static final String PREFIX = "rpe:";
    private static final Pattern HEX_COLOUR = Pattern.compile("^#?[0-9a-fA-F]{6}$");
    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final List<Permission> RISKY = List.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER,
            Permission.MANAGE_ROLES, Permission.MANAGE_CHANNEL, Permission.BAN_MEMBERS, Permission.KICK_MEMBERS,
            Permission.MANAGE_WEBHOOKS, Permission.MODERATE_MEMBERS);

    enum Page {
        ROLES("roles", "Roles", "Which roles members can pick, and how each looks"),
        APPEARANCE("appearance", "Appearance", "Title, description, colour, images, footer"),
        SETTINGS("settings", "Behaviour", "Style, mode, role limit, on/off"),
        ACCESS("access", "Access & channel", "Required or blocked role, where it is posted"),
        ROLE("role", "Role", "One role of the panel");

        final String key;
        final String label;
        final String description;

        Page(String key, String label, String description) {
            this.key = key;
            this.label = label;
            this.description = description;
        }

        static Page fromKey(String key) {
            for (Page page : values()) {
                if (page.key.equals(key)) {
                    return page;
                }
            }
            return ROLES;
        }
    }

    private record Ctx(RolePanelData panel, Page page, String action, String arg) {
    }

    public void open(SlashCommandInteractionEvent event, RolePanelData panel) {
        event.reply(new MessageCreateBuilder().useComponentsV2()
                        .addComponents(render(panel, Page.ROLES, event.getGuild(), null, -1)).build())
                .setEphemeral(true).queue();
    }

    // ---------------------------------------------------------------- ids

    private static String id(RolePanelData panel, Page page, String action) {
        return PREFIX + panel.id + ":" + page.key + ":" + action;
    }

    private static String id(RolePanelData panel, Page page, String action, String arg) {
        return id(panel, page, action) + ":" + arg;
    }

    // ---------------------------------------------------------------- rendering

    private MessageEditData view(RolePanelData panel, Page page, Guild guild, String notice, int roleIndex) {
        return new MessageEditBuilder().useComponentsV2()
                .setComponents(render(panel, page, guild, notice, roleIndex)).build();
    }

    private MessageEditData plainView(String text) {
        return new MessageEditBuilder().useComponentsV2()
                .setComponents(Container.of(TextDisplay.of(text))).build();
    }

    private Container render(RolePanelData panel, Page requested, Guild guild, String notice, int roleIndex) {
        Page page = requested == Page.ROLE && (roleIndex < 0 || roleIndex >= panel.roles.size())
                ? Page.ROLES : requested;

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# Role panel: " + truncate(panel.name, 60)));
        children.add(TextDisplay.of(statusLine(panel)));
        if (notice != null) {
            children.add(TextDisplay.of(truncate(notice, 1500)));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));

        switch (page) {
            case ROLES -> addRoles(children, panel, page, guild);
            case APPEARANCE -> addAppearance(children, panel, page);
            case SETTINGS -> addSettings(children, panel, page);
            case ACCESS -> addAccess(children, panel, page, guild);
            case ROLE -> addRole(children, panel, roleIndex, guild);
        }

        if (page != Page.ROLE) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(ActionRow.of(pageMenu(panel, page)));
            children.add(ActionRow.of(
                    Button.secondary(id(panel, page, "preview"), "Preview"),
                    Button.success(id(panel, page, "publish"), panel.messageId == null ? "Publish" : "Update panel"),
                    Button.danger(id(panel, page, "delete"), "Delete"),
                    Button.secondary(id(panel, page, "done"), "Done")));
        }
        return Container.of(children).withAccentColor(RolePanelRenderer.parseColour(panel.colour));
    }

    private static String statusLine(RolePanelData panel) {
        String where = panel.channelId == null
                ? "Not posted yet."
                : (panel.messageId == null ? "Channel set: <#" + panel.channelId + ">. Not posted yet."
                        : "Posted in <#" + panel.channelId + ">.");
        return "-# " + (panel.disabled ? "🔒 **Disabled.** " : "") + where + " Changes save automatically; press **"
                + (panel.messageId == null ? "Publish" : "Update panel") + "** to show them to members.";
    }

    private static StringSelectMenu pageMenu(RolePanelData panel, Page current) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(id(panel, current, "nav"))
                .setPlaceholder("Go to a page");
        for (Page page : Page.values()) {
            if (page != Page.ROLE) {
                menu.addOptions(SelectOption.of(page.label, page.key).withDescription(page.description));
            }
        }
        menu.setDefaultValues(current.key);
        return menu.build();
    }

    private static Section row(String buttonId, String text) {
        return Section.of(Button.secondary(buttonId, "Edit"), TextDisplay.of(text));
    }

    private void addRoles(List<ContainerChildComponent> children, RolePanelData panel, Page page, Guild guild) {
        StringBuilder text = new StringBuilder("**Roles** (" + panel.roles.size() + "/" + RolePanelData.MAX_ROLES + ")\n");
        if (panel.roles.isEmpty()) {
            text.append("*none yet: pick the roles below*");
        }
        for (int index = 0; index < panel.roles.size(); index++) {
            RolePanelData.PanelRole role = panel.roles.get(index);
            boolean missing = guild.getRoleById(role.roleId) == null;
            text.append(index + 1).append(". ")
                    .append(RolePanelRenderer.emojiFor(role).map(emoji -> emoji.getFormatted() + " ").orElse(""))
                    .append(missing ? "⚠️ *deleted role*" : "<@&" + role.roleId + ">");
            if (role.label != null && !role.label.isBlank()) {
                text.append(" as **").append(truncate(role.label, 40)).append("**");
            }
            text.append('\n');
        }
        if (panel.style == RolePanelData.Style.REACTIONS) {
            boolean lacking = panel.roles.stream().anyMatch(role -> RolePanelRenderer.emojiFor(role).isEmpty());
            if (lacking) {
                text.append("\n⚠️ The reactions style needs an emoji on every role. Open a role to set one.");
            }
        }
        children.add(TextDisplay.of(truncate(text.toString(), 1800)));

        EntitySelectMenu.Builder roles = EntitySelectMenu.create(
                        id(panel, page, "roles"), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Select the roles members can pick")
                .setRequiredRange(0, RolePanelData.MAX_ROLES);
        List<EntitySelectMenu.DefaultValue> defaults = panel.roles.stream()
                .filter(role -> guild.getRoleById(role.roleId) != null)
                .map(role -> EntitySelectMenu.DefaultValue.role(role.roleId))
                .toList();
        if (!defaults.isEmpty()) {
            roles.setDefaultValues(defaults);
        }
        children.add(ActionRow.of(roles.build()));

        if (!panel.roles.isEmpty()) {
            StringSelectMenu.Builder pick = StringSelectMenu.create(id(panel, page, "rolepick"))
                    .setPlaceholder("Customise a role: label, emoji, description, colour, order");
            for (int index = 0; index < panel.roles.size(); index++) {
                pick.addOptions(SelectOption.of(
                        truncate(RolePanelRenderer.labelFor(panel.roles.get(index), guild), 100),
                        Integer.toString(index)));
            }
            children.add(ActionRow.of(pick.build()));
        }
        children.add(TextDisplay.of("-# Only roles below your highest role and mine can be added. Managed roles "
                + "(bots, boosters) and @everyone can't be handed out."));
    }

    private void addRole(List<ContainerChildComponent> children, RolePanelData panel, int index, Guild guild) {
        RolePanelData.PanelRole role = panel.roles.get(index);
        Role discordRole = guild.getRoleById(role.roleId);
        String emoji = RolePanelRenderer.emojiFor(role).map(e -> e.getFormatted()).orElse("*none*");
        children.add(TextDisplay.of("## " + (discordRole == null ? "⚠️ Deleted role" : "<@&" + role.roleId + ">")
                + "\nLabel: " + (role.label == null || role.label.isBlank()
                        ? "*the role's name*" : truncate(role.label, 60))
                + "\nEmoji: " + emoji
                + "\nDescription: " + (role.description == null || role.description.isBlank()
                        ? "*none*" : truncate(role.description, 80))
                + "\nButton colour: " + styleName(role.buttonStyle)
                + "\n-# Description is shown in dropdowns. Colour is only used on buttons. "
                + "Position " + (index + 1) + " of " + panel.roles.size() + "."));
        children.add(ActionRow.of(Button.primary(id(panel, Page.ROLE, "rolemodal", Integer.toString(index)), "Edit"),
                Button.secondary(id(panel, Page.ROLE, "up", Integer.toString(index)), "Move up")
                        .withDisabled(index == 0),
                Button.secondary(id(panel, Page.ROLE, "down", Integer.toString(index)), "Move down")
                        .withDisabled(index == panel.roles.size() - 1),
                Button.danger(id(panel, Page.ROLE, "remove", Integer.toString(index)), "Remove"),
                Button.secondary(id(panel, Page.ROLE, "back"), "Back")));
    }

    private void addAppearance(List<ContainerChildComponent> children, RolePanelData panel, Page page) {
        children.add(row(id(panel, page, "title"), "**Title & description**\nTitle: "
                + truncate(orDefault(panel.title, panel.name), 70) + "\nDescription: "
                + (panel.description == null || panel.description.isBlank()
                        ? "*default text*" : truncate(panel.description.replace('\n', ' '), 90))));
        children.add(row(id(panel, page, "colour"), "**Colour**\n"
                + (panel.colour == null ? "#5865F2 *(default)*" : normaliseColour(panel.colour))));
        children.add(row(id(panel, page, "images"), "**Images**\nImage: "
                + (panel.imageUrl == null ? "*not set*" : "set") + "\nThumbnail: "
                + (panel.thumbnailUrl == null ? "*not set*" : "set")));
        children.add(row(id(panel, page, "footer"), "**Footer**\n"
                + (panel.footerText == null ? "*default (server name)*" : truncate(panel.footerText, 80))));
    }

    private void addSettings(List<ContainerChildComponent> children, RolePanelData panel, Page page) {
        children.add(TextDisplay.of("**Style:** " + panel.style.label));
        StringSelectMenu.Builder style = StringSelectMenu.create(id(panel, page, "style"))
                .setPlaceholder("How members pick roles");
        for (RolePanelData.Style option : RolePanelData.Style.values()) {
            style.addOptions(SelectOption.of(option.label, option.name()));
        }
        style.setDefaultValues(panel.style.name());
        children.add(ActionRow.of(style.build()));

        children.add(TextDisplay.of("**Mode:** " + panel.mode.label + "\n-# " + panel.mode.description));
        StringSelectMenu.Builder mode = StringSelectMenu.create(id(panel, page, "mode"))
                .setPlaceholder("What picking a role does");
        for (RolePanelData.Mode option : RolePanelData.Mode.values()) {
            mode.addOptions(SelectOption.of(option.label, option.name()).withDescription(option.description));
        }
        mode.setDefaultValues(panel.mode.name());
        children.add(ActionRow.of(mode.build()));

        children.add(row(id(panel, page, "max"), "**Role limit**\n"
                + (panel.mode == RolePanelData.Mode.UNIQUE ? "*not used: pick-one panels allow exactly one role*"
                        : panel.maxRoles <= 0 ? "*no limit*"
                                : panel.maxRoles + " role" + (panel.maxRoles == 1 ? "" : "s") + " from this panel")
                + "\n-# The most roles one member can hold from this panel."));
        children.add(Section.of(
                panel.disabled ? Button.success(id(panel, page, "toggle"), "Enable")
                        : Button.danger(id(panel, page, "toggle"), "Disable"),
                TextDisplay.of("**Panel status**\n" + (panel.disabled ? "🔒 Disabled" : "✅ Enabled")
                        + "\n-# Disabling greys out the posted panel right away; nobody can use it until enabled.")));
        children.add(row(id(panel, page, "dismsg"), "**Disabled message**\n" + (panel.disabledMessage == null
                ? "*default: " + RolePanelRenderer.DEFAULT_DISABLED_MESSAGE + "*"
                : truncate(panel.disabledMessage.replace('\n', ' '), 120))));
    }

    private void addAccess(List<ContainerChildComponent> children, RolePanelData panel, Page page, Guild guild) {
        children.add(TextDisplay.of("**Required role**\n"
                + (panel.requiredRoleId == null ? "*none: everyone can use the panel*" : "<@&" + panel.requiredRoleId + ">")
                + "\n-# Members need this role to use the panel."));
        children.add(ActionRow.of(singleRoleMenu(id(panel, page, "required"), "Select the required role",
                panel.requiredRoleId, guild)));
        children.add(TextDisplay.of("**Blocked role**\n"
                + (panel.blockedRoleId == null ? "*none*" : "<@&" + panel.blockedRoleId + ">")
                + "\n-# Members with this role can't use the panel."));
        children.add(ActionRow.of(singleRoleMenu(id(panel, page, "blocked"), "Select the blocked role",
                panel.blockedRoleId, guild)));
        children.add(TextDisplay.of("**Panel channel**\n"
                + (panel.channelId == null ? "*not set*" : "<#" + panel.channelId + ">")
                + "\n-# Where the panel is posted."));
        EntitySelectMenu.Builder channel = EntitySelectMenu.create(
                        id(panel, page, "channel"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setPlaceholder("Select the panel channel")
                .setRequiredRange(0, 1);
        if (panel.channelId != null && guild.getGuildChannelById(panel.channelId) != null) {
            channel.setDefaultValues(EntitySelectMenu.DefaultValue.channel(panel.channelId));
        }
        children.add(ActionRow.of(channel.build()));
    }

    private static EntitySelectMenu singleRoleMenu(String id, String placeholder, String selected, Guild guild) {
        EntitySelectMenu.Builder menu = EntitySelectMenu.create(id, EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(placeholder).setRequiredRange(0, 1);
        if (selected != null && guild.getRoleById(selected) != null) {
            menu.setDefaultValues(EntitySelectMenu.DefaultValue.role(selected));
        }
        return menu.build();
    }

    // ---------------------------------------------------------------- plumbing

    private Ctx resolve(IReplyCallback event, String customId) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            deny(event, "This editor can only be used inside a server.");
            return null;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            deny(event, "You need the Manage Server permission to edit role panels.");
            return null;
        }
        String[] parts = customId.split(":", 5);
        if (parts.length < 4) {
            deny(event, "That editor control is invalid. Reopen the editor with /rolepanel edit.");
            return null;
        }
        try {
            RolePanelData panel = RolePanelStore.getById(guild.getId(), Integer.parseInt(parts[1]));
            if (panel == null) {
                deny(event, "That role panel no longer exists.");
                return null;
            }
            return new Ctx(panel, Page.fromKey(parts[2]), parts[3], parts.length > 4 ? parts[4] : null);
        } catch (NumberFormatException exception) {
            deny(event, "Invalid role panel ID.");
        } catch (SQLException exception) {
            Log.error("RolePanelEditor.resolve failed", exception);
            deny(event, "Couldn't load the role panel. Please try again.");
        }
        return null;
    }

    private static void deny(IReplyCallback event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private void refresh(IMessageEditCallback event, RolePanelData panel, Page page, String notice, int roleIndex) {
        event.editMessage(view(panel, page, event.getGuild(), notice, roleIndex)).queue();
    }

    /** Saves, reloads (new roles get their ids) and redraws the editor. */
    private <E extends IMessageEditCallback & IReplyCallback> void saveAndRefresh(
            E event, Ctx ctx, String notice, int roleIndex) {
        RolePanelData panel = ctx.panel();
        try {
            RolePanelStore.save(panel);
            RolePanelData fresh = RolePanelStore.getById(panel.guildId, panel.id);
            if (fresh != null) {
                panel = fresh;
            }
        } catch (SQLException exception) {
            Log.error("RolePanelEditor.saveAndRefresh failed", exception);
            deny(event, "Couldn't save that change. Please try again.");
            return;
        }
        refresh(event, panel, ctx.page(), notice, roleIndex);
    }

    private static int index(Ctx ctx) {
        try {
            return ctx.arg() == null ? -1 : Integer.parseInt(ctx.arg());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- buttons

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX)) {
            return;
        }
        Ctx ctx = resolve(event, event.getComponentId());
        if (ctx == null) {
            return;
        }
        RolePanelData panel = ctx.panel();
        int roleIndex = index(ctx);
        switch (ctx.action()) {
            case "title" -> event.replyModal(Modal.create(id(panel, ctx.page(), "title"), "Title & description")
                    .addComponents(
                            Label.of("Title", input("title", TextInputStyle.SHORT, 256, panel.title,
                                    "Pick your roles", false)),
                            Label.of("Description", input("description", TextInputStyle.PARAGRAPH, 3000,
                                    panel.description, "Tell members what the roles are for", false)))
                    .build()).queue();
            case "colour" -> event.replyModal(Modal.create(id(panel, ctx.page(), "colour"), "Colour")
                    .addComponents(Label.of("Hex colour (blank for the default)", input("colour",
                            TextInputStyle.SHORT, 7, panel.colour == null ? null : normaliseColour(panel.colour),
                            "#5865F2", false)))
                    .build()).queue();
            case "images" -> event.replyModal(Modal.create(id(panel, ctx.page(), "images"), "Images")
                    .addComponents(
                            Label.of("Image link (big picture)", input("image", TextInputStyle.SHORT, 500,
                                    panel.imageUrl, "https://...", false)),
                            Label.of("Thumbnail link (small picture)", input("thumbnail", TextInputStyle.SHORT, 500,
                                    panel.thumbnailUrl, "https://...", false)))
                    .build()).queue();
            case "footer" -> event.replyModal(Modal.create(id(panel, ctx.page(), "footer"), "Footer")
                    .addComponents(Label.of("Footer text (blank for the server name)", input("footer",
                            TextInputStyle.SHORT, 200, panel.footerText, null, false)))
                    .build()).queue();
            case "max" -> event.replyModal(Modal.create(id(panel, ctx.page(), "max"), "Role limit")
                    .addComponents(Label.of("Most roles per member (0 = no limit)", input("max",
                            TextInputStyle.SHORT, 2, panel.maxRoles <= 0 ? null : Integer.toString(panel.maxRoles),
                            "0", false)))
                    .build()).queue();
            case "dismsg" -> event.replyModal(Modal.create(id(panel, ctx.page(), "dismsg"), "Disabled message")
                    .addComponents(Label.of("Shown while the panel is disabled", input("dismsg",
                            TextInputStyle.PARAGRAPH, 500, panel.disabledMessage,
                            RolePanelRenderer.DEFAULT_DISABLED_MESSAGE, false)))
                    .build()).queue();
            case "rolemodal" -> {
                if (roleIndex < 0 || roleIndex >= panel.roles.size()) {
                    refresh(event, panel, Page.ROLES, "⚠️ That role no longer exists.", -1);
                    return;
                }
                RolePanelData.PanelRole role = panel.roles.get(roleIndex);
                event.replyModal(Modal.create(id(panel, Page.ROLE, "rolemodal", Integer.toString(roleIndex)),
                                "Edit role")
                        .addComponents(
                                Label.of("Label (blank = role name)", input("label", TextInputStyle.SHORT, 80,
                                        role.label, "Gamer", false)),
                                Label.of("Emoji", input("emoji", TextInputStyle.SHORT, 80, role.emoji,
                                        "🎮 or a server emoji like <:name:id>", false)),
                                Label.of("Description (dropdown only)", input("description", TextInputStyle.SHORT, 100,
                                        role.description, "Shown under the name in the dropdown", false)),
                                Label.of("Button colour: Blue, Grey, Green or Red", input("style",
                                        TextInputStyle.SHORT, 10, role.buttonStyle == null ? null : styleName(role.buttonStyle),
                                        "Grey", false)))
                        .build()).queue();
            }
            case "up", "down" -> {
                int target = roleIndex + ("up".equals(ctx.action()) ? -1 : 1);
                if (roleIndex < 0 || roleIndex >= panel.roles.size() || target < 0 || target >= panel.roles.size()) {
                    refresh(event, panel, Page.ROLES, "⚠️ That role can't move any further.", -1);
                    return;
                }
                RolePanelData.PanelRole moved = panel.roles.remove(roleIndex);
                panel.roles.add(target, moved);
                saveAndRefresh(event, new Ctx(panel, Page.ROLE, ctx.action(), ctx.arg()),
                        "✅ Moved. Press **Update panel** to show the new order.", target);
            }
            case "remove" -> {
                if (roleIndex < 0 || roleIndex >= panel.roles.size()) {
                    refresh(event, panel, Page.ROLES, "⚠️ That role no longer exists.", -1);
                    return;
                }
                panel.roles.remove(roleIndex);
                saveAndRefresh(event, new Ctx(panel, Page.ROLES, ctx.action(), ctx.arg()),
                        "✅ Removed from the panel. Press **Update panel** to apply.", -1);
            }
            case "back" -> refresh(event, panel, Page.ROLES, null, -1);
            case "toggle" -> toggle(event, ctx);
            case "preview" -> preview(event, panel);
            case "publish" -> publish(event, ctx);
            case "delete" -> event.editMessage(deleteConfirmation(panel, ctx.page())).queue();
            case "delete_cancel" -> refresh(event, panel, ctx.page(), null, -1);
            case "delete_confirm" -> deletePanel(event, ctx);
            case "done" -> event.editMessage(plainView("Editor closed. Reopen it any time with `/rolepanel edit`."))
                    .queue();
            default -> deny(event, "That editor control isn't available.");
        }
    }

    private void toggle(ButtonInteractionEvent event, Ctx ctx) {
        RolePanelData panel = ctx.panel();
        panel.disabled = !panel.disabled;
        try {
            RolePanelStore.save(panel);
        } catch (SQLException exception) {
            Log.error("RolePanelEditor.toggle failed", exception);
            deny(event, "Couldn't save that change. Please try again.");
            return;
        }
        String state = panel.disabled ? "disabled" : "enabled";
        Guild guild = event.getGuild();
        GuildMessageChannel channel = postedChannel(guild, panel);
        if (channel == null) {
            refresh(event, panel, ctx.page(), "✅ Panel " + state + ". It isn't posted yet.", -1);
            return;
        }
        event.deferEdit().queue(hook -> RolePanelRenderer.publish(panel, channel).queue(
                message -> hook.editOriginal(view(panel, ctx.page(), guild,
                        "✅ Panel " + state + " and the posted message updated.", -1)).queue(),
                error -> hook.editOriginal(view(panel, ctx.page(), guild,
                        "⚠️ Panel " + state + ", but I couldn't update the posted message. "
                                + "Press **Update panel** to try again.", -1)).queue()));
    }

    private static GuildMessageChannel postedChannel(Guild guild, RolePanelData panel) {
        if (panel.channelId == null || panel.messageId == null) {
            return null;
        }
        return guild.getChannelById(GuildMessageChannel.class, panel.channelId);
    }

    private void preview(ButtonInteractionEvent event, RolePanelData panel) {
        var reply = event.reply("**Preview.** This is how members will see the panel (controls are disabled here).")
                .addEmbeds(RolePanelRenderer.buildEmbed(panel, event.getGuild()))
                .setEphemeral(true);
        List<ActionRow> rows = RolePanelRenderer.buildComponents(panel, event.getGuild()).stream()
                .map(row -> row.withDisabled(true)).toList();
        if (!rows.isEmpty()) {
            reply.addComponents(rows);
        }
        reply.queue();
    }

    private void publish(ButtonInteractionEvent event, Ctx ctx) {
        RolePanelData panel = ctx.panel();
        Guild guild = event.getGuild();
        GuildMessageChannel channel = panel.channelId == null
                ? null : guild.getChannelById(GuildMessageChannel.class, panel.channelId);
        if (channel == null) {
            refresh(event, panel, ctx.page(),
                    "⚠️ Select a panel channel on the **Access & channel** page first.", -1);
            return;
        }
        if (panel.roles.isEmpty()) {
            refresh(event, panel, ctx.page(), "⚠️ Add at least one role first.", -1);
            return;
        }
        if (panel.style == RolePanelData.Style.REACTIONS
                && panel.roles.stream().anyMatch(role -> RolePanelRenderer.emojiFor(role).isEmpty())) {
            refresh(event, panel, ctx.page(),
                    "⚠️ The reactions style needs an emoji on every role. Open each role and set one.", -1);
            return;
        }
        Member self = guild.getSelfMember();
        if (!self.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)
                || (panel.style == RolePanelData.Style.REACTIONS
                        && !self.hasPermission(channel, Permission.MESSAGE_ADD_REACTION))) {
            refresh(event, panel, ctx.page(), "⚠️ In " + channel.getAsMention() + " I need View Channel, Send "
                    + "Messages, Embed Links" + (panel.style == RolePanelData.Style.REACTIONS
                            ? " and Add Reactions" : "") + ".", -1);
            return;
        }

        event.deferEdit().queue(hook -> RolePanelRenderer.publish(panel, channel).queue(
                message -> finishPublish(hook, ctx, channel, message),
                error -> hook.editOriginal(view(panel, ctx.page(), guild,
                        "⚠️ I couldn't post the panel in " + channel.getAsMention() + ".", -1)).queue()));
    }

    private void finishPublish(net.dv8tion.jda.api.interactions.InteractionHook hook, Ctx ctx,
            GuildMessageChannel channel, Message message) {
        RolePanelData panel = ctx.panel();
        Guild guild = channel.getGuild();
        try {
            RolePanelStore.saveMessage(panel.id, channel.getId(), message.getId());
            panel.channelId = channel.getId();
            panel.messageId = message.getId();
        } catch (SQLException exception) {
            Log.error("RolePanelEditor.finishPublish failed", exception);
            hook.editOriginal(view(panel, ctx.page(), guild,
                    "⚠️ The panel was posted but I couldn't remember its message, so it can't be updated in place.", -1))
                    .queue();
            return;
        }
        RolePanelRenderer.syncReactions(message, panel);
        hook.editOriginal(view(panel, ctx.page(), guild, "✅ Posted in " + channel.getAsMention() + ".", -1)).queue();
    }

    private MessageEditData deleteConfirmation(RolePanelData panel, Page page) {
        return new MessageEditBuilder().useComponentsV2().setComponents(Container.of(
                TextDisplay.of("## Delete **" + truncate(panel.name, 60) + "**?"),
                TextDisplay.of("This permanently deletes the panel and removes its posted message. "
                        + "Roles members already have are not touched."),
                ActionRow.of(Button.danger(id(panel, page, "delete_confirm"), "Delete panel"),
                        Button.secondary(id(panel, page, "delete_cancel"), "Cancel")))).build();
    }

    private void deletePanel(ButtonInteractionEvent event, Ctx ctx) {
        RolePanelData panel = ctx.panel();
        Guild guild = event.getGuild();
        try {
            RolePanelStore.delete(guild.getId(), panel.id);
        } catch (SQLException exception) {
            Log.error("RolePanelEditor.deletePanel failed", exception);
            refresh(event, panel, ctx.page(), "⚠️ Couldn't delete the panel. Please try again.", -1);
            return;
        }
        GuildMessageChannel channel = postedChannel(guild, panel);
        if (channel != null) {
            channel.deleteMessageById(panel.messageId).queue(null, error -> { });
        }
        event.editMessage(plainView("**Deleted** the role panel `" + truncate(panel.name, 60) + "`.")).queue();
    }

    // ---------------------------------------------------------------- selects

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX) || event.getValues().isEmpty()) {
            return;
        }
        Ctx ctx = resolve(event, event.getComponentId());
        if (ctx == null) {
            return;
        }
        RolePanelData panel = ctx.panel();
        String value = event.getValues().get(0);
        switch (ctx.action()) {
            case "nav" -> refresh(event, panel, Page.fromKey(value), null, -1);
            case "rolepick" -> {
                int index;
                try {
                    index = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    index = -1;
                }
                if (index < 0 || index >= panel.roles.size()) {
                    refresh(event, panel, Page.ROLES, "⚠️ That role no longer exists.", -1);
                    return;
                }
                refresh(event, panel, Page.ROLE, null, index);
            }
            case "style" -> {
                panel.style = RolePanelData.Style.parse(value);
                saveAndRefresh(event, ctx, "✅ Saved. Press **Update panel** to apply."
                        + (panel.style == RolePanelData.Style.REACTIONS
                                ? " Every role needs an emoji for reactions." : ""), -1);
            }
            case "mode" -> {
                panel.mode = RolePanelData.Mode.parse(value);
                saveAndRefresh(event, ctx, "✅ Saved. Press **Update panel** to apply.", -1);
            }
            default -> deny(event, "That editor control isn't available.");
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX)) {
            return;
        }
        Ctx ctx = resolve(event, event.getComponentId());
        if (ctx == null) {
            return;
        }
        RolePanelData panel = ctx.panel();
        Guild guild = event.getGuild();
        List<IMentionable> values = new ArrayList<>(event.getValues());

        switch (ctx.action()) {
            case "roles" -> {
                Set<String> chosen = new LinkedHashSet<>();
                for (IMentionable value : values) {
                    if (value instanceof Role role) {
                        chosen.add(role.getId());
                    }
                }
                List<RolePanelData.PanelRole> next = new ArrayList<>();
                for (RolePanelData.PanelRole existing : panel.roles) {
                    if (chosen.remove(existing.roleId)) {
                        next.add(existing);
                    }
                }
                List<String> notes = new ArrayList<>();
                for (String roleId : chosen) {
                    Role role = guild.getRoleById(roleId);
                    String problem = role == null ? "no longer exists" : unusable(guild, event.getMember(), role);
                    if (problem != null) {
                        notes.add("⚠️ " + (role == null ? "A role" : role.getAsMention()) + " " + problem + ".");
                        continue;
                    }
                    List<String> risky = RISKY.stream().filter(role::hasPermission).map(Permission::getName).toList();
                    if (!risky.isEmpty()) {
                        notes.add("⚠️ " + role.getAsMention() + " has powerful permissions ("
                                + String.join(", ", risky) + "). Anyone who can use this panel can get it.");
                    }
                    next.add(new RolePanelData.PanelRole(roleId));
                }
                panel.roles = next;
                notes.add(0, "✅ Saved. Press **Update panel** to apply.");
                saveAndRefresh(event, ctx, String.join("\n", notes), -1);
            }
            case "required" -> {
                panel.requiredRoleId = firstRole(values);
                saveAndRefresh(event, ctx, "✅ Saved.", -1);
            }
            case "blocked" -> {
                panel.blockedRoleId = firstRole(values);
                saveAndRefresh(event, ctx, "✅ Saved.", -1);
            }
            case "channel" -> {
                String newChannelId = values.stream()
                        .filter(GuildMessageChannel.class::isInstance)
                        .map(value -> ((GuildMessageChannel) value).getId())
                        .findFirst().orElse(null);
                boolean moved = panel.messageId != null && !java.util.Objects.equals(newChannelId, panel.channelId);
                if (moved) {
                    GuildMessageChannel old = postedChannel(guild, panel);
                    if (old != null) {
                        old.deleteMessageById(panel.messageId).queue(null, error -> { });
                    }
                    panel.messageId = null;
                }
                panel.channelId = newChannelId;
                saveAndRefresh(event, ctx, moved
                        ? "✅ Saved. The old message was removed. Press **Publish** to post it here."
                        : "✅ Saved.", -1);
            }
            default -> deny(event, "That editor control isn't available.");
        }
    }

    private static String firstRole(List<IMentionable> values) {
        return values.stream().filter(Role.class::isInstance).map(value -> ((Role) value).getId())
                .findFirst().orElse(null);
    }

    /** Why this role can't be offered (null = fine). */
    private static String unusable(Guild guild, Member editor, Role role) {
        if (role.isPublicRole()) {
            return "is @everyone and can't be offered";
        }
        if (role.isManaged()) {
            return "is managed by an integration and can't be handed out";
        }
        if (!guild.getSelfMember().canInteract(role)) {
            return "is above my highest role. Move my role above it in Server Settings → Roles";
        }
        if (!editor.canInteract(role)) {
            return "is above your highest role, so you can't offer it";
        }
        return null;
    }

    // ---------------------------------------------------------------- modals

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(PREFIX)) {
            return;
        }
        Ctx ctx = resolve(event, event.getModalId());
        if (ctx == null) {
            return;
        }
        RolePanelData panel = ctx.panel();
        int roleIndex = -1;
        try {
            switch (ctx.action()) {
                case "title" -> {
                    panel.title = blankToNull(value(event, "title"));
                    panel.description = blankToNull(value(event, "description"));
                }
                case "colour" -> {
                    String colour = value(event, "colour");
                    if (!colour.isEmpty() && !HEX_COLOUR.matcher(colour).matches()) {
                        throw new IllegalArgumentException("Use a hex colour like `#5865F2`.");
                    }
                    panel.colour = colour.isEmpty() ? null : normaliseColour(colour);
                }
                case "images" -> {
                    panel.imageUrl = validUrl(value(event, "image"), "Image");
                    panel.thumbnailUrl = validUrl(value(event, "thumbnail"), "Thumbnail");
                }
                case "footer" -> panel.footerText = blankToNull(value(event, "footer"));
                case "dismsg" -> panel.disabledMessage = blankToNull(value(event, "dismsg"));
                case "max" -> {
                    String text = value(event, "max");
                    int limit;
                    try {
                        limit = text.isEmpty() ? 0 : Integer.parseInt(text);
                    } catch (NumberFormatException exception) {
                        limit = -1;
                    }
                    if (limit < 0 || limit > RolePanelData.MAX_ROLES) {
                        throw new IllegalArgumentException("Use a whole number from 0 to "
                                + RolePanelData.MAX_ROLES + " (0 means no limit).");
                    }
                    panel.maxRoles = limit;
                }
                case "rolemodal" -> {
                    roleIndex = index(ctx);
                    if (roleIndex < 0 || roleIndex >= panel.roles.size()) {
                        throw new IllegalArgumentException("That role no longer exists. Reopen the editor.");
                    }
                    RolePanelData.PanelRole role = panel.roles.get(roleIndex);
                    role.label = blankToNull(value(event, "label"));
                    String emoji = value(event, "emoji");
                    if (!emoji.isEmpty() && ReactionEmojiParser.parse(emoji).isEmpty()) {
                        throw new IllegalArgumentException("I couldn't read that emoji. Use a normal emoji or a "
                                + "server emoji like `<:name:id>`.");
                    }
                    role.emoji = emoji.isEmpty() ? null : ReactionEmojiParser.parse(emoji).get().getFormatted();
                    role.description = blankToNull(value(event, "description"));
                    role.buttonStyle = parseStyleInput(value(event, "style"));
                }
                default -> {
                    deny(event, "That editor control isn't available.");
                    return;
                }
            }
        } catch (IllegalArgumentException exception) {
            deny(event, exception.getMessage());
            return;
        }
        if ("rolemodal".equals(ctx.action())) {
            saveAndRefresh(event, new Ctx(panel, Page.ROLE, ctx.action(), ctx.arg()),
                    "✅ Saved. Press **Update panel** to apply.", roleIndex);
        } else {
            saveAndRefresh(event, ctx, "✅ Saved. Press **Update panel** to apply.", -1);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static TextInput input(
            String id, TextInputStyle style, int maxLength, String value, String placeholder, boolean required) {
        TextInput.Builder builder = TextInput.create(id, style).setRequired(required).setMaxLength(maxLength);
        if (placeholder != null) {
            builder.setPlaceholder(truncate(placeholder, 100));
        }
        if (value != null && !value.isBlank()) {
            builder.setValue(value.length() > maxLength ? value.substring(0, maxLength) : value);
        }
        return builder.build();
    }

    private static String value(ModalInteractionEvent event, String id) {
        var mapping = event.getValue(id);
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    private static String validUrl(String value, String what) {
        if (value.isEmpty()) {
            return null;
        }
        if (!HTTP_URL.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a link starting with http:// or https://.");
        }
        return value;
    }

    private static String parseStyleInput(String value) {
        if (value.isEmpty()) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "blue", "primary" -> "PRIMARY";
            case "grey", "gray", "secondary" -> "SECONDARY";
            case "green", "success" -> "SUCCESS";
            case "red", "danger" -> "DANGER";
            default -> throw new IllegalArgumentException("Button colour must be Blue, Grey, Green or Red.");
        };
    }

    private static String styleName(String style) {
        if (style == null) {
            return "Grey";
        }
        return switch (style.toUpperCase(Locale.ROOT)) {
            case "PRIMARY" -> "Blue";
            case "SUCCESS" -> "Green";
            case "DANGER" -> "Red";
            default -> "Grey";
        };
    }

    private static String normaliseColour(String colour) {
        return "#" + (colour.startsWith("#") ? colour.substring(1) : colour).toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
