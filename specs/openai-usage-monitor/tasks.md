# Tareas: openai-usage-monitor

Estado: aprobado

## Resumen

El trabajo se divide en cimientos de configuración (bloques de Claude y OpenAI, validación uniforme de intervalos y presencia de la sección de OpenCode), ampliaciones compartidas (resultado de sesión caducada, publicador, historial, cuenta atrás y planificador por fuente), componentes de OpenAI (credenciales, cliente HTTP y fuente), integración, interfaz, los cuatro contratos y dos NFR transversales (protección de credenciales y recursos).

## Tareas

### [ ] TASK-001 Bloques de configuración de Claude y OpenAI

- **Cubre:** REQ-001, DEC-001
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** -
- **Alcance:** En `desktop/app/src/main/resources/application.yml`, crear los bloques `subscription-viewer.claude` (con `usage-state-file` = `~/.claude/statusline-dump.json` y `usage-poll-interval` = `10s`) y `subscription-viewer.openai` (con `usage-poll-interval` = `"60s"`), retirar `app.usage-state-file` y `app.usage-poll-interval`, enlazar la configuración de Claude bajo el prefijo `subscription-viewer.claude` manteniendo los métodos públicos del proveedor de configuración, y alinear el valor por defecto interno de la ruta de Claude con el del fichero (`statusline-dump.json`). `subscription-viewer.opencode`, `app.name` y `app.tray-icon-path` no cambian.
- **Hecho cuando:** `application.yml` contiene los dos bloques con los valores de REQ-001 y la aplicación lee la ruta y el intervalo de Claude desde `subscription-viewer.claude`.
- **Pruebas automáticas:**
  - Caso feliz: con el `application.yml` entregado, `subscription-viewer.claude` devuelve la ruta `~/.claude/statusline-dump.json` y el intervalo `10s`, y `subscription-viewer.opencode` devuelve sus valores sin cambios.
  - Caso de límite: con `subscription-viewer.claude.usage-state-file` apuntando a otra ruta, la configuración efectiva devuelve esa ruta y no la de por defecto.
  - Caso de límite: `app.usage-state-file` o `app.usage-poll-interval` definidos en la configuración no influyen en el valor efectivo de Claude.
- **Prueba manual:**
  1. Abrir `desktop/app/src/main/resources/application.yml`. Resultado: aparecen `subscription-viewer.claude` (con la ruta y `10s`) y `subscription-viewer.openai` (con `"60s"`); no aparecen `app.usage-state-file` ni `app.usage-poll-interval`.
  2. Cambiar `subscription-viewer.claude.usage-state-file` por otra ruta de prueba, arrancar y comprobar que el proveedor Claude lee el fichero indicado. Resultado: la tarjeta de Claude muestra los datos de ese fichero. Restaurar la ruta original.

### [ ] TASK-002 Validación uniforme de intervalos al arrancar

- **Cubre:** REQ-009, NFR-004, DEC-008
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** TASK-001
- **Alcance:** Validar al construir la configuración los intervalos de Claude, OpenCode y OpenAI con una única regla: formato de duración válido y positivo; si no, el arranque falla con un error de configuración que nombra la propiedad. Para OpenAI, ausente o vacía usa 60 s y un valor inferior a 10 s usa 10 s. OpenCode conserva su mínimo de 10 s y Claude conserva su valor por defecto de 10 s.
- **Hecho cuando:** los criterios de REQ-009 relativos a la validación se cumplen para los tres proveedores y el error de arranque identifica la propiedad afectada.
- **Pruebas automáticas:**
  - Caso feliz: `subscription-viewer.openai.usage-poll-interval` ausente o vacío produce 60 s, y `30s` produce 30 s.
  - Caso de límite: OpenAI con `2s` produce 10 s, y OpenCode con un valor inferior a 10 s produce 10 s.
  - Caso de error: `abc`, `0s` y `-5s` en `subscription-viewer.openai.usage-poll-interval` hacen fallar el arranque con un error que contiene el nombre de la propiedad, sin usar ningún intervalo alternativo.
  - Caso de error: un valor no válido en `subscription-viewer.claude.usage-poll-interval` o en el intervalo de OpenCode hace fallar el arranque con el mismo tipo de error.
- **Prueba manual:**
  1. Poner `usage-poll-interval: "abc"` en `subscription-viewer.openai` y arrancar. Resultado: el arranque falla y el mensaje nombra `subscription-viewer.openai.usage-poll-interval`; la aplicación no queda en ejecución.
  2. Poner `usage-poll-interval: "2s"` en `subscription-viewer.openai` y arrancar. Resultado: la aplicación arranca sin error con un intervalo efectivo de 10 s.

### [ ] TASK-003 Indicador de presencia de la sección de OpenCode

- **Cubre:** REQ-003, DEC-002
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** TASK-001
- **Alcance:** Exponer si la sección `subscription-viewer.opencode` existe en la configuración efectiva, para que las credenciales de OpenAI puedan consultarla. La comprobación no debe basarse en el objeto de propiedades enlazado, que siempre existe. Los valores de OpenCode no cambian.
- **Hecho cuando:** el indicador devuelve sí o no según la presencia real de la sección.
- **Pruebas automáticas:**
  - Caso feliz: con la sección presente en la configuración, el indicador devuelve sí.
  - Caso de límite: sin la sección, el indicador devuelve no aunque el objeto de propiedades de OpenCode exista con sus valores por defecto.
  - Caso de límite: con la sección presente pero sin claves propias, el indicador devuelve sí.
