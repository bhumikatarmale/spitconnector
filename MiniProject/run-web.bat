@echo off
cd /d "%~dp0"
title CampusMesh Web Dashboard
echo ================================================================================
echo               STARTING CAMPUSMESH DECENTRALIZED MESH DASHBOARD                   
echo ================================================================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
)
where java >nul 2>nul
if errorlevel 1 (
    for /d %%D in ("%ProgramFiles%\Microsoft\jdk*") do (
        if exist "%%D\bin\java.exe" (
            set "PATH=%%D\bin;%PATH%"
            goto :java_found
        )
    )
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk*") do (
        if exist "%%D\bin\java.exe" (
            set "PATH=%%D\bin;%PATH%"
            goto :java_found
        )
    )
    for /d %%D in ("%ProgramFiles%\Java\*") do (
        if exist "%%D\bin\java.exe" (
            set "PATH=%%D\bin;%PATH%"
            goto :java_found
        )
    )
    for /d %%D in ("%USERPROFILE%\.jdks\*") do (
        if exist "%%D\bin\java.exe" (
            set "PATH=%%D\bin;%PATH%"
            goto :java_found
        )
    )
)
:java_found

if not exist bin\campusmesh\web\CampusMeshWebRunner.class (
    echo Compiling codebase...
    call build.bat
)

echo Launching embedded Web Server on http://localhost:8080 (Public Mode for Mobile)...
java -cp "bin;lib/*" campusmesh.web.CampusMeshWebRunner 8080 LAPTOP_WIN --public %*
pause
