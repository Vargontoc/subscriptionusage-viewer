# Diseño: gemini-usage-monitor

Estado: aprobado

## Resumen

Se añade Gemini como cuarto proveedor mediante una nueva `gemini-usage-source` que, en cada ciclo, ejecuta `agy -p "/usage" --output-format json` por PATH con un nuevo `gemini-cli-runner`, interpreta `command.data.groups` con un parser propio y entrega cada grupo como una serie con sus ventanas semanal y de 5 horas. La mecánica común de ejecución de CLI (proceso hijo, PATH/Windows, lectura concurrente de stdout, descarte de stderr, timeout con destrucción del proceso, resultado salida + código de salida) se extrae de `opencode-cli-runner` a una clase base `cli-runner-base` (`AbstractCliRunner`); `opencode-cli-runner` pasa a ser una implementación que hereda de ella y `gemini-cli-runner` es la otra, cada una con lo específico (comando, argumentos, timeout y propiedades). Se reutilizan `usage-poller`, `usage-history`, `usage-snapshot-publisher`, `usage-window` y `countdown-evaluator`; se amplía el resultado de lectura con un estado "consulta fallida" visible y el modelo de porcentaje pasa a admitir un decimal.

## Supuestos sobre el sistema actual

- `UsageWindowReading` guarda hoy el porcentaje como entero (confirmado en código); REQ-004 exige mostrar, p. ej., 1,8 %, por lo que se amplía (DEC-004).
- `UsageReading` ya soporta lecturas compuestas por series (`seriesReadings`, usadas por OpenCode para modelos y total); se asume reutilizable para representar un grupo de Gemini por serie.
- `UsageWindowType` tiene `FIVE_HOUR` y `WEEKLY` (confirmado); se asume que la vista sabe formatear ambos.
- `UsageReadResult` tiene los estados `DATOS`, `FALLO_TRANSITORIO` y `SESION_CADUCADA` (confirmado); se añade un cuarto.
- `UsageSource.getPollInterval` decide la configuración por defecto según el nombre del proveedor (`opencode` o el resto); Gemini debe sobrescribirlo, igual que OpenAI. El tiempo máximo por defecto es de 5 s y también se sobrescribe.
- Confirmado en `OpenCodeCliRunner.java`: hoy es una clase `final` y `@Component`, con constructores públicos usados por pruebas, que junta en un solo fichero (a) la mecánica común (ProcessBuilder con stderr descartado, cierre de stdin, lectura de stdout en un hilo daemon con límite de 1 MB, espera con plazo, destrucción del proceso y sus descendientes, envoltorio `cmd.exe /d /c` en Windows, registro de procesos activos para cierre, fallo sin detalle) y (b) lo específico de OpenCode (comando `opencode`, argumentos de `session.stats` y de `debug paths data`, timeout de `ConfigurationProvider.getOpenCodeCommandTimeout`, mensajes de log "de OpenCode"). Su resultado `CliResult` (éxito + texto) y los métodos `run(...)`, `execute(...)` y `runDataPath()` son usados por `opencode-usage-source` y por `openai-credentials-reader` (según el mapa); no se ha abierto el código de esos consumidores.
- El resultado actual no conserva el código de salida (lo reduce a éxito/fallo); la base lo incorporará, sin que lo vea ningún consumidor existente.
- `usage-poller` programa el siguiente ciclo al terminar el anterior (DEC-006 de OpenAI), lo que garantiza ausencia de solapamiento y, con un intervalo mínimo de 30 s, como máximo 2 ejecuciones por minuto.
- `usage-window` ya sabe mostrar un aviso formal de proveedor sin lectura (sesión caducada de OpenAI) y secciones por series (OpenCode); se amplía para el aviso de Gemini.
- `application.yml` (`desktop/app/src/main/resources/application.yml`) no contiene `subscription-viewer.gemini`; el diseño lo crea.
- La aplicación ya se lanza en segundo plano con `background-launcher` y la primera lectura corre en el hilo de fondo del poller, sin bloquear el arranque.
- Entorno monousuario local; `agy` ya tiene la sesión iniciada por el usuario.

