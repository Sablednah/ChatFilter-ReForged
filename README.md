# ChatFilter ReForged

A server-side chat filter for NeoForge. It censors or stops words in chat and in chat-like commands,
sees through the usual disguises, blocks links and spam, and needs nothing installed on the client.

The modern rebuild of [ChatFilter](https://legacy.curseforge.com/minecraft/bukkit-plugins/chatfilter),
the 2012 Bukkit plugin, rebuilt from scratch rather than ported. Everything people asked for on the
old project page is in it.

## What it does

- **Masks swearing silently.** By default a caught word reaches other players as `####`, drawn by
  their own client's chat filter (hover: *filtered by the server*). The sender sees exactly what they
  typed and is told nothing, chat stays signed and reportable, and players with `chatfilter.see`
  read the original.
- **Or replaces, shadows, blocks or just logs, per category.** `REPLACE` rewrites the word (the old
  `!@$#`). `SHADOW` is a soft-mute: the sender sees their line sent and nobody else sees it.
  `BLOCK` stops it, with the classic *"Oi Steve! Mind your language!"* if you want it.
- **Sees through disguises:** capitals, accents, leetspeak (`sh1t`, `$h!t`), stretched letters
  (`fuuuck`), spelled-out letters (`f.u.c.k`, `s h i t`), self-censoring (`f*ck`), full-width and
  look-alike letters from other alphabets, zero-width characters and colour codes hidden mid-word.
- **Leaves innocent words alone.** Part-word and whole-word lists (so `ass` catches neither `class`
  nor `grass`), plus an allowlist for the Scunthorpe problem.
- **Filters chat that arrives as a command**: `/msg`, `/tell`, `/w`, `/r`, `/me`, `/say`,
  `/teammsg`, `/mail send`, `/f chat` and anything else you list. Only the message is judged, never
  the player name, so you can still `/msg Assassin`.
- **Links and IP addresses**, including `example dot com`, with an allowlist for your own site and
  a `chatfilter.links` permission.
- **Spam:** shouting is lowercased, repeats and floods are stopped.
- **Strikes that escalate:** strikes expire one at a time, and a configurable ladder can alert staff,
  warn the player, or run any command (`mute {player} 10m`, `kick {player}`).
- **Staff alerts**, the console log, and `/chatfilter test <text>` to see exactly what would happen
  and why.
- **Keyword replies.** It still answers "eleven", and you can now switch that off.

## With SableCraft Standards

If [Standards](https://github.com/Sablednah/SableCraft-Standards) is installed, ChatFilter screens
everything Standards delivers itself: formatted chat, party and faction channels, `/msg`, `/r`,
`/me` and `/mail`. Each viewer gets their own copy, so the silent mask works there too. Without
Standards nothing is lost; plain vanilla chat and commands are filtered either way.

## Commands

| Command | Who | Does |
|---|---|---|
| `/chatfilter` | anyone | version, and the commands below if you may use them |
| `/chatfilter reload` | `chatfilter.admin` | recompile the lists; reports any broken entries |
| `/chatfilter test <text>` | `chatfilter.admin` | what would be caught, by what, and the result |
| `/chatfilter add\|remove <list> <entry>` | `chatfilter.admin` | edit a list in game; saved to the file |
| `/chatfilter list <list>` | `chatfilter.admin` | show a list |
| `/chatfilter strikes <player> [clear]` | `chatfilter.admin` | see or clear strikes |
| `/chatfilter alerts [on\|off\|toggle]` | `chatfilter.alerts` | your own staff alerts |

Lists: `profanity.anywhere`, `profanity.words`, `profanity.patterns`, `profanity.allowed`, the same
four for `severe`, `links.allowed`, `commands`, `responses`.

## Permissions

Through NeoForge's permission API, so LuckPerms or Standards' built-in handler can grant them.

| Node | Default | Means |
|---|---|---|
| `chatfilter.chat` | everyone | may chat at all (the old `canchat`) |
| `chatfilter.bypass` | nobody | not filtered (the old `canswear`), ops included |
| `chatfilter.see` | nobody | sees masked and censored words as written |
| `chatfilter.links` | nobody | may post links |
| `chatfilter.alerts` | ops | receives staff alerts |
| `chatfilter.admin` | ops | uses `/chatfilter` |

## Configuration

`config/chatfilter-common.toml`, with every option commented. Edits apply when you save. The old
plugin's `config.yml` and `lang.yml` map across like this:

| Old | New |
|---|---|
| `profanity` | `[profanity] anywhere` |
| `profanityWordMatch` | `[profanity] words` |
| `censor: true` / `censorText` | `[profanity] action = "REPLACE"`, `[censor] text` |
| `kick: true` | a `[strikes] ladder` entry: `"1 = command kick {player} Language"` |
| `aggressiveMatching` | `[matching] leetspeak` (now on by default) |
| `showInConsole` | `[alerts] console` |
| `triggers` / `triggerPhrase` | `[responses] rules` |
| `commands` | `[commands] list` |
| `profanityMessage` / `blockMessage` | `[profanity] message`, `[messages] noChat` |

## Versions

One jar per Minecraft line: 1.21.11, 26.1, 26.2 and 26.3, named `chatfilter-<version>+mc<mc>.jar`.
Server-side only.

## Licence

MIT.
