#!/bin/bash
# run.sh — Compiles and runs a single .g source file through the Geaux compiler
# Works on Mac and Linux.
#
# Usage: bash run.sh Examples/helloworld.g
#        bash run.sh Examples/basics.g

set -e  # stop immediately if any command fails

# ── Check arguments ─────────────────────────────────────────────────────────
if [ -z "$1" ]; then
    echo "Usage: bash run.sh <file.g>"
    echo "Example: bash run.sh Examples/helloworld.g"
    exit 1
fi

GEAUX_FILE="$1"

if [ ! -f "$GEAUX_FILE" ]; then
    echo "Error: file not found: $GEAUX_FILE"
    exit 1
fi

# ── Paths ──────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB="$SCRIPT_DIR/lib"

# Classpath: ANTLR + prog3 parser + compiled .class files in SCRIPT_DIR
CP="$LIB/antlr-4.13.2-complete.jar:$LIB/prog1.jar:$LIB/prog3.jar:$SCRIPT_DIR"

# ── Run the Geaux compiler ──────────────────────────────────────────────────
# This calls Main.java which: parses → typechecks → lowers → emits C → runs gcc → runs binary
java -cp "$CP" CodeGen.Main "$GEAUX_FILE"
