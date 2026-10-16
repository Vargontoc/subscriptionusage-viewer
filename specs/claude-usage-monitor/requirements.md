# Requisitos: claude-usage-monitor

Estado: aprobado

## Contexto

El usuario (monousuario, entorno local de desarrollo) quiere ver en tiempo real el estado de su suscripción a Claude: cuánto ha consumido de cada ventana de límite (sesión de 5 horas y semanal) y cuánto falta para su reinicio.

Claude Code es interactivo y no ofrece un comando no interactivo que informe del uso. Sin embargo, Claude Code envía a su *status line* (un script configurable por el usuario) un JSON por stdin que incluye `rate_limits` con las ventanas `five_hour` y `seven_day`, cada una con `used_percentage` (0 a 100) y `resets_at` (epoch en segundos). Como la status line solo "empuja" datos, el sistema se compone de dos partes:

1. **Hook de status line:** un script que Claude Code ejecuta y que persiste en un fichero local los datos de `rate_limits` que recibe.
2. **Aplicación de monitorización:** lee ese fichero periódicamente y muestra el estado.

La estructura debe quedar preparada para añadir otros proveedores de AI en el futuro.

## Fuera de alcance

- Otros proveedores distintos de Claude (solo se prepara la estructura).
- Histórico persistido entre ejecuciones de la aplicación (el fichero de estado solo guarda la última lectura).
- Gestión de credenciales, inicio de sesión o uso de servicios externos.
- Mensajes de error, alertas o diagnóstico de fallos de Claude Code o del hook.
- Notificaciones o avisos al acercarse a un límite.
- Estimar el uso a partir de los ficheros JSONL de Claude Code.
- Automatizar el comando `/usage` ni consultar endpoints no documentados.

## Requisitos funcionales

### REQ-001 Origen de los datos

- **Requisito:** El sistema deberá obtener los datos de uso de Claude únicamente del fichero de estado que escribe el hook de status line a partir de los datos que Claude Code le proporciona.
- **Criterios de aceptación:**
  - Dado Claude Code con suscripción activa, el hook configurado y una sesión en uso, cuando la aplicación realiza una lectura, entonces los valores mostrados coinciden con los `rate_limits` que Claude Code entregó al hook en su última ejecución.
  - Dado que la aplicación no dispone de credenciales propias, cuando realiza una lectura, entonces la lectura funciona sin que el usuario introduzca ninguna credencial en la aplicación.

### REQ-002 Porcentaje consumido por ventana

- **Requisito:** Cuando el sistema recibe datos de Claude, el sistema deberá mostrar, para cada ventana de límite (sesión de 5 horas y semanal), el porcentaje consumido informado por Claude Code.
- **Criterios de aceptación:**
  - Dado que el fichero de estado contiene un 40 % consumido en `five_hour` y un 15 % en `seven_day`, cuando se completa la lectura, entonces la aplicación muestra 40 % y 15 % en sus ventanas respectivas.

### REQ-003 Tiempo restante hasta el reinicio

- **Requisito:** Cuando el sistema recibe datos de Claude, el sistema deberá mostrar, para cada ventana de límite, el tiempo restante hasta su reinicio, calculado como la diferencia entre `resets_at` y la hora actual.
- **Criterios de aceptación:**
  - Dado que `resets_at` de la ventana de sesión está 2 h 10 min en el futuro, cuando se completa la lectura, entonces la aplicación muestra 2 h 10 min junto a la ventana de sesión.
  - Dado que no se recibe ninguna lectura nueva, cuando transcurre un minuto, entonces el tiempo restante mostrado disminuye en un minuto.

### REQ-004 Actualización automática

- **Requisito:** Mientras la aplicación está en ejecución, el sistema deberá repetir la lectura del fichero de estado cada 10 segundos sin intervención del usuario.
- **Criterios de aceptación:**
  - Dado que la aplicación está abierta y el hook actualiza el fichero con un nuevo consumo, cuando transcurren 10 segundos, entonces los valores mostrados reflejan el nuevo consumo sin que el usuario realice ninguna acción.

### REQ-005 Hora de la última actualización de los datos

- **Requisito:** Cuando el sistema lee datos de Claude, el sistema deberá mostrar la hora a la que el hook los registró por última vez.
- **Criterios de aceptación:**
  - Dado que el hook escribió el fichero a las 10:32:15, cuando el usuario mira la aplicación, entonces ve la hora 10:32:15 como hora de última actualización, aunque la aplicación haya leído el fichero más tarde.

### REQ-006 Gráfico de evolución

- **Requisito:** Cuando el sistema lee datos de Claude que son más recientes que los ya representados, el sistema deberá añadir el porcentaje consumido de cada ventana a un gráfico que muestre su evolución desde que se inició la aplicación.
- **Criterios de aceptación:**
  - Dado que la aplicación lleva abierta varias actualizaciones de datos, cuando el usuario mira el gráfico, entonces ve un punto por actualización y por ventana, ordenados en el tiempo.
  - Dado que el hook registra nuevos datos, cuando el usuario mira el gráfico, entonces aparece un nuevo punto sin recargar ni reiniciar la aplicación.
  - Dado que el fichero no ha cambiado entre dos lecturas, cuando el usuario mira el gráfico, entonces no se añaden puntos duplicados.

