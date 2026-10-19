# Diseño: openai-usage-monitor

Estado: aprobado

## Resumen

Se añade OpenAI como tercer proveedor mediante una nueva `openai-usage-source` que, en cada ciclo, relee las credenciales locales (primero `.codex/auth.json`; si no existe y OpenCode está configurado, las de opencode localizadas con `opencode debug paths data`), consulta por HTTPS el endpoint de cuota y entrega las ventanas de 5 horas (rol primario) y semanal (rol secundario) con el porcentaje de OpenAI sin recalcular. Se reutilizan `usage-poller`, `usage-history`, `usage-snapshot-publisher`, `usage-window`, `countdown-evaluator` y `opencode-cli-runner`, ampliando el resultado de lectura con el estado "sesión caducada" y dando al poller intervalo y tiempo máximo propios por fuente. Además, `configuration-provider` mueve la configuración de Claude a `subscription-viewer.claude`, añade `subscription-viewer.openai` y unifica la validación de intervalos de los tres proveedores.

## Supuestos sobre el sistema actual

- `application.yml` (`desktop/app/src/main/resources/application.yml`) NO contiene hoy el bloque `subscription-viewer.openai`, aunque el requisito lo da por existente. El diseño lo crea (ver Avisos del router).
- `app.name` y `app.tray-icon-path` no están afectados por REQ-001, que solo retira `app.usage-state-file` y `app.usage-poll-interval`. Se asume que `app` se conserva con esas dos propiedades.
- El valor por defecto interno de la ruta del fichero de estado de Claude en la clase de propiedades (`statusline-state.json`) difiere del del `application.yml` (`statusline-dump.json`). Se conserva el valor del yml (REQ-001) y se alinea el por defecto interno.
- `UsageWindowType` ya tiene `FIVE_HOUR` y `SEVEN_DAY` (usados por Claude); se asume que la vista los formatea ya y que se reutilizan para OpenAI.
- `UsageWindowReading` guarda el porcentaje como entero; se asume que `used_percent` se redondea al entero más cercano para mostrarlo y deduplicar (los ejemplos de los requisitos son enteros).
- `usage-window` oculta los proveedores sin datos y hoy no tiene estado para mostrar un aviso sin lectura; se amplía (S9).
- `opencode-cli-runner` hoy solo ejecuta `session.stats` con parámetros fijos; se generaliza (DEC-003). Ya aplica timeout configurable (por defecto 30 s), fallo silencioso y destruye el proceso al vencer.
- Entorno monousuario local con salida a internet hacia `chatgpt.com`.

## Componentes

| Componente | Responsabilidad | Existente o nuevo |
|---|---|---|
| openai-usage-source | Implementa `usage-source` para OpenAI: pide credenciales, consulta el endpoint y clasifica el resultado (datos, sesión caducada, sin credenciales, fallo transitorio) | nuevo |
| openai-credentials-reader | En cada ciclo localiza y lee `access_token` y `account_id` (Codex primero, opencode como alternativa condicionada) sin escribir ficheros y sin registrar valores; entrega el par como valor secreto enmascarado | nuevo |
| openai-usage-client | Petición HTTPS autenticada con tiempo máximo de 30 s y parseo de la respuesta (ventanas por rol, `plan_type`), descartando el resto de campos | nuevo |
| opencode-cli-runner | Se generaliza para ejecutar también `opencode debug paths data` con el mismo timeout y fallo silencioso | existente |
| usage-source | Abstracción por proveedor; el resultado clasificado añade "sesión caducada" y cada fuente declara su intervalo y su tiempo máximo | existente |
| usage-poller | Intervalo y tiempo máximo por fuente (sustituye el tiempo único de 5 s y la identificación de OpenCode por nombre); publica también los fallos de OpenAI | existente |
| usage-history | Sin cambios de responsabilidad: añade puntos por ventana solo si los valores difieren | existente |
| usage-snapshot-publisher | Añade el estado "sesión caducada" conservando el último dato válido; conserva hora del último éxito y desactualización | existente |
| usage-window | Sección diferenciada de OpenAI con plan, porcentajes, tiempo restante, hora de actualización, indicador de desactualizado, gráfico y aviso formal de sesión caducada | existente |
| countdown-evaluator | Tiempo restante por ventana a partir del instante de reinicio, mínimo 0 | existente |
| configuration-provider | Propiedades bajo `subscription-viewer.claude` y `subscription-viewer.openai`; indica si existe la sección `subscription-viewer.opencode`; valida intervalos de forma uniforme y falla el arranque identificando la propiedad | existente |
| claude-usage-source | Lee la ruta y el intervalo desde la nueva ubicación de configuración; sin cambio funcional | existente |

