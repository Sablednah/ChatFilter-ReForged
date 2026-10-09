# CLAUDE.md

Working notes. `README.md` says what the mod does; this file covers the conventions it does not.

ChatFilter ReForged is the NeoForge rebuild of Sablednah's 2012 Bukkit ChatFilter (archive:
`../ChatFilter`, branch `master`). It was read for intent and **rewritten**, with no code copied,
which is the standing rule across this family of mods. `../MobHealth-Forge` is the template (build,
stamp, deploy, publishing). `../SableCraft-Standards` is the sibling whose chat seam this mod plugs into.

## Build & run

```bash
export JAVA_HOME=/mnt/d/Repos/sable/MobHealth-Forge/tools/jdk21   # jdk25 on the 26.x branches
./gradlew compileJava
./gradlew runServer -Pselftest                  # headless self-test, standalone
./gradlew runServer -Pselftest -PwithStandards  # the same with Standards loaded beside it
./gradlew build                                 # build/libs/chatfilter-<v>+mc<mc>.jar
```

- Standards is found by `standards_libs` in `gradle.properties` (default
  `../SableCraft-Standards/build/libs`), version-matched to `minecraft_version`. Override with
  `-Pstandards_libs=...` to build against a worktree.
- `run/server.properties` uses port **25580**, so it cannot collide with the siblings' dev servers.
  Kill a dev server by matching **this repo's** classes path, never `fml.modFolders` alone:
  `ps -eo pid,args | grep "[f]ml.modFolders" | grep "ChatFilter-ReForged/build"`.
- **`SelfTest` is the only headless proof.** It keeps Standards' rules: call the real code, and test
  both directions (every disguise that must be caught sits beside an innocent word that must not be).
  A check that a parse fails silently will pass while testing nothing; assert that the parse
  succeeded first (see the `/me` check).

## How a message travels (read before changing anything)

Three routes, and **each message is judged with side effects exactly once**:

1. **Vanilla chat**: `ChatEvents.onChat` (HIGHEST) calls `FilterService.handle`. A mask needs no
   action there, because `MaskingTextFilter` has already attached it.
2. **Commands**: `CommandScreen` parses, takes the `MessageArgument` / greedy-string ranges, judges
   them, and re-parses with the censored text. A masked `MessageArgument` on a vanilla executor is
   left alone, since the mask hides it and the line stays signed.
3. **Standards' deliveries**: when `StandardsBridge.active()`, Standards calls our `MessageFilter`
   and `onChat` does nothing. `CommandScreen` skips Standards' own `MessageCommands` / `MailCommands`
   executors, found by the executor's class name. If those classes move, update `STANDARDS_SCREENED`.

`FilterService.peek` is the pure version. Only the mask may call it, because the mask runs
**before** the chat event and may see a line that is then blocked for another reason.

### The mask

`mixin/ServerPlayerFilterMixin` wraps `getTextFilter` and overrides `shouldFilterMessageTo`. Vanilla
already carries a `FilterMask` per message, the signature does not cover it, and the client draws
masked characters as `#`. Facts checked in the 1.21.11 sources (`ServerGamePacketListenerImpl.handleChat`,
`MessageArgument.resolveSignedMessage`, `PlayerList.broadcastChatMessage`, `ChatListener.showMessageToPlayer`):

- The text filter runs **before** `ServerChatEvent` on the chat path.
- A **fully** filtered message makes vanilla tell the sender "hidden from some players", so the mask
  is always partial. Whole-message silence is `SHADOW`, which cancels and sends a copy to the sender.
- The mask covers the **signed** text, and an unsigned rewrite replaces it, so a line being rewritten
  (caps) has its masked words rewritten as `###` instead (`Judge`).
- Signs and books use `processMessageBundle`, which is passed through: what a sign stores depends
  on the writer's own client setting, so a mask there reaches only players who already filter.

**When porting, check those two `ServerPlayer` methods and the call sites above first.**
`defaultRequire = 1` makes a renamed method fail at startup, not silently.

## Branches

Same as MobHealth: `main` = 1.21.11, `mc26.1`, `mc26.2`, `mc26.3`. Features land on `main` and
cherry-pick forward. Docs live on `main` only. Porting notes go in `docs/VERSIONS.md`. CurseForge
only; never add Modrinth.

## Tone

Player-facing strings are dry and never scolding; no exclamation marks except in the old plugin's
own lines, which are kept as a tribute (*"Oi {player}! Mind your language!"*).
