# Requisitos: opencode-usage-monitor-v2

Estado: aprobado

## Contexto

El usuario (monousuario, entorno local de desarrollo) quiere seguir viendo en tiempo real cuánto ha consumido de su suscripción OpenCode Go en las ventanas de 5 horas, semanal y mensual. Esta versión sustituye a `opencode-usage-monitor` (v1): deja de consultar el endpoint HTTP con API key y pasa a ejecutar el CLI de `opencode` para obtener las estadísticas de sesión y calcular los porcentajes localmente contra los topes del plan Go.

## Fuera de alcance

- Consulta del endpoint HTTP `GET /zen/go/v1/usage` (sustituido por el CLI de opencode).
- Cualquier uso, configuración, lectura o protección de API key (retirado de v1: antiguos REQ-002, REQ-010 por 401/403 y NFR-006).
- Variantes del contrato HTTP `percent`/`resetsAt` y `usagePercent`/`resetInSec` (retirado de v1: antiguos REQ-003 y REQ-004).
- Mostrar el tiempo restante hasta el reinicio de cada ventana (las ventanas son móviles y no se pide en esta versión).
- Mostrar mensajes de error, de CLI ausente o indicador de datos desactualizados al usuario (el fallo es silencioso).
- Consumo de modelos de proveedores distintos de `opencode-go`.
- Persistencia histórica del consumo entre ejecuciones.
- Notificaciones, alertas o sonidos al acercarse a un límite.
- Gestión de pagos, facturas o cambios de plan; soporte de planes distintos al plan Go.
- Introducción o edición de la tabla de topes desde la interfaz.

## Requisitos funcionales

### REQ-001 Arranque como proceso de fondo

- **Requisito:** Al iniciar la aplicación, el sistema deberá ponerse en ejecución como un proceso de fondo sin bloquear la terminal ni requerir interacción del usuario para comenzar a calcular el uso.
- **Criterios de aceptación:**
  - Dado que el usuario lanza la aplicación desde la terminal, cuando termina el arranque, entonces el proceso sigue ejecutándose en segundo plano y la interfaz está disponible.
  - Dado que la aplicación se está iniciando, cuando transcurren menos de 30 segundos, entonces el usuario puede ya ver la interfaz principal.

### REQ-002 Obtención de estadísticas mediante el CLI de opencode

- **Requisito:** Mientras la aplicación esté en ejecución, el sistema deberá obtener el consumo ejecutando el CLI de `opencode` con el comando `opencode api experimental.session.stats --param from=<fromIso> --param to=<toIso> --param tools=none`, donde `<fromIso>` y `<toIso>` son instantes en formato ISO-8601 que delimitan el rango de cada ventana, y leer su salida como JSON en texto plano.
- **Criterios de aceptación:**
  - Dado que `opencode` está instalado, cuando el sistema calcula la ventana de 5 horas, entonces ejecuta el comando con `from` igual al instante actual menos 5 horas, `to` igual al instante actual y `tools=none`, ambos en ISO-8601.
  - Dado que el comando devuelve un JSON válido, cuando se procesa, entonces se extraen de cada elemento de `models` el modelo (`id`, `providerID`, `variant`), `steps`, los tokens (`input`, `output`, `reasoning`, `cache.read`, `cache.write`) y `cost`.
  - Dado que el JSON contiene propiedades adicionales no reconocidas, cuando se procesa, entonces se ignoran sin error.

### REQ-003 Fallo silencioso del CLI

- **Requisito:** Si el ejecutable `opencode` no existe, no puede ejecutarse, termina con error o devuelve una salida que no es un JSON válido, entonces el sistema deberá descartar esa consulta sin mostrar mensajes ni indicadores de error al usuario y sin interrumpir la aplicación.
- **Criterios de aceptación:**
  - Dado que `opencode` no está instalado, cuando el sistema intenta consultar el uso, entonces la interfaz no muestra ningún mensaje ni aviso de error y la aplicación sigue en ejecución.
  - Dado que el comando termina con código de salida distinto de cero, cuando se procesa, entonces la interfaz no muestra ningún mensaje de error y conserva los últimos datos válidos sin indicador de desactualización.
  - Dado que el comando devuelve texto que no es JSON válido, cuando se procesa, entonces se descarta sin mostrar nada al usuario.
  - Dado que no se ha obtenido ningún dato válido desde el arranque, cuando el usuario mira la interfaz, entonces no aparece ninguna sección del proveedor OpenCode.
  - Dado que tras un fallo el CLI vuelve a funcionar, cuando se completa la siguiente consulta correcta, entonces los valores mostrados se actualizan con normalidad.

