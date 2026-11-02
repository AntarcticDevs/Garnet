package tickets;

public record TicketQuestion(String prompt, boolean paragraph, boolean required, String placeholder, Integer maxLength) {

    public static final int MAX_QUESTIONS = 5;
    public static final int MAX_PROMPT_LENGTH = 45;
    public static final int MAX_PLACEHOLDER_LENGTH = 100;

    public static final int MAX_ANSWER_LENGTH = 1000;

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
