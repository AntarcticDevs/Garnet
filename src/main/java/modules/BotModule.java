package modules;

import commands.Avatar;
import commands.Ban;
import commands.Embeds;
import commands.Info;
import commands.Kick;
import commands.Ping;
import commands.Purge;
import commands.ReactionRoles;
import commands.Rules;
import commands.Ticket;
import commands.UserInfo;
import config.ConfigManager;
import events.WelcomeListener;
import logging.LogCommand;
import logging.ServerLogListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import tickets.TicketButton;
import tickets.TicketPanelEditor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A feature that can be switched on or off in config.yml under {@code modules:}.
 *
 * <p>Each module lists the listeners it needs. A listener that is also a {@link commands.Command}
 * contributes its slash command, so turning a module off removes both the behaviour and the command.
 * Help and the console activity logger are not modules: they are always on.
 *
 * <p>To add a feature, add a constant here and a line to the {@code modules:} section of the default
 * config in {@link ConfigManager}.
 */
public enum BotModule {
    INFORMATION("information", "/ping, /info, /userinfo, /avatar",
            () -> List.<ListenerAdapter>of(new Ping(), new Info(), new UserInfo(), new Avatar())),
    MODERATION("moderation", "/ban, /kick, /purge",
            () -> List.<ListenerAdapter>of(new Ban(), new Kick(), new Purge())),
    REACTION_ROLES("reaction-roles", "/reaction-roles and persistent reaction role assignments",
            () -> List.<ListenerAdapter>of(new ReactionRoles())),
    TICKETS("tickets", "ticket panels, ticket channels and transcripts",
            () -> List.<ListenerAdapter>of(new Ticket(), new TicketButton(), new TicketPanelEditor())),
    WELCOMER("welcomer", "welcome messages for new members",
            () -> List.<ListenerAdapter>of(new WelcomeListener())),
    EMBEDS("embeds", "/embed and /rules",
            () -> List.<ListenerAdapter>of(new Embeds(), new Rules())),
    LOGGING("logging", "/logs and the server and ticket log channel",
            () -> List.<ListenerAdapter>of(new LogCommand(), new ServerLogListener()));

    private static final String SECTION = "modules";

    private final String key;
    private final String description;
    private final Supplier<List<ListenerAdapter>> factory;

    BotModule(String key, String description, Supplier<List<ListenerAdapter>> factory) {
        this.key = key;
        this.description = description;
        this.factory = factory;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    /** On unless config.yml says otherwise, so older config files keep every feature. */
    public boolean isEnabled() {
        return ConfigManager.getBoolean(SECTION + "." + key, true);
    }

    /** Creates the listeners (and so the slash commands) of this module. */
    public List<ListenerAdapter> create() {
        return factory.get();
    }

    /** Problems in the {@code modules:} section that would otherwise be silently ignored, such as typos. */
    public static List<String> configWarnings() {
        List<String> warnings = new ArrayList<>();

        Set<String> known = new LinkedHashSet<>();
        for (BotModule module : values()) {
            known.add(module.key);
        }

        if (!ConfigManager.isSection(SECTION)) {
            if (ConfigManager.getString(SECTION) != null) {
                warnings.add("\"" + SECTION + "\" in config.yml should list features such as  tickets: true  "
                        + "on indented lines below it; all features stay on.");
            }
            return warnings;
        }

        for (String name : ConfigManager.keysUnder(SECTION)) {
            if (!known.contains(name)) {
                warnings.add("Unknown feature \"" + name + "\" under modules in config.yml (known: "
                        + String.join(", ", known) + ").");
            }
        }
        for (BotModule module : values()) {
            String value = ConfigManager.getString(SECTION + "." + module.key);
            if (value != null && !ConfigManager.looksLikeBoolean(value)) {
                warnings.add("modules." + module.key + " in config.yml should be true or false, not \""
                        + value + "\"; treating it as true.");
            }
        }
        return warnings;
    }
}
