package logging;

import java.util.Locale;

public enum LogCategory {
    TICKETS("tickets", "Ticket opened, closed, reopened, claimed, transferred, deleted", true),
    MEMBERS("members", "Joins, leaves, nickname and role changes", true),
    MODERATION("moderation", "Bans, unbans, kicks and purges", true),
    SERVER("server", "Channels and roles created or deleted", true),
    ROLES("roles", "Roles given or removed by role panels", true),
    VOICE("voice", "Voice channel joins, leaves and moves (noisy)", false);

    private final String key;
    private final String description;
    private final boolean onByDefault;

    LogCategory(String key, String description, boolean onByDefault) {
        this.key = key;
        this.description = description;
        this.onByDefault = onByDefault;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public boolean onByDefault() {
        return onByDefault;
    }

    public static LogCategory fromKey(String key) {
        if (key == null) {
            return null;
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (LogCategory category : values()) {
            if (category.key.equals(wanted)) {
                return category;
            }
        }
        return null;
    }
}
