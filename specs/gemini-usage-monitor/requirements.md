# Requisitos: gemini-usage-monitor

Estado: aprobado

## Contexto

El usuario (monousuario, entorno local de desarrollo) quiere ver en tiempo real el consumo de su suscripción de Gemini en los tres grupos de modelos que ofrece (Gemini Models, Claude and GPT models y Gemini 4 Argon), con el porcentaje consumido de cada ventana (semanal y de 5 horas) y el tiempo restante hasta su reinicio. Para ello la aplicación ejecuta el comando local `agy -p "/usage"` y lee su respuesta JSON, sin que el usuario introduzca credenciales.

## Fuera de alcance

- Introducción, edición o almacenamiento de credenciales o sesiones desde la interfaz; el inicio de sesión de `agy` es responsabilidad del usuario.
- Uso de los campos de la respuesta distintos de los grupos y sus cubos (`conversation_id`, `usage` de tokens, `duration_seconds`, `num_turns`, descripciones de grupos y cubos).
- Cálculo local de cuotas o topes: el porcentaje procede de la respuesta del comando.
- Persistencia histórica del consumo entre ejecuciones.
- Notificaciones, alertas o sonidos al acercarse a un límite.
- Gestión de pagos, facturas o cambios de plan.
- Cambios en el comportamiento de los demás proveedores.

## Requisitos funcionales

### REQ-001 Arranque como proceso de fondo

- **Requisito:** Al iniciar la aplicación, el sistema deberá ponerse en ejecución como un proceso de fondo sin bloquear la terminal ni requerir interacción del usuario para comenzar a consultar el uso de Gemini.
- **Criterios de aceptación:**
  - Dado que el usuario lanza la aplicación desde la terminal, cuando termina el arranque, entonces el proceso sigue ejecutándose en segundo plano y la interfaz está disponible.
  - Dado que el comando `agy` tarda más de 8 segundos, cuando la aplicación arranca, entonces la interfaz principal está disponible sin esperar a que termine la primera consulta de Gemini.

### REQ-002 Ejecución del comando de uso

- **Requisito:** Mientras la aplicación esté en ejecución, el sistema deberá obtener el consumo de Gemini ejecutando el comando `agy -p "/usage" --output-format json` y leyendo su salida como JSON en texto plano. El ejecutable `agy` se localizará a través del PATH del sistema.
- **Criterios de aceptación:**
  - Dado que `agy` está instalado y accesible en el PATH, cuando el sistema consulta el uso, entonces ejecuta el comando con los argumentos `-p`, `/usage`, `--output-format` y `json`.
  - Dado que la salida del comando es un JSON con `status` igual a `SUCCESS`, cuando se procesa, entonces se extraen los grupos de `command.data.groups`.
  - Dado que el JSON contiene propiedades adicionales no reconocidas, cuando se procesa, entonces se ignoran sin error.
  - Dado que el JSON contiene tanto `command.data.groups` como el texto `response`, cuando se procesa, entonces se usa `command.data.groups` y no se interpreta el texto de `response`.

### REQ-003 Monitorización de los tres grupos

- **Requisito:** Cuando el sistema recibe una respuesta válida, el sistema deberá extraer cada grupo de `groups` (identificado por su `name`) y, de cada grupo, sus cubos (`buckets`), tomando de cada cubo su ventana (`window`), su fracción restante (`remaining_fraction`) y su instante de reinicio (`reset_time`). Los grupos esperados son "Gemini Models" y "Claude and GPT models", con ventanas semanal (`weekly`) y de 5 horas (`5h`), y "Gemini 4 Argon", solo con ventana semanal.
- **Criterios de aceptación:**
  - Dada una respuesta con los tres grupos, cuando se procesa, entonces se obtienen cinco ventanas: semanal y de 5 horas de los dos primeros grupos y semanal de "Gemini 4 Argon".
  - Dado que "Gemini 4 Argon" solo incluye un cubo semanal, cuando se muestra la interfaz, entonces ese grupo muestra únicamente la ventana semanal, sin ventana de 5 horas ni valor inventado.
  - Dado que la respuesta incluye un grupo con un nombre no previsto, cuando se procesa, entonces ese grupo también se muestra con su nombre y sus ventanas.
  - Dado que un cubo tiene una `window` distinta de `weekly` y `5h`, cuando se procesa, entonces ese cubo se ignora sin error.

