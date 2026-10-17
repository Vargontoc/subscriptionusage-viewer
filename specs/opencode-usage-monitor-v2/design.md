# Diseño: opencode-usage-monitor-v2

Estado: aprobado

## Resumen

`opencode-usage-source` deja de consultar HTTP y pasa a ejecutar el CLI `opencode` tres veces por ciclo (5 h, 7 días, mes) en un pool fijo de 3 hilos. Cada ejecución tiene timeout de 30 s y cualquier fallo es silencioso. El ciclo es atómico: si las tres consultas tienen éxito, el calculador local deriva el porcentaje por modelo y el total contra una tabla de topes de `application.yml`, y el resultado se publica de una vez. `usage-poller`, `usage-history`, `usage-snapshot-publisher` y `usage-window` se reutilizan; el código de v1 asociado a HTTP, API key y estados de autenticación se retira en una tarea de limpieza propia (REQ-014).

## Supuestos sobre el sistema actual

- No se ha abierto el código; solo se ha usado `.sdd/architecture.md` (actualizado 2026-10-16) y el diseño de v1. Las responsabilidades de los componentes existentes se toman del mapa.
- `usage-poller` ya garantiza no solapar ciclos de una misma fuente y aísla las fuentes entre sí (según el mapa); aquí se reutiliza ese comportamiento.
- `usage-source` hoy distingue causas de fallo (clave ausente, inválida, sin suscripción, transitorio). Se asume que Claude no usa las causas relacionadas con la clave de OpenCode, por lo que pueden retirarse sin afectar a Claude. Si Claude usa el indicador de "desactualizado" del publisher, se conserva para Claude; para OpenCode no se activa nunca (S9).
- `usage-history` y `usage-snapshot-publisher` admiten hoy series por proveedor y ventana; se asume que pueden ampliarse a una clave adicional "serie" (modelo o total) sin cambiar su responsabilidad.
- `usage-window` hoy pinta tarjetas por proveedor con ventanas y cuenta atrás; se asume que puede pintar filas por modelo más una fila "Total" y omitir la cuenta atrás cuando la ventana no la tiene (S5). `countdown-evaluator` queda para Claude.
- `application.yml` ya existe y está versionado; tras retirar la clave ya no contiene secretos, así que la tabla de topes puede vivir en él directamente.
- `background-launcher` y `application-launcher` de v1 cubren el arranque en segundo plano (REQ-001, NFR-001) y se conservan sin cambios (S12).
- El PATH visible para la JVM es el del usuario que la lanza (S6). En Windows `opencode` puede ser un envoltorio `.cmd`/`.ps1` en lugar de un `.exe`; no se ha comprobado.
- La carpeta `docs/contracts/opencode/` no versionada que menciona v1 no se usa como fuente.

## Componentes

| Componente | Responsabilidad | Existente o nuevo |
|---|---|---|
| opencode-usage-source | Orquesta un ciclo: calcula rangos, lanza las tres consultas en el pool, exige las tres, entrega el resultado calculado o "sin datos"; se reescribe, ya no usa HTTP | existente (reescrito) |
| opencode-cli-runner | Ejecuta `opencode` como proceso hijo con argumentos separados, localizándolo por PATH, con timeout de 30 s, lectura concurrente de stdout y descarte de stderr; devuelve texto o fallo | nuevo |
| opencode-stats-parser | Interpreta el JSON de `session.stats`: extrae de `models` id, providerID, variant, steps, tokens y `cost`, ignora propiedades desconocidas y filtra `opencode-go` | nuevo |
| usage-window-ranges | Calcula los rangos `from`/`to` de 5 h, 7 días y mes (con día de ciclo) a partir de un único instante "ahora" y la zona local | nuevo |
| opencode-usage-calculator | Suma el coste por modelo (todas las `variant`), resuelve tope por modelo, calcula porcentaje por ventana saturado a 100 y el total agregado | nuevo |
| configuration-provider | Pasa a cargar tabla de topes, tope por defecto, día de ciclo, intervalo (60 s) y timeout (30 s); retira API key y backoff | existente (modificado) |
| usage-source | Abstracción por proveedor; el resultado de OpenCode es "datos" o "sin datos", sin causas de fallo | existente (simplificado) |
| usage-poller | Ciclo periódico a intervalo fijo sin solape ni backoff ni pausa; aislado de otras fuentes; solo actúa sobre éxito/ausencia | existente (simplificado) |
| usage-history | Serie en memoria por proveedor, serie (modelo o total) y ventana, deduplicada por valores iguales | existente (ampliado) |
| usage-snapshot-publisher | Último estado por proveedor con hora del último ciclo exitoso; notifica a la vista en el hilo de interfaz; sin datos = proveedor oculto | existente (simplificado) |
| usage-window | Muestra por proveedor filas por modelo, fila "Total", hora de última actualización y gráfico; oculta proveedores sin datos; sin mensajes de error | existente (modificado) |
| lifecycle-coordinator | Al cierre detiene el planificador, que a su vez cierra el pool y destruye procesos `opencode` en curso | existente |
| background-launcher, application-launcher, application-context, tray-* | Sin cambios | existente |
| countdown-evaluator | Sin cambios; ya no se usa para OpenCode | existente |
| claude-usage-source | Sin cambios | existente |

