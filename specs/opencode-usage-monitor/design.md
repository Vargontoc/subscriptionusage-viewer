# Diseño: opencode-usage-monitor

Estado: aprobado

## Resumen

Se añade un segundo proveedor (OpenCode Go) reutilizando la arquitectura de fuentes por proveedor: una nueva `opencode-usage-source` consulta por HTTPS el endpoint de uso con la API key del usuario, normaliza las dos variantes del contrato y clasifica los fallos (clave ausente, autenticación, suscripción, red). `usage-poller`, `usage-history`, `usage-snapshot-publisher` y `usage-window` se reutilizan con ajustes menores. La API key ya no se introduce en la interfaz: `configuration-provider` la resuelve al arrancar desde `application.yml` (prioridad) o desde la variable de entorno `OPENCODE_API_KEY`, y se protege (NFR-006) con un valor secreto enmascarado, ausencia de trazas con la clave, el `application.yml` real ignorado por git y un fichero de ejemplo versionado. El arranque en segundo plano lo da un lanzador que desacopla la JVM de la terminal.

## Supuestos sobre el sistema actual

- Según el mapa, `usage-source` ya encapsula fallos y devuelve resultado opcional. Se asume que hoy no distingue la causa de la ausencia (autenticación, suscripción, red); el diseño le añade esa distinción (ver DEC-005). No se ha abierto el código.
- Se asume que `usage-poller` usa hoy un único intervalo para todas las fuentes (Claude, 10 s); el diseño requiere intervalo y retardo propios por fuente (OpenCode 60 s con backoff).
- Se asume que `usage-snapshot-publisher` guarda el último estado por proveedor y que puede conservar el último dato válido junto con un indicador de desactualización y un estado de error.
- Se asume que `usage-window` y `countdown-evaluator` admiten un número variable de ventanas por proveedor (tres en OpenCode: `rolling`, `weekly`, `monthly`, con `weekly` opcional) y formatear tiempos largos (días).
- Se asume que `usage-history` deduplica hoy por hora de registro (según el mapa); en OpenCode cada consulta tiene hora nueva, por lo que debe deduplicar también por valores iguales (DEC-006).
- Se asume que `configuration-provider` admite nuevas propiedades en `application.yml` (URL base, intervalo, límites de backoff, clave) y que puede leer una variable de entorno de forma explícita.
- Se asume que el `application.yml` actual del proyecto está versionado y no contiene secretos. Por eso la clave real vive en un `application.yml` local no versionado, en una ubicación que el framework carga además del versionado y con prioridad sobre él (ver DEC-007). La ubicación exacta no se ha comprobado en el repositorio.
- Se asume que el mapa no recoge hoy reglas de `.gitignore`; se confirmará al formalizar.
- Se asume que `lifecycle-coordinator` ya detiene el planificador y que `tray-menu` no necesita cambios por esta feature.
- Ya existe una carpeta no versionada `docs/contracts/opencode/` que el mapa no referencia; no se ha abierto y no se usa como fuente.
- Entorno monousuario local con entorno gráfico y salida a internet hacia `opencode.ai`.

## Componentes

| Componente | Responsabilidad | Existente o nuevo |
|---|---|---|
| background-launcher | Lanzador que arranca la aplicación desacoplada de la terminal y devuelve el control al usuario | nuevo |
| opencode-usage-source | Consulta el endpoint de uso con la API key, normaliza ambas variantes del contrato y clasifica el resultado (datos, clave ausente, clave inválida, sin suscripción, fallo de red) sin registrar clave ni cabecera | nuevo |
| secret-config-files | Reglas de exclusión de git para el `application.yml` local con la clave real y fichero de configuración de ejemplo versionado con marcador de posición | nuevo |
| usage-source | Abstracción por proveedor; se amplía para informar de la causa de la ausencia de datos | existente |
| usage-poller | Planificador en hilo de fondo; se amplía con intervalo propio por fuente, backoff ante fallos de red, no consultar sin clave y pausa ante 401/403 hasta reiniciar | existente |
| usage-history | Serie en memoria por proveedor y ventana; añade deduplicación por valores iguales | existente |
| usage-snapshot-publisher | Último estado por proveedor; añade indicador de desactualización, estado de error y hora de última consulta exitosa | existente |
| usage-window | Ventana de uso; muestra la tarjeta de OpenCode, hora de última actualización, desactualización, aviso de clave ausente (con dónde configurarla) y mensajes de autenticación; nunca recibe la clave | existente |
| countdown-evaluator | Tiempo restante y vigencia por ventana, sin cambios de responsabilidad | existente |
| configuration-provider | Añade propiedades de OpenCode (URL, intervalo, tope de backoff) y resuelve la API key (`application.yml` antes que `OPENCODE_API_KEY`), entregándola como valor secreto enmascarado y sin volcarla al log | existente |
| lifecycle-coordinator | Sin cambios; sigue deteniendo el planificador al cierre | existente |

