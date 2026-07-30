# MediaLabBridge

MediaLabBridge conecta una aplicación Android con un receptor local en Windows. Permite enviar texto desde el teléfono para copiarlo al portapapeles del PC y, únicamente cuando el usuario lo habilita de forma explícita, ejecutar scripts PowerShell completos y devolver su salida a Android.

## Estado

**Versión 0.3.0 — Dark Stability.**

Incluye:

- Aplicación Android nativa en Java, sin bibliotecas de red externas.
- Interfaz oscura, editor monoespaciado y contador de capacidad.
- Receptor de Windows en .NET 10, publicado como ejecutable autónomo `win-x64`.
- Autenticación mediante token aleatorio local.
- Modo seguro de portapapeles activado de forma predeterminada.
- Ejecución PowerShell opcional mediante archivos temporales UTF-8.
- PowerShell 7 cuando está disponible y Windows PowerShell como respaldo.
- Scripts de hasta 2,000,000 de caracteres.
- Tiempo seleccionable de hasta 30 minutos.
- Carpeta de trabajo configurable desde Android.
- Reintentos de red con identificadores que impiden ejecuciones duplicadas.
- Recuperación de resultados después de una interrupción de conexión.
- Controles rápidos para seleccionar todo, copiar, pegar y limpiar.
- Logo e icono propios de MediaLabBridge.
- Pruebas unitarias Android, compilación automática y prueba real de script extenso en Windows.

## Estructura

```text
app/                         Aplicación Android
desktop/                     Receptor de Windows y lanzadores
docs/PHASE_1_TEST.md         Guía de prueba inicial
docs/PHASE_2_TEST.md         Guía de controles rápidos
docs/PHASE_3_TEST.md         Guía de Dark Stability
docs/SECURITY.md             Modelo de seguridad y limitaciones
docs/PROTOCOL.md             Contrato HTTP
.github/workflows/           Pruebas y compilación automática
```

## Descargar las compilaciones

1. Abre la pestaña **Actions** del repositorio.
2. Entra en la ejecución más reciente de **MediaLabBridge Build**.
3. Descarga:
   - `MediaLabBridge-Android-debug`
   - `MediaLabBridge-Windows-win-x64`

Los artefactos permanecen disponibles durante 30 días.

## Pruebas

La prueba de scripts extensos, reintentos y recuperación está en [docs/PHASE_3_TEST.md](docs/PHASE_3_TEST.md). GitHub Actions ejecuta también un script PowerShell de más de 250,000 caracteres en un runner Windows y comprueba que un reintento no lo ejecute dos veces.

## Seguridad

La versión 0.3.0 todavía usa HTTP dentro de la red local y no debe utilizarse en redes públicas. La ejecución PowerShell está deshabilitada de forma predeterminada y nunca se inicia como administrador automáticamente. Consulta [docs/SECURITY.md](docs/SECURITY.md).

## Próximas mejoras

- Descubrimiento automático del receptor.
- Emparejamiento visual mediante código QR.
- WebSocket persistente y cola local duradera.
- Historial de solicitudes y respuestas.
- Adaptador seguro para devolver resultados a ChatGPT de escritorio.
- Cifrado de transporte y publicación de versiones firmadas.
