import config.ConfigManager;
import commands.Help;
import events.ConsoleActivityLogger;
import modules.BotModule;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

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
        try {
            boolean created = ConfigManager.createIfMissing();
            ConfigManager.load();
            if (created) {
                ConsoleLogger.logConsole("config.yml was created. Open it, add your bot token, then start the bot again.");
                System.exit(1);
                return;
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
            e.printStackTrace();
        }

        // Help and the activity logger are always on; every other feature is a module that can be
        // switched off in config.yml. Each listener that is also a Command adds its slash command.
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
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(listeners.toArray())
                    .build()
                    .awaitReady();
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
}