## Flujo

1. Al arrancar, configuration-provider carga y valida los intervalos de Claude, OpenCode y OpenAI. Un valor no válido o no positivo falla el arranque con un error que identifica la propiedad. OpenAI sin valor usa 60 s y por debajo de 10 s usa 10 s (REQ-001, REQ-009, NFR-004).
2. usage-poller programa openai-usage-source con su intervalo propio contado desde el fin de la lectura anterior, de modo que no hay solapes (REQ-009).
3. En cada ciclo openai-usage-source pide las credenciales a openai-credentials-reader, que las relee del disco (REQ-005).
4. openai-credentials-reader comprueba si existe `.codex/auth.json` en el directorio de usuario. Si existe, lee solo de él `tokens.access_token` y `tokens.account_id`; si falta alguno, las credenciales no están disponibles y no se consulta ninguna otra fuente (REQ-002).
5. Si no existe, y solo si configuration-provider confirma que existe la sección `subscription-viewer.opencode`, pide a opencode-cli-runner ejecutar `opencode debug paths data` (máximo 30 s). Si falla, no está instalado o excede el tiempo, las credenciales no están disponibles. Si funciona, lee `auth.json` de ese directorio y toma `openai.access` y `openai.accountId`, ignorando otras etiquetas; sin etiqueta `openai`, no disponibles (REQ-003, NFR-003).
6. Sin credenciales: openai-usage-source no hace ninguna petición y devuelve un fallo sin causa de red. El publisher no crea sección visible si nunca hubo datos; el poller reintenta la localización en el siguiente ciclo (REQ-004, REQ-014).
7. Con credenciales: openai-usage-client hace la petición con las tres cabeceras exigidas y límite de 30 s (REQ-005, NFR-003). La petición corre siempre en hilo de fondo, sin afectar a la interfaz (NFR-002).
8. Respuesta correcta: se toma de `rate_limit` la ventana primaria como 5 h y la secundaria como semanal, con el `used_percent` tal cual, sin depender de `limit_window_seconds` ni de `plan_type`. Si falta `secondary_window` solo hay ventana de 5 h. El instante de reinicio es `reset_at` (epoch en segundos) o, si falta, el instante de fin de consulta más `reset_after_seconds`. Campos no utilizados se ignoran (REQ-005, REQ-006, REQ-007).
9. Éxito: usage-poller entrega la lectura a usage-history (un punto por ventana solo si cambia el valor) y al publisher con la hora de fin de la consulta, que limpia desactualización y aviso. usage-window muestra plan (si lo hay), porcentajes, tiempo restante (siempre con mínimo 0, recalculado con el reloj local), hora de última actualización y gráfico (REQ-007, REQ-008, REQ-010, REQ-011).
10. 401 o 403: openai-usage-source devuelve "sesión caducada", no renueva el token, no escribe ficheros y no reintenta de inmediato; el publisher conserva los últimos datos, y usage-window muestra el texto formal exacto de REQ-012 aunque no haya datos previos (S9). En el siguiente ciclo se releen las credenciales; un éxito retira el aviso (REQ-012).
11. Red, tiempo agotado, código distinto de 401/403 o respuesta no JSON o sin `rate_limit`: resultado de fallo transitorio. El publisher conserva el último dato válido y lo marca desactualizado; sin datos previos no se muestra la sección ni mensaje técnico. La hora mostrada sigue siendo la del último éxito (REQ-010, REQ-013).
12. Los fallos de OpenAI no afectan a la lectura de Claude ni de OpenCode, que siguen sus propios ciclos, y solo se muestran proveedores con datos o con aviso (REQ-014).

## Interfaces entre componentes

