package tickets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TicketPanelData {
    public static final String DEFAULT_BUTTON_LABEL = "Create ticket";
    public static final String DEFAULT_BUTTON_STYLE = "PRIMARY";

    public int id;
    public String guildId;
    public String name;
    public String content;

    public List<String> staffRoles = new ArrayList<>();
    public List<String> pingRoles = new ArrayList<>();
    public List<String> categories = new ArrayList<>();

    public String ticketCategoryId;
    public String transcriptChannelId;

    // Appearance of the published panel message (null = use the default).
    public String embedTitle;
    public String embedColour;
    public String imageUrl;
    public String thumbnailUrl;
    public String footerText;
    public String buttonLabel;
    public String buttonEmoji;
    public String buttonStyle;

    // Where the panel is currently published, so edits can update it in place.
    public String channelId;
    public String messageId;

    // When disabled, the published panel shows a disabled button instead of the menu/button.
    public boolean disabled;
    public String disabledMessage;

    // Most open tickets one member may have from this panel at once (0 = no limit).
    public int ticketLimit;

    // Text at the top of every new ticket. Placeholders: {user} {username} {category} {panel}.
    // Null = the default text.
    public String welcomeMessage;

    // Optional description and emoji for each category in the dropdown, keyed by category name.
    public Map<String, CategoryOption> categoryOptions = new HashMap<>();

    public record CategoryOption(String description, String emoji) {
    }

    public CategoryOption categoryOption(String category) {
        CategoryOption option = categoryOptions.get(category);
        return option == null ? new CategoryOption(null, null) : option;
    }

    public int totalCategories;
    public int totalStaffRoles;
    public int totalPingRoles;
}