## Componentes

| Componente | Responsabilidad | Existente o nuevo |
|---|---|---|
| cli-runner-base | Clase base abstracta `AbstractCliRunner`: lanza el proceso hijo por PATH (con envoltorio `.cmd` en Windows), cierra stdin, descarta stderr, lee stdout en un hilo con límite, aplica el timeout destruyendo el árbol de procesos, registra procesos activos y devuelve salida y código de salida sin detalle técnico. Obtiene de la subclase comando, timeout y propiedades | nuevo |
| opencode-cli-runner | Implementación para `opencode`: aporta comando, argumentos de stats y de ruta de datos, timeout configurable y propiedades propias; hereda de cli-runner-base y conserva su API pública y su fallo silencioso | existente (refactorizado) |
| gemini-cli-runner | Implementación para `agy`: aporta comando `agy`, argumentos `-p`, `/usage`, `--output-format`, `json`, timeout fijo de 60 s y propiedades propias (límite de salida, nombre de hilo); hereda de cli-runner-base | nuevo |
| gemini-usage-source | Implementa `usage-source` para Gemini: pide la ejecución a gemini-cli-runner, interpreta el resultado y lo clasifica (datos o consulta fallida); declara su intervalo y su tiempo máximo | nuevo |
| gemini-usage-parser | Valida `status`, extrae de `command.data.groups` los grupos y cubos válidos (ventana, fracción restante, reinicio), calcula el porcentaje consumido acotado con un decimal y descarta lo no reconocido | nuevo |
| usage-source | Abstracción por proveedor; el resultado clasificado añade "consulta fallida"; intervalo y tiempo máximo por fuente | existente |
| usage-poller | Ciclo por fuente sin solapamiento; publica los fallos de Gemini; el tiempo máximo de Gemini cubre el límite de 60 s del comando | existente |
| usage-history | Un punto por grupo y ventana solo si el valor (con un decimal) difiere; solo en memoria | existente |
| usage-snapshot-publisher | Añade el estado "consulta fallida" conservando el último dato válido y la hora del último éxito; un éxito retira el aviso | existente |
| usage-window | Sección Gemini con un bloque por grupo, ventanas presentes, porcentaje con un decimal, tiempo restante, hora de actualización, gráfico y aviso formal | existente |
| countdown-evaluator | Tiempo restante por ventana desde el instante de reinicio, mínimo 0 | existente |
| configuration-provider | Propiedades bajo `subscription-viewer.gemini` (`usage-poll-interval`), mínimo de 30 s y validación uniforme que falla el arranque identificando la propiedad | existente |

## Flujo

