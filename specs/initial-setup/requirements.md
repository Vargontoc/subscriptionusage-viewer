# Requisitos: initial-setup

Estado: aprobado

## Contexto

Esta feature establece la estructura base ejecutable de la aplicación de escritorio Subscription Usage Viewer, permitiendo que el proceso arranque de forma autónoma, permanezca en segundo plano, ofrezca un pequeño indicador visual al usuario y se cierre de forma controlada en un entorno de desarrollo local monousuario.

## Fuera de alcance

- Integración con proveedores de AI.
- Recopilación, procesamiento o visualización de datos de uso.
- Persistencia de datos.
- Autenticación y autorización.
- Empaquetado, instaladores o despliegue en producción.
- Exposición de endpoints HTTP o servicios web.

## Requisitos funcionales

### REQ-001 Inicio autónomo

- **Requisito:** Cuando se ejecute la aplicación, el sistema deberá arrancar sin errores fatales.
- **Criterios de aceptación:**
  - Dado un entorno local que cumple los requisitos del stack del proyecto, cuando se lance la aplicación, entonces el proceso permanecerá en ejecución hasta que el usuario solicite el cierre.

### REQ-002 Indicador visual de ejecución

- **Requisito:** Cuando la aplicación esté en ejecución, el sistema deberá mostrar un indicador visual accesible al usuario.
- **Criterios de aceptación:**
  - Dado que la aplicación se ha iniciado correctamente, cuando el usuario consulte la bandeja del sistema, entonces observará un icono que representa la aplicación.
  - Dado que el icono de la aplicación está visible en la bandeja del sistema, cuando el usuario haga clic sobre él, entonces se mostrará al menos una opción para cerrar la aplicación.

### REQ-003 Cierre controlado

- **Requisito:** Cuando el usuario solicite el cierre desde el indicador visual, el sistema deberá detener la aplicación y liberar los recursos utilizados.
- **Criterios de aceptación:**
  - Dado que la aplicación está en ejecución, cuando el usuario seleccione la opción de cierre, entonces el proceso terminará en un tiempo inferior a 5 segundos.
  - Dado que la aplicación está en ejecución, cuando el usuario seleccione la opción de cierre, entonces no quedarán procesos huérfanos ni bloqueos de recursos.

### REQ-004 Configuración externa básica

- **Requisito:** El sistema deberá permitir configurar propiedades básicas de la aplicación mediante un fichero externo.
- **Criterios de aceptación:**
  - Dado un fichero de configuración externo con una propiedad definida, cuando se inicie la aplicación utilizando dicho fichero, entonces el sistema utilizará el valor proporcionado.
  - Dado que no existe fichero de configuración externo, cuando se inicie la aplicación, entonces se utilizarán valores por defecto documentados.

### REQ-005 Registro de inicio completado

- **Requisito:** Cuando la aplicación finalice el inicio correctamente, el sistema deberá registrar un mensaje que indique que está lista.
- **Criterios de aceptación:**
  - Dado que la aplicación se ha iniciado correctamente, cuando se consulte el registro de logs, entonces se observará un mensaje que indique que la aplicación está lista.

## Requisitos no funcionales

### NFR-001 Tiempo de inicio

- **Requisito:** El sistema deberá completar el inicio en menos de 30 segundos en un entorno de desarrollo local.
- **Criterios de aceptación:**
  - Dado un entorno local con los recursos mínimos del stack, cuando se inicie la aplicación, entonces el tiempo transcurrido entre el comando de inicio y el mensaje de listo será inferior a 30 segundos.

### NFR-002 Compatibilidad con Java 21

- **Requisito:** El sistema deberá ser compatible con Java 21.
- **Criterios de aceptación:**
  - Dado un entorno con Java 21 instalado, cuando se compile e inicie la aplicación, entonces no se producirán errores por incompatibilidad de versión.

## Supuestos

- La interfaz visual inicial se implementará como un icono en la bandeja del sistema con un menú contextual mínimo.
- No se requiere instalador ni empaquetado en esta fase; la aplicación se ejecutará directamente desde el artefacto de compilación.
- El fichero de configuración externo es opcional; si falta, se utilizarán valores por defecto.

## Preguntas abiertas

Ninguna.
