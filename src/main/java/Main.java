import commands.Info;
import commands.UserInfo;
import commands.Avatar;
import commands.Embeds;
import commands.Ping;
import commands.Help;
import commands.Rules;
import commands.Ticket;
import commands.Purge;
import commands.Ban;
import commands.Kick;

import events.WelcomeListener;
import tickets.TicketButton;
import tickets.TicketPanelEditor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.ArrayList;

import commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import database.Database;
import java.sql.SQLException;
import utils.ConsoleLogger;

import java.io.IOException;

public class Main {

        public static void main(String[] args) throws InterruptedException {
        try {
            ConfigManager.createIfMissing();
            ConfigManager.load();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        String token = ConfigManager.getString("token");

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("token in config.yml is not set");
        }

        try {
                Database.initialize();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(
                        new Info(),
                        new UserInfo(),
                        new Avatar(),
                        new Embeds(),
                        new Ping(),
                        new Help(),
                        new Rules(),
                        new Ticket(),
                        new Purge(),
                        new Ban(),
                        new Kick(),
                        // new ComponentsTest(),
                        new TicketButton(),
                        new TicketPanelEditor()
                )
                .addEventListeners(
                        new WelcomeListener()
                )
                .build()
                .awaitReady();

        ConsoleLogger.logConsole("-----------------------𝐁𝐨𝐭 𝐦𝐚𝐝𝐞 𝐛𝐲 𝐄𝐧𝐝𝐞𝐫𝐊𝐧𝐢𝐠𝐡𝐭𝟕𝐨𝐩-----------------------");
        ConsoleLogger.logConsole("✅ Garnet is online!");

        List<Command> commands = List.<Command>of(
                new Info(),
                new UserInfo(),
                new Avatar(),
                new Embeds(),
                new Ping(),
                new Help(),
                new Rules(),
                new Ticket(),
                new Purge(),
                new Ban(),
                new Kick(),
                // new ComponentsTest(),
                new WelcomeListener()
        );

        List<CommandData> commandData = new ArrayList<>();

        for (Command command : commands) {
            commandData.add(command.getCommandData());
        }

        jda.updateCommands()
                .addCommands(commandData)
                .queue();
    }
}