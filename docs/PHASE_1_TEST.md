# Prueba de la fase 1

## Objetivo

Comprobar la comunicación Android → Windows y validar dos operaciones:

1. Copiar texto al portapapeles de Windows.
2. Ejecutar un comando de PowerShell cuando el usuario habilita explícitamente ese modo.

## Preparación

- El teléfono y el PC deben estar conectados a la misma red Wi-Fi privada.
- Descarga los dos artefactos generados por GitHub Actions:
  - `MediaLabBridge-Android-debug`
  - `MediaLabBridge-Windows-win-x64`
- Instala `app-debug.apk` en Android.
- Extrae el ZIP de Windows.

## Prueba segura: portapapeles

1. Abre `start-copy-mode.cmd`.
2. Acepta el aviso del Firewall solamente para redes privadas.
3. Copia en la APK la dirección del PC, por ejemplo `192.168.1.20:8765`.
4. Copia el token mostrado en la ventana del receptor.
5. Pulsa **Comprobar conexión**.
6. Selecciona **Copiar al portapapeles**.
7. Envía una frase de prueba.
8. En Windows abre Bloc de notas y pulsa `Ctrl+V`.

## Prueba de PowerShell

1. Cierra el receptor anterior.
2. Abre `start-execution-mode.cmd` y confirma la advertencia.
3. En la APK selecciona **Ejecutar en PowerShell**.
4. Envía este comando inocuo:

```powershell
Get-Date
```

La salida debe regresar a la pantalla de Android.

## Diagnóstico rápido

- **No conecta:** comprueba que ambos equipos estén en la misma red y que el Firewall haya permitido la aplicación en redes privadas.
- **401:** el token escrito en Android no coincide con el token del receptor.
- **403 al ejecutar:** el receptor fue iniciado en modo seguro; usa el acceso directo de ejecución únicamente cuando sea necesario.
- **Tiempo agotado:** el comando superó 60 segundos o quedó esperando interacción.