### REQ-004 Porcentaje consumido

- **Requisito:** El sistema deberá mostrar, para cada ventana, el porcentaje consumido, calculado como (1 − `remaining_fraction`) × 100, en escala 0-100 y acotado entre 0 y 100, de forma coherente con la presentación del resto de proveedores.
- **Criterios de aceptación:**
  - Dado `remaining_fraction` igual a 1, cuando se muestra la interfaz, entonces la ventana refleja 0 % consumido.
  - Dado `remaining_fraction` igual a 0, cuando se muestra la interfaz, entonces la ventana refleja 100 % consumido.
  - Dado `remaining_fraction` igual a 0,5, cuando se muestra la interfaz, entonces la ventana refleja 50 % consumido.
  - Dado `remaining_fraction` igual a 0,9818568229675293, cuando se muestra la interfaz, entonces la ventana refleja aproximadamente 1,8 % consumido.
  - Dado un `remaining_fraction` fuera del intervalo 0-1, cuando se calcula, entonces el porcentaje mostrado se acota a 0 o a 100 y nunca queda fuera de ese rango.
  - Dado un cubo cuyo `remaining_fraction` falta o no es numérico, cuando se procesa, entonces ese cubo se ignora sin error.

### REQ-005 Tiempo restante hasta el reinicio

- **Requisito:** Cuando el sistema obtiene datos de una ventana, el sistema deberá mostrar el tiempo restante hasta su reinicio, calculado a partir de `reset_time` (instante ISO-8601 en UTC), con el mismo patrón de presentación que el resto de proveedores.
- **Criterios de aceptación:**
  - Dado un `reset_time` situado 2 horas por delante del reloj local, cuando se muestra la interfaz, entonces la ventana muestra un tiempo restante equivalente a 2 horas.
  - Dado `reset_time` igual a `2026-10-26T21:15:54Z`, cuando se procesa, entonces se interpreta como ese instante en UTC con independencia de la zona horaria local.
  - Dado que `reset_time` ya ha pasado respecto al reloj local, cuando se muestra la interfaz, entonces el tiempo restante se muestra como 0 y no como valor negativo.
  - Dado que `reset_time` falta o no es una fecha ISO-8601 válida, cuando se procesa, entonces la ventana se muestra con su porcentaje y sin tiempo restante, sin error.

### REQ-006 Visualización de Gemini

- **Requisito:** Cuando el sistema obtiene datos de Gemini, el sistema deberá mostrar el proveedor Gemini como sección diferenciada, con un bloque por grupo que contenga, para cada ventana, el porcentaje consumido y el tiempo restante hasta el reinicio.
- **Criterios de aceptación:**
  - Dado que hay datos de los tres grupos, cuando se muestra la interfaz, entonces aparece una sección Gemini con los grupos "Gemini Models", "Claude and GPT models" y "Gemini 4 Argon" diferenciados entre sí.
  - Dado que hay datos de Gemini y de otro proveedor, cuando se muestra la interfaz, entonces Gemini aparece como sección diferenciada del resto.
  - Dado que el grupo "Claude and GPT models" tiene 1,8 % consumido en 5 horas y 0,9 % en la semana, cuando se muestra la interfaz, entonces cada ventana de ese grupo muestra su propio valor.

### REQ-007 Refresco periódico