| Origen | Destino | Qué se comunica | Protocolo |
|---|---|---|---|
| openai-usage-client | API de cuota de OpenAI (externa) | Consulta autenticada del consumo; respuesta con ventanas por rol, instante de reinicio y plan | http |
| openai-credentials-reader | `.codex/auth.json` (fichero local) | Lectura de solo lectura del token y del identificador de cuenta | file-json |
| openai-credentials-reader | `auth.json` de opencode (fichero local) | Lectura de solo lectura de las credenciales de OpenAI bajo la etiqueta correspondiente | file-json |
| openai-credentials-reader | opencode-cli-runner | Petición de la ruta del directorio de datos de opencode | -  |
| opencode-cli-runner | CLI opencode | Ejecución de la consulta de rutas con salida de texto | stdio |
| openai-credentials-reader | configuration-provider | Consulta de si existe la sección de OpenCode | - |
| openai-usage-source | openai-credentials-reader, openai-usage-client | Credenciales enmascaradas y resultado de la consulta | - |
| usage-poller | openai-usage-source | Petición de lectura periódica con resultado clasificado, intervalo y tiempo máximo propios | - |
| usage-poller | usage-history | Lecturas nuevas por ventana | - |
| usage-poller | usage-snapshot-publisher | Resultado clasificado (incluida sesión caducada) y hora de fin de consulta; nunca credenciales | - |
| usage-snapshot-publisher | usage-window | Notificación de cambio en el hilo de interfaz | - |
| usage-history, countdown-evaluator | usage-window | Serie temporal y tiempo restante por ventana | - |
| configuration-provider | usage-poller, claude-usage-source, openai-usage-source | Intervalos validados y ruta del fichero de Claude | - |

## Modelo de datos lógico

- Proveedor: servicio de AI (Claude, OpenCode, OpenAI). Relaciones: tiene cero o más Ventanas; tiene un Estado de lectura.
- Ventana de límite: de 5 horas o semanal en OpenAI, por rol. Relaciones: pertenece a un Proveedor; tiene una Lectura vigente y una Serie.
- Lectura de uso: porcentaje consumido, instante de reinicio y hora de consulta; en OpenAI, además, un plan opcional. Relaciones: pertenece a una Ventana y a un Proveedor.
- Punto de evolución: porcentaje en un instante, solo en memoria. Relaciones: pertenece a la Serie de una Ventana.
- Estado de lectura del proveedor: correcto, desactualizado, sin datos, sesión caducada. Relaciones: pertenece a un Proveedor; referencia la última lectura válida y la hora del último éxito.
- Credenciales de OpenAI: token de acceso e identificador de cuenta, releídos en cada ciclo, solo en memoria y como valor secreto. Relaciones: usadas por la fuente de OpenAI; no forman parte de la Lectura ni del Estado.

## Decisiones

### DEC-001 Reorganización de la configuración por proveedor

- **Decisión:** La clase de propiedades enlaza un prefijo común `subscription-viewer` con subsecciones `claude`, `opencode` y `openai`. `app` queda solo con nombre e icono. Las referencias a la ruta y al intervalo de Claude (provider, mensajes de log y de error, pruebas, documentación) se actualizan a la nueva ubicación, sin dejar `app.usage-state-file` ni `app.usage-poll-interval`. Los métodos públicos de configuration-provider se mantienen para no afectar a los consumidores.
- **Alternativas descartadas:** Mantener ambos nombres con alias (incumple "dejarán de existir"); una clase de propiedades por proveedor (más ficheros sin necesidad real); renombrar los métodos del proveedor de configuración (arrastra cambios a todos los consumidores sin beneficio).
- **Cubre:** REQ-001

### DEC-002 Credenciales releídas en cada ciclo, con orden fijo y sin mezcla de fuentes

- **Decisión:** openai-credentials-reader no almacena credenciales entre ciclos y aplica el orden: Codex; si el fichero existe, es la única fuente aunque esté incompleto; opencode solo si Codex no existe y la sección `subscription-viewer.opencode` existe. Así los cambios de token por parte del CLI se recogen sin reiniciar y no se ejecuta `opencode` cuando no procede.
- **Alternativas descartadas:** Cachear credenciales al arrancar (incumple REQ-004 y REQ-005 y la recuperación de REQ-012); recurrir a opencode si Codex está incompleto (lo prohíbe REQ-002); vigilar ficheros con eventos del sistema (complejidad innecesaria con un ciclo de 10 s o más).
- **Cubre:** REQ-002, REQ-003, REQ-004, REQ-005, REQ-012, NFR-001

### DEC-003 Reutilizar el ejecutor de CLI existente

