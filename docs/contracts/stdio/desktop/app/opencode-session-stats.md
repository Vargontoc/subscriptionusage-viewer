# Contrato: stdio — invocación de `experimental.session.stats`

<!-- Origen: specs/opencode-usage-monitor-v2 (REQ-002, REQ-003, DEC-001) -->

> Este contrato describe la cara observable del CLI `opencode` desde el punto de vista de `OpenCodeCliRunner` (desktop/app) y del parser `OpenCodeStatsParser`. La implementación interna del CLI es externa al módulo y no se modifica aquí. Cualquier cambio en la invocación o en el esquema de salida debe coordinarse con este contrato.

## Participants

- **opencode-cli-runner** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java`): ejecuta el CLI como proceso hijo del JDK, con argumentos separados, timeout de 30 s, lectura concurrente de stdout y descarte de stderr; devuelve el texto de stdout o un fallo silencioso (DEC-001).
- **CLI `opencode`**: ejecutable localizado por el PATH del sistema (S6). Se invoca con `api experimental.session.stats` y tres parámetros `--param`.
- **opencode-stats-parser** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeStatsParser.java`): interpreta el texto de stdout; extrae de cada elemento de `models` los campos requeridos, descarta los modelos que no son de `opencode-go` y las propiedades desconocidas, y devuelve un resultado válido (posiblemente vacío) o un fallo (REQ-002, REQ-005).

## Channel

- **Lanzamiento:** proceso hijo del JDK mediante `ProcessBuilder` con la lista de argumentos separados (sin shell, DEC-001). En Windows el runner antepone `cmd.exe /c <comando>` para soportar envoltorios `.cmd`/`.ps1` (DEC-001).
- **stdin del proceso:** se cierra inmediatamente (no se envía nada).
- **stdout del proceso:** texto UTF-8 que contiene el JSON de estadísticas. El runner lo lee en un hilo dedicado (`opencode-cli-stdout`) mientras el proceso corre, con un tope de 1 MiB; si se supera, el resultado es un fallo silencioso (`salida-excesiva`).
- **stderr del proceso:** se descarta (`ProcessBuilder.Redirect.DISCARD`). Nunca aparece en el log del runner.
- **Timeout:** el runner espera hasta el plazo configurado (30 s por defecto, NFR-006). Al vencerse, destruye el proceso y sus descendientes y devuelve un fallo silencioso (`timeout`).

## Message 1 — `opencode-cli-runner` → CLI `opencode` (invocación)

El runner construye la siguiente línea de argumentos, en este orden, como elementos separados de la lista (DEC-001, REQ-002):

```
opencode api experimental.session.stats --param from=<fromIso> --param to=<toIso> --param tools=none
```

Argumentos y parámetros:

| Posición | Argumento | Significado |
|---|---|---|
| 1 | `api` | Subcomando del CLI. |
| 2 | `experimental.session.stats` | Operación solicitada; devuelve estadísticas de la sesión en el rango indicado. |
| 3 | `--param` | Marca que indica el siguiente argumento como par `clave=valor`. |
| 4 | `from=<fromIso>` | Instante inicial del rango en ISO-8601, en UTC). Ejemplo: `2026-10-17T09:00:00Z`. |
| 5 | `--param` | Marca, igual que la posición 3. |
| 6 | `to=<toIso>` | Instante final del rango en ISO-8601, en UTC. Ejemplo: `2026-10-17T14:00:00Z`. |
| 7 | `--param` | Marca, igual que la posición 3. |
| 8 | `tools=none` | Pide que la consulta no incluya el desglose por herramientas; valor fijo, siempre literal `none` (REQ-002). |

Notas:

- En Windows el ejecutable puede ser un `.cmd` o `.ps1`. En ese caso el runner antepone `cmd.exe /c opencode` y concatena los ocho argumentos anteriores sin alterarlos (DEC-001, TASK-006).
- Las fechas ISO-8601 (`:` `+` `T` `Z`) se entregan como argumentos separados, **nunca** concatenadas en una cadena de shell, para evitar reintrepretación (S6, DEC-001).

## Message 2 — CLI `opencode` → `opencode-cli-runner` (salida en stdout)

La salida válida es un único objeto JSON en texto plano, con codificación UTF-8, escrito íntegramente en stdout. El esquema obligatorio es:

```json
{
  "models": [
    {
      "id": "big-pickle",
      "providerID": "opencode-go",
      "variant": "big-pickle",
      "steps": 3,
      "tokens": {
        "input": 1234,
        "output": 567,
        "reasoning": 0,
        "cache": {
          "read": 0,
          "write": 12
        }
      },
      "cost": 0.42
    }
  ]
}
```

### Estructura de `models`

`models` es una lista (puede estar vacía). El runner **no** impone un orden, pero el parser conserva el orden en que aparecen los elementos filtrados.

Cada elemento es un objeto con los siguientes campos:

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `id` | string no vacío | sí | Identificador del modelo dentro del proveedor. |
| `providerID` | string no vacío | sí | Proveedor al que pertenece el modelo. Para ser contabilizado por el parser debe valer exactamente `opencode-go` (REQ-005). Los demás proveedores se descartan antes de interpretar el resto del objeto. |
| `variant` | string no vacío | sí | Variante concreta del modelo (puede coincidir con `id`). |
| `steps` | entero ≥ 0 | sí | Número de pasos de la sesión. Se lee pero no se usa en los cálculos posteriores (S8). |
| `tokens` | objeto | sí | Contadores de tokens consumidos. |
| `tokens.input` | entero ≥ 0 | sí | Tokens de entrada. |
| `tokens.output` | entero ≥ 0 | sí | Tokens de salida. |
| `tokens.reasoning` | entero ≥ 0 | sí | Tokens de razonamiento. |
| `tokens.cache.read` | entero ≥ 0 | sí | Tokens leídos de caché. |
| `tokens.cache.write` | entero ≥ 0 | sí | Tokens escritos en caché. |
| `cost` | número ≥ 0 | sí | Coste acumulado en dólares para esa variante en el rango indicado. |

