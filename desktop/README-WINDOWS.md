# Receptor de Windows — MediaLabBridge

1. Extrae **todo** el ZIP en una carpeta propia. No ejecutes los archivos directamente desde la vista comprimida.
2. Ejecuta `start-copy-mode.cmd` para iniciar el modo seguro, que únicamente copia texto al portapapeles.
3. El lanzador detecta automáticamente si `MediaLabBridge.Desktop.exe` está junto al archivo `.cmd` o dentro de la subcarpeta `MediaLabBridge.Desktop`.
4. Cuando Windows Defender Firewall pregunte, permite acceso **solo en redes privadas**.
5. Copia en la APK una de las direcciones IP que aparecen en la ventana y el token de 64 caracteres.
6. Pulsa **Comprobar conexión** en Android.

## Si el lanzador no abre

Entra en la carpeta `MediaLabBridge.Desktop` y abre manualmente `MediaLabBridge.Desktop.exe`. Si Windows oculta las extensiones, el archivo puede aparecer simplemente como `MediaLabBridge.Desktop` y su tipo debe ser **Aplicación**.

## Ejecución de PowerShell

La ejecución está desactivada de forma predeterminada. Solo se habilita al abrir `start-execution-mode.cmd` o ejecutar el programa con `--allow-execution`.

No compartas el token. El receptor usa HTTP local sin cifrado en esta primera fase, por lo que debe utilizarse únicamente en una red privada y de confianza.
