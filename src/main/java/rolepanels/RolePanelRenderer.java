package rolepanels;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.requests.RestAction;
import utils.EmbedHelpers;
import utils.ReactionEmojiParser;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds the message of a role panel and posts or updates it. */
public final class RolePanelRenderer {
    public static final String DEFAULT_DISABLED_MESSAGE = "This role panel is currently disabled.";
    private static final Color DEFAULT_COLOUR = new Color(0x5865F2);

    private RolePanelRenderer() {
    }

    public static String buttonId(RolePanelData panel, RolePanelData.PanelRole role) {
        return "rp:b:" + panel.id + ":" + role.id;
    }

    public static String menuId(RolePanelData panel) {
        return "rp:s:" + panel.id;
    }

    /** What the member sees for this role: the custom label, or the role's name. */
    public static String labelFor(RolePanelData.PanelRole role, Guild guild) {
        if (role.label != null && !role.label.isBlank()) {
            return role.label;
        }
        Role discordRole = guild == null ? null : guild.getRoleById(role.roleId);
        return discordRole == null ? "Unknown role" : discordRole.getName();
    }

    public static Optional<Emoji> emojiFor(RolePanelData.PanelRole role) {
        return role.emoji == null ? Optional.empty() : ReactionEmojiParser.parse(role.emoji);
    }

