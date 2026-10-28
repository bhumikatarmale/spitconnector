#!/usr/bin/env bash
# Linux/macOS build script (equivalent of build.bat)
set -e
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo "[ERROR] JDK not found (need javac, Java 11+). Install a JDK, not just a JRE."; exit 1; }
mkdir -p bin database
javac -cp "lib/*:src" -d bin $(find src -name '*.java')
echo "[SUCCESS] Compilation finished with 0 errors."
