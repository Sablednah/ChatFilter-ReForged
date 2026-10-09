#!/usr/bin/env bash
# Download SableCraft Standards' published jars into libs/standards/, which is where the build looks
# by default (standards_libs in gradle.properties). ChatFilter compiles against Standards' chat API
# as a SOFT dependency, and it should compile against what players actually have -- a release --
# not whatever happens to be lying in a sibling's build folder.
#
# Usage: scripts/fetch-standards.sh [tag]     (default: the version in gradle.properties)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TAG="${1:-v$(sed -n 's/^standards_version=//p' "$ROOT/gradle.properties" | head -1)}"
DEST="$ROOT/libs/standards"
mkdir -p "$DEST"
echo ">> Fetching Standards $TAG into libs/standards/"
gh release download "$TAG" -R Sablednah/SableCraft-Standards --pattern 'standards-*.jar' --dir "$DEST" --clobber
ls -1 "$DEST"