- **Prueba manual:**
  1. no aplica: indicador interno sin interfaz; su efecto se verifica en TASK-015 y TASK-017.

### [ ] TASK-004 Adaptación de claude-usage-source a la nueva configuración

- **Cubre:** REQ-001, DEC-001
- **Componente:** claude-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude
- **Depende de:** TASK-001
- **Alcance:** Actualizar a la nueva ubicación de configuración los mensajes de log, los mensajes de error y las pruebas de la fuente de Claude, sin cambiar la lectura ni la validación del fichero. Comprobar que no queda ninguna referencia activa a `app.usage-state-file` ni a `app.usage-poll-interval` en el código, la configuración de pruebas ni la documentación.
- **Hecho cuando:** la fuente de Claude lee la ruta desde `subscription-viewer.claude` y la búsqueda no encuentra referencias activas a las claves antiguas.
- **Pruebas automáticas:**
  - Caso feliz (regresión): con un fichero de estado válido en la ruta configurada, los valores devueltos son iguales a los de antes del cambio.
  - Caso de límite (regresión): con contenido inválido en el fichero se mantiene la tolerancia actual, sin excepción y sin datos.
  - Caso de búsqueda: en código, pruebas, configuración y documentación no aparecen `app.usage-state-file` ni `app.usage-poll-interval`.
- **Prueba manual:**
  1. Con Claude configurado y un fichero de estado válido, arrancar la aplicación. Resultado: la tarjeta de Claude muestra los mismos porcentajes y tiempos que antes del cambio.

### [ ] TASK-005 Resultado de sesión caducada en usage-source

- **Cubre:** REQ-012, REQ-013, DEC-005
- **Componente:** usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java; desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageReadResult.java
- **Depende de:** -
- **Alcance:** Añadir al resultado de lectura la causa "sesión caducada", distinta de fallo transitorio y de las causas existentes de OpenCode. La ausencia de credenciales de OpenAI se expresa como fallo transitorio sin causa visible. Las excepciones no llegan al llamador y el resultado no transporta contenido de respuesta ni credenciales.
- **Hecho cuando:** "sesión caducada" se puede expresar como resultado distinto de fallo transitorio y de clave inválida, y las fuentes existentes compilan y mantienen sus pruebas.
- **Pruebas automáticas:**
  - Caso feliz: una fuente simulada que devuelve sesión caducada produce ese resultado, distinto de fallo transitorio y de clave inválida.
  - Caso de límite: una fuente simulada que lanza una excepción de red produce fallo transitorio sin excepción hacia el llamador.
  - Regresión: las pruebas de Claude y de OpenCode siguen en verde sin cambiar sus expectativas.
- **Prueba manual:**
  1. no aplica: cambio de abstracción sin comportamiento observable por sí solo; su efecto se verifica en TASK-006 y TASK-019.

### [ ] TASK-006 Publicador: estados de OpenAI y sesión caducada

- **Cubre:** REQ-004, REQ-010, REQ-012, REQ-013, DEC-005, DEC-007
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java; desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageProviderState.java
- **Depende de:** TASK-005
- **Alcance:** Añadir por proveedor el estado "sesión caducada", que conserva el último dato válido, y publicarlo aunque no haya datos previos. Tras un fallo transitorio con dato previo, marcar el dato como desactualizado conservando la hora de la última consulta exitosa. Un fallo transitorio sin datos previos y la ausencia de credenciales no crean ninguna sección visible. Una lectura exitosa limpia la desactualización y el aviso. El comportamiento de Claude y de OpenCode no cambia.
- **Hecho cuando:** los estados de OpenAI se publican según REQ-012 y REQ-013, y la hora publicada es siempre la de la última consulta exitosa.
- **Pruebas automáticas:**
  - Caso feliz: tras una lectura exitosa, el estado es correcto y la hora es la de esa consulta.
  - Caso de error: un 20 % previo seguido de un fallo transitorio deja el 20 % con estado desactualizado y la hora del último éxito; una lectura exitosa posterior limpia el indicador.
  - Caso de error: sesión caducada tras datos previos conserva el último dato y publica el estado de sesión caducada; sin dato previo, la sesión caducada se publica como estado visible.
  - Caso de límite: un fallo transitorio o la ausencia de credenciales sin dato previo no publican sección visible ni hora de consulta.
  - Regresión: los estados de Claude y de OpenCode no cambian, y un fallo de OpenAI no altera el estado de Claude ni el de OpenCode.
  - Seguridad: los estados publicados no contienen ningún valor de credencial.
- **Prueba manual:**
  1. no aplica: estado interno; su efecto visible se comprueba en TASK-019.

### [ ] TASK-007 Historial: deduplicación por ventana para OpenAI

- **Cubre:** REQ-011
- **Componente:** usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java
- **Depende de:** -
- **Alcance:** Para cada ventana de OpenAI (5 horas y semanal), añadir un punto solo si el porcentaje cambia respecto al último punto de esa ventana. El instante de reinicio no forma parte de la comparación en OpenAI (ver Avisos). Sin persistencia y con el tope de puntos por serie vigente. Claude y OpenCode no cambian.
- **Hecho cuando:** dos lecturas iguales no añaden puntos y un cambio añade un punto por cada ventana afectada, en orden temporal.
- **Pruebas automáticas:**
  - Caso feliz: tres lecturas con porcentajes distintos producen tres puntos por ventana en orden temporal, y las dos ventanas son independientes.
  - Caso de límite: dos lecturas con el mismo porcentaje y distinta hora de consulta, o con un instante de reinicio distinto solo por el reloj, producen un único punto por ventana.
  - Regresión: la deduplicación de Claude y de OpenCode sigue igual (pruebas existentes en verde).