### REQ-004 Ventanas de tiempo

- **Requisito:** El sistema deberá calcular el consumo de tres ventanas: 5 horas (límite igual al 20 % del tope mensual del modelo), 7 días (50 % del tope mensual) y mes (100 % del tope mensual). Las ventanas de 5 horas y 7 días serán móviles, terminando en el instante actual; la ventana de mes abarcará desde el último inicio de ciclo mensual hasta el instante actual. El inicio de ciclo es el día del mes configurado (por defecto, día 1) a las 00:00 en la zona horaria local; es el más reciente que no sea posterior al instante actual. Si el mes correspondiente tiene menos días que el día configurado, el inicio de ciclo de ese mes será su último día.
- **Criterios de aceptación:**
  - Dado el día de ciclo 1 y que son las 14:00 del 17 de octubre, cuando se calculan las ventanas, entonces la de 5 horas va de las 09:00 de ese día a las 14:00, la de 7 días va del 10 de octubre a las 14:00 al 17 de octubre a las 14:00, y la mensual va del 1 de octubre a las 00:00 al 17 de octubre a las 14:00.
  - Dado el día de ciclo 20 y que son las 14:00 del 17 de octubre, cuando se calcula la ventana mensual, entonces va del 20 de septiembre a las 00:00 al 17 de octubre a las 14:00.
  - Dado el día de ciclo 20 y que son las 14:00 del 25 de octubre, cuando se calcula la ventana mensual, entonces va del 20 de octubre a las 00:00 al 25 de octubre a las 14:00.
  - Dado el día de ciclo 31 y que son las 10:00 del 5 de marzo (no bisiesto), cuando se calcula la ventana mensual, entonces empieza el 28 de febrero a las 00:00, último día de febrero.
  - Dado el día de ciclo 31 y que son las 10:00 del 30 de abril, cuando se calcula la ventana mensual, entonces empieza el 30 de abril a las 00:00 (último día de abril) y no el 31 de marzo.
  - Dado que se cambia el día de ciclo en la configuración y se reinicia la aplicación, cuando se recalcula, entonces la ventana mensual usa el nuevo día.
  - Dado que se llega al inicio de un nuevo ciclo, cuando se calcula la ventana mensual, entonces el consumo del ciclo anterior no se incluye.
  - Dado que el día de ciclo configurado no es un entero entre 1 y 31, cuando arranca la aplicación, entonces se usa el día 1.

### REQ-005 Filtrado del proveedor opencode-go

- **Requisito:** Al procesar la respuesta de `experimental.session.stats`, el sistema deberá contabilizar únicamente las entradas de `models` cuyo `providerID` sea `opencode-go` y descartar el resto.
- **Criterios de aceptación:**
  - Dado que la respuesta contiene un modelo con `providerID` `opencode-go` y otro con `providerID` distinto, cuando se procesa, entonces solo el primero aparece en la interfaz y se usa en los cálculos.
  - Dado que la respuesta no contiene ningún modelo de `opencode-go`, cuando se procesa, entonces no se muestra ningún modelo ni porcentaje de OpenCode.

### REQ-006 Cálculo del porcentaje consumido

