# Diseño: claude-usage-monitor

Estado: aprobado

## Resumen

Dos procesos desacoplados por un fichero JSON local: un hook de status line (Python, librería estándar) que Claude Code ejecuta y que persiste de forma atómica el estado de `rate_limits`, y la aplicación Java existente, que lo lee cada 10 s en un hilo de fondo y lo presenta en una ventana abierta desde la bandeja (porcentajes, cuenta atrás y gráfico en tiempo real). La obtención de datos se modela como una fuente por proveedor independiente, de modo que añadir otro proveedor es registrar otra fuente.

## Supuestos sobre el sistema actual

- La aplicación es non-web con icono de bandeja AWT (según el mapa). Se asume que no existe ninguna ventana ni UI más allá del icono y su menú; la UI de uso será una ventana de escritorio abierta desde el menú de la bandeja. No se ha abierto el código de los módulos del mapa.
- Se asume que `configuration-provider` admite añadir propiedades nuevas en `application.yml` sin cambiar su responsabilidad.
- Se asume que `tray-menu` puede incorporar una opción adicional además de la de cierre, y que `lifecycle-coordinator` permite registrar el cierre ordenado del planificador de lecturas.
- Se asume que la aplicación se ejecuta con entorno gráfico (ya lo exige el icono de bandeja).
- El hook y la aplicación comparten el mismo equipo y el mismo usuario del sistema operativo (S7).

## Componentes

| Componente | Responsabilidad | Existente o nuevo |
|---|---|---|
| statusline-hook | Script Python que lee stdin una vez, persiste el estado de límites, y reenvía o genera la salida de status line | nuevo |
| usage-state-file | Fichero JSON local con la última lectura de límites (artefacto compartido entre hook y aplicación) | nuevo |
| usage-source | Abstracción por proveedor: entrega una lectura de uso o ausencia de datos, sin propagar fallos | nuevo |
| claude-usage-source | Implementación de usage-source para Claude: lee y valida el fichero de estado; descarta lo inválido | nuevo |
| usage-poller | Planifica la lectura cada 10 s fuera del hilo de interfaz, sin solapar lecturas, y consulta cada fuente de forma aislada | nuevo |
| usage-history | Acumula en memoria la serie de porcentajes por proveedor y ventana, ignorando lecturas no más recientes | nuevo |
| usage-snapshot-publisher | Mantiene el último estado conocido por proveedor y notifica a la vista de cambios | nuevo |
| usage-window | Ventana de escritorio con tarjeta por proveedor (porcentajes, tiempo restante, hora de datos), gráfico y texto neutro | nuevo |
| countdown-evaluator | Calcula tiempo restante y vigencia de cada ventana frente a la hora actual, independiente de las lecturas | nuevo |
| tray-menu | Menú contextual de la bandeja; añade la opción de mostrar la ventana de uso | existente |
| configuration-provider | Propiedades externalizadas; añade ruta del fichero de estado e intervalo de lectura | existente |
| lifecycle-coordinator | Cierre ordenado; además detiene el planificador de lecturas | existente |

## Flujo

1. Claude Code ejecuta statusline-hook pasándole el JSON por stdin; el hook lo lee una sola vez.
2. Si el JSON trae `rate_limits`, el hook escribe en usage-state-file solo porcentaje y `resets_at` de cada ventana más la hora de escritura (fichero temporal + renombrado atómico, con reintentos limitados). Si no trae `rate_limits`, no escribe.
3. El hook reenvía el mismo JSON al comando de status line original y devuelve su salida sin modificar; sin comando original imprime una línea mínima con los porcentajes. Persistir y mostrar son independientes: el fallo de una no afecta a la otra.
4. usage-poller, cada 10 s contados entre el fin de una lectura y el inicio de la siguiente, pide a cada usage-source su lectura en un hilo de fondo.
5. claude-usage-source lee usage-state-file; si falta, es ilegible o inválido, o una ventana no tiene datos utilizables, devuelve ausencia para esa ventana, sin error visible.
6. usage-history incorpora un punto por ventana solo si `written_at` es posterior al último representado; esto evita duplicados.
7. usage-snapshot-publisher guarda el resultado y notifica a usage-window.
8. usage-window refresca cada segundo (o minuto) el tiempo restante mediante countdown-evaluator usando `resets_at`; oculta las ventanas caducadas y el proveedor sin ventanas vigentes. Si ningún proveedor es visible, muestra un texto neutro.
9. El usuario abre usage-window desde tray-menu; al cerrar la aplicación, lifecycle-coordinator detiene el planificador y se descarta la memoria (sin histórico persistido).

