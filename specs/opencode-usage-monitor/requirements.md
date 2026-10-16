# Requisitos: opencode-usage-monitor

Estado: aprobado

## Contexto

El usuario (monousuario, entorno local de desarrollo) quiere ver en tiempo real el estado de su suscripción OpenCode Go: cuánto ha consumido de cada ventana de límite (5 horas, semanal y mensual) y cuánto falta para su reinicio.

A diferencia del proveedor Claude, OpenCode expone un endpoint HTTP documentado que devuelve el consumo actual. La aplicación se iniciará como un proceso de fondo independiente y consultará ese endpoint periódicamente, mostrando los datos en la interfaz junto a los demás proveedores que se incorporen en el futuro.

## Fuera de alcance

- Gestión de pagos, facturas o cambios de plan de OpenCode.
- Introducción, edición o almacenamiento de la API key desde la interfaz (diálogos o formularios de configuración).
- Obtención automática de la API key del usuario.
- Notificaciones, alertas o sonidos al acercarse a un límite.
- Persistencia histórica del consumo entre ejecuciones de la aplicación.
- Escritura de datos de uso en un fichero de estado compartido con otros procesos.
- Soporte de planes distintos al plan Go.
- Consulta de endpoints de OpenCode no incluidos en `GET /zen/go/v1/usage`.

## Requisitos funcionales

### REQ-001 Arranque como proceso de fondo

- **Requisito:** Al iniciar la aplicación, el sistema deberá ponerse en ejecución como un proceso de fondo sin bloquear la terminal ni requerir interacción del usuario para comenzar a consultar el uso.
- **Criterios de aceptación:**
  - Dado que el usuario lanza la aplicación desde la terminal, cuando termina el arranque, entonces el proceso sigue ejecutándose en segundo plano y la interfaz está disponible.
  - Dado que la aplicación se está iniciando, cuando transcurren menos de 30 segundos, entonces el usuario puede ya ver la interfaz principal.

### REQ-002 Configuración de la API key

- **Requisito:** El sistema deberá leer la API key del plan Go de OpenCode del fichero de configuración de la aplicación (`application.yml`) y, solo si `application.yml` no define la clave, de la variable de entorno `OPENCODE_API_KEY`, sin que el usuario deba introducirla en la interfaz, y usarla para autenticar las peticiones al endpoint de uso.
- **Criterios de aceptación:**
  - Dado que la API key está definida en `application.yml` y no en la variable de entorno `OPENCODE_API_KEY`, cuando la aplicación arranca, entonces usa esa clave sin pedirla en la interfaz.
  - Dado que la API key está definida en la variable de entorno `OPENCODE_API_KEY` y `application.yml` no la define, cuando la aplicación arranca, entonces usa esa clave sin pedirla en la interfaz.
  - Dado que la API key está definida en ambos orígenes con valores distintos, cuando la aplicación arranca, entonces usa el valor de `application.yml`.
  - Dado que existe una API key configurada, cuando el sistema realiza una petición a OpenCode, entonces incluye la cabecera `Authorization: Bearer <api_key>`.
  - Dado que la interfaz está abierta, cuando el usuario la recorre, entonces no existe ningún diálogo ni campo para introducir la API key.

### REQ-003 Consumo desde la API de OpenCode

- **Requisito:** Mientras la aplicación esté en ejecución y exista una API key configurada, el sistema deberá consultar periódicamente el endpoint `GET https://opencode.ai/zen/go/v1/usage` para obtener el consumo actual.
- **Criterios de aceptación:**
  - Dado que la API key es válida y tiene suscripción Go, cuando el sistema consulta el endpoint, entonces obtiene los datos de las ventanas `rolling`, `weekly` y `monthly`.
  - Dado que la respuesta contiene solo las ventanas `rolling` y `monthly`, cuando se procesa, entonces se muestran únicamente esas dos ventanas sin fallar por la ausencia de `weekly`.

### REQ-004 Tolerancia a variantes del contrato

