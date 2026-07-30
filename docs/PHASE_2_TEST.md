# Prueba de la fase 2 — controles rápidos

## Objetivo

Validar la versión Android 0.2.0 y sus acciones rápidas de edición sin afectar la comunicación ya probada con Windows.

## Preparación

1. Instala la APK `MediaLabBridge-Android-debug` de la ejecución más reciente.
2. Abre el receptor de Windows en modo copia o ejecución.
3. Pega la dirección y el token con los botones rápidos de la APK.
4. Comprueba la conexión.

## Casos de prueba

### Dirección y token

- Pulsa **Pegar dirección** y confirma que elimina espacios externos.
- Pulsa **Pegar token** y confirma que elimina saltos de línea o espacios externos.
- Pulsa **Limpiar** y confirma que cada campo queda vacío.

### Editor de texto o comandos

- Escribe varias líneas y pulsa **Seleccionar todo**.
- Pulsa **Copiar** y confirma que todo el contenido llega al portapapeles cuando no hay una selección parcial.
- Selecciona solamente una palabra, pulsa **Copiar** y confirma que se copia únicamente esa selección.
- Coloca el cursor en medio del texto y pulsa **Pegar**; el contenido debe insertarse en esa posición.
- Selecciona un fragmento y pulsa **Pegar**; el contenido pegado debe reemplazar la selección.
- Pulsa **Limpiar** y confirma que el editor queda vacío.

### Respuesta del receptor

- Envía `Get-Date` con PowerShell habilitado.
- Pulsa **Seleccionar respuesta**.
- Pulsa **Copiar respuesta** y pega el contenido en otra aplicación para comprobarlo.
- Pulsa **Limpiar estado**.

### Logo

- Confirma que el icono de MediaLabBridge aparece en el lanzador de Android.
- Confirma que el logo aparece en la parte superior de la app sin deformarse.

## Resultado esperado

Todos los controles deben responder con un aviso breve y la conexión Android–Windows debe conservar el comportamiento de la fase 1.
