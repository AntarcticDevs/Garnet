package commands;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;



public class Info extends ListenerAdapter implements Command{

    @Override
    public CommandData getCommandData() {
        return Commands.slash("info", "Shows server information");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        if (!event.getName().equals("info")) {
            return;
        }

        Guild guild = event.getGuild();

        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(guild.getName() + " Information");
            embed.setDescription(String.valueOf(guild.getDescription()));

        Member owner = guild.getOwner();

        if (owner != null) {
            embed.addField("\n👑 Owner", owner.getAsMention(), true);
        }

        int roles = guild.getRoles().size();
        String totalRoles = String.valueOf(roles);

        embed.addField("💎 Roles", totalRoles, true);

        event.replyEmbeds(embed.build()).queue();
    }
}