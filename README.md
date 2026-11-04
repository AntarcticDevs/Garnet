# Garnet

Garnet is a self-hosted Discord bot engine written in Java. You run one jar, it creates its own
`config.yml`, you paste in your bot token, and you have your own bot with tickets, logging,
moderation, role panels, reaction roles, leave of absence, embeds and a welcomer. Every feature can be switched on or off in the config.

> Garnet is under active development. Run it on a test server first and keep backups of `data/`.

## Features

| Feature | What it does |
| --- | --- |
| **Tickets** | Panels with a button or category dropdown, per-category forms, claim / transfer / close / reopen, delete confirmation, transcripts, per-member limits, welcome message per panel, and a panel editor inside Discord |
| **Logging** | `/logs` sets a log channel for tickets, members, moderation, server changes and (optionally) voice |
| **Moderation** | `/ban`, `/kick`, `/purge` |
| **Role panels** | `/rolepanel`: a posted message where members pick roles with buttons, a dropdown or reactions; edited inside Discord |
| **Reaction roles** | Choose emoji-reaction or button-style role assignment with `/reaction-roles`; mappings survive restarts |
| **Embeds** | `/embed` builder, saved embeds, `/rules` |
| **Leave of absence** | `/loa`: members request leave, staff approve or deny with buttons, the LOA role is added and removed automatically |
| **Welcomer** | Welcome message for new members using a saved embed |
| **Information** | `/ping`, `/info`, `/userinfo`, `/avatar` |
| **Activity** | Admin-only `/activity`; activity settings, presets, rotation, schedules and member milestones are stored in `data/activity.json` |
| **Help** | `/help` lists only the features you turned on |

## Requirements

- **Java 21 or newer** to run it (`java -version`)
- **Maven** and a JDK to build it yourself, or a ready-made `main.jar`
- A Discord application with a bot user: <https://discord.com/developers/applications>

## Quick start

1. **Build** (skip if you were given a jar):

   ```
   mvn package
   ```

   The jar is `target/main.jar`.

2. **Run it once** in the folder where the bot should live:

   ```
   java -jar main.jar
   ```

   It creates `config.yml` and stops.

3. **Add the token.** Open `config.yml` and put your token between the quotes:

   ```yaml
   token: 'your-bot-token'
   ```

   Never share the token. If it leaks, reset it on the Bot page of your application.

4. **Turn on the Server Members Intent.** In the developer portal: *Bot* → *Privileged Gateway Intents* →
   *Server Members Intent*. Without it the bot refuses to start (it tells you so).

5. **Invite the bot** with the `bot` and `applications.commands` scopes. A good set of permissions:
   Manage Channels, Manage Roles, View Channels, Send Messages, Embed Links, Attach Files,
   Read Message History, Manage Messages, Kick Members, Ban Members and View Audit Log.
   (Administrator also works, but the bot only needs the above.)

6. **Start it again:**

   ```
   java -jar main.jar
   ```

   Slash commands are registered on every start. They can take a moment to show up in Discord.

To keep it running after you close the terminal, use `screen`, `tmux`, a systemd service or your host's
panel (any host that can run `java -jar` works).

## Configuration

Everything lives in `config.yml`, which is created next to the jar and never committed to Git.
Restart the bot after changing it.

```yaml
token: ''                 # your bot token
bot-name: 'Garnet'        # shown in /help and the console
database-path: ''         # blank = data/garnet.db

modules:                  # true = on, false = off
  information: true
  activity: true
  moderation: true
  role-panels: true
  reaction-roles: true
  tickets: true
  welcomer: true
  loa: true
  embeds: true
  logging: true

support-url: ''           # optional buttons in /help; blank hides them
terms-url: ''
privacy-url: ''
security-url: ''
help-thumbnail-url: ''    # blank = the bot's avatar
```

- A feature missing from `modules:` stays on, so an old config keeps working after an update.
- Turning a module off removes its slash commands and its section in `/help`.
- Typos and invalid values in `modules:` are reported when the bot starts.