- **Requisito:** El sistema deberá calcular, para cada modelo de `opencode-go` y cada ventana, el porcentaje consumido como el coste acumulado en dólares (`cost`) de la ventana dividido por el límite de la ventana, expresado en escala 0-100 y saturado a un máximo de 100 (no hay balance habilitado, por lo que nunca se muestra más de 100 %): porcentaje 5 h = gasto 5 h / (tope mensual × 0,20) × 100; porcentaje 7 días = gasto 7 días / (tope mensual × 0,50) × 100; porcentaje mes = gasto mes / tope mensual × 100.
- **Criterios de aceptación:**
  - Dado un modelo con tope mensual de 60 USD y gasto de 6 USD en las últimas 5 horas, cuando se calcula, entonces el porcentaje de 5 horas es 50 % (6 / 12 × 100).
  - Dado el mismo modelo con gasto de 15 USD en 7 días, cuando se calcula, entonces el porcentaje semanal es 50 % (15 / 30 × 100).
  - Dado el mismo modelo con gasto de 45 USD en el mes, cuando se calcula, entonces el porcentaje mensual es 75 %.
  - Dado que el gasto de una ventana supera su límite (por ejemplo, 15 USD en 5 horas con límite de 12 USD), cuando se calcula, entonces el porcentaje resultante es 100 % y nunca se muestra un valor superior.
  - Dado que el gasto de una ventana es exactamente igual a su límite, cuando se calcula, entonces el porcentaje es 100 %.
  - Dado que un modelo no tiene coste en una ventana, cuando se calcula, entonces su porcentaje es 0 %.
  - Dado que un mismo modelo aparece con varias `variant`, cuando se calcula, entonces sus costes se suman en un único porcentaje por modelo.

### REQ-007 Tabla de topes del plan Go

- **Requisito:** El sistema deberá comparar el gasto contra una tabla de topes mensuales en dólares por modelo de `opencode-go`, definida en la configuración de la aplicación, sin que el usuario deba introducirla en la interfaz. La configuración deberá ofrecer una estructura de tabla por modelo preparada para que el usuario rellene los valores con facilidad, incluyendo entradas de ejemplo o marcador.
- **Criterios de aceptación:**
  - Dado que se entrega la configuración por defecto, cuando el usuario la abre, entonces encuentra una tabla de topes por modelo con entradas de ejemplo o marcador, y añadir o cambiar un modelo consiste en editar una sola línea (modelo y tope en dólares).
  - Dado que una entrada marcador no tiene un valor de tope definido, cuando se calculan los porcentajes de ese modelo, entonces se trata como un modelo no listado y se usa el tope por defecto.
  - Dado que la tabla define un tope para un modelo, cuando se calculan sus porcentajes, entonces se usa ese tope.
  - Dado que un modelo no figura en la tabla, cuando se calculan sus porcentajes, entonces se usa el tope por defecto del plan (60 USD).
  - Dado que se modifica un tope en la configuración y se reinicia la aplicación, cuando se recalcula, entonces los porcentajes reflejan el nuevo tope.

### REQ-008 Visualización del consumo por ventanas

- **Requisito:** Cuando el sistema obtiene datos de uso, el sistema deberá mostrar, para cada modelo de `opencode-go` con consumo y cada ventana (5 horas, semanal y mensual), el porcentaje consumido.
- **Criterios de aceptación:**
  - Dado que un modelo ha consumido el 30 % de su límite de 5 horas, cuando se muestra la interfaz, entonces la ventana de 5 horas de ese modelo refleja 30 %.
  - Dado que hay dos modelos con consumo, cuando se muestra la interfaz, entonces cada uno muestra sus tres ventanas de forma diferenciada.
  - Dado que hay modelos con consumo, cuando se muestra la interfaz, entonces además de las filas por modelo aparece una fila o bloque "Total" con las tres ventanas, diferenciado de los modelos (ver REQ-016).

### REQ-009 Refresco periódico

- **Requisito:** Mientras la aplicación esté en ejecución, el sistema deberá repetir periódicamente la obtención y el cálculo del consumo sin intervención del usuario.
- **Criterios de aceptación:**
  - Dado que la aplicación está abierta y el consumo ha cambiado, cuando transcurre el intervalo de refresco, entonces los valores mostrados se actualizan sin que el usuario realice ninguna acción.
  - Dado que un ciclo de consulta está en curso, cuando llega el momento del siguiente, entonces el sistema no inicia uno nuevo hasta que termine el anterior.

