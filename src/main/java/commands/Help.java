package commands;

import config.ConfigManager;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;

import net.dv8tion.jda.api.components.thumbnail.Thumbnail;


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

        Thumbnail thumbnail = Thumbnail.fromUrl("https://cdn.discordapp.com/attachments/1543925274213752855/1555982521009967275/Garnet.png?backend=b2&ex=6ac28125&is=6ac12fa5&hm=c7db4d95dd5ea235683c449ed883de64513ffa92466db4c99c3673af61f22056&");

        String botName = ConfigManager.getBotName();
        Section section1 = Section.of(thumbnail, TextDisplay.of("# " + botName + " Help\n" + "**Welcome to " + botName + "! Here are the available commands.**"));

        TextDisplay information = TextDisplay.of(
                "### ℹ️ Information\n" +
                "`/ping` — Shows the bot's latency.\n" +
                "`/info` — Shows server information.\n" +
                "`/userinfo` — Shows information about a user.\n" +
                "`/avatar` — Shows a user's avatar."
        );

        TextDisplay moderation = TextDisplay.of(
                "### 🛡️ Moderation\n" +
                "`/purge amount` — Delete up to 100 recent messages (Manage Messages).\n" +
                "`/kick user [reason]` — Kick a member (Kick Members).\n" +
                "`/ban user [reason]` — Ban a user (Ban Members)."
        );
        TextDisplay fun = TextDisplay.of("### 🎉 Fun\n" + "Coming soon...");
        TextDisplay utility = TextDisplay.of("### ⚙️ Utility\n" + "`/embed` — Create, edit, and send embeds.\n" + "`/rules select` — Select a saved embed as this server's rules.");
        Button button1 = Button.link("https://dsc.gg/garnetbot", "Support Server");
        Button button2 = Button.link("https://github.com/GarnetDiscordBot/Garnet/blob/main/TERMS.md", "Terms of Service");
        Button button3 = Button.link("https://github.com/GarnetDiscordBot/Garnet/blob/main/PRIVACY.md", "Privacy");
        Button button4 = Button.link("https://github.com/GarnetDiscordBot/Garnet/blob/main/SECURITY.md", "Security");

        Container container = Container.of(
                section1,
                Separator.createDivider(Separator.Spacing.LARGE),
                information,
                moderation,
                fun,
                utility,
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(
                        button1,
                        button2,
                        button3,
                        button4
                ),

                Separator.createDivider(Separator.Spacing.LARGE),
                TextDisplay.of("-# © 2026 " + botName + " • All rights reserved.")
        );

        MessageCreateBuilder message = new MessageCreateBuilder()
                .useComponentsV2()
                .addComponents(container);

        event.reply(message.build()).queue();
    }
}
