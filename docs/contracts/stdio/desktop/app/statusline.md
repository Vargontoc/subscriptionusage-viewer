# Contrato: stdio — entrada y salida de la status line

<!-- Origen: specs/claude-usage-monitor (REQ-011, REQ-012) -->

> Este contrato describe la cara observable del hook desde el punto de vista de Claude Code y de la status line original encadenada. La implementación interna del hook es Python 3 con la librería estándar (S9) y vive fuera del módulo `desktop/app`; este módulo no la modifica.

## Participants

- **Claude Code:** cliente que invoca la status line (configurada en `~/.claude/settings.json` bajo la clave `statusLine`).
- **statusline-hook:** script puente ejecutado por Claude Code. Lee `stdin` una sola vez, persiste el estado de límites en el fichero (ver contrato `file-json`) y, opcionalmente, encadena un comando de status line original.
- **original-status-line-command:** comando opcional de status line del usuario (puede no existir). El hook le reenvía exactamente el mismo JSON por `stdin` y devuelve su `stdout` sin modificarlo.

## Channel

- **stdin del hook:** único JSON que Claude Code entrega a la status line en cada ejecución. Se lee una sola vez y se usa para dos fines: persistir el estado y (si hay comando original) reenviarlo.
- **stdout del hook:** línea de texto que Claude Code muestra como status line. Es exactamente la salida del comando original si existe; si no, una línea mínima propia.
- **stderr del hook:** no se usa para datos (los errores son transitorios y silenciosos).
- **Encoding:** texto (UTF-8 implícito en Windows con `python`).

## Message 1 — Claude Code → hook (stdin)

El hook recibe un único objeto JSON por `stdin`, una vez por ejecución. Su raíz contiene, entre otros posibles campos, una clave `rate_limits` cuando Claude Code la entrega (REQ-011, S1). Solo importan `rate_limits`:

```json
{
  "rate_limits": {
    "five_hour": { "used_percentage": 7, "resets_at": 1792186200 },
    "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
  }
}
```

Campos relevantes (los demás se ignoran: rutas, `session_id`, nombre de repositorio, costes, REQ-011):

| Campo | Tipo | Significado |
|---|---|---|
| `rate_limits.five_hour.used_percentage` | número 0–100 | Porcentaje consumido de la sesión de 5 horas. |
| `rate_limits.five_hour.resets_at` | entero (epoch segundos) | Instante de reinicio de la sesión. |
| `rate_limits.seven_day.used_percentage` | número 0–100 | Porcentaje consumido semanal. |
| `rate_limits.seven_day.resets_at` | entero (epoch segundos) | Instante de reinicio semanal. |

Si el JSON no contiene `rate_limits`, el hook **no** sobrescribe el fichero de estado (REQ-011) y continúa hacia el encadenado con la misma semántica.

## Message 2 — hook → original-status-line-command (stdio encadenado, opcional)

Si hay comando original configurado, el hook se comporta como un pipe:

1. Lee `stdin` una sola vez (el JSON de Claude Code).
2. Lo escribe tal cual por `stdin` del subproceso.
3. Espera a que termine.
4. Devuelve por su propio `stdout` lo que el subproceso escribió por su `stdout`, **sin modificaciones** (REQ-012).

El subproceso no comparte estado con el hook más allá del JSON reenviado.

## Message 3 — hook → Claude Code (stdout, línea de status line)

El `stdout` que Claude Code muestra al usuario es:

- **Con comando original configurado:** exactamente lo que el comando original imprimió por su `stdout`, con sus mismos finales de línea y codificación.
- **Sin comando original configurado:** una línea mínima propia con los porcentajes de ambas ventanas (`REQ-012`, último criterio). Ejemplo:

  ```
  five_hour: 7%  seven_day: 11%
  ```

  (El formato exacto de la línea mínima es responsabilidad del hook; el contrato fija que existe una línea y que contiene los porcentajes de `five_hour` y `seven_day`.)

## Errors and absences (no son salida visible)

El hook termina sin error visible para Claude Code en todos estos casos (REQ-011, REQ-012, NFR-002):

| Situación | Comportamiento |
|---|---|
| `stdin` vacío o no parseable como JSON | No se escribe el fichero; continúa con el encadenado si lo hay. |
| `stdin` sin `rate_limits` | No se sobrescribe el fichero. El encadenado sigue funcionando. |
| Escritura del fichero de estado fallida de forma transitoria (p. ej. `PermissionError` en Windows por el lector con el fichero abierto) | Reintento un número acotado de veces; si persiste, termina en silencio. |
| Comando original falla o no responde (ruta inexistente, código de salida distinto de 0, etc.) | El hook no queda bloqueado y el fichero de estado se actualiza igualmente; la status line queda con la línea mínima propia. |
| Sin comando original configurado | Se imprime la línea mínima con los porcentajes. |
| Falla la escritura del fichero | La salida del comando original se devuelve con normalidad (las dos operaciones son independientes, REQ-012). |

El hook **nunca** propaga `stacktrace` ni mensajes a `stdout` que no sea la línea de status line definitiva; **nunca** escribe credenciales ni datos no relacionados con `rate_limits` en el fichero de estado (NFR-002).

## Configuration (no es parte del stdio, pero fija el comportamiento)

- El hook se invoca desde `~/.claude/settings.json` con la clave `statusLine`, apuntando a `python <ruta-absoluta-al-hook>` (S10).
- El hook conoce por su propia configuración: la ruta del fichero de estado, el comando de status line original a encadenar (si lo hay) y los parámetros de reintento. Estos parámetros no entran por `stdin`; se documentan en el repositorio del hook.

## Non-goals

- El contrato no describe la implementación interna del hook (lenguaje, organización del código, formato de sus argumentos). Solo describe el comportamiento observable en `stdin`/`stdout` que Claude Code y la status line original deben respetar.
- El contrato no cubre la persistencia: eso es el contrato `file-json`.