### REQ-010 Hora de la última actualización

- **Requisito:** Cuando el sistema completa con éxito un ciclo de obtención y cálculo, el sistema deberá mostrar la hora en que se completó.
- **Criterios de aceptación:**
  - Dado que el último ciclo exitoso se completó a las 14:10:05, cuando el usuario mira la interfaz, entonces ve "14:10:05" como hora de última actualización.
  - Dado que un ciclo falla en silencio, cuando el usuario mira la interfaz, entonces la hora de última actualización sigue siendo la del último ciclo exitoso.

### REQ-011 Gráfico de evolución

- **Requisito:** Cuando el sistema obtiene datos más recientes que los ya representados, el sistema deberá añadir el porcentaje consumido de cada ventana a un gráfico que muestre su evolución desde que se inició la aplicación, tanto por modelo como del total agregado (REQ-016).
- **Criterios de aceptación:**
  - Dado que hay modelos con consumo, cuando el usuario mira el gráfico, entonces puede ver la evolución de las tres ventanas del total y de cada modelo, con valores nunca superiores a 100 %.
  - Dado que la aplicación lleva abierta varias actualizaciones, cuando el usuario mira el gráfico, entonces ve un punto por actualización y por ventana, ordenados en el tiempo.
  - Dado que se recibe una nueva lectura, cuando el usuario mira el gráfico, entonces aparece un nuevo punto sin recargar ni reiniciar la aplicación.
  - Dado que dos lecturas consecutivas devuelven los mismos valores, cuando el usuario mira el gráfico, entonces no se añaden puntos duplicados.

### REQ-012 Sin histórico persistido

- **Requisito:** El sistema no deberá conservar los datos de evolución entre ejecuciones de la aplicación.
- **Criterios de aceptación:**
  - Dado que la aplicación ha acumulado puntos en el gráfico y se cierra, cuando se vuelve a abrir, entonces el gráfico empieza vacío.

### REQ-013 Preparación para varios proveedores

- **Requisito:** El sistema deberá tratar cada proveedor de AI como una unidad independiente de obtención y presentación de datos, de modo que el fallo o la ausencia de un proveedor no afecte a los demás, y mostrar únicamente los proveedores de los que recibe información.
- **Criterios de aceptación:**
  - Dado que solo existe el proveedor OpenCode, cuando se ejecuta la aplicación, entonces solo se muestra OpenCode y no aparecen secciones vacías de otros proveedores.
  - Dado que `opencode` no está instalado y otro proveedor configurado funciona, cuando el usuario mira la interfaz, entonces el otro proveedor sigue mostrándose y actualizándose con normalidad y OpenCode no aparece.

### REQ-014 Retirada del código de la versión 1

- **Requisito:** El sistema deberá dejar de contener el código, configuración, pruebas y referencias de v1 que dejan de aplicar: cliente del endpoint HTTP `/zen/go/v1/usage`, lectura y protección de la API key (`OPENCODE_API_KEY`, propiedad en `application.yml`, ficheros de configuración de ejemplo de la clave), tratamiento de 401/403 y de las variantes de contrato `percent`/`usagePercent`. Esta limpieza se ejecutará como una tarea propia y diferenciada en la fase de tareas, de modo que el documentador actualice correctamente las referencias (README, arquitectura, contratos).
- **Criterios de aceptación:**
  - Dado que la v2 está implementada, cuando se busca en el código y la configuración `OPENCODE_API_KEY`, `Authorization: Bearer` o `/zen/go/v1/usage`, entonces no aparece ninguna referencia activa.
  - Dado que se ha hecho la limpieza, cuando se compila y se ejecutan las pruebas, entonces todo pasa sin depender de código retirado.
  - Dado que la lista de tareas de la feature, cuando se revisa, entonces la limpieza aparece como una tarea independiente de las de funcionalidad nueva.
  - Dado que la limpieza ha terminado, cuando el documentador revisa la documentación, entonces ninguna referencia apunta a elementos retirados de v1.

### REQ-015 Consulta paralela de las tres ventanas