1. Al arrancar, configuration-provider valida `subscription-viewer.gemini.usage-poll-interval`: ausente o vacío da 60 s, inferior a 30 s da 30 s, y no válido o no positivo falla el arranque con un error que nombra la propiedad (REQ-007).
2. usage-poller registra gemini-usage-source con su intervalo y su tiempo máximo propios y lanza la primera lectura en un hilo de fondo; la interfaz y los demás proveedores no esperan (REQ-001, REQ-013, NFR-001).
3. En cada ciclo, gemini-usage-source pide a gemini-cli-runner la consulta de uso. gemini-cli-runner aporta a cli-runner-base el comando `agy`, los argumentos `-p`, `/usage`, `--output-format`, `json` y el límite de 60 s; la base lo localiza por PATH, cierra stdin, descarta stderr, lee stdout en un hilo con límite y, al vencer el plazo, destruye el proceso y sus descendientes (REQ-002, NFR-002, NFR-003).
4. La base devuelve la salida y el código de salida, o indica que no se pudo ejecutar o que venció el tiempo. Cualquier código distinto de cero, ejecutable inexistente o tiempo agotado produce "consulta fallida" (REQ-011).
5. Con código cero, gemini-usage-parser interpreta la salida como JSON. Si no lo es o `status` no es `SUCCESS`, resulta "consulta fallida". Si lo es, toma `command.data.groups` e ignora `response` y todas las demás propiedades (REQ-002).
6. Por cada grupo identificado por `name`, se toman sus `buckets`; se ignoran los cubos con `window` distinta de `weekly` y `5h` y los de `remaining_fraction` ausente o no numérica. Un grupo sin cubos válidos no se incluye. Los grupos de nombre no previsto se incluyen (REQ-003, REQ-012).
7. Por cada cubo válido se calcula el porcentaje consumido como (1 − fracción) × 100 acotado a 0-100 y redondeado a un decimal, y se interpreta `reset_time` como instante UTC; si falta o no es válido, la ventana no lleva reinicio y se muestra sin tiempo restante (REQ-004, REQ-005).
8. Si tras el análisis no queda ningún grupo con cubos válidos, el resultado es "consulta fallida". Si queda alguno, el resultado es datos: una lectura compuesta con una serie por grupo (REQ-011, REQ-012).
9. usage-poller entrega las lecturas a usage-history (un punto por grupo y ventana solo si cambia el valor) y al publisher con la hora de fin de consulta, que retira el aviso y fija la hora de última actualización (REQ-008, REQ-009).
10. usage-window muestra la sección Gemini con un bloque por grupo y, en cada bloque, solo las ventanas presentes (Argon solo semanal), con porcentaje, tiempo restante recalculado con el reloj local con mínimo 0, hora de actualización y gráfico (REQ-005, REQ-006, REQ-012).
11. En caso de "consulta fallida", usage-poller no reintenta de inmediato: el siguiente ciclo se programa al terminar el anterior. El publisher conserva los últimos datos válidos y la hora del último éxito, y usage-window muestra el texto formal exacto de REQ-011, también sin datos previos y sin grupos. Un éxito posterior retira el aviso (REQ-011).
12. Los fallos de Gemini no afectan a la lectura de los demás proveedores, que siguen sus propios ciclos (REQ-013).

## Interfaces entre componentes

| Origen | Destino | Qué se comunica | Protocolo |
|---|---|---|---|
| gemini-cli-runner | CLI agy (externo) | Ejecución de la consulta de uso con salida JSON en texto plano, código de salida y límite de 60 s | stdio |
| opencode-cli-runner | CLI opencode (externo) | Sin cambio funcional: stats y ruta de datos de OpenCode | stdio |
| opencode-cli-runner, gemini-cli-runner | cli-runner-base | Herencia: la subclase aporta comando, argumentos, timeout y propiedades; la base ejecuta el proceso y devuelve salida y código de salida | - |
| gemini-usage-source | gemini-cli-runner | Petición de la consulta de uso y recepción de salida y código de salida | - |
| gemini-usage-source | gemini-usage-parser | Salida del comando y recepción de grupos con ventanas, porcentajes y reinicios, o motivo de invalidez | - |
| usage-poller | gemini-usage-source | Petición de lectura periódica con resultado clasificado, intervalo y tiempo máximo propios | - |
| usage-poller | usage-history | Lecturas nuevas por grupo y ventana | - |
| usage-poller | usage-snapshot-publisher | Resultado clasificado (incluida consulta fallida) y hora de fin de consulta; nunca la salida del comando | - |
| usage-snapshot-publisher | usage-window | Notificación de cambio en el hilo de interfaz | - |
| usage-history, countdown-evaluator | usage-window | Serie temporal y tiempo restante por ventana | - |
| configuration-provider | usage-poller, gemini-usage-source | Intervalo validado con mínimo de 30 s | - |

## Modelo de datos lógico

- Proveedor: servicio de AI (Claude, OpenCode, OpenAI, Gemini). Relaciones: tiene cero o más Grupos o Ventanas; tiene un Estado de lectura.
- Grupo de modelos: agrupación nombrada por el comando (Gemini Models, Claude and GPT models, Gemini 4 Argon u otros). Relaciones: pertenece al Proveedor Gemini; tiene una o más Ventanas.
- Ventana de límite: semanal o de 5 horas. Relaciones: pertenece a un Grupo; tiene una Lectura vigente y una Serie.
- Lectura de uso: porcentaje consumido con un decimal, instante de reinicio opcional y hora de consulta. Relaciones: pertenece a una Ventana.
- Punto de evolución: porcentaje en un instante, solo en memoria. Relaciones: pertenece a la Serie de una Ventana de un Grupo.
- Estado de lectura del proveedor: correcto, sin datos, consulta fallida. Relaciones: pertenece a un Proveedor; referencia la última lectura válida y la hora del último éxito.
- Resultado de ejecución de CLI: éxito o fallo, salida y código de salida; efímero, solo en memoria, nunca publicado. Relaciones: lo produce un ejecutor de CLI y lo consume una fuente.

