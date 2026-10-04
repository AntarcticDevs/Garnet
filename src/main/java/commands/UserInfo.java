package commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.time.OffsetDateTime;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;



public class UserInfo extends ListenerAdapter implements Command{

@Override
    public CommandData getCommandData() {
        return Commands.slash("userinfo", "Shows information of a user!")
                .addOption(
                        OptionType.USER,
                        "user",
                        "The user to get information about",
                        false
                );
}

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("userinfo")) {

            Member member = event.getOption("user", event.getMember(), option -> option.getAsMember());
            if (member == null) {
                event.reply("I couldn't resolve that user in this server.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder();

            OffsetDateTime joined = member.getTimeJoined();
            OffsetDateTime created = member.getUser().getTimeCreated();

            embed.setTitle("User Information - " + member.getUser().getName());
            embed.addField("Username", member.getUser().getName(), false);
            embed.addField("Display Name", member.getUser().getEffectiveName(), false);
            embed.addField("ID", member.getUser().getId(), false);
            embed.addField("Account Created", "<t:" + created.toEpochSecond() + ":F>", false);
            embed.addField("Joined Server", "<t:" + joined.toEpochSecond() + ":F>", false);

            String rolesText = "";

            for (Role role : member.getRoles()) {
                rolesText += role.getAsMention() + " ";
            }

            embed.addField("Roles", rolesText, false);

            member.getUser().retrieveProfile().queue(profile -> {

                String avatar = member.getUser().getEffectiveAvatarUrl();
                String banner = profile.getBannerUrl();

                if (banner != null && banner.contains("/a_")) {
                    banner = banner.replace(".webp?animated=true", ".gif?size=512");
                }


                if (avatar != null) {
                    embed.setThumbnail(avatar);
                }

                if (banner != null) {
                    embed.setImage(banner);
                }

                event.replyEmbeds(embed.build()).queue();
            });
        }
    }
}