- **Requisito:** El sistema deberá aceptar ambas variantes del contrato de respuesta: la que usa `percent` y `resetsAt` (ISO-8601), y la que usa `usagePercent` y `resetInSec`.
- **Criterios de aceptación:**
  - Dado que la respuesta contiene `usage.rolling.percent` y `usage.rolling.resetsAt`, cuando se procesa, entonces se extrae el porcentaje y el instante de reinicio correctamente.
  - Dado que la respuesta contiene `usage.rolling.usagePercent` y `usage.rolling.resetInSec`, cuando se procesa, entonces se calcula el porcentaje y el instante de reinicio equivalentes sin error.
  - Dado que la respuesta contiene ambas variantes en distintas ventanas, cuando se procesa, entonces cada ventana se interpreta según la variante que tenga disponible.

### REQ-005 Visualización del consumo por ventanas

- **Requisito:** Cuando el sistema obtiene datos de uso, el sistema deberá mostrar, para cada ventana recibida (`rolling`, `weekly`, `monthly`), el porcentaje consumido y el tiempo restante hasta su reinicio.
- **Criterios de aceptación:**
  - Dado que la respuesta indica un 30 % consumido en `rolling`, cuando se muestra la interfaz, entonces la ventana de 5 horas refleja 30 %.
  - Dado que la respuesta indica que `monthly` se reinicia dentro de 2 días, cuando se muestra la interfaz, entonces la ventana mensual muestra "2 días" como tiempo restante.

### REQ-006 Refresco periódico

- **Requisito:** Mientras la aplicación esté en ejecución, el sistema deberá repetir la consulta del consumo de OpenCode de forma periódica sin intervención del usuario.
- **Criterios de aceptación:**
  - Dado que la aplicación está abierta y el consumo ha cambiado en el servidor, cuando transcurre el intervalo de refresco, entonces los valores mostrados se actualizan sin que el usuario realice ninguna acción.
  - Dado que una consulta está en curso, cuando llega el momento de la siguiente lectura programada, entonces el sistema no inicia una nueva consulta hasta que termine la anterior.

### REQ-007 Hora de la última actualización

- **Requisito:** Cuando el sistema obtiene datos de uso, el sistema deberá mostrar la hora en que se completó la última consulta exitosa a la API.
- **Criterios de aceptación:**
  - Dado que la última consulta exitosa se completó a las 14:10:05, cuando el usuario mira la interfaz, entonces ve "14:10:05" como hora de última actualización.

### REQ-008 Gráfico de evolución

- **Requisito:** Cuando el sistema obtiene datos de uso más recientes que los ya representados, el sistema deberá añadir el porcentaje consumido de cada ventana a un gráfico que muestre su evolución desde que se inició la aplicación.
- **Criterios de aceptación:**
  - Dado que la aplicación lleva abierta varias actualizaciones de datos, cuando el usuario mira el gráfico, entonces ve un punto por actualización y por ventana, ordenados en el tiempo.
  - Dado que se recibe una nueva lectura, cuando el usuario mira el gráfico, entonces aparece un nuevo punto sin recargar ni reiniciar la aplicación.
  - Dado que dos lecturas consecutivas devuelven los mismos valores, cuando el usuario mira el gráfico, entonces no se añaden puntos duplicados.

### REQ-009 Sin histórico persistido

- **Requisito:** El sistema no deberá conservar los datos de evolución entre ejecuciones de la aplicación.
- **Criterios de aceptación:**
  - Dado que la aplicación ha acumulado puntos en el gráfico y se cierra, cuando se vuelve a abrir, entonces el gráfico empieza vacío.

### REQ-010 Manejo de API key inválida

