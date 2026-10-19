# Requisitos: openai-usage-monitor

Estado: aprobado

## Contexto

El usuario (monousuario, entorno local de desarrollo) quiere ver en tiempo real el consumo de su suscripción individual a OpenAI (ChatGPT Plus o Pro) en las ventanas de 5 horas y semanal, con el porcentaje ya calculado por OpenAI y el instante de reinicio. Para ello la aplicación reutiliza las credenciales OAuth que el CLI de Codex o el CLI de opencode ya guardan en local, sin que el usuario las introduzca. Además, se reorganiza la configuración para que los ajustes del proveedor Claude vivan bajo `subscription-viewer.claude`, junto a los de OpenCode y OpenAI.

## Fuera de alcance

- Planes business o de equipo de OpenAI (solo planes individuales).
- Introducción, edición o almacenamiento de credenciales desde la interfaz.
- Inicio de sesión OAuth propio y renovación del token con el `refresh_token` (el sistema nunca renueva el token; ver REQ-012).
- Uso de la clave `OPENAI_API_KEY` del fichero de credenciales de Codex.
- Escritura o modificación de los ficheros de credenciales de Codex u opencode.
- Persistencia histórica del consumo entre ejecuciones.
- Notificaciones, alertas o sonidos al acercarse a un límite.
- Consulta de endpoints de OpenAI distintos del endpoint de cuota indicado.
- Uso de los demás campos de la respuesta de cuota (créditos, límites adicionales, uso por modelo, control de gasto, promociones, etc.) en esta versión.
- Cambios en la configuración del proveedor OpenCode, que se mantiene tal cual.
- Cambios en el comportamiento funcional del proveedor Claude más allá del traslado de su configuración.

## Requisitos funcionales

### REQ-001 Configuración de Claude bajo subscription-viewer

- **Requisito:** El sistema deberá leer la ruta del fichero de estado de uso de Claude y su intervalo de lectura de las propiedades `subscription-viewer.claude.usage-state-file` y `subscription-viewer.claude.usage-poll-interval` de `application.yml`, en lugar de `app.usage-state-file` y `app.usage-poll-interval`, que dejarán de existir.
- **Criterios de aceptación:**
  - Dado el `application.yml` entregado, cuando el usuario lo abre, entonces `subscription-viewer.claude` contiene `usage-state-file` (con el valor actual, `~/.claude/statusline-dump.json`) y `usage-poll-interval` (valor por defecto `10s`) y `subscription-viewer.opencode` conserva sus propiedades sin cambios.
  - Dado que se cambia `subscription-viewer.claude.usage-state-file` y se reinicia la aplicación, cuando el proveedor Claude lee su estado, entonces lee el fichero indicado.
  - Dado que se busca en el código, la configuración (incluida la de pruebas) y la documentación, cuando se buscan `app.usage-state-file` y `app.usage-poll-interval`, entonces no aparece ninguna referencia activa.
  - Dado que el proveedor Claude funcionaba antes del cambio, cuando se aplica y se ejecuta la aplicación, entonces Claude muestra los mismos datos que antes con la misma configuración de valores.

### REQ-002 Localización de credenciales de Codex (prioritaria)

- **Requisito:** Cuando el sistema necesita credenciales de OpenAI, el sistema deberá buscar primero el fichero `.codex/auth.json` en el directorio de usuario (`%USERPROFILE%\.codex\auth.json` en Windows) y, si existe, deberá leer de él `tokens.access_token` y `tokens.account_id`, sin comprobar ninguna otra fuente.
- **Criterios de aceptación:**
  - Dado que existe `.codex/auth.json` con `tokens.access_token` y `tokens.account_id`, cuando el sistema obtiene credenciales, entonces usa esos valores.
  - Dado que existen a la vez `.codex/auth.json` y las credenciales de opencode con valores distintos, cuando el sistema obtiene credenciales, entonces usa solo las de `.codex` y no ejecuta el comando `opencode debug paths data`.
  - Dado que `.codex/auth.json` existe pero no contiene `tokens.access_token` o `tokens.account_id`, cuando el sistema obtiene credenciales, entonces las trata como no disponibles, no ejecuta `opencode` y no recurre a las de opencode.

### REQ-003 Credenciales de opencode como alternativa