## Decisiones

### DEC-001 Clase base AbstractCliRunner con una implementación por proveedor

- **Decisión:** La funcionalidad común del ejecutor de CLI se extrae de `opencode-cli-runner` a una clase base abstracta (`cli-runner-base`): proceso hijo por PATH, envoltorio `cmd.exe /d /c` en Windows para `.cmd`/`.bat`, cierre de stdin, descarte de stderr, lectura concurrente de stdout con límite de tamaño, espera con plazo, destrucción del proceso y sus descendientes al vencer, registro de procesos activos para el cierre, y un resultado con salida y código de salida; los fallos se registran solo por categoría. Cada proveedor implementa lo específico: nombre del ejecutable, construcción de los argumentos de cada consulta, timeout y propiedades (límite de salida, nombre del hilo lector, etiqueta de log). `opencode-cli-runner` pasa a ser una implementación (hereda de la base, conserva su API pública y su fallo silencioso) y se añade `gemini-cli-runner` para `agy`, con timeout fijo de 60 s. No hay ejecutor único parametrizable ni un segundo ejecutor copiado.
- **Alternativas descartadas:** Generalizar `opencode-cli-runner` para que sirva también a `agy` (acopla a una clase llamada y configurada para OpenCode, mezcla timeouts y mensajes de dos proveedores y obliga a que Gemini dependa del paquete y la configuración de OpenCode; descartado por el usuario); copiar el código en un ejecutor nuevo sin base común (duplica la gestión de timeouts, procesos hijos y Windows ya resuelta, con riesgo de que diverjan); composición con un único ejecutor genérico recibiendo comando y timeout como parámetros (válido, pero el usuario pide herencia y subclases con propiedades propias); ejecutar por un intérprete de comandos con entrada del usuario (riesgo de inyección y destrucción de procesos más difícil).
- **Cubre:** REQ-002, REQ-011, NFR-001, NFR-002, NFR-003

### DEC-002 Parser propio de `command.data.groups`

- **Decisión:** Un parser dedicado, tolerante, valida `status` igual a `SUCCESS`, usa solo `command.data.groups` y extrae de cada cubo `window`, `remaining_fraction` y `reset_time`; descarta todo lo demás (incluido `response`). Un cubo inválido se omite, un grupo sin cubos válidos se omite, y una respuesta sin ningún grupo válido es error. Lo omitido nunca genera aviso mientras quede algún grupo (REQ-012).
- **Alternativas descartadas:** Interpretar el texto tabulado `response` (frágil ante cambios de formato, descartado por S3); reutilizar el parser de OpenCode (formato y semántica distintos); fallar ante cualquier cubo inválido (contradice REQ-003, REQ-004 y REQ-012).
- **Cubre:** REQ-002, REQ-003, REQ-004, REQ-005, REQ-012

### DEC-003 Grupo como serie con ventanas; tipos de ventana existentes

- **Decisión:** Cada grupo se entrega como una serie dentro de una lectura compuesta, identificada por su nombre, siguiendo el patrón de OpenCode (un modelo = una serie). `weekly` se mapea a la ventana semanal y `5h` a la de 5 horas, reutilizando los tipos de ventana existentes. No hay lista cerrada de grupos: los nombres no previstos se muestran. La vista decide qué ventanas pintar por las presentes, sin inventar ninguna.
- **Alternativas descartadas:** Un proveedor independiente por grupo (triplica fuentes, ejecuciones de `agy` y ciclos, e incumple NFR-002); lista fija de tres grupos (ocultaría grupos nuevos, contra REQ-003); una nueva abstracción de grupo (duplica el soporte de series ya existente).
- **Cubre:** REQ-003, REQ-006, REQ-009, REQ-012

