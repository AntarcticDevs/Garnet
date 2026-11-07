package utils;

/** Whether the bot was allowed to connect with Discord's Message Content Intent (set by Main). */
public final class MessageContent {
    private static volatile boolean available;

    private MessageContent() {
    }

    public static void setAvailable(boolean value) {
        available = value;
    }

    public static boolean isAvailable() {
        return available;
    }

    /** A warning to append to replies of features that read message text; empty when it works. */
    public static String warning() {
        return available ? "" : "\n⚠️ This bot isn't allowed to read message text, so this feature can't see messages. "
                + "Whoever runs the bot has to turn on **Message Content Intent** under Privileged Gateway Intents "
                + "on the Bot tab of the application, then restart it.";
    }
}
