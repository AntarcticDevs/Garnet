package events;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import utils.ConsoleLogger;

public class ConsoleActivityLogger extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        Guild guild = event.getGuild();
        String command = "/" + event.getName();
        if (event.getSubcommandName() != null) {
            command += " " + event.getSubcommandName();
        }

        ConsoleLogger.logConsole("COMMAND " + command
                + " user=\"" + user.getName() + "\" userId=" + user.getId()
                + guildDetails(guild));
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User user = event.getUser();
        Guild guild = event.getGuild();
        ConsoleLogger.logConsole("MEMBER_JOIN user=\"" + user.getName() + "\" userId=" + user.getId()
                + guildDetails(guild));
    }

    private String guildDetails(Guild guild) {
        return guild == null ? " guild=DM" : " guild=\"" + guild.getName() + "\" guildId=" + guild.getId();
    }
}