### DEC-004 Un decimal de precisión en el porcentaje

- **Decisión:** El porcentaje consumido se redondea a un decimal (mitad hacia arriba) tras acotarlo a 0-100, y se mantiene con ese decimal en la lectura, en la deduplicación del historial, en el gráfico y en la vista de Gemini (0,9818568229675293 da 1,8 %). El modelo de lectura de porcentaje admite decimales; Claude, OpenCode y OpenAI siguen produciendo y mostrando valores enteros, sin cambio visible. La deduplicación compara el valor ya redondeado.
- **Alternativas descartadas:** Redondear a entero como el resto (mostraría 2 %, no cumple el ejemplo de REQ-004 ni distingue 0,9 % de 1,8 %); mostrar más decimales (ruido, cambia casi cada ciclo y satura el gráfico); modificar la presentación de los demás proveedores (fuera de alcance).
- **Cubre:** REQ-004, REQ-006, REQ-009

### DEC-005 Estado propio "consulta fallida" con aviso visible

- **Decisión:** El resultado de lectura incorpora "consulta fallida", distinto del fallo transitorio silencioso (OpenCode/OpenAI sin credenciales) y de la sesión caducada (texto de OpenAI). Los errores de Gemini (ejecutable inexistente, código distinto de cero, tiempo agotado, salida no JSON, `status` distinto de `SUCCESS`, sin grupos válidos) se clasifican en él sin transportar la salida, la traza ni el código. El publisher lo publica, conserva el último dato y la hora del último éxito, y usage-window traduce el estado al texto formal exacto, también sin datos previos. La clasificación no distingue causas porque el texto es único.
- **Alternativas descartadas:** Reutilizar "fallo transitorio" (oculta el error y no permite el aviso); reutilizar "sesión caducada" (su texto es de OpenAI y no cubre ejecutable inexistente o tiempo agotado); lanzar excepciones (rompe el aislamiento por fuente y podría filtrar la salida); que la vista interprete códigos de salida (acopla la interfaz al proveedor).
- **Cubre:** REQ-011, REQ-013, REQ-008, NFR-003

### DEC-006 Ciclo sin solapamiento y frecuencia acotada por el intervalo mínimo

- **Decisión:** gemini-usage-source declara su intervalo (valor de `subscription-viewer.gemini.usage-poll-interval`) y su tiempo máximo, algo mayor que los 60 s del comando para que venza antes el límite del ejecutor que el del poller. El poller programa el siguiente ciclo al terminar el anterior. Con ello hay como máximo una ejecución simultánea y, al ser el mínimo 30 s, nunca más de 2 por minuto aunque el valor sea `1s`. Tras un error no hay reintento inmediato.
- **Alternativas descartadas:** Intervalo fijo desde el inicio del ciclo anterior (puede solapar si `agy` tarda más de 30 s); un limitador adicional independiente (innecesario con el mínimo de 30 s); sobrescribir el tiempo máximo con el límite exacto de 60 s (el poller podría abortar antes que el ejecutor y dejar el proceso sin destruir limpiamente).
- **Cubre:** REQ-007, REQ-011, NFR-002, NFR-003, NFR-004

### DEC-007 Configuración y validación en configuration-provider

- **Decisión:** La clase de propiedades de `subscription-viewer` añade la subsección `gemini` con `usage-poll-interval`, y `application.yml` incorpora `usage-poll-interval: "60s"`. Se aplica la regla de validación uniforme existente (duración válida y positiva, si no falla el arranque nombrando la propiedad) con un mínimo específico de 30 s y un valor por defecto de 60 s si está ausente o vacío. No hay propiedad para la ruta de `agy` ni para su timeout (60 s fijo de NFR-003, propiedad de gemini-cli-runner).
- **Alternativas descartadas:** Validación propia dentro de la fuente (comportamiento no uniforme, ya corregido en OpenAI); aplicar un valor por defecto ante un valor inválido (lo prohíbe REQ-007); propiedad de ruta del ejecutable (fuera de alcance, S4); timeout configurable (no pedido; el límite es un requisito fijo).
- **Cubre:** REQ-007, REQ-011, NFR-002, NFR-003