- **Requisito:** En cada ciclo de refresco, el sistema deberá ejecutar las tres consultas al CLI (5 horas, 7 días y mes) en paralelo, con tres hilos de ejecución simultáneos como máximo, y deberá dar el ciclo por terminado solo cuando las tres hayan finalizado, sin iniciar el siguiente ciclo antes. El ciclo solo actualizará los datos mostrados si las tres consultas han tenido éxito; si alguna falla, el ciclo se descarta en silencio conforme a REQ-003.
- **Criterios de aceptación:**
  - Dado que el ciclo arranca, cuando se lanzan las consultas, entonces las tres están en ejecución a la vez y nunca hay más de tres procesos `opencode` simultáneos.
  - Dado que cada consulta tarda 4 segundos, cuando se completa un ciclo, entonces su duración total es inferior a 8 segundos (no la suma de las tres).
  - Dado que una consulta de las tres tarda más que las demás, cuando las otras ya han terminado, entonces el ciclo sigue abierto y no se inicia otro hasta que termine la última (o venza su límite de NFR-006).
  - Dado que una de las tres consultas falla y las otras dos tienen éxito, cuando termina el ciclo, entonces la interfaz conserva los últimos datos válidos completos, no muestra error y la hora de última actualización no cambia.
  - Dado que las tres consultas tienen éxito, cuando termina el ciclo, entonces los valores de las tres ventanas se muestran juntos y se actualizan a la vez.

### REQ-016 Total agregado de modelos

- **Requisito:** El sistema deberá calcular y mostrar, para cada ventana, un porcentaje total agregado de todos los modelos de `opencode-go`, igual a la suma del gasto de la ventana de todos los modelos dividida por la suma de los límites de esa ventana de los modelos con consumo en ella (gasto mayor que 0), expresado en escala 0-100 y saturado a un máximo de 100.
- **Criterios de aceptación:**
  - Dado el modelo A (tope 60 USD) con 6 USD y el modelo B (tope 100 USD) con 4 USD en 5 horas, cuando se calcula el total, entonces el porcentaje total de 5 horas es 10 / (12 + 20) × 100 = 31,25 %.
  - Dado un modelo C con tope 80 USD sin consumo en 5 horas, cuando se calcula el total de 5 horas, entonces su límite no entra en el denominador.
  - Dado que ningún modelo tiene consumo en una ventana, cuando se calcula, entonces el total de esa ventana es 0 %.
  - Dado que el cociente resulta superior a 100 %, cuando se calcula, entonces el total es 100 %.
  - Dado que hay un único modelo con consumo, cuando se calcula, entonces el total coincide con el porcentaje de ese modelo.

## Requisitos no funcionales

### NFR-001 Tiempo de arranque

- **Requisito:** El sistema deberá completar el arranque en menos de 30 segundos en un entorno monousuario local con Java 21.
- **Criterios de aceptación:**
  - Dado que el usuario lanza la aplicación en una máquina con Java 21, cuando termina el arranque, entonces el tiempo desde la invocación hasta la interfaz usable es inferior a 30 segundos.

### NFR-002 Compatibilidad con Java 21

- **Requisito:** El sistema deberá ser compatible con Java 21.
- **Criterios de aceptación:**
  - Dado que el entorno usa Java 21, cuando se compila y ejecuta la aplicación, entonces no se producen errores de versión ni advertencias de deprecación que impidan su funcionamiento.

### NFR-003 Consumo mínimo de recursos

- **Requisito:** El sistema deberá consumir recursos mínimos durante su ejecución en segundo plano.
- **Criterios de aceptación:**
  - Dado que la aplicación está en segundo plano sin interacción, cuando se mide la CPU durante 5 minutos, entonces el consumo medio es inferior al 5 % en un núcleo (sin contar el proceso `opencode` lanzado).
  - Dado que la aplicación está en segundo plano, cuando se mide la memoria, entonces el heap es inferior a 256 MB.

### NFR-004 Interfaz receptiva durante la ejecución del CLI

