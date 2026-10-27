@echo off
cd /d "%~dp0"
title CampusMesh Web Dashboard
echo ================================================================================
echo               STARTING CAMPUSMESH DECENTRALIZED MESH DASHBOARD                   
echo ================================================================================
echo.

if not exist bin\campusmesh\web\CampusMeshWebRunner.class (
    echo Compiling codebase...
    call build.bat
)

echo Launching embedded Web Server on http://localhost:8080 (Public Mode for Mobile)...
java -cp "bin;lib/*" campusmesh.web.CampusMeshWebRunner 8080 LAPTOP_WIN --public
pause