Eliminados respecto al diseño anterior: `opencode-credentials-store`, `opencode-settings-dialog` y la opción de configuración en `tray-menu` (fuera de alcance por cambio de requisitos).

## Flujo

1. El usuario ejecuta background-launcher desde la terminal; este lanza la aplicación desacoplada y devuelve el control. La aplicación arranca con el icono de bandeja, sin hacer trabajo bloqueante antes de mostrarlo (REQ-001, NFR-001).
2. Al arrancar, configuration-provider resuelve la API key de OpenCode una sola vez: toma el valor de `application.yml` si está definido (no vacío y distinto del marcador del ejemplo); si no, toma `OPENCODE_API_KEY`; si tampoco, queda "sin clave". El resultado se guarda como valor secreto enmascarado. Solo se registra en el log si hay clave y su origen, nunca el valor (REQ-002, NFR-006).
3. Sin clave: usage-poller no programa ni realiza peticiones a OpenCode y usage-snapshot-publisher publica el estado "clave ausente"; usage-window muestra en la tarjeta de OpenCode que falta la API key y que debe definirse en `application.yml` o en `OPENCODE_API_KEY`, sin campo de entrada. Otros proveedores no se ven afectados (REQ-010, REQ-012).
4. Con clave: cada 60 s (retardo contado entre fin de una lectura y el inicio de la siguiente), usage-poller pide a opencode-usage-source su lectura en un hilo de fondo, aislada de las demás fuentes.
5. opencode-usage-source obtiene la clave de configuration-provider, hace la petición HTTPS con `Authorization: Bearer`, con tiempos de espera acotados, y procesa la respuesta. La cabecera y la clave no se escriben en ningún log ni en mensajes de error. Para cada ventana presente (`rolling`, `weekly`, `monthly`) usa porcentaje e instante de reinicio de la variante disponible: si viene `resetInSec`, el instante se calcula sumándolo a la hora de finalización de la consulta. Propiedades desconocidas se ignoran y las ventanas ausentes se omiten.
6. Éxito: usage-poller entrega las ventanas a usage-history (que añade un punto solo si el valor difiere del último de esa ventana) y el resultado, con la hora de consulta exitosa, a usage-snapshot-publisher, que limpia el indicador de desactualización y notifica a usage-window en el hilo de interfaz.
7. Error 401: el resultado es "clave inválida o ausente"; 403: "sin suscripción Go activa". usage-poller no reintenta y pausa la fuente hasta que se reinicie la aplicación (S1, S7), porque la clave solo se lee al arrancar. usage-snapshot-publisher conserva el último dato válido si lo hay, y usage-window muestra el mensaje de autenticación sin afectar a otros proveedores.
8. Fallo de red u otro transitorio: usage-snapshot-publisher conserva el último dato válido y marca "desactualizado"; sin datos previos, usage-window muestra el texto neutro. usage-poller aumenta el retardo (backoff) hasta un tope y lo restablece al primer éxito. El fallo se registra sin detalles de la petición.
9. usage-window refresca el tiempo restante mediante countdown-evaluator con el instante de reinicio, independiente de las lecturas, y muestra la hora de la última consulta exitosa.
10. Al cerrar, lifecycle-coordinator detiene el planificador; la memoria se descarta sin persistir histórico (REQ-009).

## Interfaces entre componentes

