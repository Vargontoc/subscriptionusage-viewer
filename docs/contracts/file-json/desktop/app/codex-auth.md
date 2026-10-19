# Contrato: file-json — `.codex/auth.json` de Codex

<!-- Origen: specs/openai-usage-monitor (REQ-002, NFR-001, DEC-002, DEC-011) -->

## Participants

- **Writer:** CLI de Codex (proceso externo; no forma parte de este módulo).
- **Reader:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai/OpenAiCredentialsReader.java`. Lee solo los campos `tokens.access_token` y `tokens.account_id`; el resto se descartan al parsear (DEC-011).
- **Medium:** un único fichero JSON local en el sistema de ficheros.

## Channel

- **Default location:** `<user.home>/.codex/auth.json`. En Windows, `%USERPROFILE%\.codex\auth.json`. El reader construye la ruta con `Path.of(System.getProperty("user.home"), ".codex", "auth.json")`.
- **Encoding:** UTF-8. Se abre con `Files.newInputStream(file)`; el reader no crea directorios ni abre ficheros en modo escritura.
- **Modo de apertura:** solo lectura (NFR-001). El reader nunca escribe, copia ni modifica el fichero.

## Format

El reader recorre el documento con un `JsonParser` en streaming y **solo** extrae las dos claves de la tabla siguiente; cualquier otra propiedad (en la raíz, en `tokens` o en cualquier otro nivel) se omite con `parser.skipChildren()` y nunca se conserva en memoria.

### Forma canónica (campos leídos)

```json
{
  "tokens": {
    "access_token": "<access_token>",
    "account_id": "<account_id>"
  }
}
```

Campos leídos:

| Ruta JSON | Tipo | Significado |
|---|---|---|
| `tokens.access_token` | string no vacío | Token de acceso (OAuth) que se envía en la cabecera `Authorization: Bearer ...` (REQ-002, NFR-001). |
| `tokens.account_id` | string no vacío | Identificador de cuenta que se envía en la cabecera `ChatGPT-Account-Id` (REQ-002). |

Propiedades ignoradas explícitamente (DEC-011, REQ-002): `tokens.refresh_token`, `tokens.id_token`, `OPENAI_API_KEY`, `user_id`, `email`, y cualquier otra clave presente en el fichero. Estas se descartan en el momento del parseo; no llegan a la memoria de la aplicación.

### Forma con el resto de campos presente

El reader acepta y descarta correctamente el contenido real que produce Codex, manteniendo solo los dos campos solicitados:

```json
{
  "OPENAI_API_KEY": "<redacted>",
  "id_token": "<redacted>",
  "email": "<redacted>",
  "user_id": "<redacted>",
  "tokens": {
    "id_token": "<redacted>",
    "refresh_token": "<redacted>",
    "access_token": "<access_token>",
    "account_id": "<account_id>"
  }
}
```

El reader produce las credenciales con `access_token` y `account_id` exactamente iguales a los del fichero; el resto de campos queda descartado.

## Messages

- **Writer → file (replace):** el CLI de Codex mantiene el fichero. Esta aplicación no participa en la escritura.
- **Reader → file (snapshot read):** el reader abre el fichero en modo lectura en cada llamada a `read()`. Las credenciales se releen en cada ciclo del planificador, no se cachean entre ciclos (DEC-002). El reader nunca abre el fichero en modo escritura ni en modo copia.

## Errors and absences (no son excepciones)

El reader nunca propaga excepciones al llamador. La mera existencia del fichero activa la fuente prioritaria, de modo que cuando `.codex/auth.json` existe el resultado siempre refleja **únicamente** su contenido (DEC-002): incluso si está incompleto, no se recurre a opencode.

| Situación | Resultado observable |
|---|---|
| Fichero inexistente (`Files.exists` devuelve `false`) | `OpenAiCredentialsReadResult(false, null)`: ausencia. La fuente no realiza ninguna petición a `chatgpt.com` y no se ejecuta `opencode` (REQ-002, REQ-004). |
| Fichero existente pero `primary_window`-equivalente ausente (es decir, falta `tokens` o falta `tokens.access_token` o `tokens.account_id`) | `OpenAiCredentialsReadResult(true, null)`: no disponibles. |
| `tokens.access_token` o `tokens.account_id` con valor en blanco | `OpenAiCredentialsReadResult(true, null)`. |
| JSON inválido (cuerpo no parseable, raíz no es objeto, nivel de anidamiento inesperado) | `OpenAiCredentialsReadResult(true, null)`. |
| `IOException`, `SecurityException` u otra excepción durante la apertura o el parseo | `OpenAiCredentialsReadResult(true, null)` (sin excepción hacia el llamador). |
| Fichero sin los campos buscados pero con tokens extras (`refresh_token`, `id_token`, `OPENAI_API_KEY`, `user_id`, `email`, etc.) | Se devuelven las credenciales solo si ambos campos están; los demás **no** se conservan en memoria. |

`OpenAiCredentialsReadResult.exists` distingue "fichero ausente" (`false`) de "fichero presente pero no utilizable" (`true`). Las credenciales secretas viajan en un tipo `OpenAiCredentials` cuya representación textual es fija (DEC-011, TASK-012).

## Concurrency

- Varios procesos del CLI de Codex pueden escribir a la vez. El reader no bloquea: confía en la semántica del sistema de ficheros y convierte cualquier `IOException` en no disponibles sin error.
- Una lectura concurrente con una escritura del CLI puede dar lugar a un fichero que se cierra a mitad de parseo; el reader trata esa condición como JSON inválido (no disponibles sin error).

## Non-goals

- El reader no almacena credenciales entre ciclos (DEC-002).
- El reader no valida el formato del token ni del identificador de cuenta; cualquier string no vacío es aceptable para el header (la validez real la decide el servidor con su código HTTP).
- El reader no copia el fichero ni lo registra en logs (NFR-001).
- Este contrato **no** cubre la lectura alternativa desde el `auth.json` de opencode (ver contrato `file-json/desktop/app/opencode-auth.md`).