Se retiran (REQ-014, ver DEC-008): cliente HTTP y normalización de variantes en `opencode-usage-source`, resolución y enmascarado de la API key en `configuration-provider`, `secret-config-files`, estados de clave ausente/inválida/sin suscripción, backoff y pausa en `usage-poller`, mensajes de autenticación de `usage-window` y el contrato HTTP.

## Flujo

1. El usuario lanza la aplicación con `background-launcher`; la JVM queda desacoplada y la interfaz está disponible sin esperar a ningún ciclo (REQ-001, NFR-001).
2. `usage-poller` programa el ciclo de OpenCode con retardo fijo de 60 s entre el fin de un ciclo y el inicio del siguiente (el primero, nada más arrancar), en un hilo de fondo (REQ-009, NFR-004).
3. `opencode-usage-source` toma un único instante "ahora" y pide a `usage-window-ranges` los tres rangos con la zona local y el día de ciclo (REQ-004).
4. La fuente envía las tres consultas al pool de 3 hilos. Cada una la ejecuta `opencode-cli-runner` con `from`, `to` (ISO-8601 en UTC) y `tools=none` (REQ-002, REQ-015).
5. `opencode-cli-runner` lee stdout mientras el proceso corre, descarta stderr y espera hasta 30 s; si vence, destruye el proceso y devuelve fallo. Ejecutable ausente, código de salida distinto de 0 o excepción de E/S también son fallo, sin mensaje al usuario (REQ-003, NFR-006).
6. `opencode-stats-parser` valida el JSON y filtra `providerID = opencode-go`; JSON inválido es fallo (REQ-002, REQ-005).
7. La fuente espera a las tres consultas (el ciclo dura lo que la más lenta, con tope de 30 s). Si alguna falla, el ciclo se descarta entero: no hay publicación, el último dato y la hora de actualización se conservan y el siguiente ciclo ocurre a su hora (REQ-003, REQ-015).
8. Con las tres correctas, `opencode-usage-calculator` suma el coste por modelo y ventana, aplica el tope (tabla o 60 USD) y calcula porcentaje por modelo y total, saturados a 100 (REQ-006, REQ-007, REQ-016).
9. `usage-poller` entrega el resultado a `usage-snapshot-publisher` con la hora de fin del ciclo y a `usage-history`, que añade un punto por serie y ventana solo si los valores difieren de los del último punto (REQ-010, REQ-011).
10. `usage-snapshot-publisher` notifica a `usage-window` en el hilo de interfaz: filas por modelo con sus tres ventanas, fila "Total", hora de última actualización y gráfico (REQ-008, REQ-010, REQ-011).
11. Si nunca ha habido un ciclo válido o no hay modelos de `opencode-go`, `usage-window` no muestra la sección OpenCode; otros proveedores no se ven afectados (REQ-003, REQ-005, REQ-013).
12. Al cerrar, `lifecycle-coordinator` detiene el planificador, que cierra el pool y destruye procesos vivos. La memoria se descarta sin persistir (REQ-012).

## Interfaces entre componentes

