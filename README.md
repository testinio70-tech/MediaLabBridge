# MediaLabBridge

MediaLabBridge conecta una aplicación Android con un receptor local en Windows. Permite enviar texto desde el teléfono para copiarlo al portapapeles del PC y, únicamente cuando el usuario lo habilita de forma explícita, ejecutar un comando de PowerShell y devolver su salida a Android.

## Estado

**Fase 2 — interfaz rápida en desarrollo y validación.**

Incluye:

- Aplicación Android nativa en Java, sin bibliotecas de red externas.
- Receptor de Windows en .NET 10, publicado como ejecutable autónomo `win-x64`.
- Autenticación mediante token aleatorio local.
- Modo seguro de portapapeles activado de forma predeterminada.
- Modo PowerShell opcional con límite de 60 segundos.
- Controles rápidos para seleccionar todo, copiar, pegar y limpiar.
- Pegado rápido de dirección y token.
- Copia rápida de respuestas recibidas desde Windows.
- Logo e icono propios de MediaLabBridge.
- Pruebas unitarias Android y compilación automática con GitHub Actions.

## Estructura

```text
app/                         Aplicación Android
desktop/                     Receptor de Windows y lanzadores
docs/PHASE_1_TEST.md         Guía completa de prueba inicial
docs/PHASE_2_TEST.md         Guía de prueba de la interfaz rápida
docs/SECURITY.md             Modelo de seguridad y limitaciones
docs/PROTOCOL.md             Contrato HTTP
.github/workflows/           Pruebas y compilación automática
```

## Descargar las compilaciones

1. Abre la pestaña **Actions** del repositorio.
2. Entra en la ejecución más reciente de **MediaLabBridge Build**.
3. Descarga al final de la página:
   - `MediaLabBridge-Android-debug`
   - `MediaLabBridge-Windows-win-x64`

Los artefactos permanecen disponibles durante 30 días.

## Pruebas

La prueba inicial de comunicación está en [docs/PHASE_1_TEST.md](docs/PHASE_1_TEST.md). Los controles rápidos de Android se validan siguiendo [docs/PHASE_2_TEST.md](docs/PHASE_2_TEST.md).

## Seguridad

Esta versión usa HTTP dentro de la red local y no debe utilizarse en redes públicas. La ejecución de PowerShell está deshabilitada de forma predeterminada. Consulta [docs/SECURITY.md](docs/SECURITY.md).

## Próximas mejoras de la fase 2

- Emparejamiento visual mediante código QR.
- Historial local de solicitudes y respuestas.
- Confirmación opcional antes de ejecutar comandos.
- Aplicación de escritorio con interfaz gráfica.
- Cifrado de transporte y publicación de versiones firmadas.
