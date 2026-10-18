# Contrato: stdio — invocación de `experimental.session.stats`

<!-- Origen: specs/opencode-usage-monitor-v2 (REQ-002, REQ-003, DEC-001) -->

> Este contrato describe la cara observable del CLI `opencode` desde el punto de vista de `OpenCodeCliRunner` (desktop/app) y del parser `OpenCodeStatsParser`. La implementación interna del CLI es externa al módulo y no se modifica aquí. Cualquier cambio en la invocación o en el esquema de salida debe coordinarse con este contrato.

## Participants

- **opencode-cli-runner** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java`): ejecuta el CLI como proceso hijo del JDK, con argumentos separados, timeout de 30 s, lectura concurrente de stdout y descarte de stderr; devuelve el texto de stdout o un fallo silencioso (DEC-001).
- **CLI `opencode`**: ejecutable localizado por el PATH del sistema (S6). Se invoca con `api experimental.session.stats` y tres parámetros `--param`.
- **opencode-stats-parser** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeStatsParser.java`): interpreta el texto de stdout; extrae de cada entrada de `data.models` los campos requeridos, descarta los modelos cuyo `providerID` no es `opencode-go` ni `opencode` y las propiedades desconocidas, y devuelve un resultado válido (posiblemente vacío) o un fallo (REQ-002, REQ-005).
- **usage-window-ranges** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/UsageWindowRanges.java`): calcula, para cada ventana, el instante inicial y final como Unix epoch en milisegundos; el runner los entrega al CLI como cadenas de dígitos.

## Channel

- **Lanzamiento:** proceso hijo del JDK mediante `ProcessBuilder` con la lista de argumentos separados (sin shell, DEC-001). En Windows el runner antepone `cmd.exe /d /c <comando>` para soportar envoltorios `.cmd`/`.ps1` (DEC-001).
- **stdin del proceso:** se cierra inmediatamente (no se envía nada).
- **stdout del proceso:** texto UTF-8 que contiene el JSON de estadísticas. El runner lo lee en un hilo dedicado (`opencode-cli-stdout`) mientras el proceso corre, con un tope de 1 MiB; si se supera, el resultado es un fallo silencioso (`salida-excesiva`).
- **stderr del proceso:** se descarta (`ProcessBuilder.Redirect.DISCARD`). Nunca aparece en el log del runner.
- **Timeout:** el runner espera hasta el plazo configurado (30 s por defecto, NFR-006). Al vencerse, destruye el proceso y sus descendientes y devuelve un fallo silencioso (`timeout`).

## Message 1 — `opencode-cli-runner` → CLI `opencode` (invocación)

El runner construye la siguiente línea de argumentos, en este orden, como elementos separados de la lista (DEC-001, REQ-002):

```
opencode api experimental.session.stats --param from=<fromUnixMillis> --param to=<toUnixMillis> --param tools=none
```

Argumentos y parámetros:

| Posición | Argumento | Significado |
|---|---|---|
| 1 | `api` | Subcomando del CLI. |
| 2 | `experimental.session.stats` | Operación solicitada; devuelve estadísticas de la sesión en el rango indicado. |
| 3 | `--param` | Marca que indica el siguiente argumento como par `clave=valor`. |
| 4 | `from=<fromUnixMillis>` | Instante inicial del rango como timestamp Unix en milisegundos. Ejemplo: `from=1792368000000`. |
| 5 | `--param` | Marca, igual que la posición 3. |
| 6 | `to=<toUnixMillis>` | Instante final del rango como timestamp Unix en milisegundos. Ejemplo: `to=1792454400000`. |
| 7 | `--param` | Marca, igual que la posición 3. |
| 8 | `tools=none` | Pide que la consulta no incluya el desglose por herramientas; valor fijo, siempre literal `none` (REQ-002). |

Notas:

- En Windows el ejecutable puede ser un `.cmd` o `.ps1`. En ese caso el runner antepone `cmd.exe /d /c opencode` y concatena los ocho argumentos anteriores sin alterarlos (DEC-001, TASK-006).
- Los valores `fromUnixMillis` y `toUnixMillis` son cadenas de dígitos decimales sin signo que representan el instante en milisegundos desde la época Unix (1970-01-01T00:00:00Z). Se entregan como argumentos separados, **nunca** concatenados en una cadena de shell, para evitar reinterpretación (S6, DEC-001). Los calcula `UsageWindowRanges.Range.fromUnixMillis()`/`toUnixMillis()` para cada ventana (5 h, 7 días, mes).

### Ejemplo real

Para la ventana de 5 horas que termina a las 14:00:00Z del 17 de octubre de 2026 (`to=1792368000000`) y empieza 5 horas antes (`from=1792350000000`), el runner invoca:

```
cmd.exe /d /c opencode api experimental.session.stats --param from=1792350000000 --param to=1792368000000 --param tools=none
```

en Windows, y la misma lista sin `cmd.exe` en otros sistemas.

## Message 2 — CLI `opencode` → `opencode-cli-runner` (salida en stdout)

La salida válida es un único objeto JSON en texto plano, con codificación UTF-8, escrito íntegramente en stdout. El esquema obligatorio es:

```json
{
  "data": {
    "models": [
      {
        "model": {
          "id": "MiniMax-M3",
          "providerID": "opencode-go",
          "variant": "default"
        },
        "steps": 618,
        "tokens": {
          "input": 1234,
          "output": 567,
          "reasoning": 0,
          "cache": {
            "read": 0,
            "write": 12
          }
        },
        "cost": 11.18
      }
    ]
  }
}
```

### Estructura de `data.models`

La raíz del JSON es un objeto que contiene, entre otras posibles claves, `data`. `data` es un objeto que contiene, entre otras posibles claves, `models`. `models` es una lista (puede estar vacía). El runner **no** impone un orden, pero el parser conserva el orden en que aparecen los elementos filtrados.

Cada elemento de `models` es un objeto con dos partes: el subobjeto `model` (identidad del modelo) y los campos de uso (`steps`, `tokens`, `cost`).

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `model` | objeto | sí | Identidad del modelo y de su proveedor. |
| `model.id` | string no vacío | sí | Identificador del modelo dentro del proveedor. |
| `model.providerID` | string no vacío | sí | Proveedor al que pertenece el modelo. Para ser contabilizado por el parser debe valer exactamente `opencode-go` u `opencode` (REQ-005). Los demás proveedores se descartan antes de interpretar el resto del objeto. |
| `model.variant` | string no vacío | no | Variante concreta del modelo. Si está ausente o queda en blanco tras el parseo, el parser usa el literal `default`. |
| `steps` | entero ≥ 0 | sí | Número de pasos de la sesión. Se lee pero no se usa en los cálculos posteriores (S8). Vive en el elemento de `models`, **no** dentro de `model`. |
| `tokens` | objeto | sí | Contadores de tokens consumidos. Vive en el elemento de `models`, **no** dentro de `model`. |
| `tokens.input` | entero ≥ 0 | sí | Tokens de entrada. |
| `tokens.output` | entero ≥ 0 | sí | Tokens de salida. |
| `tokens.reasoning` | entero ≥ 0 | sí | Tokens de razonamiento. |
| `tokens.cache.read` | entero ≥ 0 | sí | Tokens leídos de caché. |
| `tokens.cache.write` | entero ≥ 0 | sí | Tokens escritos en caché. |
| `cost` | número ≥ 0 | sí | Coste acumulado en dólares para esa variante en el rango indicado. Vive en el elemento de `models`, **no** dentro de `model`. |

### Reglas de interpretación (parser)

- Si la raíz no es un objeto JSON, **o** no contiene la clave `data` con un objeto, **o** ese objeto no contiene `models` con una lista, la consulta se considera fallida (sin error visible para el usuario). El texto recibido **no** se conserva en el log.
- Si un elemento de `models` no es un objeto, **o** no contiene la clave `model` que sea un objeto, la consulta se considera fallida.
- Si `providerID` no es exactamente `opencode-go` ni `opencode`, el elemento se descarta y no afecta al resultado.
- Para los elementos restantes, si falta cualquier campo obligatorio, es de tipo incorrecto, está vacío (en el caso de strings), es negativo (en el caso de enteros y `cost`) o no se puede convertir a entero, la consulta se considera fallida.
- Si `model.variant` está ausente o es una cadena en blanco, el parser usa `default` como variante. Este es el único caso en que el parser **no** falla por un campo obligatorio faltante.
- Si tras filtrar la lista queda vacía, el resultado es válido y contiene cero modelos (no es un fallo).
- **Propiedades extra:** cualquier propiedad no listada en la tabla anterior (por ejemplo, en la raíz, en `data`, en un elemento de `models`, en `model`, en `tokens` o en `tokens.cache`) se ignora sin error (REQ-002). El parser usa un mapa dinámico, por lo que añadir campos nuevos en el futuro no rompe el consumo.

### Ejemplo con `providerID` filtrado y `variant` ausente

```json
{
  "data": {
    "models": [
      {
        "model": { "id": "other-model", "providerID": "other-provider" },
        "steps": 10,
        "tokens": { "input": 1, "output": 2, "reasoning": 0, "cache": { "read": 0, "write": 0 } },
        "cost": 0.05
      },
      {
        "model": { "id": "MiniMax-M3", "providerID": "opencode-go" },
        "steps": 618,
        "tokens": { "input": 1234, "output": 567, "reasoning": 0, "cache": { "read": 0, "write": 12 } },
        "cost": 11.18
      },
      {
        "model": { "id": "other-go-model", "providerID": "opencode" },
        "steps": 3,
        "tokens": { "input": 0, "output": 0, "reasoning": 0, "cache": { "read": 0, "write": 0 } },
        "cost": 0.0,
        "extraIgnored": "value"
      }
    ]
  }
}
```

El parser devuelve dos `OpenCodeModelStats`:

- `id=other-go-model`, `providerID=opencode`, `variant=default` (porque `variant` está ausente), `steps=3`, los cinco contadores de tokens y `cost=0.0`.
- `id=MiniMax-M3`, `providerID=opencode-go`, `variant=default` (porque `variant` está ausente), `steps=618`, los cinco contadores de tokens y `cost=11.18`.

La entrada con `providerID=other-provider` se descarta antes de interpretar el resto del objeto. La clave `models[1].extraIgnored` se ignora sin error.

## Errors and absences (fallos silenciosos)

Toda condición adversa se traduce en un fallo silencioso: la consulta no aporta datos, la aplicación sigue en ejecución y **no se muestra ningún mensaje al usuario** (REQ-003, DEC-001). El único rastro es un único registro de log de bajo nivel con una categoría (`debug`), sin argumentos, sin stdout y sin stderr. Los códigos de fallo internos del runner que aplican a este contrato son:

| Situación | Comportamiento del runner / parser |
|---|---|
| Ejecutable `opencode` no encontrado en PATH | El proceso no arranca; el runner devuelve un fallo silencioso (`excepcion`). El parser no se invoca. |
| `opencode` termina con código de salida distinto de 0 | El runner devuelve un fallo silencioso (`codigo-salida`). El parser no se invoca. |
| `opencode` no termina antes del timeout (30 s, NFR-006) | El runner destruye el proceso y sus descendientes y devuelve un fallo silencioso (`timeout`). El parser no se invoca. |
| stdout supera el tope de tamaño del runner (1 MiB) | El runner interrumpe la lectura y devuelve un fallo silencioso (`salida-excesiva`). El parser no se invoca. |
| `opencode` produce stdout que **no** es JSON válido | El parser devuelve un fallo. El texto **no** se registra. |
| stdout es JSON válido pero la raíz no contiene el objeto `data` con la lista `models` | El parser devuelve un fallo. |
| stdout contiene `models` con un elemento que no es un objeto, **o** cuyo subobjeto `model` no es un objeto | El parser devuelve un fallo. |
| Tras filtrar por `providerID`, `models` queda vacía | **No** es un fallo: el parser devuelve un resultado válido sin modelos (REQ-005). |
| `opencode` interrumpe la JVM (hilo interrumpido) | El runner devuelve un fallo silencioso (`interrumpido`) y propaga la marca de interrupción. |
| Se lanza cualquier otra excepción de E/S o de ejecución | El runner captura y devuelve un fallo silencioso (`excepcion`). El texto o la excepción **no** se registran. |

El parser **nunca** escribe los datos de sesión (texto recibido, argumentos, excepción) en el log.

## Configuration (no es parte del stdio, pero fija el comportamiento)

- Nombre del ejecutable: `opencode` (constante del runner; no se configura por ruta, S6).
- Timeout por consulta: configurable en `ConfigurationProvider.getOpenCodeCommandTimeout()` (30 s por defecto, NFR-006).
- Tamaño máximo de stdout aceptado por el runner: 1 MiB (`DEFAULT_MAX_OUTPUT_BYTES`, constante del runner).
- Valores `fromUnixMillis` y `toUnixMillis`: los calcula `UsageWindowRanges.Range.fromUnixMillis()`/`toUnixMillis()` para cada ventana (5 h, 7 días, mes) a partir de los `Instant` de la ventana, y se entregan al runner como cadenas de dígitos decimales.

## Non-goals

- El contrato no describe la implementación interna del CLI `opencode` ni su formato de salida fuera del esquema anterior.
- El contrato no cubre cómo se usan los campos leídos (cálculo de porcentajes, totales, publicación en la UI). Eso pertenece a `OpenCodeUsageCalculator`, `UsageSnapshotPublisher` y `UsageWindow`.
- El contrato no cubre la persistencia ni la planificación (ver contrato `file-json` para el fichero de estado de Claude y los componentes `usage-poller` y `usage-history` para el ciclo).