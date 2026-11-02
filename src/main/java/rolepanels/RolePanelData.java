package rolepanels;

import java.util.ArrayList;
import java.util.List;

/** A role panel: one message with buttons, a dropdown or reactions that give members roles. */
public class RolePanelData {
    public static final int MAX_ROLES = 25;

    /** How members pick roles. */
    public enum Style {
        BUTTONS("Buttons"),
        DROPDOWN("Dropdown menu"),
        REACTIONS("Reactions");

        public final String label;

        Style(String label) {
            this.label = label;
        }

        public static Style parse(String value) {
            for (Style style : values()) {
                if (style.name().equalsIgnoreCase(value)) {
                    return style;
                }
            }
            return BUTTONS;
        }
    }

    /** What picking a role does. */
    public enum Mode {
        TOGGLE("Toggle", "Pick a role to get it, pick it again to lose it"),
        UNIQUE("Pick one", "Only one role from this panel at a time (colours, regions)"),
        ADD_ONLY("Add only", "Roles can be gained here but not removed (verification, rules)"),
        REMOVE_ONLY("Remove only", "Roles can only be given up here (opt-out)");

        public final String label;
        public final String description;

        Mode(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public static Mode parse(String value) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return TOGGLE;
        }
    }

    /** One role offered by a panel. */
    public static class PanelRole {
        public int id;
        public String roleId;
        public String label;        // button/option text; null = the role's name
        public String emoji;        // formatted emoji; required for the reactions style
        public String description;  // dropdown option description
        public String buttonStyle;  // PRIMARY, SECONDARY, SUCCESS or DANGER; null = SECONDARY

        public PanelRole() {
        }

        public PanelRole(String roleId) {
            this.roleId = roleId;
        }
    }

    public int id;
    public String guildId;
    public String name;

    public String title;
    public String description;
    public String colour;
    public String imageUrl;
    public String thumbnailUrl;
    public String footerText;

    public Style style = Style.BUTTONS;
    public Mode mode = Mode.TOGGLE;
    public int maxRoles;              // 0 = no limit (toggle and add only)
    public String requiredRoleId;     // members need this role to use the panel
    public String blockedRoleId;      // members with this role can't use the panel
    public boolean disabled;
    public String disabledMessage;

    public String channelId;
    public String messageId;

    public List<PanelRole> roles = new ArrayList<>();

    public PanelRole roleById(int rowId) {
        for (PanelRole role : roles) {
            if (role.id == rowId) {
                return role;
            }
        }
        return null;
    }

    public PanelRole roleByRoleId(String roleId) {
        for (PanelRole role : roles) {
            if (role.roleId.equals(roleId)) {
                return role;
            }
        }
        return null;
    }
}
