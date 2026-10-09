#!/usr/bin/env bash
# Build ChatFilter and copy the jar into a NeoForge test instance's mods/ folder,
# then you launch that instance from CurseForge to see the mod live.
#
# Usage:   ./deploy.sh
# Override the target instance:
#          CHATFILTER_INSTANCE="/path/to/instance" ./deploy.sh
#
# One instance per Minecraft line, so the branch you are on decides where the jar goes.
# The calendar-versioned instances are named for their Minecraft version alone and are shared
# with the other mods in this family; on 1.21.11 it is the Standards instance, since that is
# where Standards' chat runs too. ChatFilter is server-side: a client instance only exercises
# it in single-player or LAN -- the real test is a dev server and an unmodded client.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

MC_VERSION="$(sed -n 's/^minecraft_version=//p' "$ROOT/gradle.properties" | head -1)"
if [ -z "$MC_VERSION" ]; then
    echo "!! Could not read minecraft_version from gradle.properties" >&2
    exit 1
fi

# The JDK tracks Minecraft, not the calendar: 26.1 ships java-runtime-epsilon to players, so
# every calendar-versioned line needs 25 and the 1.21.x line still needs 21. A JAVA_HOME set
# by the caller always wins, so a system JDK can be used without editing this.
if [ -z "${JAVA_HOME:-}" ]; then
    case "$MC_VERSION" in
        1.*) WANT_JDK="jdk21" ;;
        *)   WANT_JDK="jdk25" ;;
    esac
    if [ -x "$ROOT/tools/$WANT_JDK/bin/java" ]; then
        export JAVA_HOME="$ROOT/tools/$WANT_JDK"
    else
        echo "!! Minecraft $MC_VERSION needs $WANT_JDK, and tools/$WANT_JDK is not there." >&2
        echo "!! Drop a JDK in tools/$WANT_JDK, or set JAVA_HOME yourself and rerun." >&2
        exit 1
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"

case "$MC_VERSION" in
    1.21.11) DEFAULT_INSTANCE="Standards" ;;
    *)       DEFAULT_INSTANCE="$MC_VERSION" ;;
esac
INSTANCE="${CHATFILTER_INSTANCE:-/mnt/c/Users/darre/curseforge/minecraft/Instances/$DEFAULT_INSTANCE}"
MODS="$INSTANCE/mods"

echo ">> Building ChatFilter for Minecraft $MC_VERSION (JDK: $(basename "$JAVA_HOME"))..."
"$ROOT/gradlew" build --console=plain

if [ ! -d "$MODS" ]; then
    echo "!! Instance mods folder not found: $MODS" >&2
    echo "!! Create a NeoForge $MC_VERSION instance named '$DEFAULT_INSTANCE'," >&2
    echo "!! or run: CHATFILTER_INSTANCE=\"/path/to/instance\" ./deploy.sh" >&2
    exit 1
fi

# Name the jar exactly rather than taking the newest match. build/libs keeps whatever every
# other branch has built here, and "newest" is the right answer only until a build is up to
# date and does not rewrite its jar -- at which point another line's jar deploys silently.
MOD_VERSION="$(sed -n 's/^mod_version=//p' "$ROOT/gradle.properties" | head -1)"
JAR="$ROOT/build/libs/chatfilter-${MOD_VERSION}+mc${MC_VERSION}.jar"
if [ ! -f "$JAR" ]; then
    echo "!! Expected jar not found: $JAR" >&2
    echo "!! build/libs holds: $(ls "$ROOT/build/libs" 2>/dev/null | tr '\n' ' ')" >&2
    exit 1
fi

# A running instance holds the jar open, so Windows refuses to replace it. Say so plainly: this
# otherwise fails looking like a success, and you test a stale jar wondering why nothing changed.
instance_locked() {
    echo "!! Could not $1 the jar in the instance's mods folder." >&2
    echo "!! Is the '$(basename "$INSTANCE")' instance still running? Close Minecraft and retry." >&2
    exit 1
}

# Name both builds out loud, at the moment of the swap. Two jars with the same version string
# are indistinguishable on disk apart from the stamp inside them, and the moment that matters
# is this one -- deciding "the version already reads 2.5.1, so it must be current" is a
# judgement no script can stop, but printing what is actually being replaced makes it wrong
# where you can see it rather than an hour later. A jar older than the stamp says so.
jar_stamp() {
    local j="${1:-}" c b t
    [ -n "$j" ] && [ -f "$j" ] || { echo "absent"; return; }
    command -v unzip >/dev/null 2>&1 || { echo "unknown - no unzip here"; return; }
    c="$(unzip -p "$j" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | sed -n 's/^Build-Commit: //p')"
    [ -n "$c" ] || { echo "none, predates stamps"; return; }
    b="$(unzip -p "$j" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | sed -n 's/^Build-Branch: //p')"
    t="$(unzip -p "$j" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | sed -n 's/^Build-Time: //p')"
    echo "$c on $b, $t"
}

# A plain glob, not `ls ... | head`: with `set -o pipefail` above, a glob that matches nothing
# makes ls fail, which fails the assignment, which exits the script -- silently, right after a
# successful build, on the one run where there is nothing to replace (a first deploy).
OLD_JAR=""
for f in "$MODS"/chatfilter-*.jar; do
    [ -f "$f" ] && { OLD_JAR="$f"; break; }
done
echo ">> Replacing: ${OLD_JAR:+$(basename "$OLD_JAR") }[$(jar_stamp "${OLD_JAR:-}")]"
echo ">> With:      $(basename "$JAR") [$(jar_stamp "$JAR")]"

echo ">> Removing previous ChatFilter jars from the instance..."
rm -f "$MODS"/chatfilter-*.jar || instance_locked "remove"

cp "$JAR" "$MODS/" || instance_locked "copy"

# Confirm the jar really landed and matches: a half-written copy is worse than a loud failure.
if ! cmp -s "$JAR" "$MODS/$(basename "$JAR")"; then
    echo "!! The deployed jar does not match the one just built." >&2
    exit 1
fi

echo ">> Deployed: $(basename "$JAR") ($(stat -c%s "$JAR") bytes)"
echo ">> Launch the '$(basename "$INSTANCE")' instance in CurseForge to test."
