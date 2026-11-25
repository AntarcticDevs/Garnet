from __future__ import annotations

import inspect
from dataclasses import dataclass

import discord
from discord import app_commands
from discord.ext import commands

from config import configManager, modules

MENU_ID = "help:page"
OVERVIEW = "overview"
ACCENT = discord.Colour(0x5865F2)


def _b(text: str) -> str:
    return inspect.cleandoc(text)


@dataclass(frozen=True)
class Page:
    module: str   # key in the config.yml "modules" section
    key: str      # value used in the dropdown menu
    emoji: str
    title: str
    summary: str
    body: str


PAGES: list[Page] = [
    # Page(
    #     module="information",
    #     key="information",
    #     emoji="ℹ️",
    #     title="Information",
    #     summary="Latency, server, user and avatar info",
    #     body=_b("""
    #         `/ping` Shows the bot's latency.
    #         `/info` Shows information about this server.
    #         `/userinfo [user]` Shows information about a member.
    #         `/avatar [user]` Shows a member's avatar.
    #     """),
    # ),
    # Page(
    #     module="activity",
    #     key="activity",
    #     emoji="🎮",
    #     title="Activity",
    #     summary="Set or reset the bot's presence",
    #     body=_b("""
    #         `/activity set type text [url]` Sets the bot's activity. Streaming requires a Twitch or YouTube URL.
    #         `/activity reset` Clears the bot's activity.
    #         *Admins only. Presets, rotation, schedules and member-milestone rules can be configured in
    #         `data/activity.json`.*
    #     """),
    # ),
    # Page(
    #     module="moderation",
    #     key="moderation",
    #     emoji="🛡️",
    #     title="Moderation",
    #     summary="Ban, kick and clean up messages",
    #     body=_b("""
    #         `/purge amount` Deletes up to 100 recent messages. *Needs Manage Messages.*
    #         `/kick user [reason]` Kicks a member. *Needs Kick Members.*
    #         `/ban user [reason]` Bans a user. *Needs Ban Members.*
    #     """),
    # ),
    # Page(
    #     module="reaction-roles",
    #     key="reaction-roles",
    #     emoji="🎭",
    #     title="Reaction roles",
    #     summary="Give members a role with a reaction or button",
    #     body=_b("""
    #         `/reaction-roles add style role message-id [emoji] [label] [channel]` Adds a role to a message.
    #         Emoji style gives the role on reaction and takes it away when the reaction is removed.
    #         Button style toggles the role on each click and answers privately; buttons can only go on
    #         messages the bot sent (for example with `/embed send`).
    #         `/reaction-roles remove message-id [emoji]` Removes one emoji, or everything on the message.
    #         `/reaction-roles list` Shows every reaction and button role.
    #         *Needs Manage Server; the bot needs Manage Roles.*
    #     """),
    # ),
    # Page(
    #     module="role-panels",
    #     key="role-panels",
    #     emoji="🎛️",
    #     title="Role panels",
    #     summary="A posted message where members pick their own roles",
    #     body=_b("""
    #         `/rolepanel create name` Creates a panel and opens its editor.
    #         `/rolepanel edit name` Opens the editor: roles, look, style, mode, access.
    #         `/rolepanel list` Shows every panel and where it is posted.
    #         **Styles:** buttons, a dropdown menu, or reactions.
    #         **Modes:** toggle, pick one, add only, remove only. Optional role limit, required role and
    #         blocked role. Button and dropdown replies are private; reactions can't reply privately.
    #         *Needs Manage Server; the bot needs Manage Roles and its role above the roles it hands out.*
    #     """),
    # ),
    # Page(
    #     module="tickets",
    #     key="tickets",
    #     emoji="🎫",
    #     title="Tickets",
    #     summary="Panels, private ticket channels and transcripts",
    #     body=_b("""
    #         **Set up** *(needs Manage Server)*
    #         `/ticket panel create name` Creates a panel.
    #         `/ticket panel edit name` Opens the panel editor: look, roles, categories, questions, limits.
    #         `/ticket panel send name channel` Posts the panel.
    #         `/ticket panel disable name [message]` and `enable name` Turns a panel off or on.
    #         `/ticket panel staff-roles`, `ping-roles`, `transcript-channel` Quick shortcuts for routing.
    #         `/ticket set-category category` Default Discord category for new tickets.
    #
    #         **Inside a ticket**
    #         `/ticket close`, `reopen` The creator or staff.
    #         `/ticket claim`, `unclaim`, `transfer member` Staff.
    #         `/ticket transcript` A private transcript, staff only.
    #         `/ticket delete` Asks to confirm, then deletes. Admins only.
    #         -# When a ticket is closed or deleted, its transcript goes to the panel's transcript channel.
    #     """),
    # ),
    # Page(
    #     module="welcomer",
    #     key="welcomer",
    #     emoji="👋",
    #     title="Welcomer",
    #     summary="Greets new members",
    #     body=_b("""
    #         `/welcomer setchannel channel` Where welcome messages go.
    #         `/welcomer setembed` Choose which saved embed is used.
    #         `/welcomer ping on/off` Whether the welcome message mentions the new member.
    #         `/welcomer enable`, `disable` Turn welcome messages on or off (settings are kept).
    #         `/welcomer test [user]` Sends a test welcome message.
    #         *Needs Manage Server.* Embed placeholders: `{user}` `{user_mention}` `{server}` `{member_count}` `{member_count_ordinal}` `{server_icon}` `{member_avatar}`.
    #     """),
    # ),
    # Page(
    #     module="embeds",
    #     key="embeds",
    #     emoji="🧩",
    #     title="Embeds & rules",
    #     summary="Build and send custom embeds, set the rules",
    #     body=_b("""
    #         `/embed create`, `edit`, `send`, `delete` Build embeds, save them and post them anywhere.
    #         `/rules select` Pick up to five saved embeds as this server's rules.
    #     """),
    # ),
    # Page(
    #     module="loa",
    #     key="loa",
    #     emoji="🏖️",
    #     title="Leave of absence",
    #     summary="Members request leave, staff approve or deny",
    #     body=_b("""
    #         `/loa request reason duration [ign] [rank]` Ask for leave (for example `3d` or `1w 2d`).
    #         `/loa cancel` Withdraw your request, or end your leave early.
    #         **Reviewers** `/loa list`, `/loa end member`, `/loa stats [reviewer]`, plus the Approve and Deny buttons.
    #         `/loa setup` Review channel, LOA role, reviewer and ping roles, rules. *Needs Manage Server.*
    #         The LOA role is added on approval and removed when the leave ends, even after a restart.
    #     """),
    # ),
    # Page(
    #     module="tags",
    #     key="tags",
    #     emoji="🏷️",
    #     title="Tags",
    #     summary="Custom commands that reply with a message or embed",
    #     body=_b("""
    #         Members type the prefix and the tag name (for example `!ip`) or use `/tag use name`.
    #         `/tag list` Shows every tag in this server.
    #         `/tag create name [embed] [required-role]` Creates a tag: a form asks for the text.
    #         `/tag edit name [embed] [required-role]` Changes a tag (also `no-embed`, `no-role`).
    #         `/tag delete name` Removes a tag. `/tag prefix [prefix|off]` Shows or changes the prefix (default `!`).
    #         *Managing tags needs Manage Server.* Text placeholders: `{user}` `{user_mention}` `{server}` `{member_count}`.
    #     """),
    # ),
    # Page(
    #     module="counting",
    #     key="counting",
    #     emoji="🔢",
    #     title="Counting",
    #     summary="A channel where everyone counts upward together",
    #     body=_b("""
    #         Type the next number in the counting channel. Right: ✅. Wrong or twice in a row: the message is deleted and the count carries on (optionally it restarts from 1).
    #         `/counting status` Current number and high score. `/counting leaderboard` Top counters.
    #         `/counting setup channel [reset-on-mistake] [allow-double]` Pick the channel and rules. *Needs Manage Server.*
    #         `/counting reset`, `/counting set-number number`, `/counting disable` *Need Manage Server.*
    #     """),
    # ),
    # Page(
    #     module="auto-react",
    #     key="autoreact",
    #     emoji="😀",
    #     title="Auto-reactions",
    #     summary="The bot reacts to messages automatically",
    #     body=_b("""
    #         `/autoreact add emojis [channel] [when] [keyword] [include-bots]` React to every message, or only ones with an image, a link or a word.
    #         `/autoreact list` Shows the rules. `/autoreact remove id` Deletes one.
    #         *Needs Manage Server.* Up to 25 rules per server, 5 emoji each.
    #     """),
    # ),
    # Page(
    #     module="logging",
    #     key="logging",
    #     emoji="📜",
    #     title="Logging",
    #     summary="A log channel for tickets, members, moderation and more",
    #     body=_b("""
    #         `/logs channel [channel]` Choose where logs are posted (leave empty to turn logging off).
    #         `/logs toggle category on/off` Switch tickets, members, moderation, server or voice logs.
    #         `/logs status` Shows what is being logged. *Needs Manage Server.*
    #     """),
    # ),
]


