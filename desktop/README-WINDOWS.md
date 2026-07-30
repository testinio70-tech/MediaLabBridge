# Receptor de Windows — MediaLabBridge 0.3.0

1. Extrae **todo** el ZIP en una carpeta propia. No ejecutes archivos directamente desde la vista comprimida.
2. Ejecuta `start-copy-mode.cmd` para el modo seguro o `start-execution-mode.cmd` para habilitar PowerShell expresamente.
3. El lanzador detecta si `MediaLabBridge.Desktop.exe` está junto al `.cmd` o dentro de la subcarpeta `MediaLabBridge.Desktop`.
4. Cuando Windows Defender Firewall pregunte, permite acceso **solo en redes privadas**.
5. Copia en la APK una de las direcciones IP mostradas y el token de 64 caracteres.
6. Pulsa **Comprobar conexión** en Android.

## Dark Stability

La versión 0.3.0 admite scripts PowerShell multilínea de hasta 2,000,000 de caracteres y ejecuciones de hasta 30 minutos. Los scripts se guardan temporalmente en UTF-8, se ejecutan desde la carpeta seleccionada en Android y se eliminan al terminar.

PowerShell 7 se utiliza cuando está instalado. En caso contrario se utiliza Windows PowerShell.

Los reintentos con el mismo identificador no vuelven a ejecutar el trabajo. Android puede recuperar el resultado durante aproximadamente 30 minutos mediante **Recuperar último trabajo**.

## Si el lanzador no abre

Entra en `MediaLabBridge.Desktop` y abre manualmente `MediaLabBridge.Desktop.exe`. Si Windows oculta las extensiones, el archivo puede aparecer simplemente como `MediaLabBridge.Desktop` y su tipo debe ser **Aplicación**.

## Ejecución de PowerShell

La ejecución está desactivada de forma predeterminada. Solo se habilita al abrir `start-execution-mode.cmd` o ejecutar:

```powershell
.\MediaLabBridge.Desktop.exe --allow-execution
```

Solo puede existir un receptor en el puerto 8765. Cierra el modo anterior con `Ctrl+C` antes de cambiar entre copia y ejecución.

No compartas el token. El receptor todavía usa HTTP local sin cifrado, por lo que debe utilizarse únicamente en una red privada y de confianza.
