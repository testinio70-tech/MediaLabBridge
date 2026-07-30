# Receptor de Windows — MediaLabBridge

1. Extrae todo el ZIP en una carpeta propia.
2. Ejecuta `start-copy-mode.cmd` para iniciar el modo seguro, que únicamente copia texto al portapapeles.
3. Cuando Windows Defender Firewall pregunte, permite acceso **solo en redes privadas**.
4. Copia en la APK una de las direcciones IP que aparecen en la ventana y el token de 64 caracteres.
5. Pulsa **Comprobar conexión** en Android.

## Ejecución de PowerShell

La ejecución está desactivada de forma predeterminada. Solo se habilita al abrir `start-execution-mode.cmd` o ejecutar:

```powershell
.\MediaLabBridge.Desktop.exe --allow-execution
```

No compartas el token. El receptor usa HTTP local sin cifrado en esta primera fase, por lo que debe utilizarse únicamente en una red privada y de confianza.
