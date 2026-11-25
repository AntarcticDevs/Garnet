import asyncio
import os
import sys

import discord
from discord.ext import commands

import utils.consoleLogger
from config import configManager
from config import modules

from pathlib import Path


def loadConfig() -> tuple[str, str]:
    try:
        if configManager.create_if_missing():
            print("Created config.yml. Fill in your token, save it, then start the bot again.")
            sys.exit(0)

        configManager.load()

        added = configManager.add_missing_settings()
        if added:
            print("Added new settings to config.yml: " + ", ".join(added))
            print("A backup of your old file was saved as config.yml.bak.")

    except IOError as e:
        print(f"Config error: {e}")
        sys.exit(1)

    token = configManager.get_string("token")
    if token is None:
        print("No token set in config.yml.")
        sys.exit(1)

    botName = configManager.get_string("bot-name")
    if botName is None:
        print("No custom name set in config.yml for the bot, using Garnet.")
        botName = "Garnet"
    
    return token, botName


token, botName = loadConfig()


class Garnet(commands.Bot):
    print("---------------------------------------------------------------------------------------")
    def __init__(self):
        intents = discord.Intents.default()
        intents.members = True
        intents.message_content = (
            (modules.is_enabled("tags") and configManager.get_boolean("tag-prefix-commands", True))
            or modules.is_enabled("counting")
            or modules.is_enabled("auto-react")
        )

        super().__init__(
            command_prefix="!",
            intents=intents,
            help_command=None
        )

    async def setup_hook(self):
        await self.load_extension("cogs.help")  # always on



        enabled, disabled = [], []

        for key in modules.MODULES:
            if not modules.is_enabled(key):
                disabled.append(key)
                continue
            try:
                await self.load_extension(modules.extension(key))
                enabled.append(key)
            except commands.ExtensionNotFound:
                print(f"⚠️ module '{key}' is on but cogs/{key.replace('-', '_')}.py doesn't exist yet")
            except Exception as e:
                print(f"❌ Failed to load module '{key}': {e}")

        print("Features on: " + (", ".join(enabled) or "none")
            + (" | off: " + ", ".join(disabled) if disabled else ""))

        synced = await self.tree.sync()
        print(f"Synced {len(synced)} commands")

        for command in synced:
            print(f"   /{command.name}")

    async def on_ready(self):


        print(f"Logged in as {self.user}")
        print(f"Loaded in {len(self.guilds)} servers")
        print("""
                   █████████                                           █████
                  ███░░░░░███                                         ░░███
                 ███     ░░░   ██████   ████████  ████████    ██████  ███████
                ░███          ░░░░░███ ░░███░░███░░███░░███  ███░░███░░░███░
                ░███    █████  ███████  ░███ ░░░  ░███ ░███ ░███████   ░███
                ░░███  ░░███  ███░░███  ░███      ░███ ░███ ░███░░░    ░███ ███
                 ░░█████████ ░░████████ █████     ████ █████░░██████   ░░█████
                  ░░░░░░░░░   ░░░░░░░░ ░░░░░     ░░░░ ░░░░░  ░░░░░░     ░░░░░
                """)
        print("-----------------------𝐁𝐨𝐭 𝐦𝐚𝐝𝐞 𝐛𝐲 𝐄𝐧𝐝𝐞𝐫𝐊𝐧𝐢𝐠𝐡𝐭𝟕𝐨𝐩-----------------------")
        print(("✅ " + botName + " is online!"))


bot = Garnet()


async def main():
    async with bot:
        await bot.start(token)

asyncio.run(main())