    public static MessageEmbed buildEmbed(RolePanelData panel, Guild guild) {
        StringBuilder text = new StringBuilder();
        if (panel.disabled) {
            text.append(panel.disabledMessage == null || panel.disabledMessage.isBlank()
                    ? DEFAULT_DISABLED_MESSAGE
                    : panel.disabledMessage);
        } else {
            text.append(panel.description == null || panel.description.isBlank()
                    ? "Choose your roles below."
                    : panel.description);
            if (panel.style == RolePanelData.Style.REACTIONS) {
                text.append("\n\n");
                for (RolePanelData.PanelRole role : panel.roles) {
                    text.append(emojiFor(role).map(Emoji::getFormatted).orElse("•"))
                            .append("  <@&").append(role.roleId).append(">\n");
                }
            }
            if (panel.mode == RolePanelData.Mode.UNIQUE) {
                text.append("\n\n-# You can only have one of these roles at a time.");
            } else if (panel.maxRoles > 0 && panel.mode != RolePanelData.Mode.REMOVE_ONLY) {
                text.append("\n\n-# You can have up to ").append(panel.maxRoles).append(" of these roles.");
            } else if (panel.mode == RolePanelData.Mode.ADD_ONLY) {
                text.append("\n\n-# Roles from here can't be removed again.");
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(panel.title == null || panel.title.isBlank() ? panel.name : panel.title)
                .setDescription(text.length() > 4000 ? text.substring(0, 3997) + "..." : text.toString())
                .setColor(parseColour(panel.colour));
        if (panel.imageUrl != null && !panel.imageUrl.isBlank()) {
            embed.setImage(panel.imageUrl);
        }
        if (panel.thumbnailUrl != null && !panel.thumbnailUrl.isBlank()) {
            embed.setThumbnail(panel.thumbnailUrl);
        }
        if (panel.footerText != null && !panel.footerText.isBlank()) {
            embed.setFooter(panel.footerText);
        } else {
            EmbedHelpers.setDefaultFooter(embed, guild);
        }
        return embed.build();
    }

    /** The buttons or dropdown; empty for the reactions style (and for a panel without roles). */
    public static List<ActionRow> buildComponents(RolePanelData panel, Guild guild) {
        List<ActionRow> rows = new ArrayList<>();
        if (panel.roles.isEmpty()) {
            return rows;
        }
        if (panel.style == RolePanelData.Style.BUTTONS) {
            List<Button> row = new ArrayList<>();
            for (RolePanelData.PanelRole role : panel.roles) {
                String label = truncate(labelFor(role, guild), 80);
                Button button = Button.of(parseStyle(role.buttonStyle), buttonId(panel, role), label);
                Optional<Emoji> emoji = emojiFor(role);
                if (emoji.isPresent()) {
                    button = button.withEmoji(emoji.get());
                }
                row.add(panel.disabled ? button.asDisabled() : button);
                if (row.size() == 5) {
                    rows.add(ActionRow.of(row));
                    row = new ArrayList<>();
                }
            }
            if (!row.isEmpty()) {
                rows.add(ActionRow.of(row));
            }
        } else if (panel.style == RolePanelData.Style.DROPDOWN) {
            int most = panel.mode == RolePanelData.Mode.UNIQUE ? 1 : Math.min(panel.roles.size(), 25);
            StringSelectMenu.Builder menu = StringSelectMenu.create(menuId(panel))
                    .setPlaceholder(panel.mode == RolePanelData.Mode.UNIQUE ? "Choose a role" : "Choose your roles")
                    .setRequiredRange(0, most)
                    .setDisabled(panel.disabled);
            for (RolePanelData.PanelRole role : panel.roles) {
                SelectOption option = SelectOption.of(truncate(labelFor(role, guild), 100), Integer.toString(role.id));
                if (role.description != null && !role.description.isBlank()) {
                    option = option.withDescription(truncate(role.description, 100));
                }
                Optional<Emoji> emoji = emojiFor(role);
                if (emoji.isPresent()) {
                    option = option.withEmoji(emoji.get());
                }
                menu.addOptions(option);
            }
            rows.add(ActionRow.of(menu.build()));
        }
        return rows;
    }

    /**
     * Posts the panel, or edits the message it was posted in before (when it still exists in the same
     * channel). The caller stores the resulting message with {@link RolePanelStore#saveMessage}.
     */
    public static RestAction<Message> publish(RolePanelData panel, GuildMessageChannel channel) {
        MessageEmbed embed = buildEmbed(panel, channel.getGuild());
        List<ActionRow> rows = buildComponents(panel, channel.getGuild());
        boolean sameChannel = channel.getId().equals(panel.channelId);
        if (panel.messageId == null || !sameChannel) {
            var send = channel.sendMessageEmbeds(embed);
            return rows.isEmpty() ? send : send.addComponents(rows);
        }
        return channel.editMessageEmbedsById(panel.messageId, embed)
                .setComponents(rows)
                .onErrorFlatMap(error -> {
                    var send = channel.sendMessageEmbeds(embed);
                    return rows.isEmpty() ? send : send.addComponents(rows);
                });
    }

    /**
     * After posting: the reactions style gets one reaction per role (in order), every other style has its
     * reactions cleared. Needs Add Reactions, and Manage Messages to clear; missing permissions are ignored.
     */
    public static void syncReactions(Message message, RolePanelData panel) {
        if (panel.style != RolePanelData.Style.REACTIONS) {
            if (!message.getReactions().isEmpty()) {
                message.clearReactions().queue(null, error -> { });
            }
            return;
        }
        List<Emoji> emojis = new ArrayList<>();
        for (RolePanelData.PanelRole role : panel.roles) {
            emojiFor(role).ifPresent(emojis::add);
        }
        message.clearReactions().queue(
                ignored -> addReactions(message, emojis, 0),
                error -> addReactions(message, emojis, 0));
    }

    private static void addReactions(Message message, List<Emoji> emojis, int index) {
        if (index >= emojis.size()) {
            return;
        }
        message.addReaction(emojis.get(index)).queue(
                ignored -> addReactions(message, emojis, index + 1),
                error -> addReactions(message, emojis, index + 1));
    }

    public static ButtonStyle parseStyle(String style) {
        if (style == null) {
            return ButtonStyle.SECONDARY;
        }
        return switch (style.toUpperCase(java.util.Locale.ROOT)) {
            case "PRIMARY" -> ButtonStyle.PRIMARY;
            case "SUCCESS" -> ButtonStyle.SUCCESS;
            case "DANGER" -> ButtonStyle.DANGER;
            default -> ButtonStyle.SECONDARY;
        };
    }

    public static Color parseColour(String hex) {
        if (hex == null || hex.isBlank()) {
            return DEFAULT_COLOUR;
        }
        try {
            String digits = hex.trim().startsWith("#") ? hex.trim().substring(1) : hex.trim();
            return new Color(Integer.parseInt(digits, 16));
        } catch (NumberFormatException exception) {
            return DEFAULT_COLOUR;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
