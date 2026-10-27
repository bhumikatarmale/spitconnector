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