## Files the bot creates

| Path | Contents |
| --- | --- |
| `config.yml` | Your settings |
| `data/garnet.db` | Everything the bot saves (panels, tickets, embeds, reaction roles, log settings). **Back this up.** |
| `data/activity.json` (next to the database if you set `database-path`) | Bot activity and presence settings. Stop the bot before editing; back it up to preserve presets, schedules and member-milestone rules. |
| `logs/latest.log` | This run's log. `previous.log` is the run before. Send `latest.log` when asking for help. |

## Updating

Stop the bot, replace the jar, start it again. New versions add database columns and tables on startup
and never remove existing data, so panels, tickets, embeds and reaction roles carry over.

A bot from an older version that has `garnet.db` next to the jar keeps using that file. To move it into the
data folder, stop the bot, move the file to `data/garnet.db` and start it again (or set `database-path`).

## Tickets

Setup needs **Manage Server**. A short walkthrough:

1. `/ticket panel create name:Support`
2. `/ticket panel edit name:Support` opens the editor. It is one message that updates in place:
   - **Appearance:** title, description, colour, images, footer and the button
   - **Roles & routing:** staff roles, ping roles, the Discord category for new tickets, the panel channel
     and the transcript channel
   - **Categories:** the dropdown entries, each with an optional description and emoji, and the questions
     members answer for it (short or paragraph, required or optional, reorderable)
   - **Behaviour:** enable or disable the panel, the disabled message, a per-member ticket limit and the
     welcome message (`{user}`, `{username}`, `{category}`, `{panel}`)
3. Pick the panel channel in the editor and press **Publish**. Pressing **Update panel** later edits the same
   message instead of posting a new one.

In a ticket: members can close and reopen their own ticket; staff (a staff role on the panel, or Manage Channels)
can claim, unclaim, transfer, and get a private transcript. Only admins can delete, and they are asked to confirm.

**Transcripts.** The Transcript button and `/ticket transcript` reply privately to whoever asked. When a ticket is
closed or deleted, the transcript is posted to the panel's transcript channel if one is set. A delete waits for the
transcript, so a ticket is not deleted if the transcript could not be posted.

`/ticket panel disable name [message]` swaps the button or dropdown on the posted panel for a disabled notice
(your message, or a default). `/ticket panel enable name` restores it.

## Logging

`/logs channel #channel` turns it on, `/logs status` shows the settings and `/logs toggle` switches a category:

| Category | Events |
| --- | --- |
| `tickets` | opened, closed, reopened, claimed, unclaimed, transferred, deleted |
| `members` | joins, leaves, nickname and role changes |
| `moderation` | bans, unbans, kicks, purges |
| `server` | channels and roles created or deleted |
| `roles` | roles given or removed by role panels |
| `voice` | voice joins, leaves and moves (off by default; it is noisy) |

The bot needs View Channel, Send Messages and Embed Links in the log channel, and View Audit Log to show who
banned someone.

## Role panels

`/rolepanel create name` makes a panel and opens an editor inside Discord (it needs Manage Server). Pick the
roles, set how it looks, choose the channel and press **Publish**. Pressing **Update panel** later edits the
same message in place, so existing buttons and menus keep working.

- **Styles:** buttons (up to 25 roles), a dropdown menu (up to 25), or emoji reactions (every role needs an emoji).
- **Modes:** *toggle* (pick again to lose it), *pick one* (colours, regions), *add only* (verification) and
  *remove only* (opt-outs).
- **Options:** a limit on how many roles one member can hold, a required role, a blocked role, a custom
  label, emoji, description and button colour per role, and enabling or disabling the panel with its own message.
- **Replies:** button and dropdown answers are private to the member. Discord has no private replies to
  reactions, so reaction panels stay silent.
- **Logging:** role changes are posted to the log channel under the `roles` category (see `/logs`).

