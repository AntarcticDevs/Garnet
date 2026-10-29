package tickets;

/**
 * One question of a panel category's ticket form.
 *
 * @param prompt      the label shown above the text box (Discord allows at most 45 characters)
 * @param paragraph   true for a multi-line box, false for a single-line box
 * @param required    whether the member must answer
 * @param placeholder grey hint text inside the box, or null
 * @param maxLength   the longest answer allowed, or null for the default
 */
public record TicketQuestion(String prompt, boolean paragraph, boolean required, String placeholder, Integer maxLength) {

    public static final int MAX_QUESTIONS = 5;
    public static final int MAX_PROMPT_LENGTH = 45;
    public static final int MAX_PLACEHOLDER_LENGTH = 100;

    /** Answers are posted in an embed field, which holds at most 1024 characters. */
    public static final int MAX_ANSWER_LENGTH = 1000;

    /** A question as it was before per-question settings existed: a required paragraph box. */
    public static TicketQuestion simple(String prompt) {
        return new TicketQuestion(prompt, true, true, null, null);
    }

    public int effectiveMaxLength() {
        return maxLength == null ? MAX_ANSWER_LENGTH : Math.min(maxLength, MAX_ANSWER_LENGTH);
    }

    public String styleName() {
        return paragraph ? "Paragraph" : "Short";
    }
}