- **Requisito:** El sistema deberá mantener la interfaz receptiva mientras se ejecuta el CLI de `opencode`, sin bloqueos superiores a 1 segundo.
- **Criterios de aceptación:**
  - Dado que una ejecución de `opencode` tarda más de 5 segundos, cuando el usuario interactúa con la interfaz durante ese tiempo, entonces la interfaz responde en menos de 1 segundo.
  - Dado que las tres consultas paralelas están en curso, cuando el usuario interactúa con la interfaz, entonces esta responde en menos de 1 segundo.

### NFR-005 Frecuencia razonable de ejecuciones

- **Requisito:** El sistema deberá limitar la frecuencia de ejecuciones del CLI para no sobrecargar el sistema local.
- **Criterios de aceptación:**
  - Dado que la aplicación está en ejecución con `opencode` disponible, cuando transcurre un minuto, entonces el número de ejecuciones del CLI (contando las tres consultas de cada ciclo, REQ-015) no supera las 30.
  - Dado que la ejecución en paralelo lanza tres procesos por ciclo, cuando se mide en cualquier instante, entonces no hay más de tres procesos `opencode` simultáneos.

### NFR-006 Límite de tiempo de cada ejecución

- **Requisito:** El sistema deberá abortar cualquier ejecución de `opencode` que supere 30 segundos y tratarla como un fallo silencioso.
- **Criterios de aceptación:**
  - Dado que `opencode` no termina, cuando transcurren 30 segundos, entonces el sistema finaliza el proceso, no muestra ningún error y puede iniciar el siguiente ciclo.

## Supuestos

- S1: Los topes mensuales proceden de una tabla por modelo en la configuración de la aplicación, cuyos valores rellena el usuario; se entrega con entradas de ejemplo o marcador. El valor por defecto de un modelo no listado (o marcador sin valor) es 60 USD (tope mensual del plan Go según v1, con lo que 5 h = 12 USD y 7 días = 30 USD, coherentes con v1).
- S2: El límite se aplica por modelo y además se muestra un total agregado por ventana (REQ-016). El denominador del total suma solo los límites de los modelos con consumo en esa ventana; se elige esta definición por ser la propuesta del usuario y por no penalizar con topes de modelos sin uso.
- S3: La fórmula escrita con "/ 100" se interpreta como resultado en escala 0-100 (gasto / límite × 100), no como fracción 0-1. El resultado se satura a 100 % porque no hay balance habilitado.
- S4: "Mes" es el ciclo de suscripción en curso: desde el último inicio de ciclo (día configurable, por defecto 1, a las 00:00 en la zona horaria local) hasta ahora; no es una ventana de 30 días. En meses con menos días que el día configurado el ciclo empieza el último día de ese mes. Un valor de día no válido se sustituye por 1.
- S5: Las ventanas de 5 horas y 7 días son móviles (hasta ahora), por lo que no se calcula ni muestra tiempo restante hasta el reinicio.
- S6: El ejecutable `opencode` se localiza a través del PATH del sistema; no hay configuración de ruta.
- S7: Se ejecuta el comando una vez por ventana en cada ciclo de refresco, cada una con su rango `from`/`to`, y las tres ejecuciones se lanzan en paralelo con tres hilos (REQ-015). El rendimiento de cada consulta no se considera un criterio más allá de los umbrales de NFR. El ciclo es atómico: si falla una consulta, no se actualiza ninguna ventana, para no mostrar datos de instantes distintos mezclados (decisión propuesta, a revisar).
- S8: Los costes de un mismo modelo con distintas `variant` se suman; `steps` y tokens se leen pero solo `cost` interviene en los porcentajes.
- S9: El fallo silencioso no incluye indicador de desactualización; los últimos datos válidos permanecen visibles tal cual.
- S10: El refresco periódico es de 60 segundos por defecto (heredado de v1), a fijar en diseño.
- S11: Los datos de evolución solo se mantienen en memoria durante la ejecución.
- S12: La aplicación se ejecuta como un único proceso en segundo plano; el mecanismo exacto se decide en diseño.
- S13: Los umbrales de NFR-005 (30 ejecuciones por minuto) y NFR-006 (30 s) son propuestos por falta de dato del usuario.

## Preguntas abiertas

Ninguno.