The bot needs Manage Roles, its own role must sit above every role a panel hands out, and it needs View Channel,
Send Messages and Embed Links in the panel channel (plus Add Reactions for the reactions style). `/rolepanel edit name`
reopens the editor and `/rolepanel list` shows every panel. `/reaction-roles` still works for putting roles on
messages that already exist.

## Leave of absence

Run `/loa setup` (needs Manage Server) and choose the review channel and the LOA role. Optionally add reviewer
roles, roles to ping for each request, the shortest reason, the longest leave and a display name for your team.

- `/loa request reason duration [ign] [rank]`: a member asks for leave. Durations look like `12h`, `3d`, `1w` or
  `1w 2d` (at least 1 hour). `ign` and `rank` are optional extra details for teams that use them.
- The request appears in the review channel with **Approve** and **Deny** buttons. Only reviewers (the reviewer
  roles, or anyone with Manage Server) can press them, nobody can review their own request, and a denial can
  include a reason that is sent to the member.
- On approval the member gets the LOA role. It is removed when the leave ends, even if the bot was restarted
  in the meantime, and the member is told by direct message.
- `/loa cancel` withdraws a pending request or ends a running leave early. Reviewers can use `/loa list`,
  `/loa end member` and `/loa stats [reviewer]` (how many requests each reviewer approved or denied).

The bot needs Manage Roles with its role above the LOA role, and View Channel, Send Messages and Embed Links in
the review channel. Requests are saved in the database, so nothing is lost on restart.

## Welcomer

Greets new members with one of your saved embeds (build it with `/embed create`). Needs Manage Server.

- `/welcomer setchannel channel` where welcome messages are posted, `/welcomer setembed` which saved embed is used.
- `/welcomer ping on/off` whether the message mentions the new member.
- `/welcomer enable` and `disable` turn it on or off without losing the settings.
- `/welcomer test [user]` sends a test message.

Placeholders you can use in the embed's text and image links: `{user}` (name), `{user_mention}`, `{server}`,
`{member_count}`, `{member_count_ordinal}` (1st, 2nd, 3rd...), `{server_icon}` and `{member_avatar}`. They are
filled in each time someone joins.

## Reaction roles

`/reaction-roles add` puts a role on any message, either as a reaction or as a button. It needs Manage Server,
and you can only use roles below your own highest role.

- **Emoji reaction:** give an emoji, a role and the message ID. Garnet adds the reaction, gives the role
  when a member reacts, and takes it away when they remove their reaction. (Discord has no private replies
  to reactions, so there is no confirmation message for this style.)
- **Button:** give a role and the message ID; an optional emoji and label customise the button. Each click toggles
  the role and answers with a private message only that member sees. Buttons can only be added to messages the
  bot sent itself, for example one made with `/embed send`.

The message is looked up in the channel you run the command in, or the channel you pick with `channel`.
`/reaction-roles list` shows everything that is set up, and `/reaction-roles remove message-id [emoji]` removes one
emoji or all roles on a message. Roles are forgotten automatically when their message is deleted.

Garnet needs Manage Roles, Add Reactions (emoji style), and its highest role must be above the roles it assigns.
Roles are stored in `data/garnet.db` and keep working after restarts.

## Troubleshooting

- **"Discord rejected the bot token"**: copy the token again from the Bot page into `config.yml`.
- **Bot won't start and mentions an intent**: turn on the Server Members Intent (step 4).
- **Commands don't appear**: wait a minute, check the bot was invited with `applications.commands`, restart it.
- **Ticket channels aren't created**: give the bot Manage Channels and Manage Roles, and make sure its role is above the
  roles you use as staff roles.
- **Something else**: open `logs/latest.log`; errors include the full stack trace.

## Building from source

```
git clone https://github.com/EnderKnight7op/Garnet.git
cd Garnet
mvn package
```

Java 21, JDA 6.7.0, SQLite (`sqlite-jdbc`), SnakeYAML and Gson. To add a feature, add a constant to
`modules/BotModule.java` with the listeners it needs and a line to the `modules:` section in `ConfigManager`.
