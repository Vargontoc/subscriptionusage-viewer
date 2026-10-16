# Contrato: file-json — estado de límites de Claude

<!-- Origen: specs/claude-usage-monitor (REQ-001, REQ-008, REQ-011) -->

## Participants

- **Writer:** hook de status line de Claude Code (proceso externo; no forma parte de este módulo).
- **Reader:** `desktop/app` — `ClaudeUsageSource` (escrito por el desarrollador según TASK-005) a través de `DefaultStateFileLoader`.
- **Medium:** un único fichero JSON local en el sistema de ficheros, en una ruta configurable por la aplicación.

## Channel

- **Default location:** `~/.claude/statusline-state.json` (en Windows, `%USERPROFILE%\.claude\statusline-state.json`). El `~` se expande por `user.home`.
- **Configuration:** propiedad Spring `app.usage-state-file` (véase `AppProperties` y `application.yml`); si está en blanco, la aplicación falla al arrancar. El lector tolera cualquier ruta apuntada por la configuración.
- **Encoding:** UTF-8.
- **Atomicidad:** el writer escribe a un fichero temporal y lo renombra sobre el destino (decisión DEC-003). El reader abre el fichero con `Files.isRegularFile` + `Files.readString`; no usa bloqueos. El reader nunca observa contenido parcial durante el renombrado atómico.

## Format

El fichero contiene exactamente estas claves de primer nivel y nada más en el camino feliz del hook (REQ-011, sección "Fichero de estado" del requisito). Todas las marcas de tiempo son epoch en segundos (entero).

### Forma canónica (escrita por el hook)

```json
{
  "written_at": 1792175400,
  "five_hour": { "used_percentage": 5, "resets_at": 1792186200 },
  "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
}
```

Campos:

| Campo | Tipo | Significado |
|---|---|---|
| `written_at` | entero (epoch segundos) | Instante en que el hook escribió el fichero. Si falta o no es positivo, el reader usa el `lastModifiedTime` del fichero como fallback observable. |
| `five_hour` | objeto | Ventana de sesión de 5 horas. |
| `seven_day` | objeto | Ventana semanal. |
| `five_hour.used_percentage` | número 0–100 | Porcentaje consumido de la sesión. La aplicación lo entrega como entero a la vista (`(int) Math.round(...)`). |
| `five_hour.resets_at` | entero (epoch segundos) | Instante en que se reinicia la sesión de 5 horas. |
| `seven_day.used_percentage` | número 0–100 | Porcentaje consumido de la semana. |
| `seven_day.resets_at` | entero (epoch segundos) | Instante en que se reinicia la semana. |

`used_percentage` fuera de `[0, 100]` o `resets_at <= 0` se tratan como ausencia para esa ventana (no como error visible).

### Forma alternativa aceptada por el reader

El DTO `ClaudeStateFileDto` admite también el formato que entrega Claude Code al hook (preservado en el reader como compatibilidad, verificado por `ClaudeUsageStateFileIntegrationTest#shouldReadValidHookStateFile`):

```json
{
  "rate_limits": {
    "five_hour": { "used_percentage": 7, "resets_at": 1792186200 },
    "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
  }
}
```

Si existe `rate_limits`, el reader ignora los campos de primer nivel `written_at`, `five_hour` y `seven_day` y exige que ambas ventanas bajo `rate_limits` sean válidas; si una de las dos es inválida, devuelve ausencia para todo el proveedor (no entrega lectura parcial con una sola ventana).

## Messages

- **Writer → file (replace):** el writer sustituye el contenido atómicamente (temporal + renombrado). Si el renombrado falla de forma transitoria, reintenta un número acotado de veces y termina en silencio si sigue fallando (REQ-011). Si el JSON de entrada del hook no trae `rate_limits`, no se sobrescribe el fichero (REQ-011).
- **Reader → file (snapshot read):** el reader lee una instantánea del fichero en cada ciclo. El reader no escribe nunca en el fichero.

## Errors and absences (no son excepciones)

El reader nunca propaga excepciones a la vista. Estos casos se traducen en ausencia silenciosa (REQ-008), no en error visible:

| Situación | Resultado observable |
|---|---|
| Fichero inexistente (no es `RegularFile`) | `Optional.empty()` para Claude |
| Fichero ilegible / `IOException` / `SecurityException` | `Optional.empty()` para Claude |
| JSON con formato inválido (Jackson no puede parsearlo) | `Optional.empty()` para Claude |
| Sin `rate_limits` y sin `five_hour` o `seven_day` válidos a primer nivel | `Optional.empty()` para Claude |
| Una ventana con `used_percentage` ausente, no numérico o fuera de `[0, 100]` | esa ventana se omite; si la otra es válida, se entrega solo esa |
| `resets_at` ausente o `<= 0` | esa ventana se omite |
| `written_at` ausente o no positivo | se usa el `lastModifiedTime` del fichero |
| Tras las ventanas válidas, lista vacía | `Optional.empty()` para Claude |

El reader **nunca** devuelve una lectura parcial con una ventana si se eligió la forma `rate_limits`: en ese modo o vienen las dos o ninguna (`resolveWindows` con `fiveHour == null || sevenDay == null` ⇒ `List.of()`). Esto está verificado por `ClaudeUsageStateFileIntegrationTest#shouldTolerateMixedPartialDocumentAsAbsence`.

## Concurrency

- Varios procesos del hook pueden escribir a la vez (varias sesiones de Claude Code, REQ-011). El límite es por cuenta; el estado final válido es el de la última escritura. No hay coordinación entre writers.
- El reader no compite con el writer mediante bloqueos: confía en la atomicidad del renombrado. Una lectura concurrente con un renombrado en Windows puede lanzar `IOException`, que el reader convierte en `Optional.empty()`.

## Non-goals

- El fichero no contiene credenciales, rutas de proyecto, identificadores de sesión ni costes (REQ-011, NFR-002); el reader ignora cualquier campo extra.
- No se persiste histórico entre ejecuciones de la aplicación (REQ-007): este fichero guarda solo la última lectura de las dos ventanas.
