@echo off
REM compile.bat — Compiles all Java source files for the Geaux compiler
REM Works on Windows.
REM
REM Usage: compile.bat
REM Output: compiled .class files in the current directory

REM ── Paths ──────────────────────────────────────────────────────────────────
SET SCRIPT_DIR=%~dp0
SET LIB=%SCRIPT_DIR%lib
SET SRC=%SCRIPT_DIR%

REM Classpath: ANTLR jar + prog3.jar + current directory
REM Windows uses semicolons (;) instead of colons (:) to separate classpath entries
REM prog1.jar has the gLexer; prog3.jar has the ASTBuilder + gParser; antlr jar is the ANTLR runtime
SET CP=%LIB%\antlr-4.13.2-complete.jar;%LIB%\prog1.jar;%LIB%\prog3.jar;.

echo Compiling Geaux compiler...

REM Compile all Java source files
REM /F lists all files matching a pattern and feeds them to javac
javac -cp "%CP%" -sourcepath "%SRC%" -d "%SCRIPT_DIR%" ^
    "%SRC%Absyn\*.java" ^
    "%SRC%Typecheck\*.java" ^
    "%SRC%Typecheck\Pass\*.java" ^
    "%SRC%Typecheck\SymbolTable\*.java" ^
    "%SRC%Typecheck\Types\*.java" ^
    "%SRC%CodeGen\*.java" ^
    "%SRC%Main.java"

echo Done! Run with: run.bat Examples\helloworld.g
