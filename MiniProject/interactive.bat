@echo off
cd /d "%~dp0"
java -cp "bin;lib/*" campusmesh.demo.InteractiveCLI %*
