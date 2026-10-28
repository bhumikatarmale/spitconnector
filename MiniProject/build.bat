@echo off
cd /d "%~dp0"
echo ========================================
echo Compiling CampusMesh Backend...
echo ========================================

where javac >nul 2>nul
if errorlevel 1 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
)
where javac >nul 2>nul
if errorlevel 1 (
    for /d %%D in ("%USERPROFILE%\.jdks\*") do (
        if exist "%%D\bin\javac.exe" (
            set "PATH=%%D\bin;%PATH%"
            goto :jdk_found
        )
    )
    for /d %%D in ("%ProgramFiles%\Java\*") do (
        if exist "%%D\bin\javac.exe" (
            set "PATH=%%D\bin;%PATH%"
            goto :jdk_found
        )
    )
)
:jdk_found
where javac >nul 2>nul
if errorlevel 1 (
    echo [ERROR] javac not found. Install a JDK ^(Java 11+^), not just a JRE, and add it to PATH.
    exit /b 1
)

if not exist bin mkdir bin
if not exist database mkdir database

javac -cp "lib/*;src" -d bin src/campusmesh/model/*.java src/campusmesh/buffer/*.java src/campusmesh/contact/*.java src/campusmesh/routing/*.java src/campusmesh/logging/*.java src/campusmesh/networking/*.java src/campusmesh/discovery/*.java src/campusmesh/discovery/wifi/*.java src/campusmesh/ble/*.java src/campusmesh/web/*.java src/campusmesh/demo/*.java src/campusmesh/test/*.java

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Compilation finished with 0 errors.
) else (
    echo [ERROR] Compilation failed.
)
