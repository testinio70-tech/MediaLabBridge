# Protocolo HTTP de la fase 1

El receptor escucha de forma predeterminada en `http://0.0.0.0:8765`.

## Salud

```http
GET /health
```

No requiere token. Devuelve la versión y si la ejecución de PowerShell está habilitada.

## Enviar texto o comando

```http
POST /api/v1/command
Authorization: Bearer TOKEN_DEL_RECEPTOR
Content-Type: application/json
```

```json
{
  "requestId": "identificador-opcional",
  "action": "copy",
  "text": "Texto que se copiará"
}
```

Acciones:

- `copy`: copia `text` al portapapeles de Windows.
- `execute`: ejecuta `text` con Windows PowerShell; solo funciona cuando el receptor se inició con `--allow-execution`.

El tamaño máximo de `text` es 100 000 caracteres. La ejecución tiene un tiempo máximo de 60 segundos.

## Códigos principales

- `200`: solicitud procesada.
- `400`: JSON, acción o texto no válido.
- `401`: token ausente o incorrecto.
- `403`: se solicitó ejecutar, pero el receptor está en modo seguro.
- `408`: el comando superó el tiempo máximo.
- `500`: error del receptor o del portapapeles.