- **Decisión:** opencode-cli-runner se generaliza para aceptar la consulta de rutas además de `session.stats`, conservando timeout de 30 s, destrucción del proceso, límite de salida y fallo silencioso. No se crea un segundo ejecutor.
- **Alternativas descartadas:** Un nuevo ejecutor de procesos (duplicaría la gestión de timeouts y procesos hijos en Windows); deducir la ruta de datos de opencode sin ejecutar el comando (lo exige REQ-003).
- **Cubre:** REQ-003, NFR-003

### DEC-004 Cliente HTTP estándar del JDK

- **Decisión:** openai-usage-client usa el cliente HTTP de Java 21, con tiempos de conexión y de petición acotados a 30 s en conjunto, siempre en el hilo de fondo del poller. No se activa registro de peticiones, cabeceras ni cuerpo.
- **Alternativas descartadas:** Cliente de Spring web (dependencias web en una aplicación non-web); librería externa (peso innecesario para una sola petición).
- **Cubre:** REQ-005, NFR-002, NFR-003, NFR-001

### DEC-005 "Sesión caducada" como estado propio del resultado de lectura

- **Decisión:** El resultado de `usage-source` y el estado del publisher incorporan "sesión caducada", distinto del fallo transitorio. Sin credenciales se usa el fallo transitorio sin causa (sin sección si no hubo datos). El aviso lo traduce usage-window a texto de usuario; nunca viaja contenido de la respuesta ni credenciales.
- **Alternativas descartadas:** Tratar 401/403 como fallo transitorio (no se podría mostrar el aviso formal ni distinguirlo de "desactualizado"); lanzar excepciones (rompe el aislamiento por fuente y podría filtrar datos); que la vista interprete códigos HTTP (acopla la interfaz al proveedor).
- **Cubre:** REQ-012, REQ-013, REQ-004, NFR-001

### DEC-006 Intervalo y tiempo máximo por fuente en el poller

- **Decisión:** Cada fuente declara su intervalo y su tiempo máximo de lectura y el poller los usa, en lugar de identificar OpenCode por el nombre del proveedor y aplicar un único tiempo de 5 s. El tiempo máximo de OpenAI cubre los dos peores casos encadenados (ejecución de `opencode` más petición HTTP, 30 s cada uno). Se mantiene el comportamiento actual de Claude y OpenCode. El siguiente ciclo se programa al terminar el anterior.
- **Alternativas descartadas:** Mantener los 5 s globales (abortaría peticiones válidas antes de los 30 s de NFR-003); ampliar el tiempo global (retrasaría la detección de fallos de Claude); añadir más condiciones por nombre de proveedor (frágil y acumulativo).
- **Cubre:** REQ-009, NFR-003, NFR-004, REQ-014

### DEC-007 Publicación de los fallos de OpenAI

- **Decisión:** A diferencia de OpenCode (cuyos ciclos fallidos se descartan sin publicar), los resultados de fallo y de sesión caducada de OpenAI sí se publican, porque REQ-012 y REQ-013 exigen indicar desactualización y aviso. La lógica especial de OpenCode del poller y del publisher se mantiene.
- **Alternativas descartadas:** Aplicar a OpenAI el descarte de fallos de OpenCode (ocultaría desactualización y aviso); retirar la lógica de OpenCode (cambia su comportamiento, fuera de alcance).
- **Cubre:** REQ-010, REQ-012, REQ-013, REQ-014

### DEC-008 Validación uniforme de intervalos en el arranque

- **Decisión:** configuration-provider valida los tres intervalos al arrancar con una única regla: duración con formato válido y positiva; si no, falla el arranque con un error de configuración que nombra la propiedad. El mínimo de 10 s se aplica a OpenAI (y se mantiene el que ya tiene OpenCode); ausente o vacío en OpenAI usa 60 s. La validación se hace al construir la configuración, no al primer uso.
- **Alternativas descartadas:** Dejar a cada proveedor su propia validación (comportamiento actual no uniforme, ver Avisos); aplicar un valor por defecto ante un valor no válido (prohibido por REQ-009).
- **Cubre:** REQ-009, REQ-001, NFR-004

### DEC-009 Ventanas por rol, no por duración ni por plan

- **Decisión:** La ventana primaria se presenta siempre como 5 horas y la secundaria como semanal, independientemente de `limit_window_seconds` y de `plan_type`. El plan se muestra solo como etiqueta informativa. Se reutilizan los tipos de ventana de Claude.
- **Alternativas descartadas:** Clasificar por `limit_window_seconds` (los planes pueden variarlo); condicionar por plan (lo desaconsejan los criterios de REQ-006).
- **Cubre:** REQ-006, REQ-008