- **Requisito:** Si la API key configurada es inválida, ausente o no tiene autorización para acceder al endpoint, entonces el sistema deberá mostrar un indicador claro de error de autenticación sin bloquear el resto de la interfaz.
- **Criterios de aceptación:**
  - Dado que no hay API key configurada ni en `application.yml` ni en la variable de entorno, cuando se muestra la interfaz, entonces aparece un mensaje que indica que no hay API key configurada y que debe definirse en `application.yml` o en la variable de entorno, sin ofrecer un campo para introducirla.
  - Dado que no hay API key configurada, cuando transcurre el intervalo de refresco, entonces el sistema no realiza peticiones a OpenCode.
  - Dado que el endpoint responde con código 401, cuando se procesa la respuesta, entonces la interfaz muestra un mensaje que indica que la API key es inválida o está ausente.
  - Dado que el endpoint responde con código 403, cuando se procesa la respuesta, entonces la interfaz muestra un mensaje que indica que la cuenta no tiene suscripción Go activa.

### REQ-011 Manejo de errores de red

- **Requisito:** Si la consulta a OpenCode falla por problemas de red (sin conexión, tiempo de espera, resolución de DNS u otro error transitorio), entonces el sistema deberá mantener los últimos datos válidos visibles e indicar que la actualización está desactualizada, sin mostrar mensajes de error técnicos.
- **Criterios de aceptación:**
  - Dado que la red está disponible y se obtuvo previamente un consumo del 20 %, cuando se pierde la conectividad, entonces la interfaz sigue mostrando 20 % junto a un indicador de que los datos están desactualizados.
  - Dado que la conectividad se recupera, cuando se completa la siguiente consulta exitosa, entonces el indicador de desactualización desaparece.
  - Dado que no hay datos previos y la primera consulta falla por un error de red, cuando el usuario mira la interfaz, entonces se muestra un texto neutro que no indique error técnico.

### REQ-012 Preparación para varios proveedores

- **Requisito:** El sistema deberá tratar cada proveedor de AI como una unidad independiente de obtención y presentación de datos, de modo que el fallo o la ausencia de un proveedor no afecte a los demás, y mostrar únicamente los proveedores de los que recibe información.
- **Criterios de aceptación:**
  - Dado que solo existe el proveedor OpenCode configurado, cuando se ejecuta la aplicación, entonces solo se muestra OpenCode y no aparecen secciones vacías de otros proveedores.
  - Dado que OpenCode no tiene API key configurada y otro proveedor configurado funciona correctamente, cuando el usuario mira la interfaz, entonces OpenCode muestra el aviso de que falta la API key, indicando dónde configurarla, y el otro proveedor sigue mostrándose y actualizándose con normalidad.
  - Dado que OpenCode devuelve un error 401 y otro proveedor configurado funciona correctamente, cuando el usuario mira la interfaz, entonces el otro proveedor sigue mostrándose y actualizándose con normalidad.

## Requisitos no funcionales

### NFR-001 Tiempo de arranque

- **Requisito:** El sistema deberá completar el arranque en menos de 30 segundos en un entorno monousuario local con Java 21.
- **Criterios de aceptación:**
  - Dado que el usuario lanza la aplicación en una máquina con Java 21 y recursos disponibles, cuando termina el arranque, entonces el tiempo transcurrido desde la invocación hasta la interfaz usable es inferior a 30 segundos.

### NFR-002 Compatibilidad con Java 21

- **Requisito:** El sistema deberá ser compatible con Java 21.
- **Criterios de aceptación:**
  - Dado que el entorno de ejecución usa Java 21, cuando se compila y ejecuta la aplicación, entonces no se producen errores de versión ni advertencias de deprecación que impidan su funcionamiento.

### NFR-003 Consumo mínimo de recursos

- **Requisito:** El sistema deberá consumir recursos mínimos del sistema durante su ejecución en segundo plano.
- **Criterios de aceptación:**
  - Dado que la aplicación está en ejecución y en segundo plano sin interacción del usuario, cuando se mide el uso de CPU durante 5 minutos, entonces el consumo medio de CPU es inferior al 5 % en un núcleo.
  - Dado que la aplicación está en ejecución y en segundo plano, cuando se mide el uso de memoria, entonces el consumo de memoria heap es inferior a 256 MB.

### NFR-004 Tiempo de respuesta de la interfaz

