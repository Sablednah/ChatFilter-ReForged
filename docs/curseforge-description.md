# ChatFilter ReForged

**A server-side chat filter for NeoForge.** It censors or stops swearing in chat and in chat-like
commands, sees through the usual disguises, blocks links and spam, and needs nothing installed on
the client. Unmodified clients get all of it.

The modern rebuild of [ChatFilter](https://legacy.curseforge.com/minecraft/bukkit-plugins/chatfilter),
the Bukkit plugin, rewritten from scratch for NeoForge. Most of what was asked for in the old
plugin's comments is in it.

---

## Silent by default

A caught word reaches other players as `####`, drawn by their own client's chat filter, with a
*"filtered by the server"* hover. **The sender sees exactly what they typed and is told nothing**,
so there is nothing to argue with and nothing to work around. Chat stays signed. Staff with
`chatfilter.see` read the original.

Prefer the classic approach? Every category can instead:

- **REPLACE** the word for everyone (`!@$#`, `****` or cartoon swearing)
- **SHADOW** the whole line: a soft-mute where the sender sees it sent and nobody else sees it
- **BLOCK** it, with *"Oi {player}! Mind your language!"* if you like
- or just **LOG** it

## Hard to get around

Capitals, accents, leetspeak (`sh1t`, `$h!t`), stretched letters (`fuuuck`), spelled-out letters
(`f.u.c.k`, `s h i t`), self-censoring (`f*ck`), full-width and look-alike letters from other
alphabets, zero-width characters, and colour codes hidden mid-word. List the plain word once.

## Kind to innocent words

Part-word and whole-word lists, so `ass` catches neither *class* nor *grass*. There's an allowlist
for the Scunthorpe problem, and regular expressions for anything the lists can't say.
`/chatfilter test <text>` shows exactly what would be caught, by which entry, and what would happen.

## Chat that arrives as a command

`/msg`, `/tell`, `/w`, `/r`, `/me`, `/say`, `/teammsg`, `/mail send`, `/f chat`, and anything else
you list. **Only the message is judged, never the player name**, so `/msg Assassin hi` works.

## And the rest

- **Links and IP addresses**, including `example dot com`, with an allowlist for your own site and a
  permission for those who may post them
- **Spam**: shouting is lowercased, and repeats and floods are stopped
- **Strikes that expire one at a time**, and a ladder that can alert staff, warn the player, or run
  any command: `mute {player} 10m`, `kick {player}`
- **Staff alerts** with a per-person toggle, and a console log
- **Edit the lists in game**: `/chatfilter add profanity.words …`, saved to the config file
- **Keyword replies.** It still answers "eleven", and you can switch that off for good.

## With SableCraft Standards

With [SableCraft Standards](https://github.com/Sablednah/SableCraft-Standards) 1.11.0
or later installed, ChatFilter also screens everything Standards delivers itself: formatted chat,
party and faction channels, `/msg`, `/r`, `/me` and `/mail`. Each viewer gets their own copy, so the
silent mask works there too. Standards is optional. Without it, vanilla chat and commands are
filtered just the same.

## Permissions

Through NeoForge's permission API, so any permissions mod can grant them.

| Node | Default | Means |
|---|---|---|
| `chatfilter.chat` | everyone | may chat at all |
| `chatfilter.bypass` | nobody | not filtered (ops included) |
| `chatfilter.see` | nobody | reads masked words as written |
| `chatfilter.links` | nobody | may post links |
| `chatfilter.alerts` | ops | receives staff alerts |
| `chatfilter.admin` | ops | uses `/chatfilter` |

## Configuration

`config/chatfilter-common.toml`, every option commented. The defaults mask the classic word list
silently, block links and floods, quieten shouting, and filter the usual private-message commands.
The `severe` list ships empty: every server's is different, and nobody should get one they didn't
choose.

## Versions

Minecraft **1.21.11**, **26.1**, **26.2** and **26.3**, one file each. Server-side only.
Open source (MIT): [GitHub](https://github.com/Sablednah/ChatFilter-ReForged).
