@echo off
cd /d "%~dp0"
title CampusMesh Demonstration Suite
echo ================================================================================
echo                     CAMPUSMESH DEMONSTRATION SUITE                               
echo ================================================================================
echo.

if not exist bin\campusmesh\demo\BackendDemo.class (
    echo Compiling codebase...
    call build.bat
)

where java >nul 2>nul
if errorlevel 1 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
)
where java >nul 2>nul
if errorlevel 1 (
    for /d %%D in ("%USERPROFILE%\.jdks\*") do (
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
)
:java_found

echo [1/2] Running Store-Carry-Forward DTN Backend Demo (BackendDemo)...
echo.
java -cp "bin;lib/*" campusmesh.demo.BackendDemo

echo.
echo ================================================================================
echo [2/2] Running Heterogeneous Multi-Transport Mesh Demo (MultiTransportMeshDemo)...
echo ================================================================================
echo.
java -cp "bin;lib/*" campusmesh.demo.MultiTransportMeshDemo

echo.
echo ================================================================================
echo All CampusMesh Demonstrations Finished Successfully!
echo ================================================================================
pause
