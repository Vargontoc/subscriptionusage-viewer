@echo off
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
set "APP_DIR=%SCRIPT_DIR%.."
set "JAVA_CMD=%JAVA_CMD%"
set "JAR=%APP_JAR%"

if not defined JAVA_CMD if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD set "JAVA_CMD=java.exe"
"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    echo Error: no se encuentra Java 21. Instala Java 21 o define JAVA_HOME. 1>&2
    exit /b 1
)
for /f "tokens=3" %%V in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /c:"version"') do set "JAVA_VERSION=%%~V"
echo %JAVA_VERSION% | findstr /b /c:"21" >nul
if errorlevel 1 (
    echo Error: se requiere Java 21 para iniciar la aplicacion. 1>&2
    exit /b 1
)

if not defined JAR for /f "delims=" %%J in ('dir /b /a-d "%APP_DIR%\target\app-*.jar" 2^>nul') do if not defined JAR set "JAR=%APP_DIR%\target\%%J"
if not defined JAR (
    echo Error: no se encuentra el JAR de la aplicacion en %APP_DIR%\target. 1>&2
    exit /b 1
)
if not exist "%JAR%" (
    echo Error: no se encuentra el JAR de la aplicacion: %JAR%. 1>&2
    exit /b 1
)

if not defined BACKGROUND_LOG set "BACKGROUND_LOG=%APP_DIR%\target\application.log"
for %%D in ("%BACKGROUND_LOG%") do if not exist "%%~dpD" mkdir "%%~dpD" >nul 2>&1
start "Subscription Usage Viewer" /b cmd.exe /d /c call "%JAVA_CMD%" -jar "%JAR%" %* >>"%BACKGROUND_LOG%" 2>&1
if errorlevel 1 (
    echo Error: no se pudo iniciar la aplicacion en segundo plano. 1>&2
    exit /b 1
)
echo Aplicacion iniciada en segundo plano.
exit /b 0
