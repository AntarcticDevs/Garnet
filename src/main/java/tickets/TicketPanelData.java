package tickets;

import java.util.ArrayList;
import java.util.List;

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

    public int totalCategories;
    public int totalStaffRoles;
    public int totalPingRoles;
}
