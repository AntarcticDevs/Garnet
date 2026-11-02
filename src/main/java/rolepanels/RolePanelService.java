package rolepanels;

import logging.LogCategory;
import logging.LogService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import utils.Log;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The rules of a role panel: who may use it and which roles a pick adds or removes. */
public final class RolePanelService {

    private RolePanelService() {
    }

    /** Why this member may not use the panel at all, or null when they may. */
    public static String denial(Guild guild, Member member, RolePanelData panel) {
        if (panel.disabled) {
            return panel.disabledMessage == null || panel.disabledMessage.isBlank()
                    ? RolePanelRenderer.DEFAULT_DISABLED_MESSAGE
                    : panel.disabledMessage;
        }
        if (panel.requiredRoleId != null) {
            Role required = guild.getRoleById(panel.requiredRoleId);
            if (required != null && !member.getRoles().contains(required)) {
                return "You need the **" + required.getName() + "** role to use this panel.";
            }
        }
        if (panel.blockedRoleId != null) {
            Role blocked = guild.getRoleById(panel.blockedRoleId);
            if (blocked != null && member.getRoles().contains(blocked)) {
                return "You can't use this panel right now.";
            }
        }
        return null;
    }

    /** Why the bot can't hand out this role, or null when it can. */
    private static String cannotAssign(Guild guild, Role role) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            return "I'm missing the Manage Roles permission, so I can't change roles. Please tell a moderator.";
        }
        if (!guild.getSelfMember().canInteract(role)) {
            return "I can't give **" + role.getName() + "** because it is above my highest role. "
                    + "Please tell a moderator.";
        }
        return null;
    }

    private static Set<Role> heldPanelRoles(Guild guild, Member member, RolePanelData panel) {
        Set<Role> held = new LinkedHashSet<>();
        for (RolePanelData.PanelRole entry : panel.roles) {
            Role role = guild.getRoleById(entry.roleId);
            if (role != null && member.getRoles().contains(role)) {
                held.add(role);
            }
        }
        return held;
    }

    /**
     * A button click or dropdown choice. {@code reply} gets the private message for the member.
     * Every picked role is toggled (or added / removed / swapped, depending on the panel's mode).
     */
    public static void pick(Guild guild, Member member, RolePanelData panel,
            List<RolePanelData.PanelRole> picked, Consumer<String> reply) {
        String denied = denial(guild, member, panel);
        if (denied != null) {
            reply.accept("🚫 " + denied);
            return;
        }

        Set<Role> held = heldPanelRoles(guild, member, panel);
        Set<Role> add = new LinkedHashSet<>();
        Set<Role> remove = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();

        for (RolePanelData.PanelRole entry : picked) {
            Role role = guild.getRoleById(entry.roleId);
            if (role == null) {
                notes.add("ℹ️ That role no longer exists. Please tell a moderator.");
                continue;
            }
            boolean has = held.contains(role);
            switch (panel.mode) {
                case TOGGLE -> (has ? remove : add).add(role);
                case UNIQUE -> {
                    if (has) {
                        remove.add(role);
                    } else {
                        add.add(role);
                        remove.addAll(held);
                    }
                }
                case ADD_ONLY -> {
                    if (has) {
                        notes.add("ℹ️ You already have **" + role.getName() + "**.");
                    } else {
                        add.add(role);
                    }
                }
                case REMOVE_ONLY -> {
                    if (has) {
                        remove.add(role);
                    } else {
                        notes.add("ℹ️ You don't have **" + role.getName() + "**.");
                    }
                }
            }
        }

        for (Role role : add) {
            String problem = cannotAssign(guild, role);
            if (problem != null) {
                reply.accept("⚠️ " + problem);
                return;
            }
        }
        for (Role role : remove) {
            String problem = cannotAssign(guild, role);
            if (problem != null) {
                reply.accept("⚠️ " + problem);
                return;
            }
        }
        if (exceedsLimit(panel, held, add, remove)) {
            reply.accept("🚫 You can have at most " + panel.maxRoles + " role" + (panel.maxRoles == 1 ? "" : "s")
                    + " from this panel. Remove one first.");
            return;
        }
        if (add.isEmpty() && remove.isEmpty()) {
            reply.accept(notes.isEmpty() ? "Nothing changed." : String.join("\n", notes));
            return;
        }

        guild.modifyMemberRoles(member, add, remove).queue(
                ignored -> {
                    List<String> lines = new ArrayList<>();
                    for (Role role : add) {
                        lines.add("✅ You now have **" + role.getName() + "**.");
                    }
                    for (Role role : remove) {
                        lines.add("➖ **" + role.getName() + "** was removed.");
                    }
                    lines.addAll(notes);
                    reply.accept(String.join("\n", lines));
                    logChange(guild, member, panel, add, remove);
                },
                error -> {
                    Log.warn("Couldn't change roles for member " + member.getId() + " via role panel " + panel.id, error);
                    reply.accept("⚠️ I couldn't update your roles. Please tell a moderator.");
                });
    }

    /**
     * A reaction was added ({@code added}) or removed on a reactions-style panel. {@code keepReaction}
     * is told whether the member's reaction should stay (false: remove it, nothing was granted).
     */
    public static void react(Guild guild, Member member, RolePanelData panel, RolePanelData.PanelRole entry,
            boolean added, BiConsumer<Boolean, Set<Role>> keepReaction) {
        Role role = guild.getRoleById(entry.roleId);
        if (role == null) {
            keepReaction.accept(false, Set.of());
            return;
        }
        Set<Role> held = heldPanelRoles(guild, member, panel);
        boolean has = held.contains(role);
        Set<Role> add = new LinkedHashSet<>();
        Set<Role> remove = new LinkedHashSet<>();

        if (added) {
            if (denial(guild, member, panel) != null) {
                keepReaction.accept(false, Set.of());
                return;
            }
            switch (panel.mode) {
                case TOGGLE, ADD_ONLY -> {
                    if (!has) {
                        add.add(role);
                    }
                }
                case UNIQUE -> {
                    if (!has) {
                        add.add(role);
                    }
                    for (Role other : held) {
                        if (!other.equals(role)) {
                            remove.add(other);
                        }
                    }
                }
                case REMOVE_ONLY -> {
                    if (has) {
                        remove.add(role);
                    }
                }
            }
        } else {
            // Taking the reaction back gives the role up again (but never where roles can't be removed).
            if (panel.mode != RolePanelData.Mode.ADD_ONLY && has && !panel.disabled) {
                remove.add(role);
            }
        }

        if (add.isEmpty() && remove.isEmpty()) {
            keepReaction.accept(added && panel.mode != RolePanelData.Mode.REMOVE_ONLY, Set.of());
            return;
        }
        for (Role changed : add) {
            if (cannotAssign(guild, changed) != null) {
                Log.warn("Role panel " + panel.id + " can't assign role " + changed.getId()
                        + " (missing permission or role above the bot)");
                keepReaction.accept(false, Set.of());
                return;
            }
        }
        for (Role changed : remove) {
            if (cannotAssign(guild, changed) != null) {
                keepReaction.accept(true, Set.of());
                return;
            }
        }
        if (added && exceedsLimit(panel, held, add, remove)) {
            keepReaction.accept(false, Set.of());
            return;
        }
        guild.modifyMemberRoles(member, add, remove).queue(
                ignored -> {
                    logChange(guild, member, panel, add, remove);
                    keepReaction.accept(added && panel.mode != RolePanelData.Mode.REMOVE_ONLY, remove);
                },
                error -> {
                    Log.warn("Couldn't change roles for member " + member.getId() + " via role panel " + panel.id, error);
                    keepReaction.accept(false, Set.of());
                });
    }

    private static boolean exceedsLimit(RolePanelData panel, Set<Role> held, Set<Role> add, Set<Role> remove) {
        if (panel.maxRoles <= 0 || add.isEmpty()
                || panel.mode == RolePanelData.Mode.UNIQUE || panel.mode == RolePanelData.Mode.REMOVE_ONLY) {
            return false;
        }
        Set<Role> after = new LinkedHashSet<>(held);
        after.removeAll(remove);
        after.addAll(add);
        return after.size() > panel.maxRoles;
    }

    private static void logChange(Guild guild, Member member, RolePanelData panel, Set<Role> add, Set<Role> remove) {
        if (!LogService.wants(guild, LogCategory.ROLES)) {
            return;
        }
        EmbedBuilder embed = LogService.embed("Role panel used", add.isEmpty() ? LogService.RED : LogService.GREEN)
                .setDescription(member.getAsMention() + " (" + member.getUser().getName() + ", `"
                        + member.getId() + "`)")
                .addField("Panel", panel.name, true);
        if (!add.isEmpty()) {
            embed.addField("Added", String.join(" ", add.stream().map(Role::getAsMention).toList()), true);
        }
        if (!remove.isEmpty()) {
            embed.addField("Removed", String.join(" ", remove.stream().map(Role::getAsMention).toList()), true);
        }
        LogService.send(guild, LogCategory.ROLES, embed);
    }
}