- **Prueba manual:**
  1. no aplica: lógica en memoria sin interfaz propia; su efecto visible se comprueba en TASK-020.

### [ ] TASK-008 Cuenta atrás: tiempo mínimo cero para OpenAI

- **Cubre:** REQ-007, DEC-010
- **Componente:** countdown-evaluator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/CountdownEvaluator.java
- **Depende de:** -
- **Alcance:** Calcular para las ventanas de OpenAI el tiempo restante frente al reloj local, con mínimo 0 cuando el instante de reinicio ya ha pasado. Claude y OpenCode mantienen su comportamiento actual, incluida la marca de ventana vencida.
- **Hecho cuando:** un reinicio a 2 horas muestra 2 horas y un reinicio pasado muestra 0 en OpenAI, sin cambios en Claude ni en OpenCode.
- **Pruebas automáticas:**
  - Caso feliz: un reinicio dentro de 2 horas produce un tiempo restante equivalente a 2 horas.
  - Caso de límite: un instante de reinicio ya pasado produce 0 y nunca un valor negativo.
  - Regresión: para OpenCode la ventana vencida se sigue marcando como antes, y para Claude no hay cambios.
- **Prueba manual:**
  1. no aplica: cálculo sin interfaz propia; su efecto visible se comprueba en TASK-018 y TASK-019.

### [ ] TASK-009 Planificador: intervalo, tiempo máximo y reglas de OpenAI por fuente

- **Cubre:** REQ-009, REQ-012, REQ-014, NFR-003, NFR-004, DEC-006, DEC-007
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java
- **Depende de:** TASK-002, TASK-005
- **Alcance:** Cada fuente declara su intervalo y su tiempo máximo de lectura, en lugar de identificar a OpenCode por nombre y aplicar un único tiempo de 5 s. Claude y OpenCode conservan su tiempo máximo actual, su backoff y su pausa ante 401 y 403. Para OpenAI el tiempo máximo es de 60 s, el ciclo se programa al terminar la lectura anterior, no hay backoff ni pausa ante ningún fallo (incluidos 401 y 403, que se reintentan en el siguiente ciclo), y los fallos de OpenAI se entregan al publicador. El descarte de fallos de OpenCode se mantiene.
- **Hecho cuando:** OpenAI se planifica con su intervalo y su tiempo máximo propios sin solapes, y la planificación de Claude y de OpenCode no cambia.
- **Pruebas automáticas:**
  - Caso feliz: con reloj simulado y lecturas inmediatas, las lecturas de OpenAI se inician cada 60 s contados desde el fin de la anterior; con un intervalo de 30 s, cada 30 s.
  - Caso de límite: una lectura de OpenAI que tarda más que el intervalo impide iniciar la siguiente hasta que termine.
  - Caso de límite: una lectura de OpenAI que supera 60 s se aborta como fallo.
  - Caso de error: tras un 401 o un 403 de OpenAI se ejecuta el siguiente ciclo; los fallos de red no provocan backoff y el intervalo se mantiene.
  - Caso de límite (NFR-004): con un intervalo de 10 s no se superan 6 lecturas de OpenAI en un minuto.
  - Regresión: Claude mantiene 10 s sin backoff; OpenCode mantiene 60 s con backoff y pausa ante 401 y 403; un fallo de OpenAI no retrasa ni bloquea las lecturas de Claude ni de OpenCode.
- **Prueba manual:**
  1. no aplica: planificación interna; su efecto visible se comprueba en TASK-017.

### [ ] TASK-010 Parseo de la respuesta de cuota de OpenAI

- **Cubre:** REQ-006, REQ-007, REQ-008, REQ-013, DEC-009, DEC-010
- **Componente:** openai-usage-client | por definir (analista técnico)
- **Depende de:** -
- **Alcance:** Convertir una respuesta de cuota en la ventana de 5 horas (desde la ventana primaria) y la semanal (desde la secundaria, si existe), sin depender de `limit_window_seconds` ni de `plan_type`. El porcentaje se toma tal cual y se redondea al entero más cercano. El instante de reinicio es `reset_at` en epoch segundos o, si falta, la hora de fin de la consulta más `reset_after_seconds`. El plan (`plus` o `pro`) es opcional. Los campos desconocidos se ignoran. Una respuesta no JSON o sin `rate_limit` se clasifica como respuesta no válida. La función no realiza llamadas de red.
- **Hecho cuando:** los ejemplos de REQ-006 a REQ-008 y el caso de respuesta no válida de REQ-013 se resuelven como indica el requisito.
- **Pruebas automáticas:**
  - Caso feliz: primaria al 6 % y secundaria al 1 % con `reset_at` producen 6 % en la ventana de 5 horas y 1 % en la semanal; `plan_type` `plus` se identifica como Plus.
  - Caso de límite: `limit_window_seconds` distinto de 18000 o 604800 mantiene el rol de cada ventana; sin `secondary_window` solo hay ventana de 5 horas; sin `plan_type` las ventanas no cambian.
  - Caso de límite: `reset_at` 1792344925 se interpreta como segundos desde epoch; sin `reset_at` y con `reset_after_seconds` 3600, el instante resultante es la hora de consulta más 3600 s.
  - Caso de error: JSON inválido o respuesta sin `rate_limit` producen respuesta no válida; los campos desconocidos se ignoran sin error.
