from __future__ import annotations

import logging
import re
import shutil
from typing import NamedTuple

import yaml

from . import configManager

log = logging.getLogger("config")

_TOP_KEY = re.compile(r"([A-Za-z0-9_-]+):.*")
_MODULE_LINE = re.compile(r" {2}([A-Za-z0-9_-]+):.*")
_NEVER_ADDED = {"token"}


class Result(NamedTuple):
    text: str
    added: list[str]
    notes: list[str]


def run() -> list[str]:
    path = configManager.config_path()
    # newline="" keeps the file's original line endings so they can be detected.
    with path.open("r", encoding="utf-8", newline="") as f:
        original = f.read()

    modules_present = set(configManager.keys_under("modules"))
    top_present: set[str] = set()
    for key in _top_level_keys(configManager.DEFAULT_CONFIG):
        if configManager.has_key(key):
            top_present.add(key)

    result = upgrade_text(
        original,
        configManager.DEFAULT_CONFIG,
        top_present,
        modules_present,
        configManager.is_section("modules"),
    )
    for note in result.notes:
        log.warning(note)

    report = list(result.added)
    if not result.added:
        return report

    try:
        yaml.safe_load(result.text)
    except yaml.YAMLError:
        return ["Couldn't add new settings automatically (config.yml was left as it is)."]

    backup = path.with_name(path.name + ".bak")
    shutil.copyfile(path, backup)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as f:
        f.write(result.text)
    temporary.replace(path)
    configManager.load()
    return report


def upgrade_text(
    original: str,
    template: str,
    top_present: set[str],
    modules_present: set[str],
    modules_is_section: bool,
) -> Result:
    eol = "\r\n" if "\r\n" in original else "\n"
    lines = re.split(r"\r?\n", original)
    if lines and lines[-1] == "":
        lines.pop()
    added: list[str] = []
    notes: list[str] = []
    template_lines = template.split("\n")

    # 1) Add missing entries inside an existing "modules:" section.
    modules_index = _index_of_key(lines, "modules")
    missing_modules: list[str] = []
    for line in template_lines:
        m = _MODULE_LINE.fullmatch(line)
        if m and m.group(1) not in modules_present:
            missing_modules.append(line)
            added.append("modules." + m.group(1))

    if modules_index >= 0 and missing_modules:
        modules_line = lines[modules_index]
        inline_value = (
            re.fullmatch(r"modules:\s*\S.*", modules_line) is not None
            and re.fullmatch(r"modules:\s*#.*", modules_line) is None
        )
        if not modules_is_section or inline_value:
            notes.append(
                "modules in config.yml is written in a form I can't extend; add the new features by "
                "hand (missing ones stay on)."
            )
            added[:] = [a for a in added if not a.startswith("modules.")]
        else:
            last = modules_index
            for index in range(modules_index + 1, len(lines)):
                line = lines[index]
                if re.fullmatch(r"\s+\S.*", line):
                    last = index
                elif line.strip() != "":
                    break
            lines[last + 1:last + 1] = missing_modules

    # 2) Append missing top-level settings (with their comment blocks) at the end.
    append: list[str] = []
    modules_block_needed = modules_index < 0
    for index, template_line in enumerate(template_lines):
        key = _TOP_KEY.fullmatch(template_line)
        if not key:
            continue
        name = key.group(1)
        if name in _NEVER_ADDED or name in top_present or _index_of_key(lines, name) >= 0:
            continue

        start = index
        while start > 0 and template_lines[start - 1].startswith("#"):
            start -= 1
        if append and start < index:
            append.append("")

        if name == "modules":
            if not modules_block_needed:
                continue
            end = index + 1
            while end < len(template_lines) and _MODULE_LINE.fullmatch(template_lines[end]):
                end += 1
            append.extend(template_lines[start:end])
            added[:] = [a for a in added if not a.startswith("modules.")]
            added.append("modules")
        else:
            append.extend(template_lines[start:index + 1])
            added.append(name)

    if append:
        if lines and lines[-1].strip() != "":
            lines.append("")
        lines.extend(append)

    return Result(eol.join(lines) + eol, added, notes)


def _index_of_key(lines: list[str], key: str) -> int:
    pattern = re.compile(re.escape(key) + r":.*")
    for index, line in enumerate(lines):
        if pattern.fullmatch(line):
            return index
    return -1


def _top_level_keys(template: str) -> list[str]:
    keys = []
    for line in template.split("\n"):
        m = _TOP_KEY.fullmatch(line)
        if m:
            keys.append(m.group(1))
    return keys