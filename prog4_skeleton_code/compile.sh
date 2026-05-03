#!/bin/bash
# compile.sh — Compiles all Java source files for the Geaux compiler
# Works on Mac and Linux.
#
# Usage: bash compile.sh
# Output: compiled .class files in the current directory

set -e  # stop immediately if any command fails

# ── Paths ──────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"  # the directory this script lives in
LIB="$SCRIPT_DIR/lib"                        # where the .jar files are
SRC="$SCRIPT_DIR"                            # root of the source files

# Classpath: the ANTLR jar + prog3.jar (parser) + current directory (for .class files)
# prog1.jar has the gLexer; prog3.jar has the ASTBuilder + gParser; antlr jar is the ANTLR runtime
CP="$LIB/antlr-4.13.2-complete.jar:$LIB/prog1.jar:$LIB/prog3.jar:."

echo "Compiling Geaux compiler..."

# Collect all .java files in the project (Absyn, Typecheck, CodeGen, Main)
# -cp       = classpath (where to find dependencies)
# -d .      = put compiled .class files in the current directory
# -sourcepath = help javac find related source files automatically
javac -cp "$CP" \
      -sourcepath "$SRC" \
      -d "$SCRIPT_DIR" \
      "$SRC/Absyn/"*.java \
      "$SRC/Typecheck/"*.java \
      "$SRC/Typecheck/Pass/"*.java \
      "$SRC/Typecheck/SymbolTable/"*.java \
      "$SRC/Typecheck/Types/"*.java \
      "$SRC/CodeGen/"*.java

echo "Done! Run with: bash run.sh Examples/helloworld.g"