- **Requisito:** Si no existe `.codex/auth.json`, entonces el sistema deberá considerar el proveedor OpenCode como configurado solo cuando exista la sección `subscription-viewer.opencode` en la configuración efectiva y el comando `opencode debug paths data` se ejecute sin error; en ese caso, deberá usar el directorio de datos que devuelve el comando y leer de `auth.json` de ese directorio los campos `openai.access` y `openai.accountId`.
- **Criterios de aceptación:**
  - Dado que existe la sección `subscription-viewer.opencode`, que no existe `.codex/auth.json`, que el comando se ejecuta sin error y que `auth.json` de opencode contiene `openai.access` y `openai.accountId`, cuando el sistema obtiene credenciales, entonces usa esos valores.
  - Dado que no existe la sección `subscription-viewer.opencode` y no existe `.codex/auth.json`, cuando el sistema obtiene credenciales, entonces no ejecuta `opencode` y las credenciales se consideran no disponibles.
  - Dado que la sección `subscription-viewer.opencode` existe pero `opencode` no está instalado o el comando falla, cuando el sistema obtiene credenciales, entonces las considera no disponibles y la aplicación sigue en ejecución.
  - Dado que el `auth.json` de opencode no contiene la etiqueta `openai`, cuando el sistema obtiene credenciales, entonces las considera no disponibles sin error.
  - Dado que el `auth.json` de opencode contiene otras etiquetas además de `openai`, cuando se procesa, entonces se ignoran sin error.

### REQ-004 Credenciales no disponibles

- **Requisito:** Si no hay credenciales de OpenAI disponibles por ninguna de las fuentes aplicables, entonces el sistema deberá no mostrar el proveedor OpenAI, no realizar peticiones al endpoint de cuota y seguir reintentando la localización de credenciales en cada ciclo de refresco.
- **Criterios de aceptación:**
  - Dado que no existen credenciales por ninguna fuente, cuando el usuario mira la interfaz, entonces no aparece ninguna sección de OpenAI ni mensaje de error.
  - Dado que no hay credenciales, cuando transcurre el intervalo de refresco, entonces no se realiza ninguna petición a `chatgpt.com`.
  - Dado que el usuario inicia sesión en Codex con la aplicación abierta, cuando transcurre el siguiente ciclo de refresco, entonces aparece el proveedor OpenAI sin reiniciar la aplicación.

### REQ-005 Consulta del endpoint de cuota

- **Requisito:** Mientras la aplicación esté en ejecución y haya credenciales disponibles, el sistema deberá consultar periódicamente `https://chatgpt.com/backend-api/wham/usage` con las cabeceras `Authorization: Bearer <access_token>`, `ChatGPT-Account-Id: <account_id>` y `Accept: application/json`.
- **Criterios de aceptación:**
  - Dado que hay credenciales válidas, cuando el sistema consulta el endpoint, entonces la petición incluye las tres cabeceras con los valores de las credenciales.
  - Dado que las credenciales cambian en disco entre dos ciclos (por ejemplo, por renovación del token por parte del CLI), cuando se realiza el siguiente ciclo, entonces la petición usa las credenciales actualizadas.
  - Dado que la respuesta contiene campos no utilizados por esta versión o propiedades no documentadas, cuando se procesa, entonces se ignoran sin error.

### REQ-006 Extracción de las ventanas de 5 horas y semanal

- **Requisito:** Cuando el sistema recibe una respuesta válida, el sistema deberá extraer de `rate_limit.primary_window` el porcentaje consumido (`used_percent`) y el instante de reinicio de la ventana de 5 horas, y de `rate_limit.secondary_window` los de la ventana semanal, usando el porcentaje calculado por OpenAI sin recalcularlo.
- **Criterios de aceptación:**
  - Dado `primary_window.used_percent` igual a 6, cuando se procesa, entonces la ventana de 5 horas refleja 6 %.
  - Dado `secondary_window.used_percent` igual a 1, cuando se procesa, entonces la ventana semanal refleja 1 %.
  - Dado `primary_window.limit_window_seconds` igual a 18000 y `secondary_window.limit_window_seconds` igual a 604800, cuando se procesa, entonces la primera se presenta como ventana de 5 horas y la segunda como semanal.
  - Dado que `secondary_window.limit_window_seconds` no coincide con 604800, cuando se procesa, entonces la ventana secundaria se sigue presentando como semanal.
  - Dado que la respuesta no contiene `secondary_window` o es nula, cuando se procesa, entonces solo se muestra la ventana de 5 horas sin fallar.
  - Dado que la respuesta incluye `plan_type`, cuando se procesa, entonces el valor no condiciona la extracción de las ventanas.

