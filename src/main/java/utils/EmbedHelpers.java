package utils;

import config.ConfigManager;
import java.awt.Color;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

public final class EmbedHelpers {

	private EmbedHelpers() {
	}

	public record EmbedField(String name, String value, boolean inline) {
	}

	public static EmbedBuilder setDefaultFooter(EmbedBuilder embed, Guild guild) {
		if (guild != null) {
			embed.setFooter(guild.getName(), guild.getIconUrl());
		} else {
			embed.setFooter(ConfigManager.getBotName());
		}
		return embed;
	}

	public static EmbedBuilder setGuildThumbnail(EmbedBuilder embed, Guild guild) {
		if (guild != null && guild.getIconUrl() != null) {
			embed.setThumbnail(guild.getIconUrl());
		}
		return embed;
	}

	public static EmbedBuilder createEmbed(
			String title,
			String description,
			String colour,
			String image,
			String thumbnail,
			String footer,
			String footerImage,
			String author,
			String authorImage,
			String fieldName,
			String fieldValue,
			List<EmbedField> fields) {
		EmbedBuilder embed = new EmbedBuilder()
				.setTitle(title)
				.setDescription(description);

		if (colour != null) {
			embed.setColor(parseColor(colour));
		}
		if (image != null) {
			embed.setImage(image);
		}
		if (thumbnail != null) {
			embed.setThumbnail(thumbnail);
		}
		if (footer != null) {
			embed.setFooter(footer, footerImage);
		}
		if (author != null) {
			embed.setAuthor(author, null, authorImage);
		}
		if (fieldName != null && !fieldName.isEmpty()
				&& fieldValue != null && !fieldValue.isEmpty()) {
			embed.addField(fieldName, fieldValue, false);
		}
		if (fields != null) {
			for (EmbedField field : fields) {
				embed.addField(field.name(), field.value(), field.inline());
			}
		}
		return embed;
	}

	private static Color parseColor(String colour) {
		String hex = colour.startsWith("#") ? colour.substring(1) : colour;
		return new Color(Integer.parseInt(hex, 16));
	}
}
