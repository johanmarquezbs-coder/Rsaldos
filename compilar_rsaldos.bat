@echo off
rem Compila R-Saldos (APK debug) con Docker y deja el registro en build_log.txt
cd /d "%~dp0"
echo INICIO %date% %time% > build_log.txt
docker build --progress=plain --target export --output dist . >> build_log.txt 2>&1
echo EXIT=%ERRORLEVEL% >> build_log.txt
