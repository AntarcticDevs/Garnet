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
    public List<String> categorieList = new ArrayList<>();

    public String ticketCategoryId;

    // Later:
    // List<String> categories;
    // List<String> staffRoles;
    // List<String> pingRoles;
}