package tickets;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.requests.RestAction;

import java.awt.Color;
import java.util.Locale;

import utils.EmbedHelpers;


public class TicketPanel {
    public static final int DEFAULT_COLOUR = 0x5865F2;
    public static final String DEFAULT_DISABLED_MESSAGE =
            "This ticket panel is currently disabled. Please check back later.";

    /** Sends the panel as a new message. */
    public RestAction<Message> panel(TicketPanelData panelData, TextChannel channel) {
        return channel.sendMessageEmbeds(buildEmbed(panelData, channel.getGuild()))
                .addComponents(buildActionRow(panelData));
    }

    /**
     * Publishes the panel: edits the previously published message when it still exists,
     * otherwise sends a fresh one. The caller stores the resulting message's channel and ID.
     */
    public RestAction<Message> publish(TicketPanelData panelData, TextChannel channel) {
        MessageEmbed embed = buildEmbed(panelData, channel.getGuild());
        ActionRow row = buildActionRow(panelData);
        boolean sameChannel = channel.getId().equals(panelData.channelId);
        if (panelData.messageId == null || !sameChannel) {
            return channel.sendMessageEmbeds(embed).addComponents(row);
        }
        return channel.editMessageEmbedsById(panelData.messageId, embed)
                .setComponents(row)
                .onErrorFlatMap(error -> channel.sendMessageEmbeds(embed).addComponents(row));
    }

    /**
     * Edits the already-published panel message in place (for example after enabling or disabling it).
     * Returns null when the panel isn't published or its channel is gone.
     */
    public RestAction<Message> editPublished(Guild guild, TicketPanelData panelData) {
        if (panelData.channelId == null || panelData.messageId == null) {
            return null;
        }
        TextChannel channel = guild.getTextChannelById(panelData.channelId);
        if (channel == null) {
            return null;
        }
        return channel.editMessageEmbedsById(panelData.messageId, buildEmbed(panelData, guild))
                .setComponents(buildActionRow(panelData));
    }

    /** The panel embed exactly as members will see it. */
    public MessageEmbed buildEmbed(TicketPanelData panelData, Guild guild) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(blankToNull(panelData.embedTitle) == null ? panelData.name : panelData.embedTitle)
                .setDescription(panelData.disabled
                        ? (blankToNull(panelData.disabledMessage) == null
                                ? DEFAULT_DISABLED_MESSAGE
                                : panelData.disabledMessage)
                        : blankToNull(panelData.content) == null
                        ? "Use the button below to create a ticket."
                        : panelData.content)
                .setColor(parseColour(panelData.embedColour));

        if (blankToNull(panelData.imageUrl) != null) {
            embed.setImage(panelData.imageUrl);
        }
        if (blankToNull(panelData.thumbnailUrl) != null) {
            embed.setThumbnail(panelData.thumbnailUrl);
        }
        if (blankToNull(panelData.footerText) != null) {
            embed.setFooter(panelData.footerText);
        } else {
            EmbedHelpers.setDefaultFooter(embed, guild);
        }
        return embed.build();
    }

    /** The category dropdown when the panel has categories, otherwise the create button. */
    public ActionRow buildActionRow(TicketPanelData panelData) {
        if (panelData.disabled) {
            return ActionRow.of(Button.secondary("ticket:disabled:" + panelData.id, "Tickets disabled")
                    .withEmoji(Emoji.fromUnicode("\uD83D\uDD12"))
                    .asDisabled());
        }
        if (!panelData.categories.isEmpty()) {
            return ActionRow.of(categoryMenu(panelData));
        }
        return ActionRow.of(createButton(panelData));
    }

    public static Button createButton(TicketPanelData panelData) {
        String customId = panelData.id > 0 ? "ticket:create:" + panelData.id : "ticket:support";
        String label = blankToNull(panelData.buttonLabel) == null
                ? TicketPanelData.DEFAULT_BUTTON_LABEL
                : panelData.buttonLabel;
        Button button = Button.of(parseStyle(panelData.buttonStyle), customId, label);
        Emoji emoji = parseEmoji(panelData.buttonEmoji);
        return emoji == null ? button : button.withEmoji(emoji);
    }

    static StringSelectMenu categoryMenu(TicketPanelData panelData) {
        StringSelectMenu.Builder categories = StringSelectMenu.create("ticket:category:" + panelData.id)
                .setPlaceholder("Choose a ticket category");
        panelData.categories.stream().limit(25).forEach(category -> {
            TicketPanelData.CategoryOption extra = panelData.categoryOption(category);
            SelectOption option = SelectOption.of(category, category);
            if (blankToNull(extra.description()) != null) {
                String description = extra.description().trim();
                option = option.withDescription(description.length() > 100
                        ? description.substring(0, 100) : description);
            }
            Emoji emoji = parseEmoji(extra.emoji());
            if (emoji != null) {
                option = option.withEmoji(emoji);
            }
            categories.addOptions(option);
        });
        return categories.build();
    }

    // ---- parsing helpers shared with the editor -------------------------------------------

    public static ButtonStyle parseStyle(String style) {
        if (style == null) {
            return ButtonStyle.PRIMARY;
        }
        return switch (style.toUpperCase(Locale.ROOT)) {
            case "SECONDARY", "GREY", "GRAY" -> ButtonStyle.SECONDARY;
            case "SUCCESS", "GREEN" -> ButtonStyle.SUCCESS;
            case "DANGER", "RED" -> ButtonStyle.DANGER;
            default -> ButtonStyle.PRIMARY;
        };
    }

    /** Returns the parsed emoji, or null when blank or invalid. */
    public static Emoji parseEmoji(String value) {
        if (blankToNull(value) == null) {
            return null;
        }
        try {
            return Emoji.fromFormatted(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static Color parseColour(String hex) {
        if (blankToNull(hex) == null) {
            return new Color(DEFAULT_COLOUR);
        }
        try {
            String digits = hex.trim().startsWith("#") ? hex.trim().substring(1) : hex.trim();
            return new Color(Integer.parseInt(digits, 16));
        } catch (NumberFormatException exception) {
            return new Color(DEFAULT_COLOUR);
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