| Origen | Destino | Qué se comunica | Protocolo |
|---|---|---|---|
| opencode-usage-source | API de uso de OpenCode (externa) | Petición autenticada de consumo actual; respuesta con ventanas en una de dos variantes | http |
| opencode-usage-source | configuration-provider | Petición de la API key resuelta (valor secreto enmascarado) y de la URL base | - |
| usage-poller | usage-source (opencode-usage-source) | Petición de lectura con resultado clasificado; no se invoca si no hay clave | - |
| usage-poller | usage-history | Lecturas nuevas por ventana | - |
| usage-poller | usage-snapshot-publisher | Último estado (incluido "clave ausente"), causa de fallo y hora de consulta exitosa; nunca la clave | - |
| usage-snapshot-publisher | usage-window | Notificación de cambio en el hilo de interfaz | - |
| usage-history | usage-window | Serie temporal para el gráfico | - |
| countdown-evaluator | usage-window | Tiempo restante y vigencia por ventana | - |
| configuration-provider | usage-poller, opencode-usage-source | Intervalo, URL, topes de backoff y si hay clave | - |
| background-launcher | application-launcher | Arranque desacoplado del proceso | - |

## Modelo de datos lógico

- Proveedor: servicio de AI (ahora Claude y OpenCode). Relaciones: tiene cero o más Ventanas; tiene un Estado de lectura.
- Ventana de límite: `rolling` (5 h), `weekly`, `monthly`. Relaciones: pertenece a un Proveedor; tiene una Lectura vigente y una Serie.
- Lectura de uso: porcentaje consumido, instante de reinicio y hora de consulta. Relaciones: pertenece a una Ventana.
- Punto de evolución: porcentaje en un instante, solo en memoria. Relaciones: pertenece a la Serie de una Ventana.
- Estado de lectura del proveedor: correcto, desactualizado, clave ausente, clave inválida, sin suscripción, sin datos. Relaciones: pertenece a un Proveedor; referencia la hora de la última consulta exitosa.
- Credencial de OpenCode: API key del usuario, resuelta una vez al arrancar desde configuración o entorno, solo en memoria como valor secreto. Relaciones: usada por la fuente de OpenCode; no se muestra, no se registra ni forma parte del Estado de lectura.

## Decisiones

### DEC-001 Proceso de fondo mediante lanzador, sin servicio del sistema

- **Decisión:** La aplicación, que ya es un proceso non-web residente con icono de bandeja, se inicia con un lanzador que la desacopla de la terminal. No se implementa daemonización propia ni servicio del sistema (S9).
- **Alternativas descartadas:** Servicio de Windows/systemd (requiere privilegios, instalación y es excesivo para monousuario local con interfaz gráfica de escritorio); daemonización dentro de la JVM (no es portable en Java); dejar la terminal bloqueada (incumple REQ-001).
- **Cubre:** REQ-001, NFR-001, NFR-002

### DEC-002 Cliente HTTP del JDK, sin servidor ni dependencias nuevas

- **Decisión:** opencode-usage-source usa el cliente HTTP estándar de Java 21, con tiempos de espera de conexión y de petición acotados, ejecutado siempre en el hilo de fondo del poller. No se activa ningún registro de peticiones del cliente.
- **Alternativas descartadas:** Cliente de Spring web (arrastra dependencias web a una aplicación non-web); librería HTTP externa (peso innecesario para una sola petición).
- **Cubre:** REQ-003, NFR-002, NFR-003, NFR-004, NFR-006

### DEC-003 Intervalo de refresco de 60 s con mínimo de seguridad

- **Decisión (S4):** El intervalo por defecto de OpenCode es 60 s, contado entre el fin de una lectura y el inicio de la siguiente, configurable con un mínimo de 10 s. Se garantizan así como máximo 6 peticiones por minuto, muy por debajo del límite de NFR-005. El intervalo es propio de cada fuente y no afecta al de Claude (10 s).
- **Alternativas descartadas:** 10 s como Claude (carga innecesaria sobre una API remota con límites de 5 h/semana/mes que cambian despacio); 5 min (la cuenta atrás y la frescura serían pobres); cadencia fija con solape (rompe REQ-006).
- **Cubre:** REQ-006, NFR-005, NFR-003

### DEC-004 Backoff exponencial acotado ante fallos de red; sin reintento en 401/403

- **Decisión (S8):** Ante fallo transitorio el retardo se duplica sobre el intervalo base hasta un tope de 5 min y vuelve al base tras la primera lectura exitosa. Ante 401/403 no hay reintentos periódicos: la fuente se pausa hasta el siguiente reinicio de la aplicación, ya que la clave solo se lee al arrancar (S1, S7). Los códigos 5xx y 429 se tratan como transitorios. Sin clave configurada la fuente no se programa (cero peticiones).
- **Alternativas descartadas:** Reintento a intervalo fijo (insiste sobre una red caída); reintentos inmediatos múltiples (carga y espera en el hilo de fondo); seguir sondeando tras 401/403 (peticiones inútiles y riesgo de bloqueo de la clave); releer la clave en caliente (contradice S1, confirmada por el usuario).
- **Cubre:** REQ-010, REQ-011, NFR-005

