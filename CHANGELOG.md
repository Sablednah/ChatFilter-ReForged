# Changelog

## 3.0.0 — unreleased

The NeoForge rebuild. The version carries on from the Bukkit plugin's 2.0.0.

### Added (over the Bukkit plugin)

- Silent masking through the client's own chat filter, and `chatfilter.see` to read the original.
- `REPLACE`, `SHADOW`, `BLOCK` and `LOG` actions, chosen per category.
- A `severe` word list, empty by default, that blocks and costs three strikes.
- Leetspeak, stretched, spelled-out, self-censored, full-width and look-alike letters, zero-width
  characters and hidden colour codes are all caught; whole-word endings (`boob` catches `boobs`);
  an allowlist; regular-expression entries.
- Link and IP blocking with an allowlist of domains; shouting, repeats and floods.
- Strikes with a configurable escalation ladder; staff alerts with a per-person toggle.
- Commands are parsed, and only their message is judged.
- `/chatfilter test`, `add`, `remove`, `list`, `strikes` and `alerts`.
- Integration with SableCraft Standards' chat, channels, `/msg`, `/r`, `/me` and `/mail`.

### Fixed (from the Bukkit plugin)

- The "eleven" triggers can be removed, and they stay removed.
- A leading backtick, or a word caught through an accent, no longer slips through censoring.
- `/me` and private messages are filtered.
- Nothing reaches the console when the console log is switched off.
