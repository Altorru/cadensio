#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CHECK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/zwack-protocol.XXXXXX")
trap 'rm -rf "$CHECK_DIR"' EXIT HUP INT TERM
JAVAC=javac
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"; fi
"$JAVAC" -encoding UTF-8 -d "$CHECK_DIR" \
  "$HERE/app/src/main/java/fr/hugoviaud/zwack/Protocol.java" "$HERE/tests/ProtocolTest.java"
"$JAVA" -cp "$CHECK_DIR" fr.hugoviaud.zwack.ProtocolTest
