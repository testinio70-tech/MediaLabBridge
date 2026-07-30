# Protocolo HTTP de MediaLabBridge 0.3.0

El receptor escucha de forma predeterminada en `http://0.0.0.0:8765` dentro de la red local privada.

## Salud

```http
GET /health
```

No requiere token. Devuelve la versión, el modo de ejecución, los límites actuales y el motor PowerShell preferido.

## Enviar texto o script

```http
POST /api/v1/command
Authorization: Bearer TOKEN_DEL_RECEPTOR
Content-Type: application/json; charset=utf-8
```

```json
{
  "requestId": "uuid-estable-para-reintentos",
  "action": "execute",
  "text": "Get-Date\nGet-ChildItem",
  "timeoutSeconds": 900,
  "workingDirectory": "C:\\MediaLab"
}
```

Acciones:

- `copy`: copia `text` al portapapeles de Windows.
- `execute`: guarda `text` como archivo temporal UTF-8 y lo ejecuta con PowerShell 7 cuando está disponible, o Windows PowerShell como respaldo. Solo funciona cuando el receptor se inició con `--allow-execution`.

Límites de 0.3.0:

- hasta 2,000,000 de caracteres por solicitud;
- cuerpo HTTP de hasta 8 MiB;
- tiempo seleccionable entre 5 segundos y 30 minutos;
- hasta 2,000,000 de caracteres por cada flujo de salida (`stdout` y `stderr`).

`workingDirectory` es opcional. Cuando está vacío, el receptor usa la carpeta del usuario de Windows. La carpeta debe existir.

## Reintentos sin duplicar

`requestId` identifica el trabajo. Si Android pierde la conexión y repite exactamente el mismo contenido con el mismo identificador, el receptor devuelve el trabajo existente y no vuelve a ejecutarlo. Reutilizar el mismo identificador con contenido distinto devuelve `409`.

Los trabajos terminados permanecen recuperables durante aproximadamente 30 minutos:

```http
GET /api/v1/jobs/{requestId}
Authorization: Bearer TOKEN_DEL_RECEPTOR
```

Respuestas:

- `202`: el trabajo continúa ejecutándose;
- `200`: el trabajo terminó y se devuelve el resultado guardado;
- `404`: el identificador no existe o ya expiró.

## Códigos principales

- `200`: solicitud procesada o resultado recuperado.
- `202`: trabajo todavía en ejecución.
- `400`: JSON, carpeta, acción o texto no válido.
- `401`: token ausente o incorrecto.
- `403`: se solicitó ejecutar, pero el receptor está en modo seguro.
- `408`: el script superó el tiempo seleccionado.
- `409`: el mismo `requestId` fue reutilizado con contenido diferente.
- `500`: error del receptor o del portapapeles.