### DEC-005 Resultado de lectura clasificado en usage-source

- **Decisión:** `usage-source` se amplía para distinguir, sin propagar excepciones, las causas: datos, clave ausente, clave inválida, sin suscripción y fallo transitorio. El poller y el publisher actúan según la causa (no consultar, pausa, backoff, desactualización) y la vista traduce a mensajes de usuario no técnicos.
- **Alternativas descartadas:** Que la fuente lance excepciones (rompe el aislamiento de fallos de la arquitectura, y los mensajes de excepción podrían filtrar datos de la petición); que la vista interprete códigos HTTP (acopla la UI al proveedor); un indicador único de "sin datos" (impide cumplir REQ-010 y REQ-011).
- **Cubre:** REQ-010, REQ-011, REQ-012, NFR-006

### DEC-006 Deduplicación del historial por valores

- **Decisión:** Para OpenCode, usage-history añade un punto por ventana solo si el porcentaje o el instante de reinicio difieren de los del último punto, porque cada consulta tiene hora nueva. Se mantiene el tope de puntos por serie de la feature anterior y no se persiste nada.
- **Alternativas descartadas:** Deduplicar solo por hora de consulta (todas las lecturas serían "nuevas" y habría duplicados, incumpliendo REQ-008); añadir un punto por consulta (gráfico saturado y crecimiento innecesario).
- **Cubre:** REQ-008, REQ-009

### DEC-007 Origen de la API key: application.yml local con prioridad y variable de entorno de respaldo

- **Decisión (S1, S6, S11, S12):** La clave se lee solo vía configuration-provider, una vez al arrancar. La propiedad de `application.yml` se llama `subscription-viewer.opencode.api-key`. Precedencia explícita y programada: si esa propiedad tiene valor (no vacío y distinto del marcador del fichero de ejemplo) se usa; en otro caso se lee la variable `OPENCODE_API_KEY`; si tampoco, "sin clave". El nombre de la propiedad se elige a propósito para que la variable `OPENCODE_API_KEY` no se enlace automáticamente a ella por las reglas de enlace relajado del framework, porque el framework da a las variables de entorno prioridad sobre `application.yml`, justo lo contrario de REQ-002. La clave real vive en un `application.yml` local no versionado que se carga además del versionado y lo sobrescribe (ver DEC-010). Un cambio exige reiniciar.
- **Alternativas descartadas:** Nombre de propiedad `opencode.api-key` (el entorno `OPENCODE_API_KEY` la enlazaría y ganaría a `application.yml`, invirtiendo la precedencia); confiar en la precedencia por defecto del framework (mismo problema); fichero de credenciales propio en el directorio de usuario con diálogo (es lo que los nuevos requisitos eliminan); almacén de credenciales del sistema operativo (fuera de alcance); relectura en caliente (contradice S1).
- **Cubre:** REQ-002, REQ-010, REQ-012, NFR-006

### DEC-008 OpenCode como fuente independiente sin escritura en ficheros compartidos

- **Decisión:** OpenCode se registra como una usage-source más, consultada de forma aislada por el poller. No se escribe ningún fichero de estado de uso compartido con otros procesos. La vista muestra solo proveedores con información o con un estado que comunicar (clave ausente/inválida), sin secciones vacías de otros proveedores.
- **Alternativas descartadas:** Reutilizar el modelo hook-fichero de Claude (el endpoint HTTP lo hace innecesario y está fuera de alcance); un lector único con lógica por proveedor (acopla fallos).
- **Cubre:** REQ-012, REQ-009

### DEC-009 Instante de reinicio calculado en la fuente

- **Decisión:** La normalización de las variantes (`percent`/`resetsAt` frente a `usagePercent`/`resetInSec`) se hace por ventana en opencode-usage-source, convirtiendo `resetInSec` en instante absoluto con la hora de finalización de la consulta. El resto de componentes solo ven porcentaje e instante.
- **Alternativas descartadas:** Guardar segundos relativos y recalcular en la vista (la cuenta atrás se desviaría con cada lectura y complica countdown-evaluator).
- **Cubre:** REQ-004, REQ-005

### DEC-010 Protección de la API key por capas (NFR-006)