- **Prueba manual:**
  1. no aplica: parseo sin interfaz propia; su efecto visible se comprueba en TASK-018.

### [ ] TASK-011 Petición autenticada al endpoint de cuota y clasificación de respuestas

- **Cubre:** REQ-005, REQ-012, REQ-013, NFR-001, NFR-003, DEC-004, DEC-005, DEC-011
- **Componente:** openai-usage-client | por definir (analista técnico)
- **Depende de:** TASK-005, TASK-010
- **Alcance:** Hacer la petición `GET` a `https://chatgpt.com/backend-api/wham/usage` con las cabeceras `Authorization: Bearer <access_token>`, `ChatGPT-Account-Id: <account_id>` y `Accept: application/json`, con los valores recibidos, en el hilo de fondo y con un tiempo máximo de 30 s entre conexión y petición. Clasifica el resultado: 200 con respuesta válida produce datos; 401 y 403 producen sesión caducada; cualquier otro código, error de red, tiempo agotado o respuesta no válida produce fallo transitorio. No registra cabeceras, cuerpos ni mensajes de excepción; solo categoría y código de estado.
- **Hecho cuando:** la petición lleva las tres cabeceras con los valores de las credenciales y cada código se clasifica según REQ-012 y REQ-013.
- **Pruebas automáticas:**
  - Caso feliz: con un servidor simulado que responde 200 con datos válidos, la petición incluye las tres cabeceras con los valores recibidos y el resultado son datos.
  - Caso de error: 401 y 403 producen sesión caducada, sin reintento inmediato y sin petición de renovación de token.
  - Caso de error: 500, 429, error de conexión y tiempo agotado producen fallo transitorio sin excepción hacia el llamador.
  - Caso de límite: un servidor que no responde se aborta a los 30 s.
  - Seguridad: con un valor centinela en las credenciales, los logs capturados y los mensajes de error no contienen el token, el identificador de cuenta ni la cabecera `Authorization`.
- **Prueba manual:**
  1. no aplica: cliente sin interfaz propia; su efecto visible se comprueba en TASK-017 y TASK-025.

### [ ] TASK-012 Tipo secreto para las credenciales de OpenAI

- **Cubre:** NFR-001, DEC-011
- **Componente:** openai-credentials-reader | por definir (analista técnico)
- **Depende de:** -
- **Alcance:** Introducir un tipo para el token de acceso y para el identificador de cuenta cuya representación textual sea un valor fijo sin caracteres reales, y que solo entregue el valor real en el punto donde se construyen las cabeceras.
- **Hecho cuando:** ninguna representación textual del tipo contiene el valor, y el valor solo es accesible desde el punto de uso.
- **Pruebas automáticas:**
  - Caso feliz: el valor real se obtiene mediante el acceso previsto para construir las cabeceras.
  - Caso de límite: la representación textual y el volcado de un objeto que contenga el tipo no contienen el valor centinela ni ninguno de sus caracteres.
- **Prueba manual:**
  1. no aplica: tipo interno sin comportamiento observable por sí solo; su efecto se verifica en TASK-025.

### [ ] TASK-013 Lectura de credenciales desde .codex/auth.json

- **Cubre:** REQ-002, NFR-001, DEC-002, DEC-011
- **Componente:** openai-credentials-reader | por definir (analista técnico)
- **Depende de:** TASK-012
- **Alcance:** Localizar `.codex/auth.json` en el directorio de usuario. Si existe, leer solo `tokens.access_token` y `tokens.account_id`, descartando el resto de campos al parsear (en particular `refresh_token`, `id_token`, `OPENAI_API_KEY`, `user_id` y `email`). El fichero se abre solo en lectura. El resultado indica si el fichero existe y, si existe, si contiene ambos campos. Si falta alguno o el JSON no es válido, las credenciales no están disponibles.
- **Hecho cuando:** con `.codex/auth.json` completo se obtienen las credenciales, y con el fichero incompleto o ausente el resultado es el indicado sin error.
- **Pruebas automáticas:**
  - Caso feliz: un fichero con ambos campos produce las credenciales con esos valores.
  - Caso de límite: un fichero con solo uno de los dos campos produce no disponible, y los campos extra no se conservan en memoria.
  - Caso de límite: si el fichero no existe, el resultado indica ausencia sin error.
  - Caso de error (propuesto): un fichero con JSON inválido produce no disponible sin error ni excepción. Avisos: REQ-002 no define este caso.
  - Seguridad: el fichero no se modifica ni se copia; su contenido y su fecha de modificación son iguales antes y después de la lectura.
- **Prueba manual:**
  1. no aplica: lectura interna; su efecto visible se comprueba en TASK-017 y TASK-025.

### [ ] TASK-014 Consulta de la ruta de datos de opencode en el ejecutor de CLI