- **Requisito:** Mientras la aplicación esté en ejecución, el sistema deberá repetir la consulta de Gemini cada intervalo configurado en la propiedad `subscription-viewer.gemini.usage-poll-interval` de `application.yml` (60 segundos por defecto), sin intervención del usuario y sin iniciar una nueva consulta mientras otra esté en curso. Si la propiedad está ausente o vacía, el sistema deberá usar 60 segundos. Si el valor es inferior a 30 segundos, el sistema deberá usar 30 segundos. Si el valor no es una duración válida o no es positivo, entonces el sistema deberá fallar el arranque con un error de configuración que identifique la propiedad, con el mismo comportamiento que ante un valor no válido en los demás proveedores.
- **Criterios de aceptación:**
  - Dado el `application.yml` entregado, cuando el usuario lo abre, entonces `subscription-viewer.gemini` contiene `usage-poll-interval: "60s"`, con el mismo formato de duración que los demás proveedores.
  - Dado que la propiedad vale `60s` y el consumo ha cambiado, cuando transcurre el intervalo, entonces los valores mostrados se actualizan sin acción del usuario.
  - Dado que una consulta tarda más de 8 segundos, cuando llega el momento de la siguiente, entonces no se inicia hasta que termine la anterior.
  - Dado que la propiedad está ausente o vacía, cuando se inicia la aplicación, entonces el intervalo es de 60 segundos.
  - Dado que la propiedad vale `5s`, cuando se inicia la aplicación, entonces el intervalo efectivo es de 30 segundos.
  - Dado que la propiedad vale `abc`, `0s` o `-5s`, cuando se inicia la aplicación, entonces el arranque falla con un error de configuración que identifica `subscription-viewer.gemini.usage-poll-interval`.

### REQ-008 Hora de la última actualización

- **Requisito:** Cuando el sistema completa con éxito una consulta de Gemini, el sistema deberá mostrar la hora en que se completó.
- **Criterios de aceptación:**
  - Dado que la última consulta exitosa se completó a las 14:10:05, cuando el usuario mira la interfaz, entonces ve "14:10:05" como hora de última actualización de Gemini.
  - Dado que una consulta posterior falla, cuando el usuario mira la interfaz, entonces la hora sigue siendo la de la última consulta exitosa.

### REQ-009 Gráfico de evolución

- **Requisito:** Cuando el sistema obtiene datos de Gemini distintos de los ya representados, el sistema deberá añadir el porcentaje consumido de cada ventana de cada grupo a un gráfico de evolución desde que se inició la aplicación.
- **Criterios de aceptación:**
  - Dado que la aplicación lleva abierta varias actualizaciones, cuando el usuario mira el gráfico, entonces ve un punto por actualización y por ventana, ordenados en el tiempo, distinguiendo grupo y ventana.
  - Dado que se recibe una nueva lectura distinta, cuando el usuario mira el gráfico, entonces aparece un nuevo punto sin recargar ni reiniciar la aplicación.
  - Dado que dos lecturas consecutivas devuelven los mismos valores, cuando el usuario mira el gráfico, entonces no se añaden puntos duplicados.
  - Dado que los valores mostrados están acotados entre 0 y 100, cuando el usuario mira el gráfico, entonces ningún punto queda fuera de ese rango.

### REQ-010 Sin histórico persistido

- **Requisito:** El sistema no deberá conservar los datos de evolución de Gemini entre ejecuciones de la aplicación.
- **Criterios de aceptación:**
  - Dado que la aplicación ha acumulado puntos en el gráfico de Gemini y se cierra, cuando se vuelve a abrir, entonces el gráfico de Gemini empieza vacío.

### REQ-011 Error del comando con mensaje formal