- **Decisión:** Se combinan cinco medidas:
  1. **No versionado:** el `application.yml` local con la clave real (ruta de configuración local del módulo, distinta del `application.yml` versionado) figura en `.gitignore`; solo se versiona un fichero de ejemplo con la propiedad `subscription-viewer.opencode.api-key` y un marcador de posición evidente. El marcador se trata como "no definido" para que copiar el ejemplo sin editar no envíe el marcador como clave.
  2. **Sin logs:** nadie registra la clave ni la cabecera `Authorization`. opencode-usage-source no registra petición, cabeceras ni cuerpo; los fallos se registran con categoría y código de estado, no con el mensaje de la excepción del cliente ni con la petición. configuration-provider solo registra "clave configurada: sí/no" y su origen. El nivel de log de depuración del cliente HTTP y del enlace de propiedades no se activa.
  3. **Valor secreto:** la clave viaja en un tipo propio cuyo `toString` devuelve un valor enmascarado fijo (sin ningún carácter de la clave) y que solo expone el valor real en el punto donde se construye la cabecera. Así, si algún componente lo registra o lo vuelca por error, no se filtra. Los objetos de propiedades que contengan la clave tampoco la imprimen.
  4. **Sin interfaz:** usage-snapshot-publisher y usage-window solo manejan el estado "clave ausente/inválida"; la clave nunca sale de configuration-provider y opencode-usage-source, por lo que no existe ningún camino hacia la UI (ni parcial, ni enmascarada con caracteres reales).
  5. **Verificación:** pruebas automáticas que ejecutan los escenarios (clave válida, ausente, 401, 403, red) con una clave centinela reconocible y buscan el valor en los logs capturados y en los textos de la vista; y una comprobación de que el `application.yml` local está ignorado y de que el ejemplo solo contiene el marcador.
- **Alternativas descartadas:** Mostrar la clave parcialmente enmascarada (últimos caracteres) en la UI o logs (el requisito prohíbe incluso apariciones parciales); cifrar la clave en el fichero (la clave de cifrado estaría en el mismo equipo, falsa seguridad); confiar solo en la disciplina del usuario para no versionar (S6 lo declara obligatorio); versionar un `application.yml` con la clave vacía y pedir editar ese mismo fichero (riesgo de commit accidental del valor real).
- **Cubre:** NFR-006, REQ-002

## Riesgos

- El esquema real puede incluir variantes no documentadas (S2): se mitiga ignorando propiedades desconocidas y omitiendo ventanas ilegibles sin fallar; validar contra respuestas reales en pruebas manuales.
- Una ventana `rolling` o `monthly` llega a su reinicio entre dos lecturas: countdown-evaluator la oculta hasta la siguiente lectura (máx. 60 s) en vez de mostrar datos caducados.
- La clave puede filtrarse por una vía no prevista (excepción del cliente HTTP con la petición, volcado de propiedades por el framework, traza de depuración): se mitiga con el valor secreto enmascarado, el criterio de no registrar mensajes de excepción de red y las pruebas con clave centinela sobre los logs (DEC-010).
- El usuario puede poner la clave real en el `application.yml` versionado o en el ejemplo y hacer commit: se mitiga ignorando el fichero local, dejando solo marcador en el ejemplo y documentando dónde colocar la clave; el riesgo residual es responsabilidad del usuario (S6).
- La ubicación del `application.yml` local depende de cómo carga el proyecto su configuración: si el `application.yml` actual no admite un fichero de configuración local superpuesto, habrá que ajustarlo al formalizar; no se ha verificado.
- Un `OPENCODE_API_KEY` de entorno con valor distinto al de `application.yml` puede confundir: la precedencia está fijada (`application.yml` gana) y se registra solo el origen usado, nunca el valor.
- Un cambio de clave exige reiniciar (S1): tras un 401/403 la tarjeta lo indica y no hay reintento hasta el reinicio.
- El hilo de fondo queda bloqueado en red lenta: los tiempos de espera acotados evitan solapes y la interfaz no depende de ese hilo (NFR-004).
- Los cambios al poller, a usage-source y a usage-history afectan también a la fuente de Claude: se mitiga manteniendo por defecto el comportamiento actual (intervalo 10 s, sin backoff para Claude) y cubriendo con pruebas de regresión.
- Cierre de la aplicación sin el lanzador (arranque directo): el proceso queda en la terminal; el lanzador es la vía soportada de REQ-001.

## Trazabilidad

