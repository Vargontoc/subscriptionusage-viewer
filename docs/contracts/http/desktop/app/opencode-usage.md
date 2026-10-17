# Contrato: http — consumo de uso de OpenCode Go

<!-- Origen: specs/opencode-usage-monitor (REQ-002, REQ-003, REQ-004, REQ-010, NFR-006, DEC-002, DEC-009) -->

> Contrato observado por `desktop/app` al consumir el endpoint de uso de OpenCode Go. Describe solo lo implementado en `OpenCodeUsageSource` y lo exigido por los requisitos. No se basa en el contenido sin versionar de `docs/contracts/opencode/openapi.json` (descartado: ver Avisos al final de TASK-019).

## Participants

- **opencode-usage-source:** componente `desktop/app` que realiza la consulta autenticada, normaliza la respuesta y clasifica el resultado (ver `OpenCodeUsageSource.java`).
- **API de uso de OpenCode:** servicio externo en `https://opencode.ai` que responde a `GET /zen/go/v1/usage` con el consumo actual de la suscripción Go.
- **configuration-provider:** entrega la URL base (`subscription-viewer.opencode.usage-url`, por defecto `https://opencode.ai/zen/go/v1/usage`) y la API key encapsulada (`OpenCodeApiKey`) cuya representación textual es un valor enmascarado fijo; el valor real solo se obtiene en el punto de construcción de la cabecera.

## Channel

- **Protocolo:** HTTPS (`https`), `GET`, sin redirecciones seguidas por el cliente (`HttpClient.Redirect.NEVER`).
- **Tiempos de espera:** conexión 5 s, petición 10 s (`CONNECT_TIMEOUT`, `REQUEST_TIMEOUT` en `OpenCodeUsageSource`). Expirado el tiempo o cualquier `IOException`/`RuntimeException` del cliente, la fuente devuelve `UsageReadResult.falloTransitorio(null)` y registra `category=fallo-transitorio` (sin código de estado si la petición no llegó a responder).
- **Encoding del cuerpo:** UTF-8 (`HttpResponse.BodyHandlers.ofString()`).
- **Persistencia y cabeceras de depuración:** ninguna. El cliente HTTP no se configura con logger y la fuente nunca registra la petición, las cabeceras ni el cuerpo.
- **Sin clave resuelta:** si `configurationProvider.hasOpenCodeApiKey()` es `false`, la fuente devuelve `UsageReadResult.claveAusente()` sin realizar ninguna petición de red.

## Message 1 — opencode-usage-source → API de OpenCode

### Request

```
GET <usage-url> HTTP/1.1
Authorization: Bearer <YOUR_OPENCODE_API_KEY>
Accept: application/json
```

| Cabecera | Valor | Origen |
|---|---|---|
| `Authorization` | `Bearer <api_key>` (valor real obtenido vía `OpenCodeApiKey.value()` solo en este punto; la clave nunca se registra ni se imprime) | `configuration-provider` (`getOpenCodeApiKey().value()`) |
| `Accept` | `application/json` | constante de la fuente |

- **URL:** valor de la propiedad `subscription-viewer.opencode.usage-url` de `application.yml`. Por defecto `https://opencode.ai/zen/go/v1/usage` (REQ-003).
- **Cuerpo:** ninguno.
- **Redirecciones:** no se siguen; una `3xx` se trata como `fallo-transitorio` (no se distingue de un error de servidor).

### Examples

Petición realizada por la fuente con el marcador de ejemplo (el valor real viaja en `Authorization`, nunca aparece en logs ni en este contrato):

```http
GET /zen/go/v1/usage HTTP/1.1
Host: opencode.ai
Authorization: Bearer <YOUR_OPENCODE_API_KEY>
Accept: application/json
```

## Message 2 — API de OpenCode → opencode-usage-source

### Response (200 OK, variante absoluta)

Cada ventana se entrega con `percent` (número 0–100) y `resetsAt` (ISO-8601). Las ventanas ausentes se omiten sin error. Propiedades desconocidas se ignoran.

