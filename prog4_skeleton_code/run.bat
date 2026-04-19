@echo off
REM run.bat — Compiles and runs a single .g source file through the Geaux compiler
REM Works on Windows.
REM
REM Usage: run.bat Examples\helloworld.g
REM        run.bat Examples\basics.g

REM ── Check arguments ─────────────────────────────────────────────────────────
IF "%~1"=="" (
    echo Usage: run.bat ^<file.g^>
    echo Example: run.bat Examples\helloworld.g
    exit /b 1
)

SET GEAUX_FILE=%~1

IF NOT EXIST "%GEAUX_FILE%" (
    echo Error: file not found: %GEAUX_FILE%
    exit /b 1
)

REM ── Paths ──────────────────────────────────────────────────────────────────
SET SCRIPT_DIR=%~dp0
SET LIB=%SCRIPT_DIR%lib

REM Classpath uses semicolons on Windows
SET CP=%LIB%\antlr-4.13.2-complete.jar;%LIB%\prog1.jar;%LIB%\prog3.jar;%SCRIPT_DIR%

REM ── Run the Geaux compiler ──────────────────────────────────────────────────
java -cp "%CP%" CodeGen.Main "%GEAUX_FILE%"
