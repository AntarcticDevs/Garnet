package utils;

import database.Database;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;


public class EmbedManager {

    public static EmbedBuilder loadEmbed(Guild guild, Member member, String embedName) throws SQLException {
        return loadEmbed(guild, member, embedName, null);
    }

    public static EmbedBuilder loadEmbedById(Guild guild, Member member, long embedId) throws SQLException {
        return loadEmbed(guild, member, null, embedId);
    }

    private static EmbedBuilder loadEmbed(Guild guild, Member member, String embedName, Long requestedEmbedId)
            throws SQLException {

        String embedSql = """
                SELECT id, name, title, description, footer_text, footer_image,
                       image_url, author_text, author_image, colour, thumbnail_url
                FROM embeds
                WHERE guild_id = ? AND name = ?
                """;

        if (requestedEmbedId != null) {
            embedSql = """
                    SELECT id, name, title, description, footer_text, footer_image,
                           image_url, author_text, author_image, colour, thumbnail_url
                    FROM embeds
                    WHERE id = ? AND guild_id = ?
                    """;
        }

        String fieldsSql = """
                SELECT field_name, field_value, field_position, is_inline
                FROM embed_fields
                WHERE embed_id = ?
                ORDER BY field_position
                """;

        try (Connection connection = Database.getConnection();
             PreparedStatement embedStatement = connection.prepareStatement(embedSql);
             PreparedStatement fieldsStatement = connection.prepareStatement(fieldsSql)) {

            if (requestedEmbedId == null) {
                embedStatement.setString(1, guild.getId());
                embedStatement.setString(2, embedName);
            } else {
                embedStatement.setLong(1, requestedEmbedId);
                embedStatement.setString(2, guild.getId());
            }

            EmbedData embed;

            try (ResultSet result = embedStatement.executeQuery()) {

                if (!result.next()) {
                    return null;
                }

                embed = new EmbedData(
                        result.getLong("id"),
                        result.getString("title"),
                        result.getString("description"),
                        result.getString("footer_text"),
                        result.getString("footer_image"),
                        result.getString("image_url"),
                        result.getString("author_text"),
                        result.getString("author_image"),
                        result.getString("colour"),
                        result.getString("thumbnail_url"),
                        new ArrayList<>()
                );
            }

            fieldsStatement.setLong(1, embed.id());

            try (ResultSet result = fieldsStatement.executeQuery()) {

                while (result.next()) {
                    embed.fields().add(new EmbedFieldData(
                            result.getString("field_name"),
                            result.getString("field_value"),
                            result.getInt("field_position"),
                            result.getInt("is_inline") != 0
                    ));
                }
            }

            EmbedBuilder builder = new EmbedBuilder();

            if (embed.title() != null) {
                builder.setTitle(replaceVariables(embed.title(), guild, member));
            }

            if (embed.description() != null) {
                builder.setDescription(
                        replaceVariables(embed.description(), guild, member)
                );
            }

            if (embed.footerText() != null) {
                builder.setFooter(
                        replaceVariables(embed.footerText(), guild, member),
                        replaceUrl(embed.footerImage(), guild, member)
                );
            }

            String imageUrl = replaceUrl(embed.imageUrl(), guild, member);
            if (imageUrl != null) {
                builder.setImage(imageUrl);
            }

            if (embed.authorText() != null) {
                builder.setAuthor(
                        replaceVariables(embed.authorText(), guild, member),
                        null,
                        replaceUrl(embed.authorImage(), guild, member)
                );
            }

            if (embed.colour() != null) {
                builder.setColor(Color.decode(embed.colour()));
            }

            String thumbnailUrl = replaceUrl(embed.thumbnailUrl(), guild, member);
            if (thumbnailUrl != null) {
                builder.setThumbnail(thumbnailUrl);
            }

            for (EmbedFieldData field : embed.fields()) {
                builder.addField(
                        replaceVariables(field.name(), guild, member),
                        replaceVariables(field.value(), guild, member),
                        field.inline()
                );
            }

            return builder;
        }
    }

    private record EmbedData(
            long id,
            String title,
            String description,
            String footerText,
            String footerImage,
            String imageUrl,
            String authorText,
            String authorImage,
            String colour,
            String thumbnailUrl,
            List<EmbedFieldData> fields
    ) {
    }

    private record EmbedFieldData(
            String name,
            String value,
            int position,
            boolean inline
    ) {
    }

    private static String replaceVariables(String text, Guild guild, Member member) {
    if (text == null) return null;

    return text
            .replace("{user}", member.getUser().getName())
            .replace("{user_mention}", member.getAsMention())
            .replace("{server}", guild.getName())
            .replace("{member_count}", String.valueOf(guild.getMemberCount()))
            .replace("{server_icon}", guild.getIconUrl() == null ? "" : guild.getIconUrl())
            .replace("{member_avatar}", member.getEffectiveAvatarUrl());
}

    /**
     * For image fields: fills in placeholders such as {server_icon} and {member_avatar}. A blank result
     * (for example a server without an icon) means "no image" instead of an invalid link.
     */
    private static String replaceUrl(String url, Guild guild, Member member) {
        String result = replaceVariables(url, guild, member);
        return result == null || result.isBlank() ? null : result.trim();
    }
}