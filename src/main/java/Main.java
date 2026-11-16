import utils.Log;
import config.ConfigManager;
import commands.Help;
import commands.ActivityManager;
import events.ConsoleActivityLogger;
import modules.BotModule;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.util.EnumSet;

import java.util.List;
import java.util.ArrayList;

import commands.Command;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import database.Database;
import java.sql.SQLException;
import utils.ConsoleLogger;

import java.io.IOException;

public class Main {

        public static void main(String[] args) throws InterruptedException {
            ConsoleLogger.logConsole("---------------------------------------------------------------------------------------");
        try {
            boolean created = ConfigManager.createIfMissing();
            ConfigManager.load();
            if (created) {
                ConsoleLogger.logConsole("config.yml was created. Open it, add your bot token, then start the bot again.");
                System.exit(1);
                return;
            }
            try {
                List<String> added = ConfigManager.addMissingSettings();
                if (!added.isEmpty()) {
                    ConsoleLogger.logConsole("config.yml: added new settings (" + String.join(", ", added)
                            + "). Your old file was kept as config.yml.bak.");
                }
            } catch (IOException e) {
                Log.warn("Couldn't add new settings to config.yml; carrying on with the file as it is.", e);
            }
        } catch (IOException e) {
            ConsoleLogger.logConsole("Couldn't read config.yml: " + e.getMessage());
            System.exit(1);
            return;
        }

        String token = ConfigManager.getString("token");

        if (token == null) {
            ConsoleLogger.logConsole("No bot token is set. Open config.yml, fill in token, then start the bot again.");
            System.exit(1);
            return;
        }

        try {
            Database.initialize();
        } catch (SQLException e) {
            Log.error("Database initialization failed; stopping startup", e);
            ConsoleLogger.logConsole("Couldn't initialize the database. Fix the database path or permissions, "
                    + "then start the bot again.");
            System.exit(1);
            return;
        }

        List<ListenerAdapter> listeners = new ArrayList<>();
        List<CommandData> commandData = new ArrayList<>();
        Help help = new Help();
        listeners.add(help);
        commandData.add(help.getCommandData());
        listeners.add(new ConsoleActivityLogger());

        for (String warning : BotModule.configWarnings()) {
            ConsoleLogger.logConsole("WARNING " + warning);
        }
        List<String> enabledModules = new ArrayList<>();
        List<String> disabledModules = new ArrayList<>();
        for (BotModule module : BotModule.values()) {
            if (!module.isEnabled()) {
                disabledModules.add(module.key());
                continue;
            }
            enabledModules.add(module.key());
            for (ListenerAdapter listener : module.create()) {
                listeners.add(listener);
                if (listener instanceof Command command) {
                    commandData.add(command.getCommandData());
                }
            }
        }

        JDA jda;
        try {
            boolean wantMessageContent = (BotModule.TAGS.isEnabled()
                    && ConfigManager.getBoolean("tag-prefix-commands", true))
                    || BotModule.COUNTING.isEnabled() || BotModule.AUTO_REACT.isEnabled();
            jda = connect(token, listeners, wantMessageContent);
        } catch (InvalidTokenException e) {
            ConsoleLogger.logConsole("Discord rejected the bot token. Check the token in config.yml "
                    + "(copy it again from the Bot tab of your application) and start the bot again.");
            System.exit(1);
            return;
        } catch (IllegalStateException e) {
            String reason = String.valueOf(e.getMessage());
            ConsoleLogger.logConsole("Couldn't connect to Discord: " + reason.replace("\n", " "));
            if (reason.toLowerCase(java.util.Locale.ROOT).contains("intent")) {
                ConsoleLogger.logConsole("Turn on \"Server Members Intent\" under Privileged Gateway Intents "
                        + "on the Bot tab of your application at https://discord.com/developers/applications, "
                        + "then start the bot again.");
            }
            System.exit(1);
            return;
        }

        if (BotModule.ACTIVITY.isEnabled()) {
            ActivityManager activityManager = ActivityManager.get();
            activityManager.init(jda);
            Runtime.getRuntime().addShutdownHook(
                    new Thread(activityManager::shutdown, "activity-manager-shutdown"));
        }

        System.out.println("""
                   █████████                                           █████
                  ███░░░░░███                                         ░░███
                 ███     ░░░   ██████   ████████  ████████    ██████  ███████
                ░███          ░░░░░███ ░░███░░███░░███░░███  ███░░███░░░███░
                ░███    █████  ███████  ░███ ░░░  ░███ ░███ ░███████   ░███
                ░░███  ░░███  ███░░███  ░███      ░███ ░███ ░███░░░    ░███ ███
                 ░░█████████ ░░████████ █████     ████ █████░░██████   ░░█████
                  ░░░░░░░░░   ░░░░░░░░ ░░░░░     ░░░░ ░░░░░  ░░░░░░     ░░░░░
                """);
        ConsoleLogger.logConsole("-----------------------𝐁𝐨𝐭 𝐦𝐚𝐝𝐞 𝐛𝐲 𝐄𝐧𝐝𝐞𝐫𝐊𝐧𝐢𝐠𝐡𝐭𝟕𝐨𝐩-----------------------");
        ConsoleLogger.logConsole("✅ " + ConfigManager.getBotName() + " is online!");

        ConsoleLogger.logConsole("Features on: " + (enabledModules.isEmpty() ? "none" : String.join(", ", enabledModules))
                + (disabledModules.isEmpty() ? "" : " | off: " + String.join(", ", disabledModules)));

        jda.updateCommands()
                .addCommands(commandData)
                .queue();
    }





    private static JDA connect(String token, List<ListenerAdapter> listeners, boolean messageContent)
            throws InterruptedException {
        EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGE_REACTIONS);
        if (messageContent) {
            intents.add(GatewayIntent.MESSAGE_CONTENT);
        }
        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(intents)
                .addEventListeners(listeners.toArray())
                .build();
        try {
            jda.awaitReady();
        } catch (IllegalStateException e) {
            jda.shutdownNow();
            if (!messageContent) {
                throw e;
            }
            ConsoleLogger.logConsole("Couldn't connect with the Message Content Intent, so tags can't answer to "
                    + "!name and counting / keyword auto-reactions can't read messages. To enable them, turn on "
                    + "\"Message Content Intent\" under Privileged Gateway Intents on "
                    + "the Bot tab of your application at https://discord.com/developers/applications and restart. "
                    + "Trying again without it (/tag use still works).");
            return connect(token, listeners, false);
        }
        tags.TagCommand.setPrefixTriggersAvailable(messageContent);
        utils.MessageContent.setAvailable(messageContent);
        return jda;
    }
}
// cool
