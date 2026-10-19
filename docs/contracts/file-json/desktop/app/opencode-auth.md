# Contrato: file-json — `auth.json` de opencode (etiqueta `openai`)

<!-- Origen: specs/openai-usage-monitor (REQ-003, NFR-001, DEC-002) -->

## Participants

- **Writer:** CLI de opencode (proceso externo; no forma parte de este módulo).
- **Reader:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai/OpenAiCredentialsReader.java`, en la ruta de fallback. Solo se consulta **si** no existe `.codex/auth.json` y la sección `subscription-viewer.opencode` está configurada (DEC-002). La ruta del fichero se obtiene previamente invocando `opencode debug paths data` (ver contrato `stdio/desktop/app/opencode-data-path.md`).
- **Medium:** un único fichero JSON local en el sistema de ficheros.

## Channel

- **Default location:** `<directorio-de-datos-de-opencode>/auth.json`, donde `<directorio-de-datos-de-opencode>` es la ruta que devuelve `opencode debug paths data` (descrito por el contrato `stdio/desktop/app/opencode-data-path.md`). En sistemas sin la sección `subscription-viewer.opencode` o cuando esa invocación falla, **no** se accede a este fichero.
- **Encoding:** UTF-8. Se abre con `Files.newInputStream(file)`.
- **Modo de apertura:** solo lectura (NFR-001). El reader no escribe ni copia el fichero.

## Precondición de uso

Esta fuente es de fallback. El reader solo intenta leer este fichero cuando se cumplen las dos condiciones (DEC-002, REQ-002):

1. `.codex/auth.json` no existe en el directorio de usuario.
2. La configuración efectiva contiene la sección `subscription-viewer.opencode` (`configurationProvider.isOpenCodeConfigured()`).

Si falta alguna, el reader devuelve `OpenAiCredentialsReadResult(false, null)` sin ejecutar `opencode` y sin abrir `auth.json`.

## Format

El reader recorre el documento con un `JsonParser` en streaming y **solo** extrae el contenido de la clave de primer nivel `openai`, concretamente `access` y `accountId`. El resto del documento (incluidas las demás etiquetas de proveedor y todas las claves dentro de ellas) se descartan con `parser.skipChildren()` y nunca se conservan en memoria (REQ-003, DEC-011).

### Forma canónica con la etiqueta `openai` presente

```json
{
  "openai": {
    "access": "<access_token>",
    "accountId": "<account_id>"
  }
}
```

Campos leídos:

| Ruta JSON | Tipo | Significado |
|---|---|---|
| `openai.access` | string no vacío | Token de acceso que se envía en la cabecera `Authorization: Bearer ...`. |
| `openai.accountId` | string no vacío | Identificador de cuenta que se envía en la cabecera `ChatGPT-Account-Id`. |

### Forma con varias etiquetas de proveedor

El reader admite la presencia de otras etiquetas además de `openai`; las ignora sin error (REQ-003, DEC-011):

```json
{
  "anthropic": {
    "access": "<other_token>",
    "accountId": "<other_id>"
  },
  "openai": {
    "access": "<access_token>",
    "accountId": "<account_id>"
  },
  "google": {
    "access": "<other_token>",
    "accountId": "<other_id>"
  }
}
```

Resultado: se devuelven credenciales con `access = "<access_token>"` y `accountId = "<account_id>"`. El resto de etiquetas se descarta al parsear y no se conserva en memoria.

## Messages

- **Writer → file (replace):** el CLI de opencode mantiene el fichero. Esta aplicación no participa en la escritura.
- **Reader → file (snapshot read):** el reader abre el fichero en modo lectura tras obtener la ruta con `opencode debug paths data`. Al igual que con Codex, las credenciales se releen en cada ciclo del planificador; no se cachean (DEC-002).

## Errors and absences (no son excepciones)

El reader nunca propaga excepciones al llamador. Las situaciones siguientes son casos de "no disponibles" y no producen error visible:

| Situación | Resultado observable |
|---|---|
| `opencode debug paths data` falla, devuelve salida vacía, excede el timeout (30 s, NFR-003) o lanza cualquier excepción | `OpenAiCredentialsReadResult(false, null)`. **No** se accede a `auth.json`. |
| `auth.json` no existe o no es un fichero regular (`!Files.isRegularFile(file)`) | `OpenAiCredentialsReadResult(false, null)`. |
| JSON inválido (cuerpo no parseable, raíz no objeto) | `OpenAiCredentialsReadResult(true, null)`. |
| Raíz no contiene la clave `openai` (ausente o de otro tipo) | `OpenAiCredentialsReadResult(true, null)`: no disponibles sin error (REQ-003). |
| `openai` existe pero falta `access` o `accountId`, o están en blanco | `OpenAiCredentialsReadResult(true, null)`. |
| `IOException`, `SecurityException` u otra excepción durante la apertura o el parseo | `OpenAiCredentialsReadResult(true, null)`. |
| Resto de etiquetas (`anthropic`, `google`, etc.) presentes, además o en lugar de `openai` | Si `openai` está completa, se devuelven credenciales con sus valores; el resto de etiquetas se ignora sin error. Si `openai` falta o está incompleta, no disponibles sin error. |

## Concurrency

- El reader no coordina con el writer mediante bloqueos: confía en la semántica del sistema de ficheros y trata cualquier `IOException` como "no disponibles sin error".
- Una escritura concurrente con la lectura puede producir un parseo incompleto; esa condición se considera JSON inválido (no disponibles sin error).

## Non-goals

- El reader no almacena credenciales entre ciclos (DEC-002).
- El reader no ejecuta `opencode` cuando existe `.codex/auth.json` aunque esté incompleto (REQ-002, DEC-002).
- El reader no comprueba la presencia de la sección de OpenCode cuando `.codex/auth.json` está presente; en ese caso se devuelve el resultado de la lectura de Codex sin importar configuración.
- El reader no expone al llamador etiquetas distintas de `openai`. Etiquetas como `anthropic`, `google`, etc. **no** son consultables a través de este código.
- Este contrato **no** describe la invocación de `opencode debug paths data` (ver contrato `stdio/desktop/app/opencode-data-path.md`).
