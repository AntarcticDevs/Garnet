package tickets;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class TranscriptManager {
    private static final int MAX_MESSAGES = 1_000;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public byte[] createTranscript(TextChannel channel) {
        List<Message> messages = new ArrayList<>();
        String beforeId = null;

        while (messages.size() < MAX_MESSAGES) {
            int limit = Math.min(100, MAX_MESSAGES - messages.size());
            List<Message> batch;
            if (beforeId == null) {
                batch = channel.getHistory().retrievePast(limit).complete();
            } else {
                MessageHistory history = MessageHistory.getHistoryBefore(channel, beforeId)
                        .limit(limit)
                        .complete();
                batch = history.getRetrievedHistory();
            }
            if (batch.isEmpty()) {
                break;
            }

            messages.addAll(batch);
            beforeId = batch.get(batch.size() - 1).getId();
            if (batch.size() < limit) {
                break;
            }
        }

        Collections.reverse(messages);
        StringBuilder transcript = new StringBuilder()
                .append("Transcript for #").append(channel.getName())
                .append(" (").append(channel.getId()).append(")\n")
                .append("Messages are limited to the latest ").append(MAX_MESSAGES).append(".\n\n");

        for (Message message : messages) {
            transcript.append('[')
                    .append(TIMESTAMP_FORMAT.format(message.getTimeCreated()))
                    .append("] ")
                    .append(message.getAuthor().getName())
                    .append(": ")
                    .append(message.getContentDisplay().replace("\n", "\n    "));
            for (var attachment : message.getAttachments()) {
                transcript.append("\n    Attachment: ").append(attachment.getUrl());
            }
            transcript.append('\n');
        }

        return transcript.toString().getBytes(StandardCharsets.UTF_8);
    }
}