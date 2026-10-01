package commands;

import java.awt.Color;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class Help extends ListenerAdapter implements Command {

    @Override
    public CommandData getCommandData() {
        return Commands.slash("help", "Shows information about the bot and commands");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("help")) {
            return;
        }

        String nickname = event.getGuild() == null
            ? event.getJDA().getSelfUser().getName()
            : event.getGuild().getSelfMember().getEffectiveName();

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("📖 " + nickname + " Help")
            .setDescription("Welcome to " + nickname + "! Here are the available commands.")
            .setColor(Color.decode("#b9292d"))
            .addField("ℹ️ Information Commands",
                "`/ping` - Shows the bot's latency.\n"
                    + "`/info` - Shows server information.\n"
                    + "`/userinfo` - Shows information about a user.\n"
                    + "`/avatar` - Shows a user's avatar.\n",
                false)
            .addField("🛡️ Moderation commands", "Coming soon...\n", false)
            .addField("🎉 Fun", "Coming soon...\n", false)
            .addField("⚙️ Utility",
                "`/embed` - Create, edit, and send embeds.\n"
                    + "`/rules select` - Select a saved embed as this server's rules (Manage Server required).\n",
                false);
            embed.setFooter("Garnet", "https://cdn.discordapp.com/attachments/1316742573879984202/1553386560655855757/Garnet.png?ex=6ab90f78&is=6ab7bdf8&hm=e947e4f2e48f89fc5873e3d6670ec0415887703dfef1967a542bb73d4c9eb9bd&");

        event.replyEmbeds(embed.build())
            .addComponents(ActionRow.of(
                Button.link("https://github.com/EnderKnight7op", "💻 GitHub"),
                Button.link("https://dsc.gg/garnetbot", "🤝 Discord Server")))
            .queue();
    }
}