## Interfaces entre componentes

| Origen | Destino | Qué se comunica | Protocolo |
|---|---|---|---|
| Claude Code | statusline-hook | JSON de status line por stdin; salida de texto por stdout | stdio |
| statusline-hook | comando de status line original | Mismo JSON por stdin; su salida se reenvía intacta | stdio |
| statusline-hook | usage-state-file | Escritura atómica del estado de límites | file-json |
| claude-usage-source | usage-state-file | Lectura del estado de límites | file-json |
| usage-poller | usage-source | Petición de lectura por proveedor, con aislamiento de fallos | - |
| usage-poller | usage-history | Lecturas nuevas por ventana para acumular | - |
| usage-poller | usage-snapshot-publisher | Último estado por proveedor | - |
| usage-snapshot-publisher | usage-window | Notificación de cambio de estado (en el hilo de interfaz) | - |
| usage-history | usage-window | Serie temporal para el gráfico | - |
| countdown-evaluator | usage-window | Tiempo restante y vigencia por ventana | - |
| tray-menu | usage-window | Petición de mostrar la ventana | - |
| lifecycle-coordinator | usage-poller | Orden de parada en el cierre | - |
| configuration-provider | usage-poller, claude-usage-source | Intervalo y ruta del fichero | - |

## Modelo de datos lógico

- Proveedor: servicio de AI del que se muestra el uso (inicialmente Claude). Relaciones: tiene una o más Ventanas de límite.
- Ventana de límite: periodo con tope (sesión de 5 h, semanal). Relaciones: pertenece a un Proveedor; tiene una Lectura vigente y una Serie de puntos.
- Lectura de uso: porcentaje consumido, instante de reinicio y hora de registro. Relaciones: pertenece a una Ventana.
- Punto de evolución: porcentaje en un instante, solo en memoria. Relaciones: pertenece a la Serie de una Ventana.
- Estado persistido: contenido de usage-state-file (última lectura de las dos ventanas de Claude). Relaciones: origen de las Lecturas de Claude.

## Decisiones

### DEC-001 Fichero de estado como único canal entre hook y aplicación

- **Decisión:** El hook persiste en un fichero JSON local y la aplicación lo sondea; no hay conexión directa entre procesos.
- **Alternativas descartadas:** Socket o HTTP local desde el hook (abre red y obliga a que la aplicación esté arrancada; choca con NFR-002); consultar endpoints o `/usage` (fuera de alcance); estimar desde JSONL (fuera de alcance).
- **Cubre:** REQ-001, REQ-011, NFR-002

### DEC-002 Hook en Python con librería estándar

- **Decisión:** statusline-hook en Python 3 sin dependencias externas (S9, S10).
- **Alternativas descartadas:** Java (coste de arranque de la JVM en cada ejecución); shell con `jq` (no portable en Windows y dependencia externa).
- **Cubre:** REQ-011, REQ-012

### DEC-003 Escritura atómica con reintento acotado

- **Decisión:** El hook escribe a un temporal y renombra; ante fallo transitorio reintenta pocas veces y, si persiste, termina en silencio sin tocar la salida. El lector tolera cualquier contenido no utilizable como ausencia.
- **Alternativas descartadas:** Escribir en el sitio (permite lecturas parciales); bloqueo de fichero (complejidad y riesgo de colgar la status line).
- **Cubre:** REQ-011, REQ-008

