# Contrato: http — consulta de cuota de OpenAI

<!-- Origen: specs/openai-usage-monitor (REQ-005, REQ-006, REQ-007, REQ-012, NFR-001, DEC-004, DEC-009, DEC-010) -->

## Participants

- **openai-usage-client** (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai/OpenAiUsageClient.java`): realiza una petición `GET` HTTPS con `java.net.http.HttpClient` desde el JDK, con timeout total de 30 s entre conexión y respuesta (NFR-003, DEC-004), clasifica la respuesta en datos, sesión caducada o fallo transitorio, y propaga el resultado como `UsageReadResult`. Nunca registra cabeceras, cuerpo ni excepciones (DEC-011).
- **API de cuota de OpenAI** (`https://chatgpt.com/backend-api/wham/usage`): sistema externo. Devuelve el porcentaje consumido por ventana y, opcionalmente, el `plan_type`.

## Channel

- **Método y URL:** `GET https://chatgpt.com/backend-api/wham/usage` (constante `USAGE_URI` en el cliente).
- **Cabeceras obligatorias:**

| Cabecera | Valor |
|---|---|
| `Authorization` | `Bearer <access_token>` |
| `ChatGPT-Account-Id` | `<account_id>` |
| `Accept` | `application/json` |

- **Cuerpo de petición:** ausente (la petición no envía cuerpo).
- **Timeout:** el cliente fija `HttpRequest.timeout(30s)` y `HttpClient.connectTimeout(30s)` (constante `REQUEST_TIMEOUT = Duration.ofSeconds(30)`, NFR-003). Si el plazo expira antes de obtener respuesta, el resultado es **fallo transitorio** (REQ-013), nunca sesión caducada.
- **Hilo:** la petición se lanza con `sendAsync(...).orTimeout(30s, MILLIS)`, por lo que la red no se ejecuta en el hilo de interfaz (NFR-002).

## Message 1 — `openai-usage-client` → API de cuota (petición)

### Ejemplo de petición

```
GET /backend-api/wham/usage HTTP/1.1
Host: chatgpt.com
Authorization: Bearer <access_token>
ChatGPT-Account-Id: <account_id>
Accept: application/json
```

Los valores reales de `Authorization` y de `ChatGPT-Account-Id` se construyen únicamente dentro del cliente, en el momento de ensamblar la cabecera, desde el tipo secreto `OpenAiCredentials` (DEC-011, TASK-012). Los ejemplos de este contrato usan siempre marcadores como `<access_token>` y `<account_id>`.

## Message 2 — API de cuota → `openai-usage-client` (respuesta 200 válida)

El cliente considera **válida** la respuesta cuyo cuerpo es un objeto JSON que contiene la clave `rate_limit` con un objeto. La estructura relevante es:

```json
{
  "plan_type": "plus",
  "rate_limit": {
    "primary_window": {
      "used_percent": 6,
      "reset_at": 1792344925
    },
    "secondary_window": {
      "used_percent": 1,
      "limit_window_seconds": 604800,
      "reset_at": 1792947125
    }
  }
}
```

Campos interpretados:

| Campo (en `rate_limit.primary_window` y `rate_limit.secondary_window`) | Tipo | Significado |
|---|---|---|
| `used_percent` | número 0–100 | Porcentaje consumido. Se acepta como decimal y se redondea al entero más cercano (`(int) Math.round(...)`). Fuera de `[0, 100]`: la ventana se descarta; si es la primaria, la respuesta entera no es válida. |
| `reset_at` | entero (epoch segundos) | Instante absoluto de reinicio. `> 0` se interpreta con `Instant.ofEpochSecond` (REQ-007). Ausente o no positivo: se intenta el mecanismo alternativo con `reset_after_seconds`. |
| `reset_after_seconds` | entero ≥ 0 (segundos) | Solo si `reset_at` falta. El instante de reinicio resultante es `queriedAt + reset_after_seconds`, donde `queriedAt` es el `Instant.now()` del parseo. |
| `limit_window_seconds` | entero (segundos) | Se ignora: la primaria se presenta siempre como 5 horas y la secundaria como semanal (DEC-009). Valores distintos de `18000` o `604800` no cambian el rol. |
| Otros campos (en cualquier nivel) | — | Se ignoran sin error (REQ-005). |

| Campo (en la raíz) | Tipo | Significado |
|---|---|---|
| `plan_type` | string opcional | Si está presente y no está en blanco, la fuente lo etiqueta como plan (Plus, Pro o el valor literal entregado). Su valor **no** condiciona qué ventanas se extraen (DEC-009). Si falta, no hay indicación de plan y no hay error (REQ-008). |
| `rate_limit` | objeto | Obligatorio para que la respuesta sea válida. Si falta o no es un objeto, la respuesta se trata como no válida (REQ-013). |
| `rate_limit.primary_window` | objeto | Obligatorio. Define la ventana de 5 horas (rol primario). |
| `rate_limit.secondary_window` | objeto | Opcional. Si está ausente o es `null`, solo se entrega la ventana de 5 horas (REQ-006). Si está presente, define la ventana semanal (rol secundario). |

### Ejemplos cubiertos por REQ-006 a REQ-008

**Caso feliz (5 h al 6 %, semanal al 1 %, plan Plus, `reset_at` absoluto):** el de la sección anterior. Resultado: `primary_window` al **6 %** con reinicio en `Instant.ofEpochSecond(1792344925)` y `secondary_window` al **1 %** con reinicio en `Instant.ofEpochSecond(1792947125)`; `planType = "plus"`.

**Caso sin `secondary_window` (solo 5 h):**

```json
{
  "plan_type": "pro",
  "rate_limit": {
    "primary_window": {
      "used_percent": 6,
      "reset_at": 1792344925
    }
  }
}
```

Resultado: ventana de 5 horas al 6 %; semanal ausente sin error; `planType = "pro"`.

**Caso sin `reset_at` y con `reset_after_seconds` relativo (`queriedAt` = `2026-01-18T12:10:00Z`):**

```json
{
  "rate_limit": {
    "primary_window": {
      "used_percent": 6,
      "reset_after_seconds": 3600
    }
  }
}
```

Resultado: ventana de 5 horas al 6 % con reinicio en `2026-01-18T13:10:00Z`.

**Caso sin `plan_type` (la raíz no lo incluye):** se interpreta igual que el caso feliz, pero `planType` queda `null`. No se muestra indicación de plan y no hay error (REQ-008).

**Caso con `limit_window_seconds` inesperado:**

```json
{
  "rate_limit": {
    "primary_window": {
      "used_percent": 6,
      "limit_window_seconds": 12345,
      "reset_at": 1792344925
    },
    "secondary_window": {
      "used_percent": 1,
      "limit_window_seconds": 99999,
      "reset_at": 1792947125
    }
  }
}
```

Resultado: primaria sigue siendo la de 5 horas; secundaria, la semanal (DEC-009).

## Message 3 — códigos HTTP

El cliente traduce cada código a un resultado de `UsageReadResult` (códigos no listados: **fallo transitorio**):

| Código | Resultado | Notas |
|---|---|---|
| `200` con respuesta válida | `datos(reading)` | Lleva las ventanas (5 h y, si la hay, semanal), el `Instant` de fin de consulta y, si la respuesta lo trae, el `plan_type`. |
| `200` con cuerpo no JSON o sin `rate_limit` | `falloTransitorio(null)` | REQ-013: se trata como respuesta no válida. |
| `200` con `primary_window` ausente, no objeto o con `used_percent` inválido | `falloTransitorio(null)` | Sin la ventana primaria no hay lectura. |
| `401` | `sesionCaducada()` | REQ-012. No se reintenta de inmediato; se espera al siguiente ciclo. No se renueva el token. |
| `403` | `sesionCaducada()` | REQ-012. Igual que 401. |
| `429`, `500` u otro código distinto de 200/401/403 | `falloTransitorio(null)` | REQ-013. Si hay dato previo, el publisher lo conserva como desactualizado; sin dato previo, no se muestra sección. |
| Error de red (sin conexión, DNS, timeout, etc.) | `falloTransitorio(null)` | REQ-013, NFR-003. Sin excepción hacia el llamador (DEC-005). |
| Timeout agotado (30 s, NFR-003) | `falloTransitorio(null)` | El cliente destruye la operación y devuelve fallo transitorio. |

El `HttpClient` se construye con `connectTimeout(30s)` y se combina con `HttpRequest.timeout(30s)` y `orTimeout(30s)` sobre el futuro, dando un total de 30 s entre conexión y respuesta. Códigos distintos de los listados (incluido un 404 o un 502) producen `falloTransitorio(null)` (REQ-013).

## Errors and absences (no son excepciones)

El cliente **nunca** propaga excepciones al llamador (DEC-005). Cualquier error se traduce en uno de los tres valores de `UsageReadResult`:

| Situación | Resultado |
|---|---|
| Cuerpo no JSON o JSON sin `rate_limit` | `falloTransitorio(null)` |
| Cuerpo JSON válido pero `primary_window` ausente o `used_percent` ausente / no numérico / fuera de `[0, 100]` | `falloTransitorio(null)` |
| `reset_at` ausente y `reset_after_seconds` ausente / no numérico / negativo | `falloTransitorio(null)` para esa ventana; si es la primaria, la respuesta entera no es válida |
| `secondary_window` ausente o nula | La lectura incluye solo la ventana de 5 horas (sin error) |
| `HttpClient`, `HttpRequest` o `JsonProcessing` lanzan `RuntimeException` | `falloTransitorio(null)` |

Los códigos internos del cliente (categorías `debug`) nunca contienen el valor del token, del identificador de cuenta, ni la cabecera `Authorization` (NFR-001, DEC-011).

## Non-goals

- Este contrato **no** describe cómo la fuente publica el resultado ni cómo la vista lo dibuja (responsabilidad de `openai-usage-source`, `usage-snapshot-publisher` y `usage-window`).
- No cubre la renovación del token: ante 401/403 el sistema no llama a ningún endpoint de OAuth y no escribe en los ficheros de credenciales (REQ-012).
- No cubre la persistencia de las lecturas entre ciclos: `usage-history` solo conserva los puntos en memoria (REQ-011).
