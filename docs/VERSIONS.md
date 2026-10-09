# Versions

| Branch | Minecraft | NeoForge | MDG | JDK | Source changes from `main` |
|---|---|---|---|---|---|
| `main` | 1.21.11 | 21.11.42 | 2.0.141 | 21 | — |
| `mc26.1` | 26.1.2 | 26.1.2.95 | 2.0.141 | 25 | toolchain only |
| `mc26.2` | 26.2 | 26.2.0.59 | 2.0.144 | 25 | toolchain only |
| `mc26.3` | 26.3 | 26.3.0.58-beta | 2.0.147 | 25 | config registered as `LOCAL`, file name kept |

Ported 2026-10-09. Self-test on every line, standalone and with Standards: 99 and 100 checks.

## The one API change that bit

- **26.2 removed `ChatFormatting.isColor()`.** `Messages` now uses `Style.applyLegacyFormat`,
  which exists on every line and implements the legacy `&`-code rules itself (a colour clears bold
  and the rest, `&r` clears everything). The fix landed on `main` and was cherry-picked forward.

## What was checked beyond compiling

The mask relies on vanilla behaviour that a compile cannot check, so these were read in the
1.21.11 and 26.3 sources. They are the same in shape on both:

- `ServerGamePacketListenerImpl.handleChat` runs `filterTextPacket` **before** the NeoForge chat
  decorator (`ServerChatEvent`), and applies the resulting mask with `.filter(mask())`.
- `MessageArgument.resolveChatMessage` uses the **parsed** text as the unsigned content, and
  `filterPlainText` uses `player.getTextFilter().processStreamMessage`.
- `PlayerList.broadcastChatMessage` uses `sender::shouldFilterMessageTo`, `OutgoingChatMessage`
  drops fully filtered messages, and the client's `ChatListener` draws `applyWithFormatting`.
- `ServerPlayer.getTextFilter` and `shouldFilterMessageTo` are unchanged.

## When porting the next line

1. Bump the toolchain the way MobHealth's branch for that line did.
2. Check the four call sites above before anything else, because the mask depends on them. The
   mixin's `defaultRequire = 1` makes a renamed method fail at startup.
3. Build Standards' matching line first (`standards_libs`); the seam is compiled against it.
4. `./gradlew runServer -Pselftest` and `-PwithStandards`. Wipe `run/world` between lines, since one
   world folder cannot go backwards a version.