def enabled_pages() -> list[Page]:
    return [page for page in PAGES if modules.is_enabled(page.module)]


class HelpView(discord.ui.LayoutView):
    def __init__(self, bot: commands.Bot, requested: str = OVERVIEW):
        super().__init__(timeout=900)
        self.bot = bot

        bot_name = configManager.get_bot_name()
        pages = enabled_pages()
        current = next((p for p in pages if p.key == requested), None)

        thumbnail_url = configManager.get_string("help-thumbnail-url")
        if thumbnail_url is None and bot.user is not None:
            thumbnail_url = bot.user.display_avatar.url

        heading = discord.ui.TextDisplay(
            f"# {bot_name} Help\n"
            + (
                "Pick a feature from the menu to see its commands."
                if current is None
                else f"{current.emoji} **{current.title}**"
            )
        )

        container = discord.ui.Container(accent_colour=ACCENT)
        if thumbnail_url:
            container.add_item(discord.ui.Section(heading, accessory=discord.ui.Thumbnail(thumbnail_url)))
        else:
            container.add_item(heading)
        container.add_item(discord.ui.Separator(visible=True, spacing=discord.SeparatorSpacing.large))

        if current is None:
            overview = "**What I can do**\n"
            for page in pages:
                overview += f"{page.emoji} **{page.title}** · {page.summary}\n"
            overview += "\n-# Slash commands: type `/` and pick one. Many are limited to staff."
            container.add_item(discord.ui.TextDisplay(overview))
        else:
            container.add_item(discord.ui.TextDisplay(current.body))

        container.add_item(discord.ui.Separator(visible=True, spacing=discord.SeparatorSpacing.small))

        selected = current.key if current is not None else OVERVIEW
        options = [
            discord.SelectOption(
                label="Overview",
                value=OVERVIEW,
                emoji="🏠",
                description="Everything this bot can do",
                default=selected == OVERVIEW,
            )
        ]
        for page in pages:
            options.append(
                discord.SelectOption(
                    label=page.title,
                    value=page.key,
                    emoji=page.emoji,
                    description=page.summary,
                    default=selected == page.key,
                )
            )
        self.menu = discord.ui.Select(custom_id=MENU_ID, placeholder="Browse a feature", options=options)
        self.menu.callback = self.on_select
        container.add_item(discord.ui.ActionRow(self.menu))

        link_buttons = [
            discord.ui.Button(style=discord.ButtonStyle.link, label=label, url=url)
            for key, label in (
                ("support-url", "Support Server"),
                ("terms-url", "Terms of Service"),
                ("privacy-url", "Privacy"),
                ("security-url", "Security"),
            )
            if (url := configManager.get_string(key)) is not None
        ]
        if link_buttons:
            container.add_item(discord.ui.ActionRow(*link_buttons))

        plural = "" if len(pages) == 1 else "s"
        container.add_item(discord.ui.TextDisplay(f"-# {bot_name} · {len(pages)} feature{plural} enabled"))

        self.add_item(container)

    async def on_select(self, interaction: discord.Interaction):
        await interaction.response.edit_message(view=HelpView(self.bot, self.menu.values[0]))


class Help(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot

    @app_commands.command(name="help", description="Shows what this bot can do")
    async def help_command(self, interaction: discord.Interaction):
        await interaction.response.send_message(view=HelpView(self.bot), ephemeral=True)


async def setup(bot: commands.Bot):
    await bot.add_cog(Help(bot))