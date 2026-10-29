package tickets;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import database.Database;
import java.sql.SQLException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;

public class TranscriptManager {
    private static final int HISTORY_PAGE_SIZE = 100;
    private static final String TRANSCRIPT_SUFFIX = "-transcript.txt";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    public CompletableFuture<Void> sendTranscript(TextChannel channel, InteractionHook hook) {
        return sendTranscript(channel, upload -> hook.sendFiles(upload).submit());
    }

    public CompletableFuture<Void> sendTranscript(TextChannel channel, TextChannel destination) {
        return sendTranscript(channel, upload -> destination.sendFiles(upload).submit());
    }

    public TextChannel getConfiguredDestination(Guild guild, TicketData ticket) throws SQLException {
        if (ticket.panelId == null) {
            return null;
        }
        TicketPanelData panel = Database.getPanelDataById(guild.getId(), ticket.panelId);
        if (panel == null || panel.transcriptChannelId == null || panel.transcriptChannelId.isBlank()) {
            return null;
        }
        TextChannel destination = guild.getTextChannelById(panel.transcriptChannelId);
        if (destination == null) {
            throw new IllegalStateException("The configured ticket transcript channel is missing.");
        }
        return destination;
    }

    private CompletableFuture<Void> sendTranscript(
            TextChannel channel, Function<FileUpload, CompletableFuture<?>> upload) {
        List<Message> messages = new ArrayList<>();
        return retrieveMessages(channel, messages, null)
                .thenCompose(ignored -> {
                    Collections.reverse(messages);
                    String transcript = formatTranscript(channel, messages);
                    List<byte[]> parts = splitTranscript(
                            transcript, channel.getGuild().getMaxFileSize());
                    return sendTranscriptPart(channel, upload, parts, 0);
                });
    }

    private CompletableFuture<Void> sendTranscriptPart(
            TextChannel channel,
            Function<FileUpload, CompletableFuture<?>> upload,
            List<byte[]> parts,
            int partIndex) {
        if (partIndex >= parts.size()) {
            return CompletableFuture.completedFuture(null);
        }

        String fileName = parts.size() == 1
                ? channel.getName() + TRANSCRIPT_SUFFIX
                : channel.getName() + "-transcript-part-" + (partIndex + 1)
                        + "-of-" + parts.size() + ".txt";
        return upload.apply(FileUpload.fromData(parts.get(partIndex), fileName))
                .thenApply(ignored -> null)
                .thenCompose(ignored -> sendTranscriptPart(channel, upload, parts, partIndex + 1));
    }

    private CompletableFuture<Void> retrieveMessages(
            TextChannel channel, List<Message> messages, String beforeId) {
        CompletableFuture<List<Message>> batchFuture;
        if (beforeId == null) {
            batchFuture = channel.getHistory().retrievePast(HISTORY_PAGE_SIZE).submit();
        } else {
            batchFuture = MessageHistory.getHistoryBefore(channel, beforeId)
                    .limit(HISTORY_PAGE_SIZE)
                    .submit()
                    .thenApply(MessageHistory::getRetrievedHistory);
        }

        return batchFuture.thenCompose(batch -> {
            if (batch.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            messages.addAll(batch);
            String nextBeforeId = batch.get(batch.size() - 1).getId();
            if (batch.size() < HISTORY_PAGE_SIZE) {
                return CompletableFuture.completedFuture(null);
            }

            return retrieveMessages(channel, messages, nextBeforeId);
        });
    }

    private String formatTranscript(TextChannel channel, List<Message> messages) {
        StringBuilder transcript = new StringBuilder()
                .append("Transcript for #").append(channel.getName())
                .append(" (").append(channel.getId()).append(")\n")
                .append("All available channel messages are included.\n\n");

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

        return transcript.toString();
    }

    private List<byte[]> splitTranscript(String transcript, long maxFileSize) {
        if (maxFileSize <= 0) {
            throw new IllegalStateException("Discord reported an invalid file upload limit.");
        }

        List<byte[]> parts = new ArrayList<>();
        int start = 0;
        while (start < transcript.length()) {
            int end = start;
            int lastNewline = -1;
            long partSize = 0;

            while (end < transcript.length()) {
                int codePoint = transcript.codePointAt(end);
                long codePointSize = utf8Size(codePoint);
                if (partSize + codePointSize > maxFileSize) {
                    break;
                }

                end += Character.charCount(codePoint);
                partSize += codePointSize;
                if (codePoint == '\n') {
                    lastNewline = end;
                }
            }

            if (end == start) {
                throw new IllegalStateException(
                        "A transcript character exceeds Discord's file upload limit.");
            }
            if (end < transcript.length() && lastNewline > start) {
                end = lastNewline;
            }

            parts.add(transcript.substring(start, end).getBytes(StandardCharsets.UTF_8));
            start = end;
        }

        return parts;
    }

    private long utf8Size(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        return codePoint <= 0xFFFF ? 3 : 4;
    }
}