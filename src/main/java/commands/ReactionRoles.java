package commands;

import database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

import utils.Log;
import utils.ReactionEmojiParser;

public class ReactionRoles extends ListenerAdapter implements Command {
    private static final Pattern MESSAGE_ID = Pattern.compile("\\d{17,20}");

    @Override
    public CommandData getCommandData() {
        return Commands.slash("reaction-roles", "Set a role to be given for a message reaction")
                .addOption(OptionType.STRING, "emoji", "The emoji to react with", true)
                .addOption(OptionType.ROLE, "role", "The role given to members who react", true)
                .addOption(OptionType.STRING, "message-id", "The ID of the message in this channel", true);
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

        Optional<Emoji> emoji = ReactionEmojiParser.parse(event.getOption("emoji").getAsString());
        if (emoji.isEmpty()) {
            event.reply("That isn't a valid Unicode or server emoji.").setEphemeral(true).queue();
            return;
        }

        Role role = event.getOption("role").getAsRole();
        if (role.isManaged() || !event.getGuild().getSelfMember().canInteract(role)) {
            event.reply("I can't assign that role. Make sure it isn't managed and is below my highest role.")
                    .setEphemeral(true).queue();
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
            message.addReaction(emoji.get()).queue(ignored -> {
                try {
                    Database.saveReactionRole(event.getGuild().getId(), messageId,
                            emoji.get().getAsReactionCode(), role.getId());
                    event.getHook().editOriginal("Reaction role saved. React to that message with "
                            + emoji.get().getFormatted() + " to get " + role.getAsMention() + ".").queue();
                } catch (SQLException exception) {
                    Log.error("ReactionRoles.onSlashCommandInteraction failed to save mapping", exception);
                    message.removeReaction(emoji.get()).queue(
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
        }, exception -> {
            Log.warn("Couldn't retrieve a reaction-role target message", exception);
            event.getHook().editOriginal("I couldn't find that message in this channel. "
                    + "Check the message ID and my channel permissions.").queue();
        });
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
