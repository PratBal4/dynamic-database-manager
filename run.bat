@echo off
setlocal

:: Check if java is installed
where java >nul 2>nul
if %errorlevel% neq 0 goto :install_java
goto :run_app

:install_java
echo Java is not installed.
set /p confirm="Would you like to install it now using winget? (y/n): "
if /i "%confirm%"=="y" (
    echo Installing Oracle Java...
    winget install -e --id Oracle.Java
    echo Please restart your command prompt and run this script again.
    exit /b 1
) else (
    echo Please install Java manually to run the project.
    exit /b 1
)

:run_app
echo Running the application...
java --enable-native-access=ALL-UNNAMED -cp "lib\flatlaf-3.5.4.jar;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar;src" Main %*
endlocal
