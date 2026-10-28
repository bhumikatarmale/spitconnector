@echo off
cd /d "%~dp0"

echo ================================================================================
echo               STARTING CAMPUSMESH WEB DASHBOARD VIA DOCKER                      
echo ================================================================================
echo.

powershell -ExecutionPolicy Bypass -File "%~dp0run-docker.ps1"

pause