### DEC-004 Lecturas en hilo de fondo con retardo fijo entre lecturas

- **Decisión:** usage-poller usa un planificador propio con retardo fijo entre fin e inicio de lecturas, nunca en el hilo de interfaz; una lectura lenta retrasa la siguiente pero no la solapa, y la interfaz sigue mostrando y evaluando la caducidad sobre el último estado conocido.
- **Alternativas descartadas:** Cadencia fija con lecturas solapadas (rompe S3); lectura en el hilo de interfaz (rompe NFR-001).
- **Cubre:** REQ-004, NFR-001

### DEC-005 Caducidad y cuenta atrás evaluadas en la presentación

- **Decisión:** La vigencia y el tiempo restante se calculan al pintar con la hora actual y `resets_at`, no al leer. Así el tiempo restante baja sin lecturas nuevas y una ventana caducada desaparece aunque el fichero no cambie ni la lectura se atasque.
- **Alternativas descartadas:** Calcular solo al leer (el tiempo restante se congelaría entre lecturas y la caducidad se retrasaría hasta 10 s).
- **Cubre:** REQ-003, REQ-008

### DEC-006 Una fuente por proveedor con aislamiento de fallos

- **Decisión:** Cada proveedor implementa usage-source y el poller lo consulta de forma aislada (una fuente que falla o devuelve ausencia no afecta a las demás). La vista pinta solo proveedores con datos vigentes, sin secciones vacías.
- **Alternativas descartadas:** Un lector único con lógica por proveedor (acoplaría fallos y cambios); mostrar secciones vacías o con error (contradice REQ-008 y REQ-010).
- **Cubre:** REQ-008, REQ-009, REQ-010

### DEC-007 Historial en memoria, deduplicado por hora de registro

- **Decisión:** usage-history solo existe en memoria y añade un punto por ventana cuando la hora de registro es posterior a la última representada. Se acota la serie con un tope generoso de puntos para evitar crecimiento ilimitado en sesiones muy largas (aclaración de S6, descartando los más antiguos).
- **Alternativas descartadas:** Persistir el histórico (fuera de alcance); añadir un punto en cada lectura (duplicados); sin tope (crecimiento sin límite; con lecturas cada 10 s y datos que cambian poco, el riesgo es bajo, pero el tope es barato).
- **Cubre:** REQ-006, REQ-007

### DEC-008 Ventana de escritorio propia dibujada con Swing

- **Decisión:** La vista es una ventana Swing abierta desde la bandeja, con el gráfico dibujado por componente propio. Se evita añadir una librería de gráficos para una serie sencilla de dos líneas. El método de pintado concreto lo decide la capa técnica.
- **Alternativas descartadas:** Interfaz web embebida (la aplicación es non-web y añadiría servidor/puerto); JavaFX (dependencia adicional no prevista en el stack); librería de gráficos externa (peso desproporcionado).
- **Cubre:** REQ-002, REQ-003, REQ-005, REQ-006, REQ-009

## Riesgos

- Claude Code puede no entregar `rate_limits` en la versión instalada (S1, S11): la feature no sería viable; el hook no escribe en ese caso y la aplicación muestra el texto neutro.
- Con Claude Code cerrado el fichero conserva datos antiguos pero vigentes hasta `resets_at` (S4): los porcentajes pueden estar desfasados; se mitiga mostrando siempre la hora de datos (REQ-005).
- Lectura concurrente a la escritura en Windows (`PermissionError` al renombrar): se mitiga con reintento acotado en el hook y con el lector tolerante a fallos.
- Varias sesiones de Claude Code escriben a la vez: el límite es por cuenta, vale la última escritura; los puntos de la serie se ordenan por hora de registro.
- Desfase de reloj entre procesos: ambos usan el reloj del mismo equipo, riesgo despreciable.
- Si el mapa no cubre componentes de interfaz, la capa técnica debe decidir la integración de la ventana con el ciclo de vida de la bandeja.

## Trazabilidad

