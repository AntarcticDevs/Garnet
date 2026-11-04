package loa;

import commands.Command;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.JDA;
import utils.Log;

import java.awt.Color;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** /loa: members ask for a Leave of Absence, reviewers approve or deny, the role is removed when it ends. */
public class LoaCommand extends ListenerAdapter implements Command {
    private static final String APPROVE = "loa:approve:";
    private static final String DENY = "loa:deny:";
    private static final String DENY_MODAL = "loa:denymodal:";

    private static final Color PENDING_COLOUR = new Color(0xFEE75C);
    private static final Color APPROVED_COLOUR = new Color(0x57F287);
    private static final Color DENIED_COLOUR = new Color(0xED4245);
    private static final Color GREY = new Color(0x99AAB5);

    private final LoaSetup setup = new LoaSetup();
    private ScheduledExecutorService sweeper;

    public LoaSetup setupEditor() {
        return setup;
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("loa", "Leave of Absence requests")
                .addSubcommands(
                        new SubcommandData("request", "Ask for a Leave of Absence")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "reason", "Why you need the leave", true)
                                                .setMaxLength(1000),
                                        new OptionData(OptionType.STRING, "duration",
                                                "How long, for example 12h, 3d, 1w or 1w 2d", true).setMaxLength(30),
                                        new OptionData(OptionType.STRING, "ign",
                                                "Your in-game name (if your staff team uses it)", false)
                                                .setMaxLength(60),
                                        new OptionData(OptionType.STRING, "rank",
                                                "Your current rank or role (if your staff team uses it)", false)
                                                .setMaxLength(60)),
                        new SubcommandData("cancel", "Withdraw your request, or end your leave early"),
                        new SubcommandData("list", "Show running leaves and requests waiting for review"),
                        new SubcommandData("end", "End someone's leave now")
                                .addOption(OptionType.USER, "member", "Whose leave to end", true),
                        new SubcommandData("stats", "How many requests each reviewer approved or denied")
                                .addOption(OptionType.USER, "reviewer", "Only this reviewer", false),
                        new SubcommandData("setup", "Choose the review channel, the LOA role and the rules"));
    }

    // ---------------------------------------------------------------- lifecycle

    /** Checks every minute for leaves that ran out, so nothing is lost when the bot restarts. */
    @Override
    public synchronized void onReady(ReadyEvent event) {
        if (sweeper != null) {
            return;
        }
        sweeper = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "loa-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        JDA jda = event.getJDA();
        sweeper.scheduleWithFixedDelay(() -> {
            try {
                for (LoaStore.Request request : LoaStore.due(System.currentTimeMillis())) {
                    endLeave(jda, request, "Your scheduled leave has ended.", false);
                }
            } catch (Exception exception) {
                Log.error("LOA sweep failed", exception);
            }
        }, 5, 60, TimeUnit.SECONDS);
    }

    // ---------------------------------------------------------------- slash commands

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("loa")) {
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
            if (sub.equals("setup")) {
                if (!member.hasPermission(Permission.MANAGE_SERVER)) {
                    event.reply("You need the Manage Server permission to set up LOA.").setEphemeral(true).queue();
                    return;
                }
                setup.open(event);
                return;
            }
            LoaStore.Settings settings = LoaStore.getSettings(guild.getId());
            if (settings == null || !settings.ready()) {
                event.reply(member.hasPermission(Permission.MANAGE_SERVER)
                        ? "LOA isn't set up yet. Run `/loa setup` and choose the review channel and the LOA role."
                        : "LOA isn't set up on this server yet. Ask an admin to run `/loa setup`.")
                        .setEphemeral(true).queue();
                return;
            }
            switch (sub) {
                case "request" -> request(event, guild, member, settings);
                case "cancel" -> cancel(event, guild, member, settings);
                case "list" -> list(event, guild, member, settings);
                case "end" -> endByReviewer(event, guild, member, settings);
                case "stats" -> stats(event, guild, member, settings);
                default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("LoaCommand failed", exception);
            event.reply("Something went wrong with the database. Please try again.").setEphemeral(true).queue();
        }
    }

    static boolean isReviewer(Member member, LoaStore.Settings settings) {
        if (member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        for (Role role : member.getRoles()) {
            if (settings.reviewerRoleIds.contains(role.getId())) {
                return true;
            }
        }
        return false;
    }

    private static String serverName(Guild guild, LoaStore.Settings settings) {
        return settings.displayName == null || settings.displayName.isBlank() ? guild.getName() : settings.displayName;
    }

    private void request(SlashCommandInteractionEvent event, Guild guild, Member member, LoaStore.Settings settings)
            throws SQLException {
        String reason = event.getOption("reason").getAsString().trim();
        String ign = blankToNull(event.getOption("ign") == null ? null : event.getOption("ign").getAsString());
        String rank = blankToNull(event.getOption("rank") == null ? null : event.getOption("rank").getAsString());
        long durationMs = LoaText.parseDuration(event.getOption("duration").getAsString());

        if (reason.length() < settings.minReason) {
            reply(event, "❌ Your reason must be at least **" + settings.minReason + " characters** long. "
                    + "Please explain a little more.");
            return;
        }
        if (settings.blockAi && LoaText.looksLikeAi(reason)) {
            reply(event, "🤖 That reason looks copied from an AI. Please write it in your own words.");
            return;
        }
        if (durationMs < 3_600_000L) {
            reply(event, "❌ I couldn't read that duration, or it is under 1 hour. Use things like `12h`, `3d`, "
                    + "`1w` or `1w 2d`.");
            return;
        }
        if (settings.maxDays > 0 && durationMs > settings.maxDays * 86_400_000L) {
            reply(event, "❌ A leave can be at most **" + settings.maxDays + " days**.");
            return;
        }
        LoaStore.Request open = LoaStore.findOpen(guild.getId(), member.getId());
        if (open != null) {
            reply(event, open.status.equals(LoaStore.PENDING)
                    ? "You already have a request waiting for review. Use `/loa cancel` to withdraw it."
                    : "You are already on leave until <t:" + open.endsAt / 1000 + ":F>. "
                            + "Use `/loa cancel` to end it early.");
            return;
        }
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, settings.channelId);
        if (channel == null || !guild.getSelfMember().hasPermission(channel,
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)) {
            reply(event, "I can't post in the LOA review channel right now. Please tell an admin.");
            return;
        }

        int id = LoaStore.createRequest(guild.getId(), member.getId(), reason, ign, rank, durationMs);
        LoaStore.Request created = LoaStore.getRequest(guild.getId(), id);

        StringBuilder ping = new StringBuilder();
        for (String roleId : settings.pingRoleIds) {
            if (guild.getRoleById(roleId) != null) {
                ping.append("<@&").append(roleId).append("> ");
            }
        }
        var send = channel.sendMessage(ping + "New leave request from " + member.getAsMention() + " needs review.")
                .setAllowedMentions(EnumSet.of(Message.MentionType.ROLE))
                .addEmbeds(buildEmbed(created, settings, guild, member.getUser()))
                .addComponents(ActionRow.of(
                        Button.success(APPROVE + id, "Approve"),
                        Button.danger(DENY + id, "Deny")));
        event.deferReply(true).queue(hook -> send.queue(
                message -> {
                    try {
                        LoaStore.saveMessage(id, channel.getId(), message.getId());
                    } catch (SQLException exception) {
                        Log.error("LoaCommand.request couldn't save the message id", exception);
                    }
                    hook.editOriginal("✅ Your request was sent to the staff team. You'll get a message when it "
                            + "is reviewed.").queue();
                },
                error -> {
                    Log.warn("LOA request " + id + " couldn't be posted", error);
                    try {
                        LoaStore.cancel(id);
                    } catch (SQLException exception) {
                        Log.error("LoaCommand.request cleanup failed", exception);
                    }
                    hook.editOriginal("I couldn't send your request to the review channel. Please tell an admin.")
                            .queue();
                }));
    }

    private void cancel(SlashCommandInteractionEvent event, Guild guild, Member member, LoaStore.Settings settings)
            throws SQLException {
        LoaStore.Request open = LoaStore.findOpen(guild.getId(), member.getId());
        if (open == null) {
            reply(event, "You have no pending request or running leave.");
            return;
        }
        if (open.status.equals(LoaStore.PENDING)) {
            if (!LoaStore.cancel(open.id)) {
                reply(event, "That request was just reviewed. Check your DMs or `/loa cancel` again.");
                return;
            }
            open.status = LoaStore.CANCELLED;
            updateReviewMessage(guild, open, settings, member.getUser(), "Withdrawn by the applicant.");
            reply(event, "✅ Your request was withdrawn.");
            return;
        }
        endLeave(event.getJDA(), open, "You ended your leave early.", true);
        reply(event, "✅ Your leave has ended and the LOA role was removed. Welcome back!");
    }

    private void list(SlashCommandInteractionEvent event, Guild guild, Member member, LoaStore.Settings settings)
            throws SQLException {
        if (!isReviewer(member, settings)) {
            reply(event, "Only reviewers can see this list.");
            return;
        }
        StringBuilder text = new StringBuilder("**On leave**\n");
        List<LoaStore.Request> active = LoaStore.active(guild.getId());
        if (active.isEmpty()) {
            text.append("*nobody*\n");
        }
        for (LoaStore.Request request : active) {
            text.append("• <@").append(request.userId).append("> back <t:").append(request.endsAt / 1000)
                    .append(":R> (request #").append(request.id).append(")\n");
        }
        text.append("\n**Waiting for review**\n");
        List<LoaStore.Request> pending = LoaStore.pending(guild.getId());
        if (pending.isEmpty()) {
            text.append("*nothing*\n");
        }
        for (LoaStore.Request request : pending) {
            text.append("• <@").append(request.userId).append("> for ")
                    .append(LoaText.formatDuration(request.durationMs));
            if (request.channelId != null && request.messageId != null) {
                text.append(" · [review](https://discord.com/channels/").append(guild.getId()).append('/')
                        .append(request.channelId).append('/').append(request.messageId).append(')');
            }
            text.append('\n');
        }
        event.reply(LoaText.truncate(text.toString(), 1900)).setEphemeral(true)
                .setAllowedMentions(List.of()).queue();
    }

    private void endByReviewer(SlashCommandInteractionEvent event, Guild guild, Member member,
            LoaStore.Settings settings) throws SQLException {
        if (!isReviewer(member, settings)) {
            reply(event, "Only reviewers can end someone's leave.");
            return;
        }
        User target = event.getOption("member").getAsUser();
        LoaStore.Request open = LoaStore.findOpen(guild.getId(), target.getId());
        if (open == null || !open.status.equals(LoaStore.APPROVED)) {
            reply(event, "**" + target.getName() + "** isn't on leave.");
            return;
        }
        endLeave(event.getJDA(), open, "A reviewer ended your leave early.", true);
        reply(event, "✅ Ended the leave of **" + target.getName() + "**.");
    }

    private void stats(SlashCommandInteractionEvent event, Guild guild, Member member, LoaStore.Settings settings)
            throws SQLException {
        if (!isReviewer(member, settings)) {
            reply(event, "Only reviewers can see review statistics.");
            return;
        }
        Map<String, int[]> stats = LoaStore.reviewerStats(guild.getId());
        if (event.getOption("reviewer") != null) {
            User reviewer = event.getOption("reviewer").getAsUser();
            int[] counts = stats.getOrDefault(reviewer.getId(), new int[] {0, 0});
            event.reply("**" + reviewer.getName() + "** approved **" + counts[0] + "** and denied **" + counts[1]
                    + "** requests.").setEphemeral(true).queue();
            return;
        }
        if (stats.isEmpty()) {
            reply(event, "Nobody has reviewed a request yet.");
            return;
        }
        StringBuilder text = new StringBuilder("**Review statistics**\n");
        int shown = 0;
        for (Map.Entry<String, int[]> entry : stats.entrySet()) {
            text.append("• <@").append(entry.getKey()).append("> approved ").append(entry.getValue()[0])
                    .append(", denied ").append(entry.getValue()[1]).append('\n');
            if (++shown == 15) {
                break;
            }
        }
        event.reply(text.toString()).setEphemeral(true).setAllowedMentions(List.of()).queue();
    }

    // ---------------------------------------------------------------- review buttons

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        boolean approve = id.startsWith(APPROVE);
        if (!approve && !id.startsWith(DENY)) {
            return;
        }
        Guild guild = event.getGuild();
        Member reviewer = event.getMember();
        if (guild == null || reviewer == null) {
            return;
        }
        try {
            LoaStore.Settings settings = LoaStore.getSettings(guild.getId());
            LoaStore.Request request = LoaStore.getRequest(guild.getId(),
                    Integer.parseInt(id.substring((approve ? APPROVE : DENY).length())));
            if (settings == null || request == null) {
                event.reply("This request no longer exists.").setEphemeral(true).queue();
                return;
            }
            if (!isReviewer(reviewer, settings)) {
                event.reply("Only reviewers can approve or deny leave requests.").setEphemeral(true).queue();
                return;
            }
            if (request.userId.equals(reviewer.getId())) {
                event.reply("You can't review your own request.").setEphemeral(true).queue();
                return;
            }
            if (!request.status.equals(LoaStore.PENDING)) {
                event.reply("Someone already handled this request.").setEphemeral(true).queue();
                return;
            }
            if (approve) {
                approve(event, guild, reviewer, settings, request);
            } else {
                event.replyModal(Modal.create(DENY_MODAL + request.id, "Deny request")
                        .addComponents(Label.of("Reason (optional, sent to the applicant)",
                                TextInput.create("reason", TextInputStyle.PARAGRAPH)
                                        .setRequired(false).setMaxLength(500)
                                        .setPlaceholder("Why the request was denied").build()))
                        .build()).queue();
            }
        } catch (NumberFormatException | SQLException exception) {
            Log.error("LoaCommand.onButtonInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    private void approve(ButtonInteractionEvent event, Guild guild, Member reviewer, LoaStore.Settings settings,
            LoaStore.Request request) {
        Role role = guild.getRoleById(settings.roleId);
        if (role == null) {
            event.reply("The LOA role no longer exists. Choose a new one with `/loa setup`.")
                    .setEphemeral(true).queue();
            return;
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES) || !guild.getSelfMember().canInteract(role)) {
            event.reply("I can't give the " + role.getAsMention() + " role. I need Manage Roles and my role must be "
                    + "above it.").setEphemeral(true).queue();
            return;
        }
        event.deferEdit().queue(hook -> guild.retrieveMemberById(request.userId).queue(
                applicant -> guild.addRoleToMember(applicant, role).queue(
                        ignored -> {
                            long endsAt = System.currentTimeMillis() + request.durationMs;
                            try {
                                if (!LoaStore.approve(request.id, reviewer.getId(), endsAt)) {
                                    guild.removeRoleFromMember(applicant, role).queue(null, error -> { });
                                    hook.sendMessage("Someone already handled this request.").setEphemeral(true)
                                            .queue();
                                    return;
                                }
                            } catch (SQLException exception) {
                                Log.error("LoaCommand.approve failed", exception);
                                guild.removeRoleFromMember(applicant, role).queue(null, error -> { });
                                hook.sendMessage("I couldn't save the approval. Please try again.")
                                        .setEphemeral(true).queue();
                                return;
                            }
                            request.status = LoaStore.APPROVED;
                            request.reviewerId = reviewer.getId();
                            request.endsAt = endsAt;
                            hook.editOriginalEmbeds(buildEmbed(request, settings, guild, applicant.getUser()))
                                    .setContent("Leave approved by " + reviewer.getAsMention() + ".")
                                    .setAllowedMentions(List.of())
                                    .setComponents(List.of()).queue();
                            direct(applicant.getUser(), new EmbedBuilder()
                                    .setColor(APPROVED_COLOUR)
                                    .setTitle("Your leave was approved")
                                    .setDescription("You are on leave until <t:" + endsAt / 1000 + ":F> (<t:"
                                            + endsAt / 1000 + ":R>). The " + role.getName()
                                            + " role was added and will be removed when it ends.")
                                    .setFooter(serverName(guild, settings)));
                        },
                        error -> {
                            Log.warn("LOA role couldn't be given for request " + request.id, error);
                            hook.sendMessage("I couldn't give the LOA role. Check my permissions and role position.")
                                    .setEphemeral(true).queue();
                        }),
                error -> hook.sendMessage("That member is no longer in the server. You can deny the request.")
                        .setEphemeral(true).queue()));
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(DENY_MODAL)) {
            return;
        }
        Guild guild = event.getGuild();
        Member reviewer = event.getMember();
        if (guild == null || reviewer == null) {
            return;
        }
        try {
            LoaStore.Settings settings = LoaStore.getSettings(guild.getId());
            LoaStore.Request request = LoaStore.getRequest(guild.getId(),
                    Integer.parseInt(event.getModalId().substring(DENY_MODAL.length())));
            if (settings == null || request == null || !isReviewer(reviewer, settings)
                    || request.userId.equals(reviewer.getId())) {
                event.reply("You can't review this request.").setEphemeral(true).queue();
                return;
            }
            String reason = blankToNull(event.getValue("reason") == null ? null : event.getValue("reason").getAsString());
            if (!LoaStore.deny(request.id, reviewer.getId(), reason)) {
                event.reply("Someone already handled this request.").setEphemeral(true).queue();
                return;
            }
            request.status = LoaStore.DENIED;
            request.reviewerId = reviewer.getId();
            request.denyReason = reason;
            event.getJDA().retrieveUserById(request.userId).queue(applicant -> {
                event.editMessageEmbeds(buildEmbed(request, settings, guild, applicant))
                        .setContent("Leave denied by " + reviewer.getAsMention() + ".")
                        .setAllowedMentions(List.of())
                        .setComponents(List.of()).queue();
                direct(applicant, new EmbedBuilder()
                        .setColor(DENIED_COLOUR)
                        .setTitle("Your leave request was denied")
                        .setDescription(reason == null ? "No reason was given." : "Reason: " + reason)
                        .setFooter(serverName(guild, settings)));
            }, error -> event.editMessageEmbeds(buildEmbed(request, settings, guild, null))
                    .setComponents(List.of()).queue());
        } catch (NumberFormatException | SQLException exception) {
            Log.error("LoaCommand.onModalInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    // ---------------------------------------------------------------- ending a leave

    /**
     * Ends a running leave: marks it over, takes the role away and tells the applicant. Used when the time
     * runs out, when the member returns early and when a reviewer ends it.
     */
    private void endLeave(JDA jda, LoaStore.Request request, String message, boolean early) {
        try {
            LoaStore.Settings settings = LoaStore.getSettings(request.guildId);
            Guild guild = jda.getGuildById(request.guildId);
            if (guild == null || settings == null) {
                return; // try again on the next sweep instead of ending the leave without removing the role
            }
            if (!LoaStore.markEnded(request.id)) {
                return;
            }
            Role role = settings.roleId == null ? null : guild.getRoleById(settings.roleId);
            if (role != null) {
                guild.removeRoleFromMember(UserSnowflake.fromId(request.userId), role).queue(null,
                        error -> Log.warn("LOA role couldn't be removed from " + request.userId
                                + " (left the server, or missing permission)", error));
            }
            jda.retrieveUserById(request.userId).queue(user -> direct(user, new EmbedBuilder()
                    .setColor(GREY)
                    .setTitle("Welcome back")
                    .setDescription(message + (role == null ? "" : " The " + role.getName() + " role was removed."))
                    .setFooter(serverName(guild, settings))), error -> { });
            GuildMessageChannel channel = settings.channelId == null
                    ? null : guild.getChannelById(GuildMessageChannel.class, settings.channelId);
            if (channel != null && guild.getSelfMember().hasPermission(channel,
                    Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
                channel.sendMessage("<@" + request.userId + "> is back from leave" + (early ? " (ended early)." : "."))
                        .setAllowedMentions(List.of()).queue(null, error -> { });
            }
        } catch (SQLException exception) {
            Log.error("LoaCommand.endLeave failed", exception);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void updateReviewMessage(Guild guild, LoaStore.Request request, LoaStore.Settings settings, User applicant,
            String note) {
        if (request.channelId == null || request.messageId == null) {
            return;
        }
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, request.channelId);
        if (channel != null) {
            channel.editMessageEmbedsById(request.messageId, buildEmbed(request, settings, guild, applicant))
                    .setContent(note).setComponents(List.of()).queue(null, error -> { });
        }
    }

    MessageEmbed buildEmbed(LoaStore.Request request, LoaStore.Settings settings, Guild guild, User applicant) {
        String status;
        Color colour;
        switch (request.status) {
            case LoaStore.APPROVED -> {
                status = request.ended ? "⚪ `Ended`" : "🟢 `Approved` by <@" + request.reviewerId + ">";
                colour = request.ended ? GREY : APPROVED_COLOUR;
            }
            case LoaStore.DENIED -> {
                status = "🔴 `Denied` by <@" + request.reviewerId + ">";
                colour = DENIED_COLOUR;
            }
            case LoaStore.CANCELLED -> {
                status = "⚪ `Withdrawn`";
                colour = GREY;
            }
            default -> {
                status = "🟡 `Pending review`";
                colour = PENDING_COLOUR;
            }
        }
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(colour)
                .setTitle("📑 Leave of Absence request")
                .addField("👤 Applicant", "<@" + request.userId + ">\n"
                        + (applicant == null ? "" : "`" + applicant.getName() + "`\n") + "`" + request.userId + "`", true);
        if (request.ign != null || request.rank != null) {
            embed.addField("🎮 Details", (request.ign == null ? "" : "**IGN:** `" + request.ign + "`\n")
                    + (request.rank == null ? "" : "**Rank:** `" + request.rank + "`"), true);
        }
        String timeline = "**Requested:** " + LoaText.formatDuration(request.durationMs);
        if (request.status.equals(LoaStore.APPROVED)) {
            timeline += "\n**Back:** <t:" + request.endsAt / 1000 + ":D> (<t:" + request.endsAt / 1000 + ":R>)";
        }
        embed.addField("⏳ Timeline", timeline, true)
                .addField("📊 Status", status, true)
                .addField("📝 Reason", "```\n" + LoaText.truncate(request.reason.replace("```", "'''"), 1000)
                        + "\n```", false);
        if (request.denyReason != null) {
            embed.addField("Denial reason", LoaText.truncate(request.denyReason, 500), false);
        }
        return embed.setFooter(serverName(guild, settings) + " • Request #" + request.id)
                .setTimestamp(Instant.ofEpochMilli(request.createdAt)).build();
    }

    private static void direct(User user, EmbedBuilder embed) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessageEmbeds(embed.build()))
                .queue(null, error -> { });
    }

    private static void reply(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