- **Requisito:** Si el ejecutable `agy` no existe o no puede ejecutarse, termina con código de salida distinto de cero, supera el límite de tiempo de NFR-003, devuelve una salida que no es un JSON válido, devuelve un `status` distinto de `SUCCESS` o devuelve una respuesta sin ningún grupo con cubos válidos, entonces el sistema deberá mostrar el proveedor Gemini con el aviso formal: "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde.", sin mostrar detalles técnicos (salida del comando, trazas ni códigos), y deberá seguir consultando en cada ciclo de refresco.
- **Criterios de aceptación:**
  - Dado que `agy` no está instalado, cuando el sistema intenta consultar el uso, entonces la interfaz muestra la sección Gemini con exactamente el texto del aviso y la aplicación sigue en ejecución.
  - Dado que el comando termina con código de salida distinto de cero, devuelve texto que no es JSON o devuelve `status` distinto de `SUCCESS`, cuando se procesa, entonces la interfaz muestra exactamente el texto del aviso, sin la salida del comando.
  - Dado que se obtuvieron datos antes del error, cuando se muestra el aviso, entonces los últimos datos válidos siguen visibles junto con el aviso y la hora de última actualización no cambia.
  - Dado que el aviso está activo y la siguiente consulta tiene éxito, cuando se completa, entonces el aviso desaparece y los datos se actualizan.
  - Dado que ocurre un error, cuando se procesa, entonces no se reintenta de inmediato; se espera al siguiente ciclo de refresco.
  - Dado que no se ha obtenido ningún dato válido desde el arranque y el comando falla, cuando el usuario mira la interfaz, entonces aparece la sección Gemini con el aviso y sin grupos.

### REQ-012 Grupos y ventanas ausentes

- **Requisito:** Si una respuesta válida omite alguno de los grupos esperados o alguna de sus ventanas, entonces el sistema deberá mostrar únicamente los grupos y ventanas presentes, sin error, sin aviso y sin valores inventados.
- **Criterios de aceptación:**
  - Dada una respuesta sin el grupo "Gemini 4 Argon" y con los otros dos, cuando se procesa, entonces se muestran los dos grupos presentes, no aparece Argon y no hay aviso de error.
  - Dado un grupo sin el cubo de 5 horas, cuando se procesa, entonces ese grupo muestra solo las ventanas que tiene.
  - Dado un grupo con la lista `buckets` vacía o ausente, cuando se procesa, entonces ese grupo no se muestra.
  - Dado que una respuesta posterior vuelve a incluir un grupo antes ausente, cuando se procesa, entonces el grupo aparece sin reiniciar la aplicación.

### REQ-013 Independencia entre proveedores

- **Requisito:** El sistema deberá tratar Gemini como un proveedor independiente, de modo que su fallo en tiempo de ejecución o la ausencia de `agy` no afecte a los demás proveedores, y mostrar únicamente los proveedores de los que recibe información o, en el caso de Gemini, el aviso de REQ-011. Este requisito no cubre la configuración inválida, que sigue las reglas de arranque de REQ-007.
- **Criterios de aceptación:**
  - Dado que `agy` falla y los demás proveedores funcionan, cuando el usuario mira la interfaz, entonces los demás proveedores siguen mostrándose y actualizándose con normalidad.
  - Dado que la consulta de Gemini tarda más de 8 segundos, cuando se actualizan los demás proveedores, entonces estos no esperan a que termine la de Gemini.

## Requisitos no funcionales

### NFR-001 Interfaz receptiva

- **Requisito:** El sistema deberá mantener la interfaz receptiva mientras se ejecuta `agy`, sin bloqueos superiores a 1 segundo.
- **Criterios de aceptación:**
  - Dado que una ejecución de `agy` tarda más de 8 segundos, cuando el usuario interactúa con la interfaz durante ese tiempo, entonces responde en menos de 1 segundo.

### NFR-002 Frecuencia razonable de ejecuciones

- **Requisito:** El sistema deberá ejecutar `agy` como máximo 2 veces por minuto y como máximo una ejecución simultánea, cualquiera que sea el valor configurado del intervalo.
- **Criterios de aceptación:**
  - Dado que la aplicación está en ejecución con `agy` disponible y el intervalo por defecto, cuando transcurre un minuto, entonces el número de ejecuciones de `agy` no supera 2.
  - Dado que la propiedad de intervalo vale `1s`, cuando transcurre un minuto, entonces el número de ejecuciones de `agy` no supera 2.
  - Dado que una ejecución está en curso, cuando se mide en cualquier instante, entonces no hay más de un proceso `agy` simultáneo lanzado por la aplicación.

