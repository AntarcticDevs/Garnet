package tickets;

import database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.sql.SQLException;
import java.util.Locale;

public final class TicketPermissions {

    public enum Action {
        CLOSE,
        REOPEN,
        CLAIM,
        UNCLAIM,
        TRANSFER,
        TRANSCRIPT,
        DELETE;

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

    public static boolean isAdmin(Member member) {
        return member.hasPermission(Permission.MANAGE_CHANNEL)
                || member.hasPermission(Permission.MANAGE_SERVER);
    }

    public static boolean canManagePanels(Member member) {
        return member.hasPermission(Permission.MANAGE_SERVER);
    }

    public static boolean isCreator(Member member, TicketData ticket) {
        return ticket.creatorId != null && ticket.creatorId.equals(member.getId());
    }

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