### REQ-007 Instante y tiempo restante hasta el reinicio

- **Requisito:** Cuando el sistema obtiene datos de una ventana, el sistema deberá mostrar el tiempo restante hasta su reinicio, calculado a partir de `reset_at` (epoch en segundos) o, si este falta, de `reset_after_seconds`.
- **Criterios de aceptación:**
  - Dado `reset_at` igual a un instante (epoch en segundos) situado 2 horas por delante del reloj local, cuando se muestra la interfaz, entonces la ventana muestra un tiempo restante equivalente a 2 horas.
  - Dado `reset_at` igual a 1792344925, cuando se procesa, entonces se interpreta como el instante 1792344925 segundos desde epoch y no como milisegundos.
  - Dado que `reset_at` está ausente y `reset_after_seconds` vale 3600, cuando se procesa, entonces el tiempo restante mostrado es de 1 hora.
  - Dado que `reset_at` ya ha pasado respecto al reloj local, cuando se muestra la interfaz, entonces el tiempo restante se muestra como 0 y no como valor negativo.

### REQ-008 Visualización de OpenAI

- **Requisito:** Cuando el sistema obtiene datos de OpenAI, el sistema deberá mostrar el proveedor OpenAI con el porcentaje consumido y el tiempo restante de cada ventana, y el tipo de plan (`plus` o `pro`) cuando la respuesta lo incluya.
- **Criterios de aceptación:**
  - Dado `plan_type` igual a `plus`, cuando se muestra la interfaz, entonces se identifica el plan Plus.
  - Dado que la respuesta no incluye `plan_type`, cuando se muestra la interfaz, entonces se muestran las ventanas sin indicación de plan y sin error.
  - Dado que hay datos de OpenAI y de otro proveedor, cuando se muestra la interfaz, entonces OpenAI aparece como sección diferenciada del resto.

### REQ-009 Refresco periódico

- **Requisito:** Mientras la aplicación esté en ejecución, el sistema deberá repetir la consulta de OpenAI cada intervalo configurado en la propiedad `subscription-viewer.openai.usage-poll-interval` de `application.yml` (60 segundos por defecto), sin intervención del usuario y sin iniciar una nueva consulta mientras otra esté en curso. Si la propiedad está ausente o vacía, el sistema deberá usar 60 segundos. Si el valor es inferior a 10 segundos, el sistema deberá usar 10 segundos. Si el valor no es una duración válida o no es positivo, entonces el sistema deberá fallar el arranque de la aplicación con un error de configuración que identifique la propiedad, sin usar ningún valor por defecto, con el mismo comportamiento que ante un valor no válido en los proveedores Claude y OpenCode.
- **Criterios de aceptación:**
  - Dado el `application.yml` entregado, cuando el usuario lo abre, entonces `subscription-viewer.openai` contiene `usage-poll-interval: "60s"`, con el mismo formato de duración que `subscription-viewer.claude.usage-poll-interval`.
  - Dado que la propiedad vale `60s`, cuando transcurren 60 segundos, entonces los valores mostrados se actualizan sin acción del usuario si el consumo ha cambiado en el servidor.
  - Dado que la aplicación está en ejecución con credenciales válidas y la propiedad vale `60s`, cuando se observan dos consultas consecutivas, entonces el intervalo entre sus inicios es de 60 segundos (con una tolerancia de 5 segundos).
  - Dado que la propiedad vale `30s` y se reinicia la aplicación, cuando se observan dos consultas consecutivas, entonces el intervalo entre sus inicios es de 30 segundos (con una tolerancia de 5 segundos).
  - Dado que la propiedad está ausente o vacía, cuando se inicia la aplicación, entonces el intervalo es de 60 segundos.
  - Dado que la propiedad vale `2s`, cuando se inicia la aplicación, entonces el intervalo efectivo es de 10 segundos.
  - Dado que la propiedad vale `abc`, `0s` o `-5s`, cuando se inicia la aplicación, entonces el arranque falla con un error de configuración que identifica la propiedad `subscription-viewer.openai.usage-poll-interval`, y no se usa 60 segundos ni ningún otro intervalo.
  - Dado que `subscription-viewer.claude.usage-poll-interval` o el intervalo de OpenCode tienen un valor no válido, cuando se inicia la aplicación, entonces el arranque falla igual que con OpenAI (comportamiento uniforme entre proveedores).
  - Dado que una consulta está en curso, cuando llega el momento de la siguiente, entonces no se inicia hasta que termine la anterior.

