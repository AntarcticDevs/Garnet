package utils;

import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReactionEmojiParser {

    private static final Pattern CUSTOM = Pattern.compile("^<(a)?:([A-Za-z0-9_]{2,32}):(\\d{17,20})>$");
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private ReactionEmojiParser() {}

    public static Optional<Emoji> parse(String input) {
        if (input == null)
            return Optional.empty();
        String s = input.strip();
        if (s.isEmpty())
            return Optional.empty();

        Matcher custom = CUSTOM.matcher(s);
        if (custom.matches()) {
            boolean animated = custom.group(1) != null;
            return Optional.of(Emoji.fromCustom(custom.group(2), Long.parseLong(custom.group(3)), animated));
        }

        return isSingleUnicodeEmoji(s) ? Optional.of(Emoji.fromUnicode(s)) : Optional.empty();
    }

    private static boolean isSingleUnicodeEmoji(String s) {
        Matcher m = GRAPHEME.matcher(s);
        if (!m.find() || m.start() != 0 || m.end() != s.length())
            return false;

        boolean sawPresentation = false;
        for (int cp : s.codePoints().toArray()) {
            boolean allowed = Character.isEmoji(cp)
                    || Character.isEmojiModifier(cp)
                    || cp == 0x200D
                    || cp == 0xFE0F
                    || cp == 0x20E3
                    || (cp >= 0xE0020 && cp <= 0xE007F);
            if (!allowed)
                return false;

            if (Character.isEmojiPresentation(cp) || cp == 0xFE0F || cp == 0x20E3) {
                sawPresentation = true;
            }
        }
        return sawPresentation;
    }
}