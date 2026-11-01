package logging;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/** Dyno-style server events, posted to the log channel when their category is on. */
public class ServerLogListener extends ListenerAdapter {

    private static String who(User user) {
        return user.getAsMention() + " (" + user.getName() + ", `" + user.getId() + "`)";
    }

    // ---- members ------------------------------------------------------------------------

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        if (!LogService.wants(guild, LogCategory.MEMBERS)) {
            return;
        }
        User user = event.getUser();
        LogService.send(guild, LogCategory.MEMBERS, LogService.embed("Member joined", LogService.GREEN)
                .setThumbnail(user.getEffectiveAvatarUrl())
                .setDescription(who(user))
                .addField("Account created", "<t:" + user.getTimeCreated().toEpochSecond() + ":R>", true)
                .addField("Members now", String.valueOf(guild.getMemberCount()), true));
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Guild guild = event.getGuild();
        if (!LogService.wants(guild, LogCategory.MEMBERS)) {
            return;
        }
        User user = event.getUser();
        EmbedBuilder embed = LogService.embed("Member left", LogService.RED)
                .setThumbnail(user.getEffectiveAvatarUrl())
                .setDescription(who(user));
        if (event.getMember() != null && event.getMember().hasTimeJoined()) {
            embed.addField("Joined", "<t:" + event.getMember().getTimeJoined().toEpochSecond() + ":R>", true);
        }
        LogService.send(guild, LogCategory.MEMBERS, embed);
    }

    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        Guild guild = event.getGuild();
        if (!LogService.wants(guild, LogCategory.MEMBERS)) {
            return;
        }
        String before = event.getOldNickname() == null ? "*none*" : event.getOldNickname();
        String after = event.getNewNickname() == null ? "*none*" : event.getNewNickname();
        LogService.send(guild, LogCategory.MEMBERS, LogService.embed("Nickname changed", LogService.BLUE)
                .setDescription(who(event.getUser()))
                .addField("Before", before, true)
                .addField("After", after, true));
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        roleChange(event.getGuild(), event.getUser(), event.getRoles().stream()
                .map(role -> role.getAsMention()).collect(Collectors.joining(" ")), "Roles added", LogService.GREEN);
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        roleChange(event.getGuild(), event.getUser(), event.getRoles().stream()
                .map(role -> role.getAsMention()).collect(Collectors.joining(" ")), "Roles removed", LogService.RED);
    }

    private void roleChange(Guild guild, User user, String roles, String title, java.awt.Color colour) {
        if (!LogService.wants(guild, LogCategory.MEMBERS)) {
            return;
        }
        LogService.send(guild, LogCategory.MEMBERS, LogService.embed(title, colour)
                .setDescription(who(user) + "\n" + roles));
    }

    // ---- moderation ---------------------------------------------------------------------

    @Override
    public void onGuildBan(GuildBanEvent event) {
        Guild guild = event.getGuild();
        if (!LogService.wants(guild, LogCategory.MODERATION)) {
            return;
        }
        User target = event.getUser();
        OffsetDateTime started = OffsetDateTime.now();
        // The moderator and reason are only in the audit log (needs View Audit Log); without it we still log the ban.
        guild.retrieveAuditLogs().type(ActionType.BAN).limit(10).queue(entries -> {
            var match = entries.stream()
                    .filter(entry -> entry.getTargetId().equals(target.getId()))
                    .filter(entry -> entry.getTimeCreated().isAfter(started.minusSeconds(30)))
                    .findFirst();
            EmbedBuilder embed = LogService.embed("Member banned", LogService.RED).setDescription(who(target));
            match.ifPresent(entry -> {
                if (entry.getUser() != null) {
                    embed.addField("Moderator", who(entry.getUser()), false);
                }
                embed.addField("Reason", entry.getReason() == null ? "No reason provided" : entry.getReason(), false);
            });
            LogService.send(guild, LogCategory.MODERATION, embed);
        }, error -> LogService.send(guild, LogCategory.MODERATION,
                LogService.embed("Member banned", LogService.RED).setDescription(who(target))));
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        LogService.send(event.getGuild(), LogCategory.MODERATION,
                LogService.embed("Member unbanned", LogService.GREEN).setDescription(who(event.getUser())));
    }

    // ---- server -------------------------------------------------------------------------

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        LogService.send(event.getGuild(), LogCategory.SERVER, LogService.embed("Channel created", LogService.GREEN)
                .setDescription(event.getChannel().getAsMention() + " (`" + event.getChannel().getName() + "`)")
                .addField("Type", event.getChannelType().name().toLowerCase().replace('_', ' '), true));
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        LogService.send(event.getGuild(), LogCategory.SERVER, LogService.embed("Channel deleted", LogService.RED)
                .setDescription("`" + event.getChannel().getName() + "`")
                .addField("Type", event.getChannelType().name().toLowerCase().replace('_', ' '), true));
    }

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        LogService.send(event.getGuild(), LogCategory.SERVER, LogService.embed("Role created", LogService.GREEN)
                .setDescription(event.getRole().getAsMention() + " (`" + event.getRole().getName() + "`)"));
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        LogService.send(event.getGuild(), LogCategory.SERVER, LogService.embed("Role deleted", LogService.RED)
                .setDescription("`" + event.getRole().getName() + "`"));
    }

    // ---- voice --------------------------------------------------------------------------

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();
        if (!LogService.wants(guild, LogCategory.VOICE)) {
            return;
        }
        AudioChannelUnion left = event.getChannelLeft();
        AudioChannelUnion joined = event.getChannelJoined();
        User user = event.getMember().getUser();
        if (left == null && joined != null) {
            LogService.send(guild, LogCategory.VOICE, LogService.embed("Joined voice", LogService.GREEN)
                    .setDescription(who(user) + "\n" + joined.getAsMention()));
        } else if (left != null && joined == null) {
            LogService.send(guild, LogCategory.VOICE, LogService.embed("Left voice", LogService.RED)
                    .setDescription(who(user) + "\n" + left.getAsMention()));
        } else if (left != null) {
            LogService.send(guild, LogCategory.VOICE, LogService.embed("Moved voice channel", LogService.BLUE)
                    .setDescription(who(user) + "\n" + left.getAsMention() + " → " + joined.getAsMention()));
        }
    }
}
