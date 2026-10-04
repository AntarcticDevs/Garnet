package tickets;

import java.util.ArrayList;
import java.util.List;

public class TicketPanelData {
    public int id;
    public String guildId;
    public String name;
    public String content;

    public List<String> staffRoles = new ArrayList<>();
    public List<String> pingRoles = new ArrayList<>();
    public List<String> categories = new ArrayList<>();

    public String ticketCategoryId;

    public int totalCategories;
    public int totalStaffRoles;
    public int totalPingRoles;
}