### DEC-008 Publicar los fallos de Gemini y mantener el aislamiento

- **Decisión:** A diferencia de OpenCode, cuyos ciclos fallidos se descartan sin publicar, los de Gemini sí se publican (con el estado "consulta fallida") para exigir el aviso. La lógica especial de OpenCode del poller y del publisher no cambia. Cada fuente se ejecuta en su propio hilo con su propio tiempo máximo, por lo que Gemini no retrasa a las demás. Cada implementación del ejecutor mantiene su propio registro de procesos y su propio hilo lector, de modo que un fallo o cierre de `agy` no toca los procesos de `opencode` y viceversa.
- **Alternativas descartadas:** Aplicar el descarte de OpenCode (ocultaría el error); un único hilo compartido (la lectura de Gemini de más de 8 s bloquearía a los demás proveedores y a la interfaz).
- **Cubre:** REQ-011, REQ-013, NFR-001

### DEC-009 Protección de la salida y de los datos del comando

- **Decisión:** La salida de `agy` solo se interpreta en memoria y nunca se registra ni se muestra. De ella solo se conservan nombre de grupo, ventana, fracción y reinicio; `conversation_id`, tokens de uso, descripciones y `response` se descartan al leer. Los fallos se registran solo con categoría (en la base, con la etiqueta que aporte cada implementación), sin salida, argumentos ni código. El estado publicado y la vista solo manejan datos de consumo y la bandera de aviso. La verificación se hace con una salida centinela sobre logs y textos de la vista en todos los estados.
- **Alternativas descartadas:** Registrar la salida para diagnóstico (REQ-011 prohíbe mostrar detalles técnicos y puede contener identificadores de conversación); conservar el JSON completo en memoria (amplía innecesariamente la superficie).
- **Cubre:** REQ-011, REQ-002

### DEC-010 Refactor de OpenCodeCliRunner compatible con sus consumidores

- **Decisión:** La extracción a la base se hace sin cambiar la API pública observable de `opencode-cli-runner` (métodos de stats y de ruta de datos, el tipo de resultado y su semántica de fallo silencioso, constructores usados por pruebas y por Spring). La base añade el código de salida al resultado de forma compatible: los consumidores existentes lo ignoran. El comportamiento de OpenCode (comando, argumentos, timeout configurable de 30 s, límite de 1 MB, validación de la ruta de una sola línea) permanece en la subclase. Se ejecuta antes que Gemini y con las pruebas existentes de OpenCode y OpenAI como red de seguridad; la clase deja de ser `final` solo si la herencia lo exige (la base sí es abstracta).
- **Alternativas descartadas:** Cambiar la API de OpenCode para alinearla con Gemini (obliga a tocar `opencode-usage-source` y `openai-credentials-reader`, sin beneficio para la feature y contra "Cambios en el comportamiento de los demás proveedores", fuera de alcance); dejar `opencode-cli-runner` intacto y que Gemini herede de una copia (reintroduce la duplicación).
- **Cubre:** REQ-013, NFR-001, NFR-002, NFR-003

## Riesgos