### NFR-003 Límite de tiempo de cada ejecución

- **Requisito:** El sistema deberá abortar cualquier ejecución de `agy` que supere 60 segundos y tratarla como error conforme a REQ-011.
- **Criterios de aceptación:**
  - Dado que `agy` no termina, cuando transcurren 60 segundos, entonces el sistema finaliza el proceso, muestra el aviso de REQ-011 y puede iniciar el siguiente ciclo.
  - Dado que `agy` tarda 12 segundos en responder correctamente, cuando se completa, entonces se procesa como una consulta correcta y no se trata como error.

### NFR-004 Consumo mínimo de recursos

- **Requisito:** El sistema deberá mantener, con Gemini añadido, un consumo medio de CPU inferior al 5 % en un núcleo durante 5 minutos y un heap inferior a 256 MB en segundo plano, sin contar el proceso `agy` lanzado.
- **Criterios de aceptación:**
  - Dado que la aplicación está en segundo plano con Gemini activo, cuando se mide la CPU durante 5 minutos, entonces la media es inferior al 5 % en un núcleo.
  - Dado el mismo escenario, cuando se mide la memoria, entonces el heap es inferior a 256 MB.

## Supuestos

- S1: El argumento literal `--output-format -json` del texto del usuario se interpreta como un error tipográfico de `--output-format json`, porque la salida descrita es JSON. Hay que confirmar que `agy` acepta `json` (REQ-002).
- S2: Se muestra el porcentaje consumido ((1 − `remaining_fraction`) × 100), no el disponible, por coherencia con el resto de proveedores, que muestran consumo. El usuario describió la fracción como "100 % disponible"; esta decisión es a revisar (REQ-004).
- S3: Se parsea el JSON estructurado `command.data.groups` y no el texto tabulado de `response`, por ser más robusto; el texto `response` no se usa (REQ-002).
- S4: El ejecutable `agy` se localiza a través del PATH del sistema; no hay configuración de ruta.
- S5: El intervalo de refresco por defecto es de 60 s, con mínimo de 30 s (mayor que en otros proveedores porque el comando tarda más de 8 s), configurable en `subscription-viewer.gemini.usage-poll-interval` con las mismas reglas de validación que los demás proveedores. Los umbrales de 30 s mínimo, 2 ejecuciones por minuto y límite de ejecución de 60 s (NFR-002, NFR-003) son propuestos por falta de dato del usuario.
- S6: El "mensaje formal" es el texto exacto de REQ-011, redactado por analogía con el aviso de sesión caducada de OpenAI. Se muestra dentro de la sección Gemini, conserva los últimos datos válidos y desaparece en cuanto una consulta posterior tiene éxito. A diferencia del fallo silencioso de OpenCode, aquí el fallo es visible. Texto y comportamiento a revisar.
- S7: Si no hay datos previos y el comando falla, la sección Gemini se muestra igualmente con el aviso, para que el usuario sepa qué ocurre (criterio de OpenAI para sesión caducada).
- S8: Las ventanas se identifican por el campo `window` (`weekly`, `5h`) y los grupos por su `name`; los nombres mostrados son los devueltos por el comando. Los grupos con nombre no previsto se muestran igualmente.
- S9: Los datos de evolución solo se mantienen en memoria durante la ejecución.
- S10: La aplicación se ejecuta como único proceso en segundo plano ya existente; el mecanismo se decide en diseño.
- S11: La precisión decimal del porcentaje mostrado sigue la del resto de proveedores y se fija en diseño.
- S12: `reset_time` se interpreta como instante UTC (sufijo `Z`) y el tiempo restante se calcula localmente; la descripción textual del cubo ("will fully refresh in...") no se usa.

## Preguntas abiertas

Ninguno.
