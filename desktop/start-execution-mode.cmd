@echo off
cd /d "%~dp0"
echo ADVERTENCIA: este modo permite ejecutar los comandos enviados desde la APK.
echo Usa solamente una red privada y no compartas el token.
pause
MediaLabBridge.Desktop.exe --allow-execution
pause