- Regresión de OpenCode por el refactor de `opencode-cli-runner`: `opencode-usage-source` ejecuta tres consultas CLI por ciclo (5h, 7d, mes) con descarte silencioso de fallos y dependiendo de timeout, límite de 1 MB y destrucción de procesos. Mitigación: refactor mecánico previo a Gemini, API pública intacta y re-ejecución completa de las pruebas del runner (timeout, salida excesiva, código de salida, Windows, procesos vivos) y de `opencode-usage-source`.
- Regresión de OpenAI: `openai-credentials-reader` depende de `opencode-cli-runner` (consulta de la ruta de datos, `runDataPath`) para localizar las credenciales de opencode; si la ruta falla, OpenAI quedaría sin credenciales o con sesión caducada, de forma poco visible. Mitigación: mantener `runDataPath` y su validación de una línea en la subclase y volver a ejecutar las pruebas de `openai-credentials-reader` y `openai-usage-source`; verificar a mano con OpenAI activo tras el refactor.
- Efectos laterales del cambio de estructura de clases: la clase actual es `final` y `@Component`, con constructores públicos usados en pruebas, y el registro de procesos activos (`liveProcessCount`, `destroyActiveProcesses`) lo usan las pruebas y quizá el cierre ordenado. Mitigación: conservarlos accesibles en la base o la subclase; revisar quién los llama (lifecycle-coordinator no los referencia en el mapa) antes de moverlos.
- Dos beans de ejecutor (`opencode-cli-runner`, `gemini-cli-runner`) con una base común pueden provocar inyección ambigua si algún consumidor pide el tipo base. Mitigación: los consumidores inyectan la implementación concreta, nunca la base.
- Resolución de `agy` por PATH en Windows: el ejecutable puede ser un script `.cmd`/`.bat`. La base ya lo resuelve con `cmd.exe /d /c`, que concatena los argumentos sin entrecomillar; `/usage` y `json` no llevan espacios. Mitigación: probarlo en Windows con la instalación real y no aceptar argumentos de entrada del usuario.
- Destrucción de procesos hijos al vencer los 60 s: `agy` podría lanzar subprocesos. Mitigación: la base destruye el árbol de procesos, como hacía el ejecutor de `opencode`.
- Suposición S1: si `agy` no acepta `--output-format json`, siempre habría aviso. Mitigación: validar el argumento antes de implementar y corregir los requisitos si es necesario.
- Ampliar el porcentaje a decimales toca el modelo compartido (`UsageWindowReading`), `usage-history` y la vista; riesgo de regresión en Claude, OpenCode y OpenAI. Mitigación: pruebas de regresión de los tres y de la deduplicación con valores enteros.
- El estado nuevo de resultado afecta a poller, publisher y vista compartidos. Mitigación: los proveedores existentes no lo producen y se mantienen sus pruebas.
- Un aviso permanente (por ejemplo, `agy` no instalado) genera una ejecución fallida cada intervalo (máx. 2 por minuto): coste bajo y aceptado por NFR-002.
- Un grupo con ventana `5h` y sin `weekly` (o viceversa) mostraría solo una: coherente con REQ-012, sin valores inventados.
- Un nuevo nombre de ventana (distinto de `weekly` y `5h`) se ignora en silencio; una respuesta con solo ventanas desconocidas produce aviso de error por no haber grupos válidos.
- Limitar el consumo (NFR-004): una lectura cada 30-60 s lanza un proceso `agy` por ciclo; se verifica la CPU y el heap con Gemini activo.

## Trazabilidad

| Requisito | Componentes | Decisiones |
|---|---|---|
| REQ-001 | usage-poller, gemini-usage-source | DEC-006, DEC-008 |
| REQ-002 | gemini-usage-source, gemini-usage-parser, gemini-cli-runner, cli-runner-base | DEC-001, DEC-002, DEC-009 |
| REQ-003 | gemini-usage-parser, gemini-usage-source, usage-window | DEC-002, DEC-003 |
| REQ-004 | gemini-usage-parser, usage-history, usage-window | DEC-002, DEC-004 |
| REQ-005 | gemini-usage-parser, countdown-evaluator, usage-window | DEC-002 |
| REQ-006 | usage-window, usage-snapshot-publisher | DEC-003, DEC-004 |
| REQ-007 | configuration-provider, usage-poller, gemini-usage-source | DEC-006, DEC-007 |
| REQ-008 | usage-snapshot-publisher, usage-window | DEC-005 |
| REQ-009 | usage-history, usage-poller, usage-window | DEC-003, DEC-004 |
| REQ-010 | usage-history | DEC-004 |
| REQ-011 | gemini-usage-source, gemini-cli-runner, cli-runner-base, usage-source, usage-poller, usage-snapshot-publisher, usage-window | DEC-001, DEC-005, DEC-006, DEC-007, DEC-008, DEC-009 |
| REQ-012 | gemini-usage-parser, usage-window, usage-snapshot-publisher | DEC-002, DEC-003 |
| REQ-013 | usage-poller, gemini-usage-source, usage-snapshot-publisher, usage-window, opencode-cli-runner, cli-runner-base | DEC-005, DEC-008, DEC-010 |
| NFR-001 | usage-poller, cli-runner-base, gemini-cli-runner, gemini-usage-source, opencode-cli-runner | DEC-001, DEC-008, DEC-010 |
| NFR-002 | usage-poller, configuration-provider, cli-runner-base, gemini-cli-runner, opencode-cli-runner | DEC-001, DEC-006, DEC-007, DEC-010 |
| NFR-003 | cli-runner-base, gemini-cli-runner, opencode-cli-runner, usage-poller, gemini-usage-source | DEC-001, DEC-005, DEC-006, DEC-007, DEC-010 |
| NFR-004 | usage-poller, usage-history, configuration-provider | DEC-006 |