```json
{
  "usage": {
    "rolling": {
      "status": "ok",
      "percent": 30,
      "resetsAt": "2026-10-17T20:00:00Z"
    },
    "weekly": {
      "percent": 12,
      "resetsAt": "2026-10-20T00:00:00Z"
    },
    "monthly": {
      "percent": 4,
      "resetsAt": "2026-11-01T00:00:00Z"
    }
  }
}
```

Forma reducida (sin `weekly`, que es opcional) que la fuente procesa sin error (REQ-003, S10):

```json
{
  "usage": {
    "rolling": { "percent": 30, "resetsAt": "2026-10-17T20:00:00Z" },
    "monthly": { "percent": 4,  "resetsAt": "2026-11-01T00:00:00Z" }
  }
}
```

### Response (200 OK, variante relativa)

Cada ventana entrega `usagePercent` (número 0–100) y `resetInSec` (segundos hasta el reinicio). La fuente calcula el instante absoluto de reinicio como `<hora de finalización de la consulta> + resetInSec` (DEC-009). El resto de componentes solo ve porcentaje e instante absoluto.

```json
{
  "usage": {
    "rolling":  { "usagePercent": 30, "resetInSec": 18000 },
    "weekly":   { "usagePercent": 12, "resetInSec": 259200 },
    "monthly":  { "usagePercent": 4,  "resetInSec": 1209600 }
  }
}
```

### Response (respuesta mixta por ventana)

Las dos variantes pueden coexistir en ventanas distintas de la misma respuesta; cada ventana se interpreta según la pareja de campos que contenga (REQ-004).

```json
{
  "usage": {
    "rolling": { "usagePercent": 30, "resetInSec": 18000 },
    "monthly": { "percent": 4,      "resetsAt": "2026-11-01T00:00:00Z" }
  }
}
```

### Campos relevantes

| Campo | Tipo | Significado |
|---|---|---|
| `usage` | objeto (obligatorio) | Contenedor de las ventanas. Si falta o no es objeto, el resultado es `DATOS` con lista de ventanas vacía. |
| `usage.rolling` | objeto (opcional) | Ventana de 5 horas. |
| `usage.weekly` | objeto (opcional) | Ventana semanal. Ausencia admitida sin error (REQ-003). |
| `usage.monthly` | objeto (opcional) | Ventana mensual. |
| `…percent` | número 0–100 (variante absoluta) | Porcentaje consumido; se normaliza con `(int) Math.round(...)`. |
| `…resetsAt` | string ISO-8601 (variante absoluta) | Instante de reinicio. |
| `…usagePercent` | número 0–100 (variante relativa) | Equivalente a `percent`. |
| `…resetInSec` | número ≥ 0 (variante relativa) | Segundos hasta el reinicio; el instante absoluto es `queryFinishedAt + resetInSec`. |
| `…status` | string (`"ok"` o `"rate-limited"`) | Informativo; la fuente no lo consume. |

Cualquier otro campo (incluido `status` o futuras extensiones del esquema, S10) se ignora silenciosamente.

## Message 3 — API de OpenCode → opencode-usage-source (errores)

La fuente clasifica cada respuesta no-200 sin propagar excepciones:

| Código | Acción | Resultado de `UsageReadResult` | `UsageReadResult.Estado` |
|---|---|---|---|
| `200` | Normalizar según sección anterior | `datos(...)` con `UsageReading` (puede tener lista vacía si la respuesta no trae `usage` o todas las ventanas son inválidas) | `DATOS` |
| `401` | Clave inválida o ausente | `claveInvalida()` | `CLAVE_INVALIDA` |
| `403` | Sin suscripción Go activa | `sinSuscripcion()` | `SIN_SUSCRIPCION` |
| `429`, `5xx` y cualquier otro 2xx/3xx/4xx fuera de los anteriores | Tratar como transitorio | `falloTransitorio(null)` | `FALLO_TRANSITORIO` |

