package rolepanels;

import commands.Command;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import utils.Log;

import java.sql.SQLException;
import java.util.List;

public class RolePanelCommand extends ListenerAdapter implements Command {
    private final RolePanelEditor editor;

    public RolePanelCommand(RolePanelEditor editor) {
        this.editor = editor;
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("rolepanel", "Create role panels where members pick their own roles")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("create", "Create a new role panel and open its editor")
                                .addOptions(new OptionData(OptionType.STRING, "name",
                                        "A name for the panel (only you and other admins see it)", true)
                                        .setMaxLength(50)),
                        new SubcommandData("edit", "Open the editor of a role panel")
                                .addOptions(new OptionData(OptionType.STRING, "name", "The panel to edit", true)
                                        .setAutoComplete(true)),
                        new SubcommandData("list", "Show every role panel in this server"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("rolepanel")) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission to manage role panels.").setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        try {
            switch (String.valueOf(event.getSubcommandName())) {
                case "create" -> {
                    String name = event.getOption("name").getAsString().trim();
                    if (name.isEmpty()) {
                        event.reply("Give the panel a name.").setEphemeral(true).queue();
                        return;
                    }
                    if (RolePanelStore.getByName(guildId, name) != null) {
                        event.reply("A role panel called **" + name + "** already exists. "
                                + "Use `/rolepanel edit` to change it.").setEphemeral(true).queue();
                        return;
                    }
                    RolePanelData panel = new RolePanelData();
                    panel.guildId = guildId;
                    panel.name = name;
                    panel.title = name;
                    RolePanelStore.save(panel);
                    editor.open(event, RolePanelStore.getById(guildId, panel.id));
                }
                case "edit" -> {
                    RolePanelData panel = RolePanelStore.getByName(guildId, event.getOption("name").getAsString());
                    if (panel == null) {
                        event.reply("I couldn't find that role panel. See `/rolepanel list`.")
                                .setEphemeral(true).queue();
                        return;
                    }
                    editor.open(event, panel);
                }
                case "list" -> list(event, RolePanelStore.list(guildId));
                default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
            }
        } catch (SQLException exception) {
            Log.error("RolePanelCommand failed", exception);
            event.reply("Something went wrong with the database. Please try again.").setEphemeral(true).queue();
        }
    }

    private static void list(SlashCommandInteractionEvent event, List<RolePanelData> panels) {
        if (panels.isEmpty()) {
            event.reply("There are no role panels yet. Create one with `/rolepanel create`.")
                    .setEphemeral(true).queue();
            return;
        }
        StringBuilder text = new StringBuilder("**Role panels**\n");
        for (RolePanelData panel : panels) {
            text.append("• **").append(panel.name).append("** · ").append(panel.style.label)
                    .append(" · ").append(panel.mode.label).append(" · ").append(panel.roles.size())
                    .append(panel.roles.size() == 1 ? " role" : " roles")
                    .append(panel.disabled ? " · 🔒 disabled" : "");
            if (panel.channelId != null && panel.messageId != null) {
                text.append(" · [posted](https://discord.com/channels/").append(panel.guildId).append('/')
                        .append(panel.channelId).append('/').append(panel.messageId).append(')');
            } else {
                text.append(" · not posted");
            }
            text.append('\n');
            if (text.length() > 1800) {
                text.append("…");
                break;
            }
        }
        event.reply(text.toString()).setEphemeral(true).queue();
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("rolepanel") || event.getGuild() == null
                || !event.getFocusedOption().getName().equals("name")) {
            return;
        }
        try {
            List<Choice> choices = RolePanelStore.names(event.getGuild().getId(),
                    event.getFocusedOption().getValue(), 25).stream()
                    .map(name -> new Choice(name, name))
                    .toList();
            event.replyChoices(choices).queue();
        } catch (SQLException exception) {
            Log.error("RolePanelCommand autocomplete failed", exception);
        }
    }
}