| Origen | Destino | Qué se comunica | Protocolo |
|---|---|---|---|
| opencode-cli-runner | CLI `opencode` (proceso externo) | Ejecución con argumentos `api experimental.session.stats` y `--param` from/to/tools; devuelve JSON por salida estándar | stdio |
| opencode-usage-source | usage-window-ranges | Instante actual, zona y día de ciclo; devuelve tres rangos | - |
| opencode-usage-source | opencode-cli-runner | Una consulta por ventana, ejecutada en el pool de 3 hilos | - |
| opencode-cli-runner | opencode-stats-parser | Texto de stdout para interpretar | - |
| opencode-usage-source | opencode-usage-calculator | Gasto por modelo de cada ventana | - |
| opencode-usage-calculator | configuration-provider | Consulta de tope por modelo, tope por defecto y día de ciclo | - |
| usage-poller | usage-source (opencode-usage-source) | Petición de ciclo; respuesta "datos" o "sin datos" | - |
| usage-poller | usage-history | Porcentajes nuevos por serie y ventana | - |
| usage-poller | usage-snapshot-publisher | Resultado del ciclo y hora de éxito | - |
| usage-snapshot-publisher | usage-window | Notificación de cambio en el hilo de interfaz | - |
| usage-history | usage-window | Series para el gráfico | - |
| configuration-provider | usage-poller, opencode-usage-source, opencode-cli-runner | Intervalo, timeout, ruta de ejecución por defecto | - |
| lifecycle-coordinator | usage-poller | Orden de cierre; el poller cierra pool y procesos | - |

## Modelo de datos lógico

- Proveedor: servicio de AI (Claude y OpenCode). Relaciones: tiene cero o más Series por ventana; tiene un Estado de lectura.
- Modelo de OpenCode: identificado por su id dentro de `opencode-go`; agrupa todas sus `variant`. Relaciones: tiene un Gasto por Ventana; tiene un Tope.
- Ventana: 5 horas, 7 días o mes (ciclo). Relaciones: tiene un Rango (desde, hasta) y un Límite proporcional (20 %, 50 %, 100 % del tope).
- Tabla de topes: entradas modelo-tope en dólares de la configuración, más un tope por defecto. Relaciones: aporta el Tope de cada Modelo.
- Consumo: porcentaje 0-100 por Modelo o Total y Ventana. Relaciones: pertenece a una Serie.
- Serie: puntos de evolución en memoria de un Modelo o del Total para una Ventana. Relaciones: pertenece a un Proveedor.
- Estado de lectura: datos con hora del último ciclo exitoso, o sin datos. Relaciones: pertenece a un Proveedor.

## Decisiones

### DEC-001 Ejecución del CLI como proceso hijo del JDK

- **Decisión:** `opencode-cli-runner` usa el lanzador de procesos estándar de Java 21 con la lista de argumentos separados (sin concatenar en una cadena de shell), de modo que el sistema localiza `opencode` por el PATH heredado (S6) y las fechas ISO no se interpretan por un shell. En Windows, donde el CLI puede ser un `.cmd`, se invoca mediante el intérprete de comandos con los mismos argumentos separados. La salida de error se descarta y la entrada se cierra. stdout se lee en un hilo mientras el proceso corre (para que un buffer lleno no lo bloquee) con un tope de tamaño; al superarlo, es fallo. El timeout de 30 s (NFR-006) se aplica a la espera del proceso; al vencer se destruye el proceso (y sus hijos) y cuenta como fallo. Todo fallo (no encontrado, código distinto de 0, timeout, JSON inválido, excepción) se resuelve a "consulta fallida" sin mensaje y con un único registro de log de bajo nivel (categoría, sin salida del CLI ni argumentos).
- **Alternativas descartadas:** Biblioteca externa de procesos (dependencia innecesaria); ejecutar vía shell con cadena completa (riesgo de inyección y de comillas, nada que ganar); ruta configurable del ejecutable (descartada por S6); volcar stderr al log (puede contener datos de sesión).
- **Cubre:** REQ-002, REQ-003, NFR-003, NFR-004, NFR-006

### DEC-002 Pool fijo de 3 hilos y ciclo atómico