### DEC-010 Instante de reinicio absoluto calculado en la fuente

- **Decisión:** La fuente entrega siempre un instante absoluto: `reset_at` interpretado en segundos desde epoch o, si falta, la hora de fin de consulta más `reset_after_seconds`. countdown-evaluator calcula el tiempo restante con el reloj local, con mínimo 0.
- **Alternativas descartadas:** Guardar segundos relativos (la cuenta atrás se desviaría con cada lectura); interpretar `reset_at` como milisegundos (lo prohíbe REQ-007).
- **Cubre:** REQ-007

### DEC-011 Protección de credenciales y datos personales

- **Decisión:** (1) Las credenciales viajan en un tipo secreto cuya representación textual es fija y sin caracteres reales, y solo se expone el valor al construir las cabeceras. (2) El parser del fichero de credenciales extrae únicamente los campos necesarios; `refresh_token`, `id_token`, `OPENAI_API_KEY`, `user_id`, `email` y similares se descartan al leer. (3) Nadie registra cabeceras, cuerpos, excepciones del cliente ni contenido de ficheros; los fallos se registran solo con categoría y código de estado. (4) El estado publicado y la vista solo manejan "sesión caducada" y datos de consumo. (5) Los ficheros de credenciales solo se abren en lectura y nunca se copian. (6) Verificación con valores centinela sobre logs y textos de la vista en todos los estados.
- **Alternativas descartadas:** Mostrar el token enmascarado parcialmente (prohibido incluso parcial); conservar el fichero de credenciales completo en memoria (amplía innecesariamente la superficie de fuga).
- **Cubre:** NFR-001, REQ-012

## Riesgos

- Detectar "existe la sección `subscription-viewer.opencode`": la clase de propiedades siempre instancia la subsección de OpenCode, por lo que hay que comprobar su presencia en el entorno efectivo y no en el objeto enlazado. Mitigación: prueba con y sin la sección.
- El tiempo máximo mayor para OpenAI podría retrasar su propio ciclo hasta 60 s en el peor caso (opencode lento y red lenta); no afecta a las demás fuentes porque cada una tiene su lectura aislada.
- Cambios en poller, `usage-source` y publisher afectan a Claude y OpenCode: se mitiga manteniendo sus valores por defecto y con pruebas de regresión de ambos.
- El enlace de propiedades de Spring puede rechazar formatos que el análisis manual de OpenCode aceptaba (por ejemplo, un número sin unidad). Mitigación: documentar los formatos admitidos y cubrirlos en las pruebas de REQ-009.
- Estructura del endpoint no documentada oficialmente: se ignoran propiedades desconocidas, se trata como no válida la respuesta sin `rate_limit` y se omite la ventana semanal si falta.
- Credenciales de opencode con etiqueta distinta o ausente: se consideran no disponibles sin error.
- Valores decimales en `used_percent` con un modelo de lectura de porcentaje entero: se redondea; si el usuario necesita decimales habría que ampliar el modelo (no pedido).
- Un 401/403 persistente genera una petición por ciclo (máximo 6 por minuto, NFR-004), aceptable al no existir pausa por requisito.

## Trazabilidad

