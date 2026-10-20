# Contrato: stdio — consulta de uso de Gemini con `agy`

<!-- Origen: specs/gemini-usage-monitor (REQ-002, REQ-011, NFR-003, DEC-001, DEC-002, DEC-009) -->

> Este contrato describe la cara observable del CLI `agy` desde el punto de vista de `gemini-cli-runner` (`GeminiCliRunner`) y del parser `gemini-usage-parser` (`GeminiUsageParser`). La implementación interna de `agy` es externa al módulo y no se modifica aquí. Cualquier cambio en la invocación o en el esquema de salida debe coordinarse con este contrato.
>
> Los ejemplos de este contrato usan datos inventados. No se incluye ningún contenido real de conversación, identificador, descripción ni texto devuelto en el campo `response` (DEC-009, NFR-001).

## Participants

- **gemini-cli-runner** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiCliRunner.java`): ejecuta `agy` como proceso hijo del JDK, con argumentos fijos, límite de 60 s, lectura concurrente de stdout, descarte de stderr y fallo silencioso; devuelve el texto de stdout o un fallo sin detalle técnico (REQ-002, NFR-003, DEC-001). Es una implementación de la base común `cli-runner-base` (`AbstractCliRunner`), de la que hereda la mecánica de proceso, la resolución por PATH, el envoltorio de Windows, el límite de salida y la destrucción del árbol de procesos.
- **CLI `agy`**: ejecutable localizado por el PATH del sistema. Se invoca con los cuatro argumentos fijos `-p`, `/usage`, `--output-format` y `json` (REQ-002).
- **gemini-usage-parser** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiUsageParser.java`): valida el sobre JSON y extrae de `command.data.groups` los grupos, sus cubos y, de cada cubo, la ventana, la fracción restante y el instante de reinicio. Ignora toda propiedad no reconocida y el texto `response` (REQ-002, REQ-003, DEC-002, DEC-009).
- **gemini-usage-source** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiUsageSource.java`): pide la consulta, entrega la salida al parser y clasifica el resultado como datos o como consulta fallida, sin transportar salida, código ni traza (REQ-011, DEC-005).

## Channel

- **Lanzamiento:** proceso hijo del JDK mediante `ProcessBuilder` con la lista de argumentos separados (sin shell, DEC-001). En Windows el runner antepone `cmd.exe /d /c <comando>` para soportar envoltorios `.cmd`/`.bat`; los cuatro argumentos no contienen espacios, por lo que la concatenación no altera su valor (DEC-001).
- **stdin del proceso:** se cierra inmediatamente (no se envía nada).
- **stdout del proceso:** texto UTF-8 que contiene el JSON de la consulta. El runner lo lee en un hilo dedicado (`gemini-cli-stdout`) mientras el proceso corre, con un tope de 1 MiB; si se supera, el resultado es un fallo silencioso (`salida-excesiva`).
- **stderr del proceso:** se descarta (`ProcessBuilder.Redirect.DISCARD`). Nunca aparece en el log.
- **Código de salida:** el runner lo obtiene del proceso y lo conserva en el resultado interno de la ejecución. El código distinto de cero se considera fallo; el código no se propaga al consumidor ni se registra (DEC-009).
- **Timeout:** el runner espera hasta 60 s (NFR-003). Al vencerse, destruye el proceso y sus descendientes y devuelve un fallo silencioso (`timeout`). El límite es fijo y no se configura.

## Message 1 — `gemini-cli-runner` → CLI `agy` (invocación)

El runner construye la siguiente línea de argumentos, en este orden, como elementos separados de la lista, siempre los mismos y sin entrada del usuario (REQ-002, DEC-001):

```
agy -p /usage --output-format json
```

| Posición | Argumento | Significado |
|---|---|---|
| 1 | `-p` | Indica que el siguiente argumento es el texto que se envía a la CLI. |
| 2 | `/usage` | Consulta de uso. Es un literal fijo, no proviene de la entrada del usuario. |
| 3 | `--output-format` | Selecciona el formato de la salida. |
| 4 | `json` | Formato de salida estructurado (supuesto S1, verificado en TASK-001). |

Notas:

- En Windows el ejecutable puede ser un `.cmd` o `.bat`. En ese caso el runner antepone `cmd.exe /d /c agy` y concatena los cuatro argumentos anteriores sin alterarlos (DEC-001):
  ```
  cmd.exe /d /c agy -p /usage --output-format json
  ```
  y la misma lista sin `cmd.exe` en otros sistemas.
- No se pasan parámetros adicionales ni banderas. No hay variantes de la consulta: solo existe `run()`, sin argumentos (DEC-001).

## Message 2 — CLI `agy` → `gemini-cli-runner` (salida en stdout)

La salida válida es un único objeto JSON en texto plano, con codificación UTF-8, escrito íntegramente en stdout y terminado con código de salida `0`. El esquema obligatorio es:

```json
{
  "status": "SUCCESS",
  "command": {
    "name": "usage",
    "data": {
      "groups": [
        {
          "name": "Gemini Models",
          "buckets": [
            {
              "window": "5h",
              "remaining_fraction": 0.9818568229675293,
              "reset_time": "2026-10-20T18:00:00Z"
            },
            {
              "window": "weekly",
              "remaining_fraction": 0.990625,
              "reset_time": "2026-10-26T00:00:00Z"
            }
          ]
        },
        {
          "name": "Claude and GPT models",
          "buckets": [
            {
              "window": "5h",
              "remaining_fraction": 1.0,
              "reset_time": "2026-10-20T18:00:00Z"
            },
            {
              "window": "weekly",
              "remaining_fraction": 0.991,
              "reset_time": "2026-10-26T00:00:00Z"
            }
          ]
        },
        {
          "name": "Gemini 4 Argon",
          "buckets": [
            {
              "window": "weekly",
              "remaining_fraction": 0.85,
              "reset_time": "2026-10-26T00:00:00Z"
            }
          ]
        }
      ]
    }
  },
  "response": "<texto de presentación que el parser no utiliza>"
}
```

### Estructura del sobre

La raíz es un objeto JSON que contiene, entre otras posibles claves, `status` y `command`.

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `status` | string | sí | Resultado de la consulta. Debe valer exactamente `SUCCESS`; cualquier otro valor invalida la respuesta (REQ-002). |
| `command` | objeto | sí | Datos estructurados de la operación. |
| `command.data` | objeto | sí | Contenido de la operación. |
| `command.data.groups` | lista | sí | Grupos de consumo. Puede estar vacía, pero una lista vacía no deja ningún grupo válido. |
| `response` | texto | no | Presentación legible de la consulta. **No se interpreta nunca**: el parser usa solo `command.data.groups` (REQ-002, DEC-002, S3). |

### Estructura de un grupo (`groups[]`)

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `name` | string | sí | Nombre del grupo, tal cual lo devuelve la CLI. No hay lista cerrada: un nombre no previsto se conserva y se muestra (REQ-003). |
| `buckets` | lista | sí | Cubos (ventanas) del grupo. Un grupo sin cubos válidos se omite. |

### Estructura de un cubo (`buckets[]`)

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `window` | string | sí | Ventana del cubo. Solo se interpretan `weekly` y `5h`; cualquier otro valor se ignora sin error (REQ-003). |
| `remaining_fraction` | número | sí | Fracción de cuota **restante**, de 0 a 1. El porcentaje consumido se calcula como `(1 - fracción) × 100`, acotado a 0-100 y redondeado a un decimal (mitad hacia arriba). Ejemplo: `0.9818568229675293` produce `1.8`. Una `1` produce `0`, una `0` produce `100`, una `0.5` produce `50`. Un valor fuera de 0-1 se acota al extremo correspondiente (REQ-004, DEC-004). |
| `reset_time` | string ISO-8601 | no | Instante de reinicio de la ventana. El sufijo `Z` se interpreta como UTC. Si está ausente o no es ISO-8601, la ventana se conserva sin instante de reinicio (sin error), y la vista no muestra tiempo restante para ella (REQ-005). |

### Reglas de interpretación (parser)

- Si la salida no es un JSON válido, o la raíz no es un objeto, el motivo de invalidez es `no-es-json`.
- Si `status` está ausente, no es texto o no vale `SUCCESS`, el motivo de invalidez es `status-no-es-success`.
- Si `command`, `command.data` o `command.data.groups` no existen con el tipo esperado, el motivo de invalidez es `grupos-ausentes`.
- Un elemento de `groups` que no sea un objeto, o sin `name` textual, o sin `buckets` como lista, se omite sin error.
- Un cubo que no sea un objeto, o sin `window` textual, o sin `remaining_fraction` numérico, o con `window` distinta de `weekly` y `5h`, se omite sin error.
- Un grupo sin ningún cubo válido se omite. Si no queda ningún grupo con cubos válidos, el motivo de invalidez es `sin-grupos-validos` y el resultado de la consulta es "consulta fallida".
- **Propiedades extra:** cualquier propiedad no listada en las tablas anteriores (en la raíz, en `command`, en `command.data`, en un grupo o en un cubo) se ignora sin error (REQ-002). Entre ellas se descartan al leer los identificadores de conversación, los contadores de uso y los textos descriptivos que la CLI pueda incluir (DEC-009).
- La salida se interpreta solo en memoria. El parser no escribe la salida en ningún mensaje de log y el motivo de invalidez es siempre una categoría interna, nunca el texto recibido (REQ-011, DEC-009).

### Ejemplo con propiedades ignoradas y sin ventana de 5 horas

```json
{
  "status": "SUCCESS",
  "command": {
    "data": {
      "groups": [
        {
          "name": "Gemini 4 Argon",
          "buckets": [
            { "window": "weekly", "remaining_fraction": 0.85, "reset_time": "2026-10-26T00:00:00Z" }
          ],
          "descriptionIgnored": "texto de ejemplo",
          "usageTokensIgnored": 1234
        },
        {
          "name": "Grupo Nuevo",
          "buckets": [
            { "window": "5h", "remaining_fraction": 0.5, "reset_time": "2026-10-20T18:00:00Z" },
            { "window": "monthly", "remaining_fraction": 0.25 }
          ]
        }
      ],
      "extraIgnored": true
    }
  },
  "response": "<texto de presentación que el parser no utiliza>"
}
```

El parser devuelve dos grupos:

- `Gemini 4 Argon`, con la ventana semanal al 15 % de consumo y reinicio el 26 de octubre de 2026. Las claves `descriptionIgnored` y `usageTokensIgnored` se ignoran.
- `Grupo Nuevo`, con la ventana de 5 horas al 50 % de consumo. El cubo `monthly` se ignora por ventana no reconocida, sin error.

El cubo inválido no invalida el grupo ni los demás grupos: la presencia de al menos un grupo válido hace que el resultado sea una consulta correcta (REQ-012, DEC-002).

## Errors and absences (consulta fallida)

Toda condición adversa se clasifica como **consulta fallida**: la sección Gemini muestra el aviso formal de REQ-011 — "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde." — **sin mostrar detalles técnicos** (salida del comando, trazas ni códigos), se conservan los últimos datos válidos si los hay, se sigue consultando en cada ciclo de refresco y no afecta a los demás proveedores (REQ-011, REQ-013, DEC-005, DEC-008).

El único rastro es un registro de log de nivel `debug` con una categoría, sin argumentos, sin stdout, sin stderr y sin código (DEC-009). Las categorías internas del runner que aplican a este contrato son:

| Situación | Comportamiento |
|---|---|
| Ejecutable `agy` no encontrado en PATH, o no puede iniciarse | El proceso no arranca; el runner devuelve un fallo silencioso (`excepcion`). El parser no se invoca. |
| `agy` termina con código de salida distinto de `0` | El runner devuelve un fallo silencioso (`codigo-salida`), conservando el código solo internamente. El parser no se invoca. |
| `agy` no termina antes del límite de 60 s (NFR-003) | El runner destruye el proceso y sus descendientes y devuelve un fallo silencioso (`timeout`). El parser no se invoca. |
| `agy` produce stdout que supera el tope de 1 MiB | El runner interrumpe la lectura y devuelve un fallo silencioso (`salida-excesiva`). El parser no se invoca. |
| Hilo interrumpido | El runner devuelve un fallo silencioso (`interrumpido`) y propaga la marca de interrupción. |
| Cualquier otra excepción de E/S o de ejecución | El runner captura y devuelve un fallo silencioso (`excepcion`). El texto o la excepción **no** se registran. |
| Salida que no es un JSON válido | El parser devuelve un motivo `no-es-json`. |
| JSON sin `status` igual a `SUCCESS` | El parser devuelve un motivo `status-no-es-success`. |
| JSON sin `command.data.groups` con el tipo esperado | El parser devuelve un motivo `grupos-ausentes`. |
| Respuesta sin ningún grupo con cubos válidos | El parser devuelve un motivo `sin-grupos-validos`. |

El resultado que recibe `gemini-usage-source` es binario: éxito con la salida, o fallo sin salida ni código. El estado "consulta fallida" no distingue causas, porque el texto visible es único (DEC-005).

## Configuration (no es parte del stdio, pero fija el comportamiento)

- Nombre del ejecutable: `agy` (constante del runner; no se configura por ruta, REQ-002).
- Timeout por ejecución: 60 s fijos, propiedad de `gemini-cli-runner`; no hay propiedad configurable (NFR-003, DEC-007).
- Tamaño máximo de stdout aceptado por el runner: 1 MiB (`GeminiCliRunner.DEFAULT_MAX_OUTPUT_BYTES`).
- Variable de entorno `PATH`: la CLI se localiza por `PATH` del proceso. No se configura ruta alternativa.

## Concurrency

- Una ejecución por consulta: `GeminiCliRunner.run()` lanza el comando, lee su stdout hasta que termina y devuelve un único `CliResult`.
- El registro de procesos activos y el hilo lector (`gemini-cli-stdout`) son propios de esta implementación: destruir los procesos de `agy` no afecta a los procesos de `opencode` ni a su registro, y viceversa (DEC-008).
- Cada fuente se ejecuta en su propio hilo con su propio tiempo máximo, de modo que una consulta larga de `agy` no bloquea la interfaz ni a los demás proveedores (NFR-001, DEC-008).

## Non-goals

- El contrato no describe la implementación interna de `agy` ni su formato de salida fuera del esquema anterior.
- El contrato no interpreta el texto `response`, ni los identificadores de conversación, los contadores de uso o los textos descriptivos que la CLI pueda incluir (DEC-002, DEC-009).
- El contrato no describe el cálculo de porcentajes, el historial, la publicación del estado ni la vista de la sección Gemini: pertenecen a `gemini-usage-parser`, `usage-history`, `usage-snapshot-publisher` y `usage-window`.
- El contrato no cubre la planificación de los ciclos ni la validación del intervalo de refresco (`subscription-viewer.gemini.usage-poll-interval`), que pertenecen a `gemini-usage-source` y `configuration-provider`.
- El contrato no cubre los otros ejecutores: `agy` no comparte base de contrato con `opencode` aunque ambos usen la misma mecánica común (`AbstractCliRunner`).