- **Decisión:** `opencode-usage-source` mantiene un pool fijo de 3 hilos demonio, creado al arrancar y cerrado al cierre. Cada ciclo envía exactamente 3 tareas y espera a las 3 (con límite derivado de los 30 s de cada proceso). El resultado solo existe si las tres tienen éxito (S7); si no, se devuelve "sin datos" sin tocar nada publicado. Así no se mezclan lecturas de instantes distintos y nunca hay más de 3 procesos `opencode` a la vez. El poller no inicia un ciclo hasta que el anterior ha acabado, por lo que el máximo es 3 procesos y, con el intervalo mínimo de 10 s, 18 ejecuciones por minuto (NFR-005: 30).
- **Alternativas descartadas:** Publicar parcialmente las ventanas que sí llegan (datos mezclados, contra S7 y REQ-015); un solo hilo en serie (3 × 4 s > 8 s, incumple REQ-015); un hilo nuevo por consulta sin pool (no acota procesos); hilos virtuales sin límite (la acotación a 3 se perdería y ya hay un pool de tamaño fijo exigido).
- **Cubre:** REQ-009, REQ-015, NFR-004, NFR-005, NFR-006

### DEC-003 Cálculo de ventanas con un único "ahora" y zona local

- **Decisión:** `usage-window-ranges` recibe un único instante "ahora" por ciclo (reloj inyectable para pruebas) y la zona del sistema. 5 horas: `to` = ahora, `from` = ahora menos 5 horas de duración real. 7 días: `from` = el mismo instante local de 7 días atrás (misma hora local, así 14:00 es 14:00 incluso con cambio de horario); `to` = ahora. Mes: `to` = ahora; `from` = inicio de ciclo. Inicio de ciclo: candidato en el mes actual = día efectivo `min(díaConfigurado, díasDelMes)` a las 00:00 locales; si el candidato es posterior a ahora, se toma el candidato del mes anterior calculado igual (con sus propios días). Cubre ciclo 20 (17 oct → 20 sep; 25 oct → 20 oct), 31 (5 mar → 28 feb; 30 abr → 30 abr). El consumo del ciclo anterior queda fuera porque `from` avanza. Un día de ciclo no entero o fuera de 1-31 se sustituye por 1 al leer la configuración (se lee como texto y se valida para que un valor inválido no impida arrancar). Todas las fechas se pasan al CLI en ISO-8601 en UTC.
- **Alternativas descartadas:** Calcular "ahora" por consulta (ventanas con instantes distintos, contra la atomicidad); mes como 30 días móviles (contra S4); 7 días como 168 horas fijas (se desvía de la hora local con cambio de horario, contra los ejemplos de REQ-004); fallar el arranque con día inválido (contra REQ-004).
- **Cubre:** REQ-002, REQ-004

### DEC-004 Porcentaje por modelo y total

- **Decisión:** `opencode-usage-calculator` agrupa por id de modelo y suma el coste de todas las `variant` por ventana (S8). Límite de la ventana = tope del modelo × 0,20, × 0,50 o × 1,00. Porcentaje del modelo = gasto / límite × 100, limitado a 100. Un coste negativo, ausente o no numérico cuenta como 0. Total de la ventana = suma del gasto de todos los modelos / suma de los límites solo de los modelos con gasto mayor que 0 en esa ventana, × 100, limitado a 100; sin ningún modelo con gasto, el total es 0. Un modelo aparece en la interfaz si tiene gasto en alguna de las tres ventanas (0 % en las demás). Los cálculos se hacen sobre los costes sin redondear; el redondeo es solo de presentación. El tope de un modelo es el de la tabla si es un número positivo; en otro caso (no listado, marcador vacío, cero o negativo) es el tope por defecto (60 USD).
- **Alternativas descartadas:** Total como media de porcentajes por modelo (no refleja el gasto, contra la definición de REQ-016); incluir en el denominador los topes de modelos sin gasto (los penalizaría, contra S2); no saturar y recortar solo en la vista (el gráfico y el total podrían exceder 100, contra REQ-006/011); un porcentaje por cada `variant` (contra REQ-006).
- **Cubre:** REQ-005, REQ-006, REQ-007, REQ-008, REQ-016

### DEC-005 Estructura de configuración de topes y día de ciclo

