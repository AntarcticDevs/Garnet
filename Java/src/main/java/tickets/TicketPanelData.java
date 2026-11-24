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

    public String embedTitle;
    public String embedColour;
    public String imageUrl;
    public String thumbnailUrl;
    public String footerText;
    public String buttonLabel;
    public String buttonEmoji;
    public String buttonStyle;

    public String channelId;
    public String messageId;

    public boolean disabled;
    public String disabledMessage;

    public int ticketLimit;

    public String welcomeMessage;

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