## Contratos afectados

| Protocolo | Contrato | Cambio |
|---|---|---|
| stdio | docs/contracts/stdio/desktop/app/agy-usage.md | Se crea: ejecución de `agy -p "/usage" --output-format json` por PATH desde gemini-cli-runner, salida JSON con `status` y `command.data.groups` (grupos, cubos, ventana, fracción restante, instante de reinicio), código de salida, límite de 60 s y propiedades ignoradas; sin datos reales de conversación en los ejemplos |
| stdio | docs/contracts/stdio/desktop/app/opencode-session-stats.md | Se revisa sin cambio funcional: el origen pasa a ser opencode-cli-runner como implementación de la base común (mecánica de proceso, timeout y salida compartida); debe confirmarse que lo documentado (argumentos, timeout, límite, fallo silencioso) sigue valiendo |
| stdio | docs/contracts/stdio/desktop/app/opencode-data-path.md | Se revisa sin cambio funcional, por la misma razón que el anterior |

## Cambios sugeridos al mapa

- + Módulo: cli-runner-base | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/cli/AbstractCliRunner.java | Base común de ejecución de CLI: proceso, PATH/Windows, stdout concurrente, timeout, salida y código | -
- ~ Módulo: opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java | Implementación de cli-runner-base para `opencode`: stats y ruta de datos, timeout configurable | cli-runner-base, configuration-provider
- + Módulo: gemini-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiCliRunner.java | Implementación de cli-runner-base para `agy` con timeout fijo de 60 s | cli-runner-base
- + Módulo: gemini-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini | Orquesta la consulta de `agy`, clasifica el resultado y declara intervalo y tiempo máximo | usage-source, configuration-provider, gemini-cli-runner, gemini-usage-parser
- + Módulo: gemini-usage-parser | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini | Interpreta command.data.groups en grupos, ventanas, porcentaje consumido y reinicio | -
- ~ Módulo: configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config | Propiedades por proveedor bajo subscription-viewer (claude, opencode, openai, gemini); validación uniforme de intervalos | application-context
- ~ Módulo: usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java | Abstracción por proveedor con resultado clasificado (incluye consulta fallida), intervalo y tiempo máximo | -
- ~ Módulo: usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java | Último estado por proveedor con desactualización, sesión caducada y consulta fallida | -
- ~ Módulo: usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java | Historial en memoria por proveedor, serie y ventana con porcentaje decimal, deduplicado | -
- ~ Módulo: usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java | Ventana con secciones de OpenAI y Gemini (grupos, un decimal, aviso de consulta fallida) | usage-snapshot-publisher, usage-history, countdown-evaluator
- + Contrato: stdio | docs/contracts/stdio/desktop/app/agy-usage.md | gemini-cli-runner -> CLI agy
- + Convención: gemini-poll-interval-property: subscription-viewer.gemini.usage-poll-interval
- + Convención: gemini-poll-interval-default-seconds: 60
- + Convención: gemini-poll-interval-min-seconds: 30
- + Convención: gemini-command-timeout-seconds: 60
- + Convención: gemini-percentage-decimals: 1
- + Convención: cli-runner-extension: nuevo ejecutor de CLI = subclase de AbstractCliRunner que aporta comando, argumentos, timeout y propiedades
