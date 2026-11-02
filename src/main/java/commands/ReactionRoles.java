package commands;

import database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.entities.Message;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import utils.Log;
import utils.ReactionEmojiParser;

public class ReactionRoles extends ListenerAdapter implements Command {
    private static final Pattern MESSAGE_ID = Pattern.compile("\\d{17,20}");
    private static final String BUTTON_PREFIX = "reaction-role:";
    private static final String STYLE_REACTION = "reaction";
    private static final String STYLE_BUTTON = "button";

    @Override
    public CommandData getCommandData() {
        OptionData style = new OptionData(OptionType.STRING, "style", "How members get the role", true)
                .addChoice("Emoji reaction", STYLE_REACTION)
                .addChoice("Button", STYLE_BUTTON);
        OptionData label = new OptionData(
                OptionType.STRING, "label", "Button label (button style only; defaults to role name)", false)
                .setMaxLength(80);
        return Commands.slash("reaction-roles", "Set up a reaction or button role")
                .addOptions(style)
                .addOption(OptionType.ROLE, "role", "The role given to members", true)
                .addOption(OptionType.STRING, "message-id", "The ID of the message in this channel", true)
                .addOption(OptionType.STRING, "emoji", "Reaction emoji, or optional button icon", false)
                .addOptions(label);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("reaction-roles")) {
            return;
        }

        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission to configure reaction roles.")
                    .setEphemeral(true).queue();
            return;
        }
        if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("I need the Manage Roles permission to assign reaction roles.")
                    .setEphemeral(true).queue();
            return;
        }

        Role role = event.getOption("role").getAsRole();
        if (role.isManaged() || !event.getGuild().getSelfMember().canInteract(role)) {
            event.reply("I can't assign that role. Make sure it isn't managed and is below my highest role.")
                    .setEphemeral(true).queue();
            return;
        }

        String style = event.getOption("style").getAsString();
        String emojiInput = event.getOption("emoji") == null
                ? null
                : event.getOption("emoji").getAsString();
        Optional<Emoji> emoji = emojiInput == null
                ? Optional.empty()
                : ReactionEmojiParser.parse(emojiInput);
        if (emojiInput != null && emoji.isEmpty()) {
            event.reply("That isn't a valid Unicode or server emoji.").setEphemeral(true).queue();
            return;
        }
        if (STYLE_REACTION.equals(style) && emoji.isEmpty()) {
            event.reply("Choose an emoji for emoji-reaction style.").setEphemeral(true).queue();
            return;
        }
        if (!STYLE_REACTION.equals(style) && !STYLE_BUTTON.equals(style)) {
            event.reply("Choose either emoji reaction or button style.").setEphemeral(true).queue();
            return;
        }

        String messageId = event.getOption("message-id").getAsString().strip();
        if (!MESSAGE_ID.matcher(messageId).matches()) {
            event.reply("Enter a valid Discord message ID.").setEphemeral(true).queue();
            return;
        }
        if (!(event.getChannel() instanceof MessageChannel channel)) {
            event.reply("Run this command in the channel containing the target message.")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        channel.retrieveMessageById(messageId).queue(message -> {
            if (!message.getGuild().getId().equals(event.getGuild().getId())) {
                event.getHook().editOriginal("That message is not in this server.").queue();
                return;
            }
            if (STYLE_REACTION.equals(style)) {
                configureReaction(event, message, messageId, role, emoji.orElseThrow());
            } else {
                String label = event.getOption("label") == null
                        ? role.getName().substring(0, Math.min(role.getName().length(), 80))
                        : event.getOption("label").getAsString();
                configureButton(event, message, messageId, role, emoji, label);
            }
        }, exception -> {
            Log.warn("Couldn't retrieve a reaction-role target message", exception);
            event.getHook().editOriginal("I couldn't find that message in this channel. "
                    + "Check the message ID and my channel permissions.").queue();
        });
    }

    private static void configureReaction(SlashCommandInteractionEvent event, Message message, String messageId,
            Role role, Emoji emoji) {
        message.addReaction(emoji).queue(ignored -> {
            try {
                Database.saveReactionRole(event.getGuild().getId(), messageId,
                        emoji.getAsReactionCode(), role.getId());
                event.getHook().editOriginal("Reaction role saved. React to that message with "
                        + emoji.getFormatted() + " to get " + role.getAsMention() + ".").queue();
            } catch (SQLException exception) {
                Log.error("ReactionRoles.configureReaction failed to save mapping", exception);
                message.removeReaction(emoji).queue(
                        ignoredRemoval -> { },
                        cleanupException -> Log.warn("Couldn't remove the setup reaction after a database error",
                                cleanupException));
                event.getHook().editOriginal("The reaction was added, but I couldn't save the role mapping. "
                        + "Please try again.").queue();
            }
        }, exception -> {
            Log.warn("Couldn't add the reaction for a reaction-role setup", exception);
            event.getHook().editOriginal("I couldn't add that reaction. Check my permissions and the emoji.")
                    .queue();
        });
    }

    private static void configureButton(SlashCommandInteractionEvent event, Message message, String messageId,
            Role role, Optional<Emoji> emoji, String label) {
        String buttonLabel = label.strip();
        if (buttonLabel.isEmpty()) {
            event.getHook().editOriginal("Button labels can't be empty.").queue();
            return;
        }
        if (buttonLabel.length() > 80) {
            event.getHook().editOriginal("Button labels can be at most 80 characters.").queue();
            return;
        }
        if (!message.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
            event.getHook().editOriginal("I can only add buttons to messages sent by this bot.")
                    .queue();
            return;
        }
        if (message.isUsingComponentsV2()) {
            event.getHook().editOriginal("I can't add a button to a message using the newer component layout.")
                    .queue();
            return;
        }
        String buttonId = BUTTON_PREFIX + UUID.randomUUID();
        Button button = Button.primary(buttonId, buttonLabel);
        if (emoji.isPresent()) {
            button = button.withEmoji(emoji.get());
        }
        List<MessageTopLevelComponent> components = new ArrayList<>(message.getComponents());
        boolean addedToExistingRow = false;
        if (!message.getComponents().isEmpty()) {
            int lastIndex = components.size() - 1;
            var lastComponent = message.getComponents().get(lastIndex);
            if (lastComponent.getType() == Component.Type.ACTION_ROW) {
                ActionRow row = lastComponent.asActionRow();
                List<ActionRowChildComponent> children = new ArrayList<>(row.getComponents());
                if (children.size() < 5
                        && children.stream().allMatch(child -> child.getType() == Component.Type.BUTTON)) {
                    children.add(button);
                    components.set(lastIndex, row.withComponents(children));
                    addedToExistingRow = true;
                }
            }
        }
        if (!addedToExistingRow) {
            if (components.size() >= 5) {
                event.getHook().editOriginal("That message has no room for another button.")
                        .queue();
                return;
            }
            components.add(ActionRow.of(button));
        }

        try {
            Database.saveReactionRoleButton(event.getGuild().getId(), messageId, buttonId, role.getId());
        } catch (SQLException exception) {
            Log.error("ReactionRoles.configureButton failed to save mapping", exception);
            event.getHook().editOriginal("Couldn't save this button role. Please try again.")
                    .queue();
            return;
        }

        message.editMessageComponents(components).queue(
                ignored -> event.getHook().editOriginal("Button role saved. Members can click **" + buttonLabel
                        + "** to toggle " + role.getAsMention() + ".").queue(),
                exception -> {
                    Log.warn("Couldn't add a reaction-role button to the message", exception);
                    try {
                        Database.deleteReactionRoleButton(event.getGuild().getId(), messageId, buttonId);
                    } catch (SQLException databaseException) {
                        Log.error("ReactionRoles.configureButton failed to remove an unpublished mapping",
                                databaseException);
                    }
                    event.getHook().editOriginal("I couldn't add the button. Check my message permissions.")
                            .queue();
                });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith(BUTTON_PREFIX)) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("This button only works in a server.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        try {
            String roleId = Database.getReactionRoleButton(event.getGuild().getId(),
                    event.getMessageId(), buttonId);
            if (roleId == null) {
                event.getHook().editOriginal("This role button is no longer configured.").queue();
                return;
            }
            Role role = event.getGuild().getRoleById(roleId);
            if (role == null) {
                Log.warn("Reaction-role button references missing role " + roleId
                        + " in guild " + event.getGuild().getId());
                event.getHook().editOriginal("That role no longer exists. Please contact a server moderator.")
                        .queue();
                return;
            }
            boolean remove = event.getMember().getRoles().contains(role);
            UserSnowflake user = UserSnowflake.fromId(event.getUser().getId());
            (remove ? event.getGuild().removeRoleFromMember(user, role)
                    : event.getGuild().addRoleToMember(user, role))
                    .queue(ignored -> event.getHook().editOriginal(
                                    remove ? "Removed " + role.getAsMention() + "."
                                            : "Added " + role.getAsMention() + ".")
                                    .queue(),
                            exception -> {
                                Log.warn("Couldn't " + (remove ? "remove" : "assign") + " reaction role "
                                        + roleId + " for member " + event.getUser().getId(), exception);
                                event.getHook().editOriginal("I couldn't update that role. "
                                        + "Please contact a server moderator.").queue();
                            });
        } catch (SQLException exception) {
            Log.error("ReactionRoles.onButtonInteraction failed to load mapping", exception);
            event.getHook().editOriginal("Couldn't load this role button. Please try again later.")
                    .queue();
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        var guild = event.getGuild();
        if (guild != null) {
            updateRole(guild, event.getMessageId(), event.getEmoji().getAsReactionCode(),
                    event.getUserId(), true);
        }
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        var guild = event.getGuild();
        if (guild != null) {
            updateRole(guild, event.getMessageId(), event.getEmoji().getAsReactionCode(),
                    event.getUserId(), false);
        }
    }

    private static void updateRole(net.dv8tion.jda.api.entities.Guild guild, String messageId, String emoji,
            String userId, boolean add) {
        if (guild == null || guild.getSelfMember().getId().equals(userId)) {
            return;
        }
        String guildId = guild.getId();

        try {
            String roleId = Database.getReactionRole(guildId, messageId, emoji);
            if (roleId == null) {
                return;
            }
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                Log.warn("Reaction role " + roleId + " no longer exists in guild " + guildId);
                return;
            }
            UserSnowflake user = UserSnowflake.fromId(userId);
            (add ? guild.addRoleToMember(user, role) : guild.removeRoleFromMember(user, role))
                    .queue(ignored -> { },
                            exception -> Log.warn("Couldn't " + (add ? "assign" : "remove") + " reaction role "
                                    + roleId + " for member " + userId + " in guild " + guildId, exception));
        } catch (SQLException exception) {
            Log.error("ReactionRoles.updateRole failed to load mapping", exception);
        }
    }
}
