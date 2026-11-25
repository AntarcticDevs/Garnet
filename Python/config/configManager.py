"""Python port of the Java ConfigManager (configManager.py). Requires PyYAML: pip install pyyaml"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

CONFIG_PATH = Path("config.yml")

DEFAULT_CONFIG = """\
# -----------------------------------------------------------------------------
# Bot configuration. Edit this file, save it, then start the bot again.
# Tip: keep values inside quotes, for example  token: 'your-token-here'
# -----------------------------------------------------------------------------

# Your bot's token (https://discord.com/developers/applications -> your app -> Bot).
# Never share it. On that same page, turn on "Server Members Intent" under
# "Privileged Gateway Intents", otherwise the bot cannot start. Also turn on
# "Message Content Intent" there so tags can answer to !name (optional).
token: ''

# The name shown in /help, embed footers and the console.
bot-name: 'Garnet'

# Where the bot keeps its data (ticket panels, embeds, ...). Leave blank to use
# data/garnet.db. A bot that already has a garnet.db next to the jar keeps using it.
database-path: ''

# Turn features on or off (true = on, false = off) and restart the bot afterwards.
# A feature missing from this list stays on. Turning one off also removes its slash commands
# from Discord and hides it from /help.
modules:
  information: true   # /ping, /info, /userinfo, /avatar
  activity: true      # admin-only /activity
  moderation: true    # /ban, /kick, /purge
  reaction-roles: true # /reaction-roles
  role-panels: true   # /rolepanel: role menus with buttons, dropdown or reactions
  tickets: true       # ticket panels, ticket channels and transcripts
  welcomer: true      # welcome messages for new members
  embeds: true        # /embed and /rules
  loa: true           # /loa: leave of absence requests with staff review
  tags: true          # /tag: custom commands such as !ip that reply with a message or embed
  counting: true      # /counting: a counting game (needs Message Content Intent)
  auto-react: true    # /autoreact: the bot adds reactions to messages automatically
  logging: true       # /logs: a log channel for tickets, members, moderation and more

# Let members trigger tags by typing a prefix and the tag name, such as !ip (each server can
# change the prefix with /tag prefix). This needs the "Message Content Intent": turn it on under
# "Privileged Gateway Intents" on the Bot tab of your application. If it is off the bot still
# starts, but only /tag use works. Set to false to never ask for it.
tag-prefix-commands: true

# Optional links shown as buttons in /help. Leave a link blank to hide its button.
support-url: ''
terms-url: ''
privacy-url: ''
security-url: ''

# Optional picture shown in /help. Leave blank to use the bot's own avatar.
help-thumbnail-url: ''
"""

_config: dict[str, Any] = {}


def add_missing_settings() -> list[str]:
    # Java: ConfigUpgrader.run() -- that class wasn't included, so port it separately.
    from configUpgrader import run  # type: ignore

    return run()


def config_path() -> Path:
    return CONFIG_PATH


def create_if_missing() -> bool:
    if CONFIG_PATH.exists():
        return False
    CONFIG_PATH.write_text(DEFAULT_CONFIG, encoding="utf-8")
    return True


def load() -> None:
    global _config
    try:
        with CONFIG_PATH.open("r", encoding="utf-8") as f:
            loaded = yaml.safe_load(f)
    except yaml.YAMLError as exc:
        raise IOError(f"config.yml is not valid YAML: {exc}") from exc

    if loaded is None:
        _config = {}
        return
    if not isinstance(loaded, dict):
        raise IOError("config.yml must contain settings written as  key: value")

    _config = {str(k): v for k, v in loaded.items()}


def _to_text(value: Any) -> str:
    # Match Java's String.valueOf: booleans become "true"/"false".
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def get_string(key: str) -> str | None:
    value = _find(key)
    if value is None:
        return None
    text = _to_text(value).strip()
    return text or None


def get_bot_name() -> str:
    return get_string("bot-name") or "Bot"


def get_boolean(key: str, fallback: bool) -> bool:
    text = get_string(key)
    if text is None:
        return fallback
    value = _parse_boolean(text)
    return fallback if value is None else value


def looks_like_boolean(text: str | None) -> bool:
    return text is not None and _parse_boolean(text.strip()) is not None


def keys_under(path: str) -> set[str]:
    # Python sets are unordered; use a dict to keep insertion order like LinkedHashSet.
    section = _find(path)
    if isinstance(section, dict):
        return set(dict.fromkeys(str(k) for k in section))
    return set()


def keys_under_ordered(path: str) -> list[str]:
    """Same as keys_under but preserves YAML order."""
    section = _find(path)
    if isinstance(section, dict):
        return [str(k) for k in section]
    return []


def is_section(path: str) -> bool:
    return isinstance(_find(path), dict)


def has_key(key: str) -> bool:
    return key in _config


def _parse_boolean(text: str) -> bool | None:
    lowered = text.lower()
    if lowered in ("true", "yes", "on"):
        return True
    if lowered in ("false", "no", "off"):
        return False
    return None


def _find(path: str) -> Any:
    # Exact top-level key first (so keys containing dots still work).
    if path in _config:
        return _config[path]

    current: Any = _config
    for part in path.split("."):
        if not isinstance(current, dict):
            return None
        nxt = None
        for k, v in current.items():
            if str(k) == part:
                nxt = v
                break
        current = nxt
    return current