package commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;



public class Avatar extends ListenerAdapter implements Command{

@Override
    public CommandData getCommandData() {
        return Commands.slash("avatar", "Shows the avatar of a user!")
                .addOption(
                        OptionType.USER,
                        "user",
                        "The user to get avatar of",
                        false
                );
}
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("avatar")) {
            Member member = event.getOption("user", event.getMember(), option -> option.getAsMember());
            if (member == null) {
                event.reply("I couldn't resolve that user in this server.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("🖼️ User Avatar of " + member.getUser().getName());

                String avatar = member.getUser().getEffectiveAvatarUrl();
                System.out.print(avatar);

                if (avatar != null) {
                    if (avatar.contains("/a_")) {
                        if (avatar.contains(".webp")) {
                            avatar = avatar.replace(".webp", ".gif");
                        }
                        if (avatar.contains("?")) {
                            avatar = avatar.replaceAll("\\?size=\\d+", "?size=1024");
                        } else {
                            avatar += "?size=1024";
                        }
                    } else {
                        if (avatar.contains("?")) {
                            avatar = avatar.replaceAll("\\?size=\\d+", "?size=1024");
                        } else {
                            avatar += "?size=1024";}}}

                Button button = Button.link(avatar, "Open Avatar");

                if (avatar != null) {
                    embed.setImage(avatar);
                }
                event.replyEmbeds(embed.build()).addComponents(ActionRow.of(button))
                .queue();
            };
        }
    }