- **Cubre:** REQ-003, NFR-003, DEC-003
- **Componente:** opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java
- **Depende de:** -
- **Alcance:** Generalizar el ejecutor para ejecutar también `opencode debug paths data` con un tiempo máximo de 30 s, destrucción del proceso al vencer y fallo silencioso. De la salida se toma la ruta del directorio de datos. El ejecutor sigue atendiendo `session.stats` sin cambios.
- **Hecho cuando:** la consulta devuelve la ruta cuando el comando funciona y "no disponible" en los fallos, y `session.stats` se comporta igual que antes.
- **Pruebas automáticas:**
  - Caso feliz: con un comando simulado que imprime una ruta, la consulta devuelve esa ruta.
  - Caso de error: un comando inexistente o que termina con código de error produce no disponible sin excepción.
  - Caso de límite: un comando que no termina se finaliza al vencer el tiempo de 30 s y se devuelve no disponible.
  - Caso de límite (propuesto): una salida vacía o sin ruta produce no disponible. Avisos: REQ-003 no define este caso.
  - Regresión: la consulta `session.stats` de OpenCode sigue en verde.
- **Prueba manual:**
  1. no aplica: ejecutor interno sin interfaz propia; su efecto se verifica en TASK-017.

### [ ] TASK-015 Orden de fuentes de credenciales de OpenAI

- **Cubre:** REQ-002, REQ-003, REQ-004, NFR-003, DEC-002
- **Componente:** openai-credentials-reader | por definir (analista técnico)
- **Depende de:** TASK-003, TASK-013, TASK-014
- **Alcance:** En cada ciclo, obtener las credenciales aplicando el orden fijo. Si `.codex/auth.json` existe, es la única fuente aunque esté incompleto, y opencode no se ejecuta. Si no existe y la sección `subscription-viewer.opencode` existe, pedir la ruta de datos de opencode, leer su `auth.json` y tomar `openai.access` y `openai.accountId`, ignorando otras etiquetas. Sin etiqueta `openai`, o si el comando falla o excede 30 s, las credenciales no están disponibles sin error. Sin sección de OpenCode no se ejecuta `opencode`. No se almacenan credenciales entre ciclos.
- **Hecho cuando:** los criterios de REQ-002 y REQ-003 se cumplen y las credenciales se releen en cada ciclo.
- **Pruebas automáticas:**
  - Caso feliz: sin `.codex/auth.json`, con la sección de OpenCode y un comando correcto, un `auth.json` de opencode con `openai` produce esas credenciales.
  - Caso de límite: con `.codex/auth.json` y credenciales de opencode distintas, se usan solo las de Codex y no se ejecuta el comando.
  - Caso de límite: `.codex/auth.json` existe pero incompleto produce no disponible y no ejecuta opencode.
  - Caso de límite: sin sección de OpenCode y sin Codex, no se ejecuta opencode y las credenciales no están disponibles.
  - Caso de error: un comando no instalado o con fallo produce no disponible sin error; un `auth.json` sin etiqueta `openai` produce no disponible sin error; las demás etiquetas se ignoran.
  - Caso de límite: un cambio de token en disco entre dos ciclos se refleja en el siguiente.
- **Prueba manual:**
  1. no aplica: lectura interna; su efecto visible se comprueba en TASK-017 y TASK-025.

### [ ] TASK-016 Fuente de uso de OpenAI

- **Cubre:** REQ-004, REQ-005, REQ-009, REQ-012, REQ-013, REQ-014, NFR-003, DEC-005, DEC-006
- **Componente:** openai-usage-source | por definir (analista técnico)
- **Depende de:** TASK-002, TASK-005, TASK-011, TASK-015
- **Alcance:** Implementar la fuente de OpenAI sobre la abstracción de usage-source. En cada ciclo pide las credenciales; sin credenciales devuelve fallo transitorio sin causa y no hace ninguna petición; con credenciales llama al cliente y devuelve el resultado clasificado (datos, sesión caducada o fallo transitorio). Declara su intervalo, el validado en configuración con 60 s por defecto, y su tiempo máximo de 60 s. No lanza excepciones hacia el planificador y no registra valores sensibles.
- **Hecho cuando:** sin credenciales no hay peticiones ni sección visible, y cada ciclo produce exactamente uno de los resultados de REQ-012 y REQ-013.
- **Pruebas automáticas:**
  - Caso feliz: credenciales válidas y respuesta 200 producen datos con las ventanas y el plan.
  - Caso de límite: sin credenciales no se realiza ninguna petición de red y el resultado es fallo transitorio sin causa.
  - Caso de error: 401 produce sesión caducada, y una caída de red produce fallo transitorio; ninguno lanza excepción.
  - Caso de límite: la fuente declara el intervalo de configuración y un tiempo máximo de 60 s.
- **Prueba manual:**
  1. no aplica: fuente interna; su efecto visible se comprueba en TASK-017.

### [ ] TASK-017 Integración: planificador con la fuente de OpenAI

