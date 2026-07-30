# MediaLabBridge

MediaLabBridge conecta una aplicación Android con un receptor local en Windows. La fase 1 permite enviar texto desde el teléfono para copiarlo al portapapeles del PC y, únicamente cuando el usuario lo habilita de forma explícita, ejecutar un comando de PowerShell y devolver su salida a Android.

## Estado

**Fase 1 — prototipo funcional para pruebas.**

Incluye:

- Aplicación Android nativa en Java, sin bibliotecas de red externas.
- Receptor de Windows en .NET 10, publicado como ejecutable autónomo `win-x64`.
- Autenticación mediante token aleatorio local.
- Modo seguro de portapapeles activado de forma predeterminada.
- Modo PowerShell opcional con límite de 60 segundos.
- GitHub Actions para compilar la APK y el programa de Windows automáticamente.

## Estructura

```text
app/                         Aplicación Android
desktop/                     Receptor de Windows y lanzadores
docs/PHASE_1_TEST.md         Guía completa de prueba
docs/SECURITY.md             Modelo de seguridad y limitaciones
docs/PROTOCOL.md             Contrato HTTP de la fase 1
.github/workflows/           Compilación automática
```

## Descargar las compilaciones

1. Abre la pestaña **Actions** del repositorio.
2. Entra en la ejecución más reciente de **Phase 1 Build**.
3. Descarga al final de la página:
   - `MediaLabBridge-Android-debug`
   - `MediaLabBridge-Windows-win-x64`

Los artefactos permanecen disponibles durante 30 días.

## Primera prueba

Sigue [docs/PHASE_1_TEST.md](docs/PHASE_1_TEST.md). Empieza con `start-copy-mode.cmd`; ese modo no ejecuta comandos.

## Seguridad

Esta versión usa HTTP dentro de la red local y no debe utilizarse en redes públicas. La ejecución de PowerShell está deshabilitada de forma predeterminada. Consulta [docs/SECURITY.md](docs/SECURITY.md).

## Próximas fases

- Emparejamiento visual mediante código QR.
- Cifrado de transporte.
- Historial local de solicitudes y respuestas.
- Aplicación de escritorio con interfaz gráfica y confirmación por comando.
- Firma de APK y publicación automática de versiones.