### REQ-010 Hora de la última actualización

- **Requisito:** Cuando el sistema completa con éxito una consulta de OpenAI, el sistema deberá mostrar la hora en que se completó.
- **Criterios de aceptación:**
  - Dado que la última consulta exitosa se completó a las 14:10:05, cuando el usuario mira la interfaz, entonces ve "14:10:05" como hora de última actualización de OpenAI.
  - Dado que una consulta posterior falla, cuando el usuario mira la interfaz, entonces la hora sigue siendo la de la última consulta exitosa.

### REQ-011 Gráfico de evolución

- **Requisito:** Cuando el sistema obtiene datos de OpenAI distintos de los ya representados, el sistema deberá añadir el porcentaje de cada ventana a un gráfico de evolución desde que se inició la aplicación, sin conservarlo entre ejecuciones.
- **Criterios de aceptación:**
  - Dado que la aplicación lleva abierta varias actualizaciones, cuando el usuario mira el gráfico, entonces ve un punto por actualización y por ventana, ordenados en el tiempo.
  - Dado que dos lecturas consecutivas devuelven los mismos valores, cuando el usuario mira el gráfico, entonces no se añaden puntos duplicados.
  - Dado que la aplicación se cierra y se vuelve a abrir, cuando el usuario mira el gráfico de OpenAI, entonces empieza vacío.

### REQ-012 Sesión caducada o no válida

- **Requisito:** Si el endpoint de cuota responde con código 401 o 403, entonces el sistema deberá no renovar el token y deberá mostrar el proveedor OpenAI con el aviso formal: "La sesión de OpenAI ha caducado o no es válida. Por favor, vuelva a iniciar sesión mediante el CLI de Codex u opencode para continuar consultando el consumo de su suscripción.", y deberá seguir releyendo las credenciales en cada ciclo para recuperarse en cuanto el fichero contenga un token válido.
- **Criterios de aceptación:**
  - Dado que el endpoint responde 401 o 403, cuando se procesa la respuesta, entonces la interfaz muestra exactamente el texto del aviso indicado en el requisito, sin mostrar el valor del token.
  - Dado que el endpoint responde 401 o 403, cuando se procesa la respuesta, entonces el sistema no realiza ninguna petición de renovación de token ni escribe en los ficheros de credenciales.
  - Dado que se obtuvieron datos antes del 401 o 403, cuando se muestra el aviso, entonces los últimos datos válidos siguen visibles.
  - Dado que el aviso está activo y el CLI renueva el token en el fichero, cuando se completa el siguiente ciclo con éxito, entonces el aviso desaparece y los datos se actualizan.
  - Dado que ocurre un 401 o 403, cuando se procesa, entonces no se reintenta de inmediato; se espera al siguiente ciclo de refresco.

### REQ-013 Errores de red y respuestas no válidas

- **Requisito:** Si la consulta de OpenAI falla por red (sin conexión, tiempo de espera, DNS u otro error transitorio), por un código distinto de 401 y 403, o por una respuesta que no sea un JSON válido, entonces el sistema deberá mantener los últimos datos válidos, indicar que están desactualizados y no mostrar mensajes técnicos.
- **Criterios de aceptación:**
  - Dado que se obtuvo un 20 % y se pierde la conectividad, cuando se muestra la interfaz, entonces sigue mostrando 20 % con un indicador de datos desactualizados.
  - Dado que la conectividad se recupera, cuando se completa la siguiente consulta exitosa, entonces el indicador desaparece.
  - Dado que no hay datos previos y la primera consulta falla por red, cuando el usuario mira la interfaz, entonces no aparece ninguna sección de OpenAI ni mensaje técnico.
  - Dado que el endpoint responde con un JSON sin `rate_limit`, cuando se procesa, entonces se trata como respuesta no válida conforme a este requisito.

### REQ-014 Independencia entre proveedores

- **Requisito:** El sistema deberá tratar OpenAI como un proveedor independiente, de modo que su fallo en tiempo de ejecución (red, 401/403, respuesta no válida) o la ausencia de sus credenciales no afecte a los demás proveedores, y mostrar únicamente los proveedores de los que recibe información. Este requisito no cubre la configuración inválida, que sigue las reglas de arranque de REQ-009 y falla el arranque de la aplicación.
- **Criterios de aceptación:**
  - Dado que OpenAI falla (red, 401/403 o sin credenciales) y Claude y OpenCode funcionan, cuando el usuario mira la interfaz, entonces Claude y OpenCode siguen mostrándose y actualizándose con normalidad.
  - Dado que solo OpenAI tiene datos, cuando se ejecuta la aplicación, entonces solo se muestra OpenAI y no aparecen secciones vacías de otros proveedores.