### REQ-007 Sin histórico persistido

- **Requisito:** El sistema no deberá conservar los datos de evolución entre ejecuciones de la aplicación.
- **Criterios de aceptación:**
  - Dado que la aplicación ha acumulado puntos y se cierra, cuando se vuelve a abrir, entonces el gráfico empieza vacío.

### REQ-008 Fallo silencioso y datos caducados

- **Requisito:** Si el fichero de estado no existe, no es legible, no tiene un contenido utilizable o los datos de una ventana han caducado (su `resets_at` ya ha pasado), entonces el sistema no deberá mostrar ningún mensaje de error y deberá omitir esa ventana en la vista; si ninguna ventana de Claude tiene datos vigentes, deberá omitir a Claude.
- **Criterios de aceptación:**
  - Dado que el fichero de estado no existe, cuando la aplicación intenta una lectura, entonces no aparece ningún mensaje de error ni aviso y Claude no se muestra.
  - Dado que el fichero está incompleto o con formato inválido, cuando la aplicación intenta una lectura, entonces no aparece ningún mensaje de error y la ventana afectada se omite hasta que haya datos vigentes.
  - Dado que Claude Code lleva cerrado más de 5 horas, cuando la aplicación lee el fichero, entonces la ventana de sesión no se muestra por haber caducado, y la semanal se sigue mostrando mientras su `resets_at` no haya pasado.
  - Dado que Claude estaba oculto, cuando una lectura posterior encuentra datos vigentes, entonces Claude vuelve a mostrarse con esos datos.

### REQ-009 Texto neutro sin proveedores

- **Requisito:** Si ningún proveedor tiene datos que mostrar, entonces el sistema deberá mostrar un texto neutro que no indique error.
- **Criterios de aceptación:**
  - Dado que ninguna lectura devuelve datos vigentes, cuando el usuario mira la aplicación, entonces ve un texto informativo neutro (sin términos de error, fallo o advertencia) en lugar de ventanas de uso.

### REQ-010 Preparación para varios proveedores

- **Requisito:** El sistema deberá tratar cada proveedor de AI como una unidad independiente de obtención y presentación de datos, de modo que el fallo o la ausencia de un proveedor no afecte a los demás, y mostrar únicamente los proveedores de los que recibe información.
- **Criterios de aceptación:**
  - Dado que solo existe el proveedor Claude, cuando se ejecuta la aplicación, entonces solo se muestra Claude y no aparecen secciones vacías de otros proveedores.
  - Dado un segundo proveedor incorporado en el futuro, cuando uno de los dos no devuelve datos, entonces el otro sigue mostrándose y actualizándose con normalidad.

### REQ-011 Hook de status line

- **Requisito:** Cuando Claude Code ejecuta el hook de status line con datos de `rate_limits`, el hook deberá escribir en el fichero de estado únicamente el porcentaje consumido y el `resets_at` de cada ventana, junto con la hora de escritura, de forma atómica y sin alterar la salida de la status line.
- **Criterios de aceptación:**
  - Dado un JSON de entrada con `rate_limits.five_hour` y `rate_limits.seven_day`, cuando se ejecuta el hook, entonces el fichero de estado contiene `used_percentage` y `resets_at` de ambas ventanas y la hora de escritura, y ningún otro dato del JSON de entrada (rutas, identificadores de sesión, nombre de repositorio, costes).
  - Dado que la aplicación lee el fichero mientras el hook escribe, cuando se completan ambas operaciones, entonces la aplicación nunca obtiene un contenido parcial (escritura en fichero temporal y renombrado).
  - Dado que el renombrado falla de forma transitoria (por ejemplo, `PermissionError` en Windows porque el lector tiene el fichero abierto), cuando se ejecuta el hook, entonces reintenta un número reducido de veces y, si sigue fallando, termina sin error visible ni cambio en la salida de la status line.
  - Dado un JSON de entrada sin `rate_limits` (por ejemplo, cuenta de API o versión que no lo incluye), cuando se ejecuta el hook, entonces no sobrescribe el fichero de estado con datos vacíos.
  - Dado que varias sesiones de Claude Code están abiertas a la vez, cuando todas ejecutan el hook, entonces el fichero queda con datos válidos de la última escritura (el límite es por cuenta, no por sesión).

### REQ-012 Encadenado con una status line existente

- **Requisito:** Donde el usuario ya tenga una status line propia, el hook deberá poder ejecutarla y reenviar su salida sin modificarla, de modo que persistir el estado y mostrar la status line original sean operaciones independientes.
- **Criterios de aceptación:**
  - Dado que el hook se configura con el comando de la status line original, cuando Claude Code lo ejecuta, entonces el hook lee stdin una sola vez, pasa el mismo JSON al comando original y devuelve su salida tal cual.
  - Dado que el comando original falla o no responde, cuando se ejecuta el hook, entonces el fichero de estado se actualiza igualmente.
  - Dado que la escritura del fichero de estado falla, cuando se ejecuta el hook, entonces la salida de la status line original se muestra con normalidad.
  - Dado que no hay status line original configurada, cuando se ejecuta el hook, entonces imprime una línea mínima propia con los porcentajes de ambas ventanas.

