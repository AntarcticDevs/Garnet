package rolepanels;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import utils.Log;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Handles clicks, dropdown choices and reactions on posted role panels. */
public class RolePanelListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("rp:b:")) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("This only works in a server.").setEphemeral(true).queue();
            return;
        }
        String[] parts = id.split(":");
        try {
            RolePanelData panel = parts.length == 4
                    ? RolePanelStore.getById(event.getGuild().getId(), Integer.parseInt(parts[2]))
                    : null;
            RolePanelData.PanelRole role = panel == null ? null : panel.roleById(Integer.parseInt(parts[3]));
            if (role == null) {
                event.reply("This role button isn't set up any more.").setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue();
            RolePanelService.pick(event.getGuild(), event.getMember(), panel, List.of(role),
                    message -> event.getHook().editOriginal(message).queue());
        } catch (NumberFormatException | SQLException exception) {
            Log.error("RolePanelListener.onButtonInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("rp:s:")) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("This only works in a server.").setEphemeral(true).queue();
            return;
        }
        try {
            RolePanelData panel = RolePanelStore.getById(event.getGuild().getId(),
                    Integer.parseInt(id.substring("rp:s:".length())));
            if (panel == null) {
                event.reply("This role menu isn't set up any more.").setEphemeral(true).queue();
                return;
            }
            List<RolePanelData.PanelRole> picked = new ArrayList<>();
            for (String value : event.getValues()) {
                RolePanelData.PanelRole role = panel.roleById(Integer.parseInt(value));
                if (role != null) {
                    picked.add(role);
                }
            }
            if (picked.isEmpty()) {
                event.reply("Those roles aren't available any more.").setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue();
            // Put the menu back to "nothing chosen", so the same role can be picked again straight away.
            event.getMessage().editMessageComponents(RolePanelRenderer.buildComponents(panel, event.getGuild()))
                    .queue(null, error -> { });
            RolePanelService.pick(event.getGuild(), event.getMember(), panel, picked,
                    message -> event.getHook().editOriginal(message).queue());
        } catch (NumberFormatException | SQLException exception) {
            Log.error("RolePanelListener.onStringSelectInteraction failed", exception);
            event.reply("Something went wrong. Please try again.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        handleReaction(event, true);
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        handleReaction(event, false);
    }

    private void handleReaction(GenericMessageReactionEvent event, boolean added) {
        // Reactions in DMs have no guild, and the bot's own setup reactions must not count.
        if (!event.isFromGuild() || event.getUserId().equals(event.getJDA().getSelfUser().getId())
                || (event.getUser() != null && event.getUser().isBot())) {
            return;
        }
        RolePanelData panel;
        RolePanelData.PanelRole entry = null;
        try {
            panel = RolePanelStore.getByMessage(event.getGuild().getId(), event.getMessageId());
        } catch (SQLException exception) {
            Log.error("RolePanelListener.handleReaction failed", exception);
            return;
        }
        if (panel == null || panel.style != RolePanelData.Style.REACTIONS) {
            return;
        }
        String code = event.getEmoji().getAsReactionCode();
        for (RolePanelData.PanelRole candidate : panel.roles) {
            if (RolePanelRenderer.emojiFor(candidate).map(Emoji::getAsReactionCode).orElse("").equals(code)) {
                entry = candidate;
            }
        }
        if (entry == null) {
            return;
        }

        RolePanelData.PanelRole chosen = entry;
        event.retrieveMember().queue(member -> RolePanelService.react(event.getGuild(), member, panel, chosen, added,
                (keep, removedRoles) -> {
                    User user = member.getUser();
                    if (!added) {
                        return;
                    }
                    if (!keep) {
                        event.getGuildChannel().removeReactionById(event.getMessageId(), event.getEmoji(), user)
                                .queue(null, error -> { });
                    }
                    // Pick-one panels: also take the member's reactions for the roles that were swapped out.
                    for (RolePanelData.PanelRole other : panel.roles) {
                        if (other == chosen || other.roleId == null) {
                            continue;
                        }
                        boolean swappedOut = removedRoles.stream().anyMatch(role -> role.getId().equals(other.roleId));
                        if (swappedOut) {
                            RolePanelRenderer.emojiFor(other).ifPresent(emoji -> event.getGuildChannel()
                                    .removeReactionById(event.getMessageId(), emoji, user)
                                    .queue(null, error -> { }));
                        }
                    }
                }),
                error -> Log.warn("Couldn't load member " + event.getUserId() + " for a role panel reaction", error));
    }

    /** If a posted panel is deleted, forget the message so the editor offers to post it again. */
    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (event.isFromGuild()) {
            forget(event.getGuild().getId(), event.getMessageId());
        }
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        for (String messageId : event.getMessageIds()) {
            forget(event.getGuild().getId(), messageId);
        }
    }

    private static void forget(String guildId, String messageId) {
        try {
            RolePanelStore.clearMessage(guildId, messageId);
        } catch (SQLException exception) {
            Log.error("RolePanelListener.forget failed", exception);
        }
    }
}
