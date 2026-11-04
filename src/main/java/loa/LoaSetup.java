package loa;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import utils.Log;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** The /loa setup editor: review channel, LOA role, reviewer and ping roles, and the rules. */
public class LoaSetup extends ListenerAdapter {
    private static final String PREFIX = "loas:";

    void open(SlashCommandInteractionEvent event) throws SQLException {
        LoaStore.Settings settings = settingsOf(event.getGuild());
        event.reply(new MessageCreateBuilder().useComponentsV2()
                .addComponents(render(settings, event.getGuild(), null)).build()).setEphemeral(true).queue();
    }

    private static LoaStore.Settings settingsOf(Guild guild) throws SQLException {
        LoaStore.Settings settings = LoaStore.getSettings(guild.getId());
        if (settings == null) {
            settings = new LoaStore.Settings();
            settings.guildId = guild.getId();
        }
        return settings;
    }

    // ---------------------------------------------------------------- rendering

    private MessageEditData view(LoaStore.Settings settings, Guild guild, String notice) {
        return new MessageEditBuilder().useComponentsV2().setComponents(render(settings, guild, notice)).build();
    }

    private Container render(LoaStore.Settings settings, Guild guild, String notice) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# Leave of Absence setup"));
        children.add(TextDisplay.of(settings.ready()
                ? "-# ✅ LOA is ready. Members can use `/loa request`. Changes save automatically."
                : "-# ⚠️ Choose a review channel and the LOA role to turn LOA on. Changes save automatically."));
        if (notice != null) {
            children.add(TextDisplay.of(notice));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));

        children.add(TextDisplay.of("**Review channel**\n"
                + (settings.channelId == null ? "*not set*" : "<#" + settings.channelId + ">")
                + "\n-# Requests are posted here with Approve and Deny buttons."));
        EntitySelectMenu.Builder channel = EntitySelectMenu.create(PREFIX + "channel",
                        EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setPlaceholder("Select the review channel").setRequiredRange(0, 1);
        if (settings.channelId != null && guild.getGuildChannelById(settings.channelId) != null) {
            channel.setDefaultValues(EntitySelectMenu.DefaultValue.channel(settings.channelId));
        }
        children.add(ActionRow.of(channel.build()));

        children.add(TextDisplay.of("**LOA role**\n"
                + (settings.roleId == null ? "*not set*" : "<@&" + settings.roleId + ">")
                + "\n-# Given when a request is approved, removed when the leave ends."));
        children.add(ActionRow.of(roleMenu("role", "Select the LOA role", settings.roleId == null
                ? List.of() : List.of(settings.roleId), 1, guild)));

        children.add(TextDisplay.of("**Reviewer roles**\n" + roleList(settings.reviewerRoleIds, guild)
                + "\n-# Can approve, deny and end leaves. Members with Manage Server always can."));
        children.add(ActionRow.of(roleMenu("reviewers", "Select reviewer roles", settings.reviewerRoleIds, 25, guild)));

        children.add(TextDisplay.of("**Ping roles**\n" + roleList(settings.pingRoleIds, guild)
                + "\n-# Mentioned in the review channel for each new request."));
        children.add(ActionRow.of(roleMenu("pings", "Select roles to ping", settings.pingRoleIds, 25, guild)));

        children.add(Section.of(Button.secondary(PREFIX + "limits", "Edit"), TextDisplay.of("**Rules**\n"
                + "Reason: at least " + settings.minReason + " characters\nLongest leave: "
                + (settings.maxDays <= 0 ? "no limit" : settings.maxDays + " days"))));
        children.add(Section.of(
                settings.blockAi ? Button.danger(PREFIX + "ai", "Turn off") : Button.success(PREFIX + "ai", "Turn on"),
                TextDisplay.of("**AI-text check:** " + (settings.blockAi ? "on" : "off")
                        + "\n-# Rejects reasons containing phrases such as \"as an ai\". It is a rough check "
                        + "and can be wrong.")));
        children.add(Section.of(Button.secondary(PREFIX + "name", "Edit"), TextDisplay.of("**Display name**\n"
                + (settings.displayName == null ? "*the server name*" : settings.displayName)
                + "\n-# Shown in the footer of requests and in direct messages.")));
        return Container.of(children).withAccentColor(0xFEE75C);
    }

    private static String roleList(List<String> ids, Guild guild) {
        List<String> valid = ids.stream().filter(id -> guild.getRoleById(id) != null)
                .map(id -> "<@&" + id + ">").toList();
        return valid.isEmpty() ? "*none*" : String.join(" ", valid);
    }

    private static EntitySelectMenu roleMenu(String action, String placeholder, List<String> selected, int max,
            Guild guild) {
        EntitySelectMenu.Builder menu = EntitySelectMenu.create(PREFIX + action, EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(placeholder).setRequiredRange(0, max);
        List<EntitySelectMenu.DefaultValue> defaults = selected.stream()
                .filter(id -> guild.getRoleById(id) != null).map(EntitySelectMenu.DefaultValue::role).toList();
        if (!defaults.isEmpty()) {
            menu.setDefaultValues(defaults);
        }
        return menu.build();
    }

    // ---------------------------------------------------------------- plumbing

    private static boolean allowed(IReplyCallback event) {
        Member member = event.getMember();
        if (event.getGuild() == null || member == null) {
            event.reply("This only works inside a server.").setEphemeral(true).queue();
            return false;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission to set up LOA.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private <E extends IMessageEditCallback & IReplyCallback> void saveAndRefresh(
            E event, LoaStore.Settings settings, String notice) {
        try {
            LoaStore.saveSettings(settings);
        } catch (SQLException exception) {
            Log.error("LoaSetup.saveAndRefresh failed", exception);
            event.reply("Couldn't save that change. Please try again.").setEphemeral(true).queue();
            return;
        }
        event.editMessage(view(settings, event.getGuild(), notice)).queue();
    }

    // ---------------------------------------------------------------- events

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX) || !allowed(event)) {
            return;
        }
        try {
            LoaStore.Settings settings = settingsOf(event.getGuild());
            switch (event.getComponentId().substring(PREFIX.length())) {
                case "limits" -> event.replyModal(Modal.create(PREFIX + "limits", "LOA rules").addComponents(
                        Label.of("Shortest reason, in characters (0-1000)", input("minreason", 4,
                                Integer.toString(settings.minReason), "30")),
                        Label.of("Longest leave in days (0 = no limit)", input("maxdays", 4,
                                Integer.toString(settings.maxDays), "90"))).build()).queue();
                case "name" -> event.replyModal(Modal.create(PREFIX + "name", "Display name").addComponents(
                        Label.of("Name (blank = the server name)", input("name", 60,
                                settings.displayName, "My Staff Team"))).build()).queue();
                case "ai" -> {
                    settings.blockAi = !settings.blockAi;
                    saveAndRefresh(event, settings, "✅ Saved.");
                }
                default -> event.reply("That control isn't available.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("LoaSetup.onButtonInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX) || !allowed(event)) {
            return;
        }
        Guild guild = event.getGuild();
        List<IMentionable> values = new ArrayList<>(event.getValues());
        try {
            LoaStore.Settings settings = settingsOf(guild);
            switch (event.getComponentId().substring(PREFIX.length())) {
                case "channel" -> {
                    GuildMessageChannel channel = values.stream().filter(GuildMessageChannel.class::isInstance)
                            .map(GuildMessageChannel.class::cast).findFirst().orElse(null);
                    settings.channelId = channel == null ? null : channel.getId();
                    boolean missing = channel != null && !guild.getSelfMember().hasPermission(channel,
                            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS);
                    saveAndRefresh(event, settings, missing
                            ? "⚠️ Saved, but I need View Channel, Send Messages and Embed Links in "
                                    + channel.getAsMention() + "."
                            : "✅ Saved.");
                }
                case "role" -> {
                    Role role = values.stream().filter(Role.class::isInstance).map(Role.class::cast)
                            .findFirst().orElse(null);
                    String problem = role == null ? null : unusable(guild, event.getMember(), role);
                    if (problem != null) {
                        event.editMessage(view(settings, guild, "⚠️ " + role.getAsMention() + " " + problem + ".")).queue();
                        return;
                    }
                    settings.roleId = role == null ? null : role.getId();
                    saveAndRefresh(event, settings, "✅ Saved.");
                }
                case "reviewers" -> {
                    settings.reviewerRoleIds = ids(values);
                    saveAndRefresh(event, settings, "✅ Saved.");
                }
                case "pings" -> {
                    settings.pingRoleIds = ids(values);
                    saveAndRefresh(event, settings, "✅ Saved.");
                }
                default -> event.reply("That control isn't available.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("LoaSetup.onEntitySelectInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(PREFIX) || !allowed(event)) {
            return;
        }
        try {
            LoaStore.Settings settings = settingsOf(event.getGuild());
            switch (event.getModalId().substring(PREFIX.length())) {
                case "limits" -> {
                    Integer minReason = number(event, "minreason", 0, 1000);
                    Integer maxDays = number(event, "maxdays", 0, 365);
                    if (minReason == null || maxDays == null) {
                        event.reply("Use whole numbers: a reason length from 0 to 1000 and a number of days "
                                + "from 0 to 365.").setEphemeral(true).queue();
                        return;
                    }
                    settings.minReason = minReason;
                    settings.maxDays = maxDays;
                }
                case "name" -> {
                    String name = event.getValue("name") == null ? "" : event.getValue("name").getAsString().trim();
                    settings.displayName = name.isEmpty() ? null : name;
                }
                default -> {
                    event.reply("That control isn't available.").setEphemeral(true).queue();
                    return;
                }
            }
            saveAndRefresh(event, settings, "✅ Saved.");
        } catch (SQLException exception) {
            Log.error("LoaSetup.onModalInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> ids(List<IMentionable> values) {
        return values.stream().filter(Role.class::isInstance).map(Role.class::cast)
                .filter(role -> !role.isPublicRole()).map(Role::getId).distinct().toList();
    }

    private static String unusable(Guild guild, Member editor, Role role) {
        if (role.isPublicRole()) {
            return "is @everyone and can't be used";
        }
        if (role.isManaged()) {
            return "is managed by an integration and can't be handed out";
        }
        if (!guild.getSelfMember().canInteract(role)) {
            return "is above my highest role. Move my role above it in Server Settings → Roles";
        }
        if (!editor.canInteract(role)) {
            return "is above your highest role";
        }
        return null;
    }

    private static Integer number(ModalInteractionEvent event, String id, int min, int max) {
        var mapping = event.getValue(id);
        String text = mapping == null ? "" : mapping.getAsString().trim();
        try {
            int value = Integer.parseInt(text);
            return value < min || value > max ? null : value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static TextInput input(String id, int maxLength, String value, String placeholder) {
        TextInput.Builder builder = TextInput.create(id, TextInputStyle.SHORT).setRequired(false)
                .setMaxLength(maxLength).setPlaceholder(placeholder);
        if (value != null && !value.isBlank()) {
            builder.setValue(value);
        }
        return builder.build();
    }
}
