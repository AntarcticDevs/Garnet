import logging

import discord
from discord import app_commands
from discord.ext import commands

from config import configManager

log = logging.getLogger(__name__)


class Moderation(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot

    async def cog_app_command_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        if isinstance(error, app_commands.MissingPermissions):
            message = "You don't have permission to use this command."
        elif isinstance(error, app_commands.BotMissingPermissions):
            message = "I'm missing the permissions I need to do that."
        else:
            log.error("Moderation command failed", exc_info=error)
            message = "Something went wrong running that command."

        if interaction.response.is_done():
            await interaction.followup.send(message, ephemeral=True)
        else:
            await interaction.response.send_message(message, ephemeral=True)

    @staticmethod
    def _problem_with(interaction: discord.Interaction, target: discord.Member) -> str | None:
        guild = interaction.guild
        moderator = interaction.user
        assert guild is not None

        if target.id == moderator.id:
            return "You can't do that to yourself."
        if target.id == guild.owner_id:
            return "You can't do that to the server owner."
        if target.id == guild.me.id:
            return "I can't do that to myself."
        if (
            isinstance(moderator, discord.Member)
            and moderator.id != guild.owner_id
            and target.top_role >= moderator.top_role
        ):
            return "That member's top role is equal to or higher than yours."
        if target.top_role >= guild.me.top_role:
            return "That member's top role is equal to or higher than mine."
        return None

    @staticmethod
    async def _notify(user: discord.Member, title: str, text: str, reason: str | None):
        embed = discord.Embed(title=title, description=text)
        if reason:
            embed.add_field(name="Reason", value=reason, inline=False)
        embed.set_footer(text=configManager.get_bot_name())
        try:
            await user.send(embed=embed)
        except discord.HTTPException:
            pass  # DMs closed or blocked...

    @app_commands.command(name="kick", description="Kick a member")
    @app_commands.guild_only()
    @app_commands.default_permissions(kick_members=True)
    @app_commands.checks.has_permissions(kick_members=True)
    @app_commands.checks.bot_has_permissions(kick_members=True)
    async def kick(self, interaction: discord.Interaction, user: discord.Member, reason: str | None = None):
        guild = interaction.guild
        assert guild is not None

        problem = self._problem_with(interaction, user)
        if problem:
            await interaction.response.send_message(problem, ephemeral=True)
            return

        await interaction.response.defer(ephemeral=True)
        await self._notify(user, "Kicked", f"You have been kicked from **{guild.name}**.", reason)
        try:
            await user.kick(reason=f"{interaction.user} ({interaction.user.id}): {reason or 'No reason given'}")
        except discord.HTTPException as e:
            await interaction.followup.send(f"Couldn't kick {user.mention}: {e}", ephemeral=True)
            return
        await interaction.followup.send(f"Kicked {user.mention}.", ephemeral=True)

    @app_commands.command(name="ban", description="Ban a member")
    @app_commands.guild_only()
    @app_commands.default_permissions(ban_members=True)
    @app_commands.checks.has_permissions(ban_members=True)
    @app_commands.checks.bot_has_permissions(ban_members=True)
    async def ban(self, interaction: discord.Interaction, user: discord.Member, reason: str | None = None):
        guild = interaction.guild
        assert guild is not None

        problem = self._problem_with(interaction, user)
        if problem:
            await interaction.response.send_message(problem, ephemeral=True)
            return

        await interaction.response.defer(ephemeral=True)
        await self._notify(user, "Banned", f"You have been banned from **{guild.name}**.", reason)
        try:
            await user.ban(reason=f"{interaction.user} ({interaction.user.id}): {reason or 'No reason given'}")
        except discord.HTTPException as e:
            await interaction.followup.send(f"Couldn't ban {user.mention}: {e}", ephemeral=True)
            return
        await interaction.followup.send(f"Banned {user.mention}.", ephemeral=True)

    @app_commands.command(name="purge", description="Delete recent messages")
    @app_commands.guild_only()
    @app_commands.default_permissions(manage_messages=True)
    @app_commands.checks.has_permissions(manage_messages=True)
    @app_commands.checks.bot_has_permissions(manage_messages=True, read_message_history=True)
    async def purge(self, interaction: discord.Interaction, amount: app_commands.Range[int, 1, 100]):
        channel = interaction.channel
        if not isinstance(channel, (discord.TextChannel, discord.Thread, discord.VoiceChannel)):
            await interaction.response.send_message("I can't purge messages in this channel.", ephemeral=True)
            return

        await interaction.response.defer(ephemeral=True)
        deleted = await channel.purge(limit=amount)
        await interaction.followup.send(f"Deleted **{len(deleted)}** messages.", ephemeral=True)


async def setup(bot: commands.Bot):
    await bot.add_cog(Moderation(bot))