- **Cubre:** REQ-004, REQ-005, REQ-009, REQ-011, REQ-012, REQ-014, NFR-004
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java; openai-usage-source | por definir (analista técnico)
- **Depende de:** TASK-006, TASK-007, TASK-009, TASK-016
- **Alcance:** Conectar el planificador con la fuente de OpenAI para que cada lectura llegue clasificada al publicador y, cuando tiene datos, al historial. Sin credenciales, el planificador vuelve a intentar la localización en cada ciclo sin hacer peticiones. Los fallos y el estado de sesión caducada llegan al publicador. Una sesión de Codex iniciada con la aplicación abierta se recoge en el siguiente ciclo.
- **Hecho cuando:** una lectura real de OpenAI llega al historial y al publicador, el planificador reintenta en cada ciclo y los fallos de OpenAI no afectan a Claude ni a OpenCode.
- **Pruebas automáticas:**
  - Caso feliz: con un servidor simulado que responde 200, los datos llegan al historial y al publicador.
  - Caso de error: con un 401 simulado, el planificador ejecuta el siguiente ciclo sin pausa y el estado publicado es sesión caducada.
  - Caso de límite: sin credenciales, en cada ciclo se vuelve a buscar la localización y no se hace ninguna petición.
  - Caso de límite: con un fallo de red, se mantiene el último dato válido y el intervalo no cambia.
  - Caso de error: un fallo de OpenAI no altera el estado ni la planificación de Claude ni de OpenCode.
- **Prueba manual:**
  1. Con `.codex/auth.json` válido, arrancar la aplicación y observar el tráfico hacia `chatgpt.com`. Resultado: una petición cada 60 s con cabeceras de autenticación.
  2. Sin credenciales de OpenAI, arrancar y esperar dos ciclos. Resultado: no hay tráfico hacia `chatgpt.com` y la aplicación sigue en ejecución.
  3. Con la aplicación abierta y sin credenciales, iniciar sesión en Codex y esperar un ciclo. Resultado: aparece el proveedor OpenAI sin reiniciar la aplicación.

### [ ] TASK-018 Ventana: sección de OpenAI con datos

- **Cubre:** REQ-006, REQ-007, REQ-008, REQ-010, REQ-014
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-006, TASK-008, TASK-017
- **Alcance:** Mostrar la sección de OpenAI como sección diferenciada, con el plan (si la respuesta lo incluye), el porcentaje y el tiempo restante de las ventanas de 5 horas y semanal, y la hora de la última consulta exitosa. La sección aparece solo cuando hay datos.
- **Hecho cuando:** con datos de OpenAI, la sección muestra porcentajes, tiempos restantes y hora de actualización según REQ-006 a REQ-010.
- **Pruebas automáticas:**
  - Caso feliz: con 6 % en la ventana de 5 horas y 1 % en la semanal, la sección muestra ambos porcentajes.
  - Caso de límite: `plan_type` `plus` identifica el plan Plus; sin `plan_type` no hay indicación de plan y no hay error.
  - Caso de límite: con datos de OpenAI y de otro proveedor, OpenAI aparece como sección separada.
  - Caso de límite: una última consulta exitosa a las 14:10:05 muestra "14:10:05", y tras un fallo posterior la hora se mantiene.
- **Prueba manual:**
  1. Con `.codex/auth.json` válido, abrir la ventana de uso. Resultado: aparece la sección de OpenAI con los porcentajes de 5 horas y semanal, su tiempo restante y la hora de actualización.
  2. Con Claude y OpenCode configurados a la vez, abrir la ventana de uso. Resultado: las tres secciones muestran sus datos de forma independiente.
  3. Comparar el porcentaje mostrado con el consumo que muestra la cuenta de OpenAI en el mismo momento. Resultado: coinciden, con el redondeo al entero.

### [ ] TASK-019 Ventana: estados de OpenAI (desactualizado, sesión caducada, sin datos)

- **Cubre:** REQ-004, REQ-012, REQ-013, REQ-014
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-006, TASK-018
- **Alcance:** Mostrar el indicador de datos desactualizados con el último dato válido tras un fallo transitorio. Mostrar el aviso formal de sesión caducada con el texto exacto de REQ-012, sin ningún valor de token, también cuando no hay datos previos. Sin credenciales o con un fallo transitorio sin datos no se muestra ninguna sección ni mensaje técnico. El aviso desaparece tras una lectura exitosa.
- **Hecho cuando:** los textos mostrados coinciden con REQ-012 y REQ-013 y no aparece ningún mensaje técnico.
- **Pruebas automáticas:**
  - Caso feliz: con sesión caducada se muestra exactamente el texto del aviso formal de REQ-012.
  - Caso de error: un fallo de red tras un 20 % muestra 20 % con indicador de desactualizado; sin datos previos no aparece la sección.
  - Caso de límite: una sesión caducada tras datos conserva los últimos datos visibles, y el aviso desaparece con la siguiente lectura exitosa.
  - Caso de límite: sin credenciales no aparece sección ni mensaje, y una respuesta sin `rate_limit` sin datos previos tampoco muestra sección.
- **Prueba manual:**
  1. Con datos visibles, sustituir en una copia de prueba de `.codex/auth.json` el token por uno inválido y esperar un ciclo. Resultado: aparece el aviso formal exacto y los últimos datos siguen visibles.
  2. Restaurar el token válido y esperar un ciclo. Resultado: el aviso desaparece y los datos se actualizan.
  3. Desconectar la red tras una lectura correcta y esperar un ciclo. Resultado: se muestra el último porcentaje con indicador de desactualizado, sin mensaje técnico.
  4. Con el fichero de Codex ausente y sin sección de OpenCode, arrancar la aplicación. Resultado: no aparece la sección de OpenAI ni ningún mensaje de error.

### [ ] TASK-020 Ventana: gráfico de evolución de OpenAI