- **Decisión:** Bajo el prefijo `subscription-viewer.opencode` de `application.yml`: `cycle-day` (por defecto 1), `default-cap-usd` (60), `poll-interval-seconds` (60, mínimo 10), `command-timeout-seconds` (30) y `model-caps`, una lista en la que cada entrada es una línea con dos valores: `model` y `cap-usd`. El fichero se entrega con una o dos entradas marcador (un nombre de ejemplo y `cap-usd` vacío), comentadas para explicar cómo editar. Añadir o cambiar un modelo es editar una línea. Un `cap-usd` vacío o no numérico se trata como modelo no listado (REQ-007). Los cambios se aplican al reiniciar. Se usa lista de entradas y no un mapa por clave porque los ids de modelo suelen contener puntos, que el enlace de propiedades del framework interpreta como separadores y exigirían notación especial en las claves. Esto es configuración propia de v2; las propiedades de la API key y del backoff desaparecen.
- **Alternativas descartadas:** Mapa modelo → tope (claves con puntos problemáticas); fichero de topes aparte (otra ubicación para el usuario, sin ganancia); edición desde la interfaz (fuera de alcance); relectura en caliente (no pedida, contra la consistencia con el resto de propiedades).
- **Cubre:** REQ-004, REQ-007, REQ-009, NFR-005, NFR-006

### DEC-006 Intervalo fijo sin backoff ni pausa

- **Decisión:** El refresco es de retardo fijo de 60 s entre ciclos (S10), configurable con mínimo de 10 s. Ya no hay backoff exponencial ni pausa tras un fallo: con el CLI ausente cada ciclo falla en milisegundos y el coste es de 3 intentos por minuto, y el caso "CLI vuelve a funcionar" (REQ-003) se recupera solo en el siguiente ciclo.
- **Alternativas descartadas:** Mantener el backoff de v1 (añade estado y retrasa la recuperación del CLI sin ganancia, porque no hay red ni clave de por medio); intervalo de 10 s (carga de procesos innecesaria, el consumo cambia despacio).
- **Cubre:** REQ-003, REQ-009, NFR-003, NFR-005

### DEC-007 Resultado de la fuente sin causas de fallo; vista que oculta

- **Decisión:** Para OpenCode, `usage-source` devuelve solo "datos" o "sin datos". `usage-snapshot-publisher` conserva el último "datos" y su hora; ante "sin datos" no cambia nada (fallo silencioso, sin indicador de desactualización, S9). `usage-window` solo muestra la sección OpenCode si hay datos con al menos un modelo; sin ellos la oculta. No se introduce ningún texto de error. Se retiran los estados de clave ausente, clave inválida y sin suscripción (los usaba v1).
- **Alternativas descartadas:** Conservar la clasificación por causas (dejaría vivo código de v1 y contradice REQ-014); mostrar tarjeta con "no disponible" (contra REQ-003 y REQ-013).
- **Cubre:** REQ-003, REQ-005, REQ-010, REQ-013, REQ-014

### DEC-008 Alcance de la limpieza de v1 (REQ-014)

- **Decisión:** La limpieza es una tarea propia, separada de las de funcionalidad, y retira: el cliente HTTP y la normalización de `percent`/`resetsAt` y `usagePercent`/`resetInSec` en `opencode-usage-source`; `subscription-viewer.opencode.api-key`, `OPENCODE_API_KEY`, la precedencia y el valor secreto enmascarado en `configuration-provider`; el módulo `secret-config-files` (ejemplo de configuración y reglas de `.gitignore` de la clave); la propiedad de backoff; el tratamiento de 401/403, de clave ausente, y los mensajes de autenticación y de desactualización de OpenCode en `usage-poller`, `usage-snapshot-publisher` y `usage-window`; las pruebas asociadas (incluida la verificación con clave centinela); el contrato `http` de OpenCode; y las referencias en README, arquitectura y contratos, que actualiza el documentador. Se ejecuta después de que la v2 funcione, para no dejar el sistema sin fuente. Criterio de comprobación: `OPENCODE_API_KEY`, `Authorization: Bearer` y `/zen/go/v1/usage` no aparecen en código ni configuración.
- **Alternativas descartadas:** Retirar v1 durante la implementación de cada pieza (mezcla funcionalidad y limpieza, contra REQ-014); conservar el código "por si acaso" (contra REQ-014).
- **Cubre:** REQ-014

### DEC-009 Series por modelo y total en el historial

