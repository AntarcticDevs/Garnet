from config import configManager

MODULES = [
    "information", "activity", "moderation", "reaction-roles", "role-panels",
    "tickets", "welcomer", "embeds", "loa", "tags", "counting", "auto-react", "logging",
]


def is_enabled(key: str) -> bool:
    return configManager.get_boolean(f"modules.{key}", True)  # missing = on


def extension(key: str) -> str:
    return "cogs." + key.replace("-", "_")