- **Cubre:** REQ-011
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-007, TASK-018
- **Alcance:** Dibujar en el gráfico de OpenAI un punto por actualización y por ventana, ordenados en el tiempo, que aparecen en vivo y se descartan al cerrar la aplicación.
- **Hecho cuando:** los criterios de REQ-011 se cumplen en la interfaz.
- **Pruebas automáticas:**
  - Caso feliz: tres lecturas con valores distintos muestran tres puntos por ventana en orden temporal.
  - Caso de límite: dos lecturas con los mismos valores no añaden puntos duplicados.
  - Caso de límite: sin lecturas, el gráfico de OpenAI está vacío.
- **Prueba manual:**
  1. Esperar tres actualizaciones con valores distintos. Resultado: aparece un punto nuevo por actualización y ventana, sin recargar la aplicación.
  2. Esperar un ciclo sin cambios en el consumo. Resultado: no aparecen puntos nuevos.
  3. Cerrar la aplicación y volver a abrirla. Resultado: el gráfico de OpenAI empieza vacío.

### [ ] TASK-021 Contrato: http - consulta de cuota de OpenAI

- **Cubre:** REQ-005, REQ-006, REQ-007, REQ-012, NFR-001, DEC-004, DEC-009, DEC-010
- **Componente:** openai-usage-client | por definir (analista técnico)
- **Ubicación del contrato:** `docs/contracts/http/`, con el nombre del fichero por definir (analista técnico). El diseño propone `desktop/app/openai-usage.md` dentro de esa carpeta.
- **Depende de:** TASK-011
- **Alcance:** Crear el contrato de la petición `GET` de cuota: cabeceras, ventana primaria y secundaria, plan opcional, instante de reinicio en epoch segundos o segundos restantes, respuesta 200, respuestas 401 y 403, y fallo transitorio. Los ejemplos usan solo marcadores.
- **Hecho cuando:** el contrato existe en `docs/contracts/http/` y describe la interfaz tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: los ejemplos de respuesta son JSON válido.
  - Caso de error: el contrato recoge las respuestas 401 y 403 (sesión caducada) y el comportamiento ante fallo transitorio.
  - Seguridad: ningún ejemplo contiene un token o un identificador de cuenta reales, ni nada con su forma.
- **Prueba manual:**
  1. Repasar que aparecen la petición con las tres cabeceras, la respuesta con ventana primaria, secundaria y plan, el 401, el 403 y el fallo transitorio, y que cada uno coincide con lo implementado en TASK-011.

### [ ] TASK-022 Contrato: file-json - .codex/auth.json

- **Cubre:** REQ-002, NFR-001, DEC-002, DEC-011
- **Componente:** openai-credentials-reader | por definir (analista técnico)
- **Ubicación del contrato:** `docs/contracts/file-json/`, con el nombre del fichero por definir (analista técnico). El diseño propone `desktop/app/codex-auth.md` dentro de esa carpeta.
- **Depende de:** TASK-013
- **Alcance:** Crear el contrato de lectura de solo lectura de `.codex/auth.json`: campos leídos (`tokens.access_token` y `tokens.account_id`), resto de campos ignorados y tratamiento de fichero ausente o incompleto.
- **Hecho cuando:** el contrato existe en `docs/contracts/file-json/` y describe la lectura tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: el ejemplo de fichero completo con marcadores es JSON válido y solo contiene los campos leídos.
  - Caso de error: el contrato recoge el tratamiento de fichero incompleto como no disponible.
  - Seguridad: ningún ejemplo contiene valores reales ni nada con su forma.
- **Prueba manual:**
  1. Repasar que el contrato lista solo los dos campos leídos y el tratamiento de ausencia e incompletitud, y que coincide con TASK-013.

### [ ] TASK-023 Contrato: file-json - auth.json de opencode (etiqueta openai)

- **Cubre:** REQ-003, NFR-001, DEC-002
- **Componente:** openai-credentials-reader | por definir (analista técnico)
- **Ubicación del contrato:** `docs/contracts/file-json/`, con el nombre del fichero por definir (analista técnico). El diseño propone `desktop/app/opencode-auth.md` dentro de esa carpeta.
- **Depende de:** TASK-015
- **Alcance:** Crear el contrato de lectura de solo lectura del `auth.json` de opencode, limitado a la etiqueta `openai` (`access` y `accountId`), con las demás etiquetas ignoradas y el tratamiento de la ausencia de etiqueta.
- **Hecho cuando:** el contrato existe en `docs/contracts/file-json/` y describe la lectura tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: el ejemplo con la etiqueta `openai` con marcadores es JSON válido.
  - Caso de error: el contrato recoge el caso sin etiqueta `openai` como no disponible sin error.
  - Seguridad: ningún ejemplo contiene valores reales ni nada con su forma.
- **Prueba manual:**
  1. Repasar que el contrato describe solo la etiqueta `openai` y que las demás se ignoran, y que coincide con TASK-015.

### [ ] TASK-024 Contrato: stdio - consulta de la ruta de datos de opencode

- **Cubre:** REQ-003, NFR-003, DEC-003
- **Componente:** opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java
- **Ubicación del contrato:** `docs/contracts/stdio/`, con el nombre del fichero por definir (analista técnico). El diseño propone `desktop/app/opencode-data-path.md` dentro de esa carpeta.
- **Depende de:** TASK-014
- **Alcance:** Crear el contrato de la ejecución de `opencode debug paths data`: argumentos, tiempo máximo de 30 s, formato de salida de una línea y tratamiento de fallo silencioso.
- **Hecho cuando:** el contrato existe en `docs/contracts/stdio/` y describe la ejecución tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: el ejemplo de salida de una línea es válido según el contrato.
  - Caso de error: el contrato recoge el fallo silencioso y el tiempo máximo de 30 s con finalización del proceso.
