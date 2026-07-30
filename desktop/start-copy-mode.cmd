@echo off
setlocal
cd /d "%~dp0"

set "APP=%~dp0MediaLabBridge.Desktop.exe"
if not exist "%APP%" set "APP=%~dp0MediaLabBridge.Desktop\MediaLabBridge.Desktop.exe"

if not exist "%APP%" (
  echo ERROR: No se encontro MediaLabBridge.Desktop.exe.
  echo Extrae todo el ZIP antes de ejecutar este archivo.
  echo El ejecutable debe estar aqui o dentro de la carpeta MediaLabBridge.Desktop.
  pause
  exit /b 1
)

"%APP%"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo El receptor termino con el codigo %EXIT_CODE%.
  pause
)

endlocal