- **Requisito:** El sistema deberá mantener la interfaz receptiva mientras se realiza una consulta a la API de OpenCode, sin bloqueos superiores a 1 segundo.
- **Criterios de aceptación:**
  - Dado que una consulta a OpenCode tarda más de 5 segundos en responder, cuando el usuario interactúa con la interfaz durante ese tiempo, entonces la interfaz responde en menos de 1 segundo.

### NFR-005 Intervalo de refresco razonable

- **Requisito:** El sistema deberá consultar el endpoint de uso con una frecuencia que mantenga los datos actualizados sin sobrecargar la API ni el sistema local.
- **Criterios de aceptación:**
  - Dado que la aplicación está en ejecución con OpenCode configurado, cuando transcurre un minuto, entonces el número de peticiones realizadas a OpenCode no supera las 60.

### NFR-006 Protección de la API key

- **Requisito:** El sistema deberá garantizar que la API key real nunca se versiona, nunca se escribe en los logs y nunca se muestra en la interfaz.
- **Criterios de aceptación:**
  - Dado que el repositorio está bajo control de versiones, cuando se consulta qué ficheros ignora el control de versiones, entonces el `application.yml` que contiene la clave real figura excluido y no aparece como fichero a incorporar.
  - Dado que el repositorio contiene un fichero de configuración de ejemplo versionado, cuando se inspecciona su contenido, entonces no contiene ninguna clave real, solo un marcador de posición.
  - Dado que la aplicación ha estado en ejecución con una API key válida, con una clave ausente y con respuestas 401 y 403, cuando se buscan en todos los logs generados la clave real y su cabecera `Authorization`, entonces no se encuentra ninguna aparición del valor de la clave.
  - Dado que la interfaz está abierta en cualquier estado (datos correctos, clave ausente, error 401, 403 o de red), cuando se inspeccionan los textos mostrados y el contenido de las respuestas que la interfaz recibe, entonces no aparece el valor de la clave, ni completo ni parcial.

## Supuestos

- S1: El usuario define una API key válida del plan Go de OpenCode en `application.yml` o en la variable de entorno `OPENCODE_API_KEY` antes de arrancar la aplicación; la aplicación no puede obtener el uso sin ella y no la solicita en la interfaz. Un cambio de la clave exige reiniciar la aplicación (confirmado por el usuario).
- S2: El endpoint `https://opencode.ai/zen/go/v1/usage` y el esquema de respuesta del contrato son correctos; cualquier variante adicional no documentada deberá validarse contra respuestas reales.
- S3: Las ventanas mostradas son `rolling` (5 horas), `weekly` (semana) y `monthly` (mes), con los límites de 12 USD, 30 USD y 60 USD respectivamente; la aplicación muestra porcentajes, no importes en dólares.
- S4: El refresco periódico se realiza cada 60 segundos por defecto; este intervalo se fijará en diseño.
- S5: Los datos de evolución solo se mantienen en memoria durante la ejecución de la aplicación; al cerrarse se pierden.
- S6: La API key reside únicamente en la configuración local del usuario (`application.yml` o variable de entorno) en un entorno monousuario; la aplicación no la escribe en otros ficheros ni la comparte o sincroniza. La protección de la clave es una medida de seguridad crítica y obligatoria (NFR-006), no una recomendación: es responsabilidad del usuario no versionar manualmente la clave real.
- S11: Precedencia confirmada por el usuario: `application.yml` prevalece; la variable de entorno solo se usa cuando `application.yml` no define la clave (REQ-002).
- S12: El nombre de la variable de entorno es `OPENCODE_API_KEY` (REQ-002); el nombre de la propiedad en `application.yml` se fijará en diseño.
- S7: Una respuesta 401 o 403 no se reintenta automáticamente; se informa al usuario para que corrija la configuración.
- S8: Los errores de red se reintentan con un backoff moderado; los reintentos se detallarán en diseño.
- S9: La aplicación se ejecuta en segundo plano como un único proceso; el mecanismo exacto de daemonización o servicio se decidirá en diseño.
- S10: El contrato admite propiedades adicionales no documentadas; la aplicación las ignora si no son reconocidas.

## Preguntas abiertas

Ninguna.