- **Prueba manual:**
  1. Repasar que el contrato coincide con la ejecución implementada en TASK-014 (comando, tiempo máximo y tratamiento de fallos).

### [ ] TASK-025 NFR transversal: protección de credenciales de OpenAI de extremo a extremo

- **Cubre:** NFR-001, REQ-002, REQ-005, REQ-012, DEC-011
- **Componente:** application-launcher | src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java (verificación de la aplicación completa); prueba: por definir (analista técnico)
- **Depende de:** TASK-015, TASK-017, TASK-019
- **Alcance:** Verificar con valores centinela reconocibles (token, identificador de cuenta, `user_id` y `email`) en la aplicación completa que no aparecen en los logs ni en los textos de la interfaz, en los escenarios de credenciales válidas, 401 y 403, credenciales ausentes y fallo de red, y que los ficheros de credenciales no se modifican.
- **Hecho cuando:** en todos los escenarios no aparecen los valores centinela ni la cabecera `Authorization` en los logs capturados ni en los textos de la interfaz, y los ficheros de credenciales quedan intactos.
- **Pruebas automáticas:**
  - Caso feliz: escenario con credenciales centinela válidas; ningún valor centinela aparece en logs ni en textos de la interfaz.
  - Caso de error: escenarios 401, 403 y fallo de red; ningún valor centinela aparece en logs ni en textos.
  - Caso de límite: credenciales ausentes; no hay petición y ningún valor centinela aparece.
  - Caso de límite: contenido y fecha de modificación de los ficheros de credenciales son iguales antes y después de la ejecución.
- **Prueba manual:**
  1. Con un token de prueba reconocible en una copia de `.codex/auth.json`, arrancar, usar la ventana y revisar el log. Resultado: no aparecen el token, el identificador de cuenta ni la cabecera `Authorization`.
  2. Con un token inválido, observar la sección de OpenAI tras el 401. Resultado: el aviso no contiene el valor del token ni parte de él.
  3. Comparar el fichero de credenciales antes y después de la sesión de prueba. Resultado: contenido y fecha de modificación idénticos.

### [ ] TASK-026 NFR transversal: recursos y respuesta de la interfaz con OpenAI activo

- **Cubre:** NFR-002, NFR-005
- **Componente:** application-launcher | src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java (verificación de la aplicación completa); prueba: por definir (analista técnico)
- **Depende de:** TASK-017, TASK-018, TASK-020
- **Alcance:** Medir, con OpenAI activo, el consumo de CPU y de heap en segundo plano, y la respuesta de la interfaz mientras una consulta a OpenAI tarda más de 5 s.
- **Hecho cuando:** los criterios de NFR-002 y NFR-005 se cumplen con valores medidos.
- **Pruebas automáticas:**
  - Caso feliz (regresión): la suite de pruebas del proyecto pasa con OpenAI configurado.
  - Caso de límite: con un servidor simulado que tarda 6 s, la interfaz responde en menos de 1 s mientras espera.
- **Prueba manual:**
  1. Lanzar la aplicación con OpenAI activo y dejarla en segundo plano durante 5 minutos. Resultado: el consumo medio de CPU es inferior al 5 % de un núcleo.
  2. Medir el heap con una herramienta de la JVM tras esos 5 minutos. Resultado: inferior a 256 MB.
  3. Con una herramienta de red que retrase la respuesta de `chatgpt.com` más de 5 s, pulsar "mostrar uso" durante la consulta. Resultado: la ventana se abre en menos de 1 s.

## Cobertura

| Requisito | Tareas |
|---|---|
| REQ-001 | TASK-001, TASK-004 |
| REQ-002 | TASK-013, TASK-015, TASK-022, TASK-025 |
| REQ-003 | TASK-003, TASK-014, TASK-015, TASK-023, TASK-024 |
| REQ-004 | TASK-006, TASK-015, TASK-016, TASK-017, TASK-019 |
| REQ-005 | TASK-011, TASK-016, TASK-017, TASK-021, TASK-025 |
| REQ-006 | TASK-010, TASK-018, TASK-021 |
| REQ-007 | TASK-008, TASK-010, TASK-018, TASK-021 |
| REQ-008 | TASK-010, TASK-018 |
| REQ-009 | TASK-002, TASK-009, TASK-016, TASK-017 |
| REQ-010 | TASK-006, TASK-018 |
| REQ-011 | TASK-007, TASK-017, TASK-020 |
| REQ-012 | TASK-005, TASK-006, TASK-009, TASK-011, TASK-016, TASK-017, TASK-019, TASK-021, TASK-025 |
| REQ-013 | TASK-005, TASK-006, TASK-010, TASK-011, TASK-016, TASK-019 |
| REQ-014 | TASK-009, TASK-016, TASK-017, TASK-018, TASK-019 |
| NFR-001 | TASK-011, TASK-012, TASK-013, TASK-021, TASK-022, TASK-023, TASK-025 |
| NFR-002 | TASK-026 |
| NFR-003 | TASK-009, TASK-011, TASK-014, TASK-015, TASK-016, TASK-024 |
| NFR-004 | TASK-002, TASK-009, TASK-017 |
| NFR-005 | TASK-026 |