| Requisito | Componentes | Decisiones |
|---|---|---|
| REQ-001 | background-launcher, application-launcher, tray-menu | DEC-001 |
| REQ-002 | configuration-provider, opencode-usage-source, secret-config-files, usage-window | DEC-007, DEC-010 |
| REQ-003 | opencode-usage-source, usage-poller | DEC-002, DEC-003 |
| REQ-004 | opencode-usage-source | DEC-009 |
| REQ-005 | usage-window, countdown-evaluator, opencode-usage-source | DEC-009 |
| REQ-006 | usage-poller, configuration-provider | DEC-003 |
| REQ-007 | usage-snapshot-publisher, usage-window | DEC-005 |
| REQ-008 | usage-history, usage-window | DEC-006 |
| REQ-009 | usage-history, usage-snapshot-publisher | DEC-006, DEC-008 |
| REQ-010 | configuration-provider, opencode-usage-source, usage-source, usage-poller, usage-snapshot-publisher, usage-window | DEC-004, DEC-005, DEC-007 |
| REQ-011 | usage-source, usage-poller, usage-snapshot-publisher, usage-window | DEC-004, DEC-005 |
| REQ-012 | usage-source, usage-poller, usage-snapshot-publisher, usage-window | DEC-005, DEC-007, DEC-008 |
| NFR-001 | background-launcher, usage-poller, usage-window | DEC-001 |
| NFR-002 | opencode-usage-source, background-launcher | DEC-001, DEC-002 |
| NFR-003 | usage-poller, opencode-usage-source | DEC-002, DEC-003 |
| NFR-004 | usage-poller, opencode-usage-source, usage-snapshot-publisher | DEC-002 |
| NFR-005 | usage-poller, configuration-provider | DEC-003, DEC-004 |
| NFR-006 | configuration-provider, opencode-usage-source, secret-config-files, usage-snapshot-publisher, usage-window | DEC-002, DEC-005, DEC-007, DEC-010 |

## Contratos afectados

| Protocolo | Contrato | Cambio |
|---|---|---|
| http | docs/contracts/http/ (consumo de uso de OpenCode Go, p. ej. `opencode-usage`) | Se crea: petición autenticada con Bearer al endpoint de uso, respuesta con ventanas `rolling`/`weekly`/`monthly` en las dos variantes, propiedades adicionales ignoradas y códigos 401/403; el contrato no debe incluir ninguna clave real en ejemplos |

Se elimina del diseño anterior el contrato `file-json` de credenciales de OpenCode (no se creará).

## Cambios sugeridos al mapa

- + Módulo: background-launcher | scripts/start-background | Lanza la aplicación desacoplada de la terminal | application-launcher
- + Módulo: opencode-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode | Consulta y normaliza el uso de OpenCode Go y clasifica fallos | usage-source, configuration-provider
- + Módulo: secret-config-files | desktop/app/config | Ejemplo versionado con marcador y exclusión git del application.yml con la clave real | -
- ~ Módulo: configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config | Propiedades externalizadas; resuelve API key de OpenCode (application.yml antes que entorno) como valor enmascarado | application-context
- ~ Módulo: usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java | Abstracción por proveedor con resultado clasificado por causa de fallo | -
- ~ Módulo: usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java | Lecturas en hilo de fondo con intervalo por fuente, backoff, pausa y sin consulta sin clave | usage-source, usage-history, usage-snapshot-publisher, configuration-provider, lifecycle-coordinator
- ~ Módulo: usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java | Historial en memoria deduplicado por hora de registro y por valores | -
- ~ Módulo: usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java | Último estado por proveedor con desactualización y estado de error; notifica a la vista | -
- ~ Módulo: usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java | Ventana con tarjetas por proveedor, hora de actualización, desactualización, clave ausente y errores de autenticación | usage-snapshot-publisher, usage-history, countdown-evaluator
- + Contrato: http | docs/contracts/http/desktop/app/opencode-usage.md | opencode-usage-source -> API de OpenCode
- + Convención: opencode-api-key-property: subscription-viewer.opencode.api-key
- + Convención: opencode-api-key-env: OPENCODE_API_KEY
- + Convención: opencode-api-key-precedence: application.yml, luego variable de entorno
- + Convención: opencode-poll-interval-seconds: 60
- + Convención: opencode-backoff-max-seconds: 300
- + Convención: secrets-policy: la API key nunca se versiona, ni se registra, ni se muestra en la interfaz