| Requisito | Componentes | Decisiones |
|---|---|---|
| REQ-001 | statusline-hook, usage-state-file, claude-usage-source | DEC-001 |
| REQ-002 | claude-usage-source, usage-snapshot-publisher, usage-window | DEC-008 |
| REQ-003 | countdown-evaluator, usage-window | DEC-005, DEC-008 |
| REQ-004 | usage-poller, configuration-provider, lifecycle-coordinator | DEC-004 |
| REQ-005 | claude-usage-source, usage-window | DEC-008 |
| REQ-006 | usage-history, usage-window | DEC-007, DEC-008 |
| REQ-007 | usage-history | DEC-007 |
| REQ-008 | claude-usage-source, countdown-evaluator, usage-snapshot-publisher, usage-window | DEC-003, DEC-005, DEC-006 |
| REQ-009 | usage-window, usage-snapshot-publisher | DEC-006, DEC-008 |
| REQ-010 | usage-source, claude-usage-source, usage-poller, usage-window | DEC-006 |
| REQ-011 | statusline-hook, usage-state-file | DEC-001, DEC-002, DEC-003 |
| REQ-012 | statusline-hook | DEC-002 |
| NFR-001 | usage-poller, usage-snapshot-publisher, usage-window | DEC-004 |
| NFR-002 | statusline-hook, claude-usage-source, usage-poller | DEC-001 |

## Contratos afectados

| Protocolo | Contrato | Cambio |
|---|---|---|
| file-json | docs/contracts/file-json/ (estado de límites, p. ej. `statusline-state`) | Se crea: formato del fichero de estado, escrito por statusline-hook y leído por claude-usage-source, con ubicación por defecto configurable y marcas en epoch de segundos |
| stdio | docs/contracts/stdio/ (entrada y salida de la status line) | Se crea: JSON de entrada de Claude Code al hook, encadenado al comando original y salida de texto devuelta |

## Cambios sugeridos al mapa

- + Módulo: statusline-hook | hook/statusline-state.py | Persiste límites de Claude en fichero y encadena la status line | -
- + Módulo: usage-source | src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java | Abstracción por proveedor de lectura de uso, sin propagar fallos | -
- + Módulo: claude-usage-source | src/main/java/es/vargontoc/subscriptionviewer/usage/claude | Lee y valida el fichero de estado de Claude | usage-source, configuration-provider
- + Módulo: usage-poller | src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java | Lee las fuentes cada 10 s fuera del hilo de interfaz | usage-source, usage-history, usage-snapshot-publisher, configuration-provider, lifecycle-coordinator
- + Módulo: usage-history | src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java | Serie en memoria de porcentajes por proveedor y ventana | -
- + Módulo: usage-snapshot-publisher | src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java | Guarda el último estado por proveedor y notifica a la vista | -
- + Módulo: countdown-evaluator | src/main/java/es/vargontoc/subscriptionviewer/usage/CountdownEvaluator.java | Calcula tiempo restante y vigencia frente a la hora actual | -
- + Módulo: usage-window | src/main/java/es/vargontoc/subscriptionviewer/ui | Ventana con ventanas de uso, cuenta atrás, gráfico y texto neutro | usage-snapshot-publisher, usage-history, countdown-evaluator
- ~ Módulo: tray-menu | src/main/java/es/vargontoc/subscriptionviewer/tray/TrayMenu.java | Menú contextual con opciones de mostrar uso y cierre | lifecycle-coordinator, usage-window
- ~ Módulo: lifecycle-coordinator | src/main/java/es/vargontoc/subscriptionviewer/lifecycle/LifecycleCoordinator.java | Mantiene el proceso vivo y orquesta el cierre ordenado | application-context, tray-indicator, usage-poller
- + Contrato: file-json | docs/contracts/file-json/statusline-state | statusline-hook -> claude-usage-source
- + Contrato: stdio | docs/contracts/stdio/statusline | Claude Code -> statusline-hook
- + Convención: usage-state-file-default: ~/.claude/statusline-state.json
- + Convención: usage-poll-interval-seconds: 10
