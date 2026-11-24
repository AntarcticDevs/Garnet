package utils;


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


    public static String warning() {
        return available ? "" : "\n⚠️ This bot isn't allowed to read message text, so this feature can't see messages. "
                + "Whoever runs the bot has to turn on **Message Content Intent** under Privileged Gateway Intents "
                + "on the Bot tab of the application, then restart it.";
    }
}
