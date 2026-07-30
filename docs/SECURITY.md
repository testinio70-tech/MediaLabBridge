# Seguridad de MediaLabBridge 0.3.0

MediaLabBridge sigue diseñado para una red local privada y de confianza. La mayor capacidad de scripts no cambia el principio de seguridad: PowerShell solo se habilita de forma explícita.

## Controles incluidos

- Token aleatorio de 256 bits almacenado localmente en el PC.
- Comparación del token en tiempo constante.
- Ejecución de PowerShell desactivada de forma predeterminada.
- Límite de 2,000,000 de caracteres por script y 8 MiB por solicitud HTTP.
- Tiempo máximo absoluto de 30 minutos.
- Una sola ejecución PowerShell a la vez para evitar conflictos y sobrecarga accidental.
- Identificador estable y caché de trabajos para impedir ejecuciones duplicadas durante reintentos.
- Scripts temporales UTF-8 eliminados al terminar.
- PowerShell se inicia sin perfil, sin interfaz interactiva y en un proceso separado.
- El receptor no registra el contenido completo de los scripts.
- Salida limitada para evitar consumo ilimitado de memoria.

## Permisos

El script se ejecuta con los mismos permisos de la cuenta que abrió el receptor. MediaLabBridge no debe permanecer iniciado como administrador. Cuando una tarea requiera elevación, debe tratarse como una acción separada y visible.

## Limitaciones conocidas

- El transporte de 0.3.0 continúa siendo HTTP sin TLS.
- Quien tenga acceso a la red local y al token puede enviar solicitudes.
- Un script autorizado puede modificar o borrar archivos accesibles para la cuenta de Windows.
- Los trabajos completados se guardan en memoria durante unos 30 minutos para permitir recuperación; desaparecen al cerrar el receptor.

## Reglas de uso

- Permitir el receptor únicamente en redes privadas de Windows.
- No utilizar Wi-Fi público.
- No compartir, publicar ni fotografiar el token.
- Mantener el modo de ejecución apagado cuando solo se necesite copiar texto.
- Leer scripts desconocidos antes de ejecutarlos.
- Cerrar el receptor al terminar.

Las siguientes entregas incorporarán emparejamiento visual, cifrado de transporte y confirmaciones por sesión.