| Requisito | Componentes | Decisiones |
|---|---|---|
| REQ-001 | configuration-provider, claude-usage-source | DEC-001, DEC-008 |
| REQ-002 | openai-credentials-reader, openai-usage-source | DEC-002 |
| REQ-003 | openai-credentials-reader, opencode-cli-runner, configuration-provider | DEC-002, DEC-003 |
| REQ-004 | openai-usage-source, openai-credentials-reader, usage-snapshot-publisher, usage-poller | DEC-002, DEC-005 |
| REQ-005 | openai-usage-client, openai-credentials-reader, openai-usage-source | DEC-002, DEC-004 |
| REQ-006 | openai-usage-client, openai-usage-source | DEC-009 |
| REQ-007 | openai-usage-client, countdown-evaluator, usage-window | DEC-010 |
| REQ-008 | usage-window, openai-usage-source | DEC-009 |
| REQ-009 | configuration-provider, usage-poller, openai-usage-source | DEC-006, DEC-008 |
| REQ-010 | usage-snapshot-publisher, usage-window, usage-poller | DEC-007 |
| REQ-011 | usage-history, usage-poller, usage-window | DEC-007 |
| REQ-012 | openai-usage-source, usage-source, usage-snapshot-publisher, usage-window | DEC-005, DEC-007, DEC-011 |
| REQ-013 | openai-usage-source, usage-snapshot-publisher, usage-window | DEC-005, DEC-007 |
| REQ-014 | usage-poller, usage-snapshot-publisher, usage-window | DEC-006, DEC-007 |
| NFR-001 | openai-credentials-reader, openai-usage-client, openai-usage-source, usage-snapshot-publisher, usage-window | DEC-002, DEC-004, DEC-011 |
| NFR-002 | openai-usage-client, openai-credentials-reader, usage-poller | DEC-004 |
| NFR-003 | openai-usage-client, opencode-cli-runner, usage-poller | DEC-003, DEC-004, DEC-006 |
| NFR-004 | configuration-provider, usage-poller | DEC-006, DEC-008 |
| NFR-005 | usage-poller, openai-usage-source, usage-history | DEC-004, DEC-006 |

## Contratos afectados

| Protocolo | Contrato | Cambio |
|---|---|---|
| http | docs/contracts/http/desktop/app/openai-usage.md | Se crea: consulta autenticada con token y cuenta al endpoint de cuota, ventanas primaria y secundaria, plan, instante de reinicio en epoch segundos o segundos restantes, códigos 401/403; sin ningún token real en ejemplos |
| file-json | docs/contracts/file-json/desktop/app/codex-auth.md | Se crea: lectura de solo lectura de `.codex/auth.json` (token y cuenta); el resto de campos, ignorados |
| file-json | docs/contracts/file-json/desktop/app/opencode-auth.md | Se crea: lectura de solo lectura de `auth.json` de opencode, solo la etiqueta de OpenAI |
| stdio | docs/contracts/stdio/desktop/app/opencode-data-path.md | Se crea: ejecución de la consulta de ruta de datos de opencode con timeout y salida de una línea |

## Cambios sugeridos al mapa

- + Módulo: openai-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai | Orquesta credenciales y consulta de OpenAI y clasifica el resultado | usage-source, configuration-provider, openai-credentials-reader, openai-usage-client
- + Módulo: openai-credentials-reader | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai | Lee credenciales de Codex u opencode en cada ciclo, solo lectura | configuration-provider, opencode-cli-runner
- + Módulo: openai-usage-client | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai | Consulta HTTPS del endpoint de cuota y parseo de ventanas | -
- ~ Módulo: opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java | Ejecuta `opencode` por PATH (stats y ruta de datos) con timeout y fallo silencioso | configuration-provider
- ~ Módulo: configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config | Propiedades por proveedor bajo subscription-viewer (claude, opencode, openai); validación uniforme de intervalos | application-context
- ~ Módulo: usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java | Abstracción por proveedor con resultado clasificado, intervalo y tiempo máximo | -
- ~ Módulo: usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java | Planificador con intervalo y tiempo máximo por fuente, sin solapamiento y aislamiento | usage-source, usage-history, usage-snapshot-publisher, configuration-provider, lifecycle-coordinator
- ~ Módulo: usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java | Último estado por proveedor con desactualización y sesión caducada | -
- ~ Módulo: usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java | Ventana con sección OpenAI, plan, desactualización y aviso de sesión caducada | usage-snapshot-publisher, usage-history, countdown-evaluator
- + Contrato: http | docs/contracts/http/desktop/app/openai-usage.md | openai-usage-client -> API de cuota de OpenAI
- + Contrato: file-json | docs/contracts/file-json/desktop/app/codex-auth.md | - -> openai-credentials-reader
- + Contrato: file-json | docs/contracts/file-json/desktop/app/opencode-auth.md | - -> openai-credentials-reader
- + Contrato: stdio | docs/contracts/stdio/desktop/app/opencode-data-path.md | opencode-cli-runner -> CLI opencode
- + Convención: openai-poll-interval-property: subscription-viewer.openai.usage-poll-interval
- + Convención: openai-poll-interval-default-seconds: 60
- + Convención: openai-poll-interval-min-seconds: 10
- + Convención: claude-config-prefix: subscription-viewer.claude
- + Convención: secrets-policy: tokens, identificadores de cuenta, user_id y email nunca se registran ni se muestran