## Requisitos no funcionales

### NFR-001 Protección de credenciales y datos personales

- **Requisito:** El sistema deberá garantizar que el `access_token`, el `refresh_token`, el `id_token`, la `OPENAI_API_KEY`, el `account_id`, el `user_id` y el `email` nunca se escriben en los logs ni se muestran en la interfaz, y que los ficheros de credenciales nunca se modifican ni se copian.
- **Criterios de aceptación:**
  - Dado que la aplicación ha funcionado con token válido, sesión caducada (401/403), credenciales ausentes y error de red, cuando se buscan en todos los logs los valores del token, del identificador de cuenta, del `user_id` y del `email`, y la cabecera `Authorization`, entonces no aparece ninguna ocurrencia.
  - Dado que la interfaz está abierta en cualquier estado, cuando se inspeccionan los textos mostrados, entonces no aparece ningún valor de credencial, ni de `user_id` ni de `email`, ni completo ni parcial.
  - Dado que la aplicación ha estado en ejecución, cuando se comparan los ficheros `auth.json` antes y después, entonces su contenido y fecha de modificación no han cambiado por la aplicación.

### NFR-002 Interfaz receptiva

- **Requisito:** El sistema deberá mantener la interfaz receptiva mientras consulta OpenAI o ejecuta `opencode`, sin bloqueos superiores a 1 segundo.
- **Criterios de aceptación:**
  - Dado que la consulta a OpenAI tarda más de 5 segundos, cuando el usuario interactúa con la interfaz, entonces responde en menos de 1 segundo.

### NFR-003 Límite de tiempo de las operaciones externas

- **Requisito:** El sistema deberá abortar como fallo cualquier petición al endpoint de cuota que supere 30 segundos y cualquier ejecución de `opencode debug paths data` que supere 30 segundos.
- **Criterios de aceptación:**
  - Dado que el endpoint no responde, cuando transcurren 30 segundos, entonces la petición se aborta, se aplica REQ-013 y puede iniciarse el siguiente ciclo.
  - Dado que `opencode` no termina, cuando transcurren 30 segundos, entonces el proceso se finaliza y las credenciales de esa fuente se consideran no disponibles.

### NFR-004 Frecuencia razonable de peticiones

- **Requisito:** El sistema deberá realizar como máximo 6 peticiones por minuto al endpoint de cuota de OpenAI, cualquiera que sea el valor configurado en `subscription-viewer.openai.usage-poll-interval` (intervalo mínimo efectivo de 10 segundos, ver REQ-009).
- **Criterios de aceptación:**
  - Dado que la aplicación está en ejecución con credenciales válidas y el intervalo por defecto, cuando transcurre un minuto, entonces el número de peticiones a `chatgpt.com` no supera 6.
  - Dado que la propiedad vale `1s`, cuando transcurre un minuto, entonces el número de peticiones a `chatgpt.com` no supera 6.

### NFR-005 Consumo mínimo de recursos

- **Requisito:** El sistema deberá mantener, con OpenAI añadido, un consumo medio de CPU inferior al 5 % en un núcleo durante 5 minutos y un heap inferior a 256 MB en segundo plano.
- **Criterios de aceptación:**
  - Dado que la aplicación está en segundo plano con OpenAI activo, cuando se mide la CPU durante 5 minutos, entonces la media es inferior al 5 % en un núcleo.
  - Dado el mismo escenario, cuando se mide la memoria, entonces el heap es inferior a 256 MB.

## Supuestos

- S6: El directorio de usuario se obtiene del sistema (`%USERPROFILE%` en Windows); no se añade una propiedad para cambiar la ruta de `.codex/auth.json`.
- S9: Una vez que el aviso de sesión caducada (REQ-012) se muestra, no se oculta la sección aunque nunca se hayan obtenido datos, para que el usuario sepa qué hacer; esto es distinto de errores de red sin datos previos, que no muestran la sección.
- S10: Los datos de evolución solo se mantienen en memoria.
- S11: La aplicación se ejecuta como único proceso en segundo plano ya existente; no se añaden requisitos de arranque.

## Preguntas abiertas

Ninguno.
