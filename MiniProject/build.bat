@echo off
cd /d "%~dp0"
echo ========================================
echo Compiling CampusMesh Backend...
echo ========================================

if not exist bin mkdir bin
if not exist database mkdir database

javac -cp "lib/*;src" -d bin src/campusmesh/model/*.java src/campusmesh/buffer/*.java src/campusmesh/contact/*.java src/campusmesh/routing/*.java src/campusmesh/logging/*.java src/campusmesh/networking/*.java src/campusmesh/discovery/*.java src/campusmesh/discovery/wifi/*.java src/campusmesh/ble/*.java src/campusmesh/web/*.java src/campusmesh/demo/*.java src/campusmesh/test/*.java

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Compilation finished with 0 errors.
) else (
    echo [ERROR] Compilation failed.
)