- **Decisión:** `usage-history` guarda una serie por proveedor, serie (cada modelo o el total) y ventana; añade un punto solo si el porcentaje cambia respecto al último de esa serie. Los puntos son siempre ≤ 100 por saturación previa. Mantiene el tope de puntos por serie de v1 y no persiste nada. Un modelo que aparece más tarde empieza su serie en ese momento.
- **Alternativas descartadas:** Solo la serie total (contra REQ-011, que pide por modelo y total); un punto por ciclo aunque no cambie (duplicados, contra REQ-011); persistir (contra REQ-012).
- **Cubre:** REQ-011, REQ-012

## Riesgos

- El CLI en Windows puede ser un `.cmd` o `.ps1` y no localizarse con el lanzador estándar: se mitiga con la invocación vía intérprete de comandos de DEC-001 y se valida con una prueba manual en el entorno del usuario.
- La JVM lanzada desde el lanzador de fondo puede heredar un PATH distinto del de la terminal interactiva, y `opencode` no encontrarse: el fallo es silencioso (REQ-003), así que no habrá aviso. Se mitiga documentando que el PATH debe incluir `opencode` y con la prueba manual del arranque real.
- El esquema JSON del CLI puede cambiar o traer campos nuevos: se ignoran los desconocidos; si faltan los necesarios, se descarta el ciclo en silencio y el usuario no lo sabrá (consecuencia aceptada de REQ-003).
- Un fallo persistente y silencioso deja datos antiguos sin indicador (S9): es una decisión del usuario; la hora de última actualización lo hace deducible.
- Si una de las tres consultas falla de forma sistemática (p. ej. un rango muy grande que tarda más de 30 s), el ciclo nunca publica: se mitiga con el timeout y la pruebas manuales con el rango mensual real.
- Procesos huérfanos si la aplicación se cierra a mitad de ciclo: el cierre destruye los procesos vivos (DEC-001/DEC-002).
- Los ids de modelo del CLI pueden no coincidir con los nombres que escriba el usuario en la tabla: el modelo cae al tope por defecto de 60 USD; se mitiga con comparación exacta documentada en los comentarios del `application.yml` entregado.
- Si el gasto de una ventana corta supera el de otra más larga (inconsistencias de ciclo), el cálculo no lo corrige: cada ventana se calcula de su propia consulta.
- Cambios en `usage-poller`, `usage-source` y `usage-history` afectan a Claude: se mitiga con pruebas de regresión del flujo de Claude.
- El primer ciclo ocurre de inmediato y puede coincidir con el arranque de la JVM: se lanza en el hilo de fondo y no retrasa la interfaz (NFR-001, NFR-004).

## Trazabilidad

| Requisito | Componentes | Decisiones |
|---|---|---|
| REQ-001 | background-launcher, application-launcher | - |
| REQ-002 | opencode-usage-source, opencode-cli-runner, opencode-stats-parser, usage-window-ranges | DEC-001, DEC-003 |
| REQ-003 | opencode-cli-runner, opencode-stats-parser, opencode-usage-source, usage-poller, usage-snapshot-publisher, usage-window | DEC-001, DEC-006, DEC-007 |
| REQ-004 | usage-window-ranges, configuration-provider | DEC-003, DEC-005 |
| REQ-005 | opencode-stats-parser, opencode-usage-calculator, usage-window | DEC-004, DEC-007 |
| REQ-006 | opencode-usage-calculator | DEC-004 |
| REQ-007 | configuration-provider, opencode-usage-calculator | DEC-004, DEC-005 |
| REQ-008 | opencode-usage-calculator, usage-snapshot-publisher, usage-window | DEC-004 |
| REQ-009 | usage-poller, opencode-usage-source, configuration-provider | DEC-002, DEC-005, DEC-006 |
| REQ-010 | usage-poller, usage-snapshot-publisher, usage-window | DEC-007 |
| REQ-011 | usage-history, usage-poller, usage-window | DEC-009 |
| REQ-012 | usage-history, usage-snapshot-publisher, lifecycle-coordinator | DEC-009 |
| REQ-013 | usage-poller, usage-snapshot-publisher, usage-window, usage-source | DEC-007 |
| REQ-014 | opencode-usage-source, configuration-provider, usage-poller, usage-snapshot-publisher, usage-window | DEC-007, DEC-008 |
| REQ-015 | opencode-usage-source, opencode-cli-runner, usage-poller | DEC-002 |
| REQ-016 | opencode-usage-calculator, usage-window, usage-history | DEC-004, DEC-009 |
| NFR-001 | background-launcher, usage-poller, usage-window | - |
| NFR-002 | opencode-cli-runner, opencode-usage-source | DEC-001 |
| NFR-003 | usage-poller, opencode-cli-runner | DEC-001, DEC-006 |
| NFR-004 | usage-poller, opencode-usage-source, opencode-cli-runner | DEC-001, DEC-002 |
| NFR-005 | usage-poller, configuration-provider | DEC-002, DEC-005, DEC-006 |
| NFR-006 | opencode-cli-runner, configuration-provider | DEC-001, DEC-002, DEC-005 |