Además, sin haber recibido respuesta, se clasifican como `FALLO_TRANSITORIO` (registro sin código de estado):

| Situación | Origen | Resultado |
|---|---|---|
| `IOException` (red, DNS, TLS) | cliente HTTP | `falloTransitorio(null)` |
| `InterruptedException` (timeout de petición) | cliente HTTP | `falloTransitorio(null)`; además se restaura la bandera de interrupción del hilo |
| `RuntimeException` del cliente | cliente HTTP | `falloTransitorio(null)` |
| Cuerpo 200 no parseable como JSON o sin estructura de ventanas válidas | normalizador | `falloTransitorio(null)` |

La fuente **nunca** propaga excepciones al llamador (`usage-poller`) ni registra el mensaje de la excepción del cliente (algunas implementaciones incluyen la URI o las cabeceras en él, DEC-010).

### Registro de fallos

Cada fallo se registra como:

```
OpenCode usage failure category=<categoría> status=<código>
```

Categorías: `clave-invalida` (401), `sin-suscripcion` (403), `fallo-transitorio` (resto). El log **no** contiene el cuerpo, la URI, las cabeceras ni el mensaje de excepción del cliente. La clave centinela, su cabecera `Authorization` ni la URL con la clave incrustada no aparecen en logs (NFR-006).

## Errors and absences (no son excepciones)

| Situación | Resultado observable por `usage-poller` y `usage-window` |
|---|---|
| Sin API key resuelta al arrancar | `CLAVE_AUSENTE`, sin petición de red, sin hora de última consulta. |
| API key presente, respuesta 200 con `usage` ausente o todas las ventanas inválidas | `DATOS` con lista vacía; la fuente lo entrega como `UsageReading` sin ventanas. |
| Ventana individual con `percent`/`usagePercent` no numérico, fuera de `[0, 100]` o `resetsAt`/`resetInSec` inválido | esa ventana se omite; las demás ventanas válidas se procesan con normalidad. |
| 401 | `CLAVE_INVALIDA`; el poller pausa la fuente hasta el siguiente reinicio de la aplicación (S1, S7). |
| 403 | `SIN_SUSCRIPCION`; el poller pausa la fuente hasta el siguiente reinicio de la aplicación (S1, S7). |
| 429 / 5xx / error de red / timeout / JSON inválido | `FALLO_TRANSITORIO`; el publicador conserva el último dato válido marcado como desactualizado; el poller aplica backoff exponencial acotado. |

## Configuration (no es parte del HTTP, pero fija el comportamiento)

- `subscription-viewer.opencode.usage-url` — URL completa del endpoint. Por defecto `https://opencode.ai/zen/go/v1/usage`. Si está en blanco, la aplicación falla al arrancar (`IllegalStateException` en `ConfigurationProvider`).
- `subscription-viewer.opencode.api-key` — clave resuelta por `configuration-provider` desde `application.yml` (prioridad) o desde la variable de entorno `OPENCODE_API_KEY`. Un valor vacío o igual a `<YOUR_OPENCODE_API_KEY>` cuenta como no definido. Nunca se registra, nunca se imprime.
- `subscription-viewer.opencode.usage-poll-interval` — intervalo base entre lecturas (por defecto 60 s, mínimo efectivo 10 s).
- `subscription-viewer.opencode.backoff-max-interval` — tope del backoff exponencial ante `FALLO_TRANSITORIO` (por defecto 300 s).

## Non-goals

- El contrato no describe la autenticación desde la interfaz: la clave nunca sale de `configuration-provider` y `opencode-usage-source` (DEC-010).
- El contrato no incluye importes en dólares ni ventanas que no sean `rolling`, `weekly` y `monthly` (fuera de alcance de REQ-003).
- El contrato no describe el comportamiento del poller ni del publicador ante cada estado; eso vive en el mapa (`usage-poller`, `usage-snapshot-publisher`).