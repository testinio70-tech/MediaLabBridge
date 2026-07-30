# Seguridad de la fase 1

MediaLabBridge fase 1 está pensado para pruebas dentro de una red local privada.

## Controles incluidos

- Token aleatorio de 256 bits generado en el PC.
- Comparación del token en tiempo constante.
- Ejecución de PowerShell desactivada de forma predeterminada.
- Límite de 100 000 caracteres por solicitud.
- Tiempo máximo de ejecución de 60 segundos.
- PowerShell se inicia sin perfil, sin interfaz interactiva y en un proceso separado.
- El receptor no registra el contenido de los comandos.

## Limitaciones conocidas

- El transporte es HTTP sin TLS durante la fase 1.
- Quien tenga acceso a la red local y al token puede enviar solicitudes.
- El modo de ejecución remota puede modificar el sistema con los mismos permisos del usuario que inició el receptor.

## Reglas de uso

- Permitir el receptor únicamente en redes privadas de Windows.
- No utilizar Wi-Fi público.
- No publicar ni fotografiar el token.
- Mantener el modo de ejecución apagado cuando solo se necesite copiar texto.
- Cerrar el receptor al terminar.

La fase 2 añadirá emparejamiento, rotación de credenciales y transporte cifrado.