## Contratos afectados

| Protocolo | Contrato | Cambio |
|---|---|---|
| stdio | docs/contracts/stdio/desktop/app/opencode-session-stats.md | Se crea: invocación del CLI `opencode` con `api experimental.session.stats` y los parámetros `from`, `to` y `tools=none`; salida JSON en stdout con `models` (id, providerID, variant, steps, tokens, cost); propiedades extra ignoradas; salida no JSON, código de salida distinto de 0 y timeout de 30 s tratados como fallo silencioso |
| http | docs/contracts/http/desktop/app/opencode-usage.md | Se retira (REQ-014): endpoint `/zen/go/v1/usage` y variantes `percent`/`usagePercent` |

## Cambios sugeridos al mapa

- ~ Módulo: opencode-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode | Orquesta el ciclo atómico de tres consultas del CLI y entrega porcentajes calculados | usage-source, configuration-provider, opencode-cli-runner, opencode-stats-parser, usage-window-ranges, opencode-usage-calculator
- + Módulo: opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode | Ejecuta `opencode` por PATH con timeout, stdout concurrente y fallo silencioso | configuration-provider
- + Módulo: opencode-stats-parser | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode | Interpreta el JSON de session.stats y filtra el proveedor opencode-go | -
- + Módulo: usage-window-ranges | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode | Calcula rangos de 5 h, 7 días y ciclo mensual con día configurable | configuration-provider
- + Módulo: opencode-usage-calculator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode | Calcula porcentajes por modelo y total, saturados, contra la tabla de topes | configuration-provider
- ~ Módulo: configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config | Propiedades externalizadas con valores por defecto; incluye tabla de topes, día de ciclo, intervalo y timeout de OpenCode | application-context
- ~ Módulo: usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java | Abstracción por proveedor con resultado datos o sin datos | -
- ~ Módulo: usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java | Planificador en hilo de fondo con intervalo por fuente, sin solape y aislamiento entre fuentes | usage-source, usage-history, usage-snapshot-publisher, configuration-provider, lifecycle-coordinator
- ~ Módulo: usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java | Historial en memoria por proveedor, modelo o total y ventana, deduplicado por valores | -
- ~ Módulo: usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java | Último estado por proveedor con hora de último ciclo exitoso; notifica a la vista | -
- ~ Módulo: usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java | Ventana con filas por modelo y Total, hora de actualización y gráfico; oculta proveedores sin datos | usage-snapshot-publisher, usage-history, countdown-evaluator
- - Módulo: secret-config-files | desktop/app/config | Fichero de configuración de ejemplo versionado con marcador de posición y exclusión del application.yml local con la clave real | -
- - Contrato: http | docs/contracts/http/desktop/app/opencode-usage.md | opencode-usage-source -> opencode.ai
- + Contrato: stdio | docs/contracts/stdio/desktop/app/opencode-session-stats.md | opencode-cli-runner -> CLI opencode
- - Convención: opencode-api-key-property | subscription-viewer.opencode.api-key
- - Convención: opencode-api-key-env | OPENCODE_API_KEY
- - Convención: opencode-api-key-precedence | application.yml, luego variable de entorno
- - Convención: opencode-backoff-max-seconds | 300
- - Convención: secrets-policy | la API key nunca se versiona, ni se registra, ni se muestra en la interfaz
- ~ Convención: opencode-poll-interval-seconds: 60
- + Convención: opencode-cycle-day-property: subscription-viewer.opencode.cycle-day
- + Convención: opencode-model-caps-property: subscription-viewer.opencode.model-caps
- + Convención: opencode-default-cap-usd: 60
- + Convención: opencode-command-timeout-seconds: 30
- + Convención: opencode-cli-threads: 3
