package commands;

import config.ConfigManager;
import modules.BotModule;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
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

import java.util.ArrayList;
import java.util.List;

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

        String botName = ConfigManager.getBotName();
        String thumbnailUrl = ConfigManager.getString("help-thumbnail-url");
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            thumbnailUrl = event.getJDA().getSelfUser().getEffectiveAvatarUrl();
        }

        TextDisplay heading = TextDisplay.of(
                "# " + botName + " Help\n" + "**Welcome to " + botName + "! Here are the available commands.**");
        ContainerChildComponent headingComponent = thumbnailUrl == null || thumbnailUrl.isBlank()
                ? heading
                : Section.of(Thumbnail.fromUrl(thumbnailUrl), heading);

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
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(headingComponent);
        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        // Only list commands that are actually registered (see modules: in config.yml).
        if (BotModule.INFORMATION.isEnabled()) {
            children.add(information);
        }
        if (BotModule.MODERATION.isEnabled()) {
            children.add(moderation);
        }
        children.add(fun);
        if (BotModule.EMBEDS.isEnabled()) {
            children.add(utility);
        }

        List<Button> linkButtons = new ArrayList<>();
        addLinkButton(linkButtons, "support-url", "Support Server");
        addLinkButton(linkButtons, "terms-url", "Terms of Service");
        addLinkButton(linkButtons, "privacy-url", "Privacy");
        addLinkButton(linkButtons, "security-url", "Security");
        if (!linkButtons.isEmpty()) {
            children.add(Separator.createDivider(Separator.Spacing.LARGE));
            children.add(ActionRow.of(linkButtons));
        }

        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        children.add(TextDisplay.of("-# © 2026 " + botName + " • All rights reserved."));
        Container container = Container.of(children);

        MessageCreateBuilder message = new MessageCreateBuilder()
                .useComponentsV2()
                .addComponents(container);

        event.reply(message.build()).queue();
    }

    private void addLinkButton(List<Button> buttons, String configKey, String label) {
        String url = ConfigManager.getString(configKey);
        if (url != null && !url.isBlank()) {
            buttons.add(Button.link(url, label));
        }
    }
}