### Reglas de interpretación (parser)

- Si la raíz no es un objeto JSON, **o** no contiene la clave `models` con una lista, la consulta se considera fallida (sin error visible para el usuario). El texto recibido **no** se conserva en el log.
- Si un elemento de `models` no es un objeto, la consulta se considera fallida.
- Si `providerID` no es exactamente `opencode-go`, el elemento se descarta y no afecta al resultado.
- Para los elementos restantes, si falta cualquier campo obligatorio, es de tipo incorrecto, está vacío (en el caso de strings), es negativo (en el caso de enteros y `cost`) o no se puede convertir a entero, la consulta se considera fallida.
- Si tras filtrar la lista queda vacía, el resultado es válido y contiene cero modelos (no es un fallo).
- **Propiedades extra:** cualquier propiedad no listada en la tabla anterior (por ejemplo, en la raíz, en un elemento de `models`, en `tokens` o en `tokens.cache`) se ignora sin error (REQ-002). El parser usa un mapa dinámico, por lo que añadir campos nuevos en el futuro no rompe el consumo.

### Ejemplo con propiedades extra ignoradas

```json
{
  "models": [
    {
      "id": "big-pickle",
      "providerID": "opencode-go",
      "variant": "big-pickle",
      "steps": 3,
      "tokens": {
        "input": 1234,
        "output": 567,
        "reasoning": 0,
        "cache": { "read": 0, "write": 12 }
      },
      "cost": 0.42,
      "extra": "ignored",
      "tokens": { "nested": "ignored" }
    }
  ],
  "totals": { "ignored": true }
}
```

El parser devuelve un único `OpenCodeModelStats` con `id=big-pickle`, `providerID=opencode-go`, `variant=big-pickle`, `steps=3`, los cinco contadores de tokens y `cost=0.42`. Las claves `models[0].extra`, `models[0].tokens.nested` y `totals` no afectan al resultado.

## Errors and absences (fallos silenciosos)

Toda condición adversa se traduce en un fallo silencioso: la consulta no aporta datos, la aplicación sigue en ejecución y **no se muestra ningún mensaje al usuario** (REQ-003, DEC-001). El único rastro es un único registro de log de bajo nivel con una categoría (`debug`), sin argumentos, sin stdout y sin stderr. Los códigos de fallo internos del runner que aplican a este contrato son:

| Situación | Comportamiento del runner / parser |
|---|---|
| Ejecutable `opencode` no encontrado en PATH | El proceso no arranca; el runner devuelve un fallo silencioso (`excepcion`). El parser no se invoca. |
| `opencode` termina con código de salida distinto de 0 | El runner devuelve un fallo silencioso (`codigo-salida`). El parser no se invoca. |
| `opencode` no termina antes del timeout (30 s, NFR-006) | El runner destruye el proceso y sus descendientes y devuelve un fallo silencioso (`timeout`). El parser no se invoca. |
| stdout supera el tope de tamaño del runner (1 MiB) | El runner interrumpe la lectura y devuelve un fallo silencioso (`salida-excesiva`). El parser no se invoca. |
| `opencode` produce stdout que **no** es JSON válido | El parser devuelve un fallo. El texto **no** se registra. |
| stdout es JSON válido pero la raíz no contiene la lista `models` | El parser devuelve un fallo. |
| stdout contiene `models` con un elemento que no es un objeto | El parser devuelve un fallo. |
| Tras filtrar por `providerID`, `models` queda vacía | **No** es un fallo: el parser devuelve un resultado válido sin modelos (REQ-005). |
| `opencode` interrumpe la JVM (hilo interrumpido) | El runner devuelve un fallo silencioso (`interrumpido`) y propaga la marca de interrupción. |
| Se lanza cualquier otra excepción de E/S o de ejecución | El runner captura y devuelve un fallo silencioso (`excepcion`). El texto o la excepción **no** se registran. |

El parser **nunca** escribe los datos de sesión (texto recibido, argumentos, excepción) en el log.

## Configuration (no es parte del stdio, pero fija el comportamiento)

- Nombre del ejecutable: `opencode` (constante del runner; no se configura por ruta, S6).
- Timeout por consulta: configurable en `ConfigurationProvider.getOpenCodeCommandTimeout()` (30 s por defecto, NFR-006).
- Tamaño máximo de stdout aceptado por el runner: 1 MiB (`DEFAULT_MAX_OUTPUT_BYTES`, constante del runner).
- Valores `fromIso` y `toIso`: los calcula `UsageWindowRanges` para cada ventana (5 h, 7 días, mes) y se entregan al runner como cadenas ISO-8601 en UTC (DEC-003).

## Non-goals

- El contrato no describe la implementación interna del CLI `opencode` ni su formato de salida fuera del esquema anterior.
- El contrato no cubre cómo se usan los campos leídos (cálculo de porcentajes, totales, publicación en la UI). Eso pertenece a `OpenCodeUsageCalculator`, `UsageSnapshotPublisher` y `UsageWindow`.
- El contrato no cubre la persistencia ni la planificación (ver contrato `file-json` para el fichero de estado de Claude y los componentes `usage-poller` y `usage-history` para el ciclo).