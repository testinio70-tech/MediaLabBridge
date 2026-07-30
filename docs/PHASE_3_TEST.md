# Prueba física — MediaLabBridge 0.3.0 Dark Stability

## Preparación

1. Instala la APK 0.3.0 sobre la versión anterior.
2. Extrae el receptor Windows 0.3.0 en una carpeta nueva.
3. Cierra cualquier receptor anterior.
4. Abre `start-execution-mode.cmd`.
5. Copia en Android la dirección y el token mostrados por Windows.
6. Pulsa **Comprobar conexión** y confirma que `/health` informa la versión `0.3.0`.

## Interfaz oscura

- Confirma que fondo, barra de estado, barra de navegación, editor y panel de respuesta aparecen oscuros.
- Confirma que el editor y la respuesta utilizan una fuente monoespaciada.
- Confirma que el contador permite hasta `2,000,000` de caracteres.

## Script multilínea

Selecciona **Ejecutar script PowerShell**, elige **15 minutos** y usa `C:\MediaLab` como carpeta de trabajo cuando exista.

```powershell
function Get-BridgeSummary {
    param([string]$Path)

    [pscustomobject]@{
        Computer = $env:COMPUTERNAME
        User = $env:USERNAME
        PowerShell = $PSVersionTable.PSVersion.ToString()
        WorkingDirectory = (Get-Location).Path
        TargetExists = Test-Path $Path
        Timestamp = Get-Date -Format o
    } | Format-List
}

Get-BridgeSummary -Path 'C:\MediaLab'
```

Debe regresar salida completa, código `0`, motor PowerShell, duración y carpeta de trabajo.

## Recuperación tras interrupción

1. Envía este trabajo con un tiempo de 5 minutos:

```powershell
Start-Sleep -Seconds 20
"RECUPERACION-OK $(Get-Date -Format o)"
```

2. Durante la espera, desactiva y vuelve a activar el Wi-Fi del teléfono.
3. La app debe reintentar sin ejecutar el script dos veces.
4. Si muestra error, vuelve a conectar y pulsa **Recuperar último trabajo**.
5. Debe aparecer la respuesta del trabajo original.

## Comando extenso

GitHub Actions prueba automáticamente un script de más de 250,000 caracteres. Para una prueba manual basta pegar un bloque grande de funciones o configuración y verificar que supera el antiguo límite de 100,000 caracteres sin rechazo.

## Regresión

- Prueba **Copiar al portapapeles**.
- Ejecuta `Get-Date`.
- Cierra y vuelve a abrir el receptor; confirma que la app conserva dirección, token, carpeta y tiempo seleccionado.
- Comprueba que solo existe un proceso receptor y que el puerto 8765 no está ocupado por una copia anterior.