## Requisitos no funcionales

### NFR-001 Fluidez de la interfaz durante la lectura

- **Requisito:** El sistema deberá mantener la interfaz receptiva mientras se ejecuta una lectura del fichero de estado, sin bloqueos superiores a 1 segundo.
- **Criterios de aceptación:**
  - Dado que una lectura del fichero tarda más de 5 segundos en responder (por ejemplo, por un disco lento o bloqueado), cuando el usuario interactúa con la aplicación durante ese tiempo, entonces la interfaz responde en menos de 1 segundo.

### NFR-002 Sin conexiones propias ni credenciales

- **Requisito:** Ni la aplicación ni el hook deberán iniciar conexiones de red propias a servicios externos ni almacenar credenciales del usuario.
- **Criterios de aceptación:**
  - Dado que la aplicación está en ejecución durante 5 minutos, cuando se inspecciona su actividad de red, entonces no se observan conexiones iniciadas por la aplicación ni por el hook.
  - Dado el almacenamiento de la aplicación y el contenido del fichero de estado, cuando se revisan tras su uso, entonces no contienen credenciales del usuario.

## Fichero de estado

- **Ubicación por defecto:** `~/.claude/statusline-state.json` (en Windows, `C:\Users\<usuario>\.claude\statusline-state.json`). La ruta debe ser configurable en la aplicación y en el hook.
- **Formato:** JSON con todas las marcas de tiempo en epoch de segundos. Solo contiene estos campos:

```json
{
  "written_at": 1792175400,
  "five_hour": { "used_percentage": 5, "resets_at": 1792186200 },
  "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
}
```

- `used_percentage` es un número de 0 a 100 que puede ser entero (Claude Code lo entrega como entero); la aplicación lo muestra tal cual, sin asumir decimales.

## Supuestos

- S1: Claude Code no ofrece un comando no interactivo que informe del uso; los datos se obtienen del JSON que Claude Code entrega a la status line (`rate_limits.five_hour` y `rate_limits.seven_day`, con `used_percentage` y `resets_at` en epoch de segundos). Este campo existe solo con suscripción (Pro/Max, autenticación OAuth) y depende de la versión de Claude Code; en algunas versiones se ha reportado ausente. Verificado en la versión 2.1.301 (Windows), cuyo JSON incluye ambas ventanas; en otras versiones la viabilidad se comprueba con el JSON real. Si la versión instalada no lo incluye, la feature no es viable tal como se pide.
- S2: Las dos ventanas mostradas son únicamente la sesión de 5 horas y la semanal; otros límites que Claude Code pudiera informar no se muestran.
- S3: Los 10 segundos se cuentan entre lecturas sucesivas; una lectura lenta no provoca lecturas simultáneas solapadas.
- S4: Los datos solo se refrescan mientras haya una sesión de Claude Code abierta que ejecute la status line; con Claude Code cerrado el fichero conserva la última lectura y la aplicación la considera vigente hasta que pasa el `resets_at` de cada ventana.
- S5: Tras un fallo de lectura o datos caducados, la ventana afectada se oculta hasta que haya datos vigentes (no se mantiene el último valor conocido); los puntos ya acumulados en el gráfico se conservan mientras la aplicación siga abierta.
- S6: El gráfico muestra el porcentaje consumido (0 a 100 %) de cada ventana frente al tiempo, sin límite de puntos acumulados durante la sesión de la aplicación; el diseño decidirá si conviene acotarlo.
- S7: Uso monousuario local, una sola instancia de la aplicación y una sola cuenta de Claude (la autenticada en Claude Code).
- S8: El usuario configura manualmente la entrada `statusLine` en `~/.claude/settings.json` para apuntar al hook; la aplicación no modifica la configuración de Claude Code.
- S9: El hook se implementa en Python 3 usando solo la librería estándar, por su portabilidad, su arranque rápido (Claude Code lo ejecuta repetidamente) y por no depender de `jq`. No se usa Java para el hook por el coste de arranque de la JVM en cada ejecución.
- S10: En Windows, el comando de la status line usa `python` (no `python3`, que suele ser un alias de la Microsoft Store que no funciona) y una ruta absoluta o `%USERPROFILE%` en lugar de `~`. Ejemplo: `python C:\Users\<usuario>\.claude\statusline-state.py`.
- S11: La versión mínima de Claude Code no se fija de forma contractual: el changelog sitúa la incorporación de `rate_limits` en la 2.1.80, pero hay reportes de ausencia en versiones posteriores. La viabilidad se determina por la presencia del campo en el JSON real.

## Preguntas abiertas

Ninguna.