package tickets;

import database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Single source of truth for who may do what with a ticket.
 *
 * <p>Three levels are recognised:
 * <ul>
 *   <li><b>Creator</b> - the member who opened the ticket.</li>
 *   <li><b>Staff</b> - members holding one of the staff roles configured on the ticket's panel.</li>
 *   <li><b>Admin</b> - members with Manage Channels or Manage Server. Admins are always staff.</li>
 * </ul>
 *
 * <p>Both the slash commands and the ticket buttons must go through this class so
 * the two entry points can never disagree.
 */
public final class TicketPermissions {

    public enum Action {
        CLOSE,
        REOPEN,
        CLAIM,
        UNCLAIM,
        TRANSFER,
        TRANSCRIPT,
        DELETE;

        /** Maps a command or button action name (e.g. "claim") to an Action, or null if unknown. */
        public static Action fromName(String name) {
            if (name == null) {
                return null;
            }
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    private TicketPermissions() {
    }

    /** Admins can do everything with a ticket, whatever the panel's staff roles are. */
    public static boolean isAdmin(Member member) {
        return member.hasPermission(Permission.MANAGE_CHANNEL)
                || member.hasPermission(Permission.MANAGE_SERVER);
    }

    /** Creating, editing and sending ticket panels is limited to Manage Server. */
    public static boolean canManagePanels(Member member) {
        return member.hasPermission(Permission.MANAGE_SERVER);
    }

    public static boolean isCreator(Member member, TicketData ticket) {
        return ticket.creatorId != null && ticket.creatorId.equals(member.getId());
    }

    /** True for admins and for members with a staff role on the ticket's panel. */
    public static boolean isStaff(Member member, TicketData ticket) throws SQLException {
        if (isAdmin(member)) {
            return true;
        }
        if (ticket.panelId == null) {
            return false;
        }
        TicketPanelData panel = Database.getPanelDataById(member.getGuild().getId(), ticket.panelId);
        if (panel == null || panel.staffRoles == null) {
            return false;
        }
        return member.getRoles().stream()
                .anyMatch(role -> panel.staffRoles.contains(role.getId()));
    }

    /**
     * Permission matrix:
     * <pre>
     * CLOSE, REOPEN              creator, staff, admin
     * CLAIM, UNCLAIM, TRANSCRIPT staff, admin
     * TRANSFER                   admin, or staff who holds the claim (any staff if unclaimed)
     * DELETE                     admin
     * </pre>
     */
    public static boolean can(Member member, TicketData ticket, Action action) throws SQLException {
        return switch (action) {
            case CLOSE, REOPEN -> isCreator(member, ticket) || isStaff(member, ticket);
            case CLAIM, UNCLAIM, TRANSCRIPT -> isStaff(member, ticket);
            case TRANSFER -> isAdmin(member)
                    || (isStaff(member, ticket)
                            && (ticket.claimerId == null || ticket.claimerId.equals(member.getId())));
            case DELETE -> isAdmin(member);
        };
    }

    /** The message shown to someone who is not allowed to perform the action. */
    public static String denialMessage(Action action) {
        return switch (action) {
            case CLOSE -> "Only the ticket creator or ticket staff can close this ticket.";
            case REOPEN -> "Only the ticket creator or ticket staff can reopen this ticket.";
            case CLAIM -> "Only ticket staff can claim tickets.";
            case UNCLAIM -> "Only ticket staff can unclaim tickets.";
            case TRANSCRIPT -> "Only ticket staff can create transcripts.";
            case TRANSFER -> "Only the staff member who claimed this ticket, or a ticket admin, can transfer it.";
            case DELETE -> "Only ticket admins (Manage Channels or Manage Server) can delete tickets.";
        };
    }
}
