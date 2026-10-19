# Sprint 05: openai-usage-monitor - desktop/app

Estado: aprobado

## Alcance

- Tareas: TASK-001 a TASK-026 (de `specs/openai-usage-monitor/tasks.md`).
- Dependencias externas: Ninguna; todas las tareas y contratos pertenecen a `desktop/app`.

## Enfoque técnico

Se reorganizará la configuración bajo `subscription-viewer` y se extenderá el flujo común de lectura para distinguir datos, fallo transitorio y sesión caducada, manteniendo sin cambios la semántica de Claude y OpenCode. La nueva fuente `openai` releerá credenciales locales en cada ciclo, las mantendrá en un tipo secreto y delegará la petición HTTPS y el parseo puro al cliente JDK.
El planificador pasará a consumir intervalo y tiempo máximo declarados por cada fuente, programando el siguiente ciclo desde el fin del anterior. Los fallos de OpenAI se publicarán para conservar datos y comunicar su estado, pero permanecerán aislados de los otros proveedores.
La interfaz reutilizará tarjetas, cuenta atrás e historial para una sección OpenAI diferenciada, incluidos plan opcional, gráfico y aviso formal. Las tareas `Contrato:` son responsabilidad del documentador: el desarrollador no modifica los contratos, y el sprint las ordena tras sus implementaciones.

## Cambios por tarea

### TASK-001 Bloques de configuración de Claude y OpenAI

- **Ficheros:** `src/main/resources/application.yml`, `config/AppProperties.java`, `config/ConfigurationProvider.java`, `config/ConfigurationProviderTest.java` (modificar).
- **Enfoque:** mover las propiedades de Claude a la subsección `subscription-viewer.claude`, añadir `openai` con 60 s y alinear el valor interno de la ruta; conservar los métodos públicos consumidores y `app` limitado a nombre e icono.
- **Pruebas automáticas:** configuración empaquetada, sobrescritura de ruta Claude y neutralidad de las claves `app` retiradas; `mvn -f desktop/app/pom.xml test -Dtest=ConfigurationProviderTest`.
- **Prueba manual:** revisar el YAML, cambiar temporalmente la ruta Claude y comprobar que la tarjeta lee el fichero alternativo.

### TASK-002 Validación uniforme de intervalos al arrancar

- **Ficheros:** `config/AppProperties.java`, `config/ConfigurationProvider.java`, `config/ConfigurationProviderTest.java` (modificar).
- **Enfoque:** centralizar el parseo y validación de duraciones positivas durante la construcción; identificar la propiedad en el error, aplicar mínimo de 10 s a OpenAI/OpenCode y 60 s ante OpenAI ausente o vacío.
- **Pruebas automáticas:** OpenAI ausente, vacío, 30 s y 2 s; `abc`, cero y negativo para los tres proveedores con fallo de arranque identificable; comando de TASK-001.
- **Prueba manual:** arrancar con `abc` y con `2s` en OpenAI, verificando respectivamente el fallo y el mínimo efectivo.

### TASK-003 Indicador de presencia de la sección de OpenCode

- **Ficheros:** `config/ConfigurationProvider.java`, `config/ConfigurationProviderTest.java` (modificar).
- **Enfoque:** consultar la presencia efectiva del prefijo en `Environment`, no el objeto de propiedades instanciado por defecto; exponer un indicador para el lector de credenciales.
- **Pruebas automáticas:** sección presente con y sin claves, y sección ausente pese al objeto enlazado; comando de TASK-001.
- **Prueba manual:** no aplica; se valida funcionalmente en TASK-015 y TASK-017.

### TASK-004 Adaptación de claude-usage-source a la nueva configuración

- **Ficheros:** `usage/claude/ClaudeUsageSource.java`, sus pruebas y configuración de prueba; documentación activa del módulo si procede (modificar).
- **Enfoque:** actualizar mensajes, errores y fixtures a la nueva clave sin alterar lectura ni tolerancia del fichero; retirar toda referencia activa a las dos claves antiguas.
- **Pruebas automáticas:** regresión de fichero válido e inválido y búsqueda de las claves retiradas; `mvn -f desktop/app/pom.xml test -Dtest=ClaudeUsageSourceTest,ClaudeUsageStateFileIntegrationTest`.
- **Prueba manual:** arrancar con un estado Claude válido y confirmar los mismos porcentajes y tiempos.

### TASK-005 Resultado de sesión caducada en usage-source

- **Ficheros:** `usage/UsageSource.java`, `usage/UsageReadResult.java`, `usage/UsageSourceTest.java`, pruebas de fuentes existentes (modificar).
- **Enfoque:** añadir el estado explícito de sesión caducada y preservar el envoltorio de excepciones como fallo transitorio; no transportar respuestas HTTP ni credenciales en el resultado.
- **Pruebas automáticas:** resultado caducado distinguible, excepción de fuente convertida a fallo y regresión Claude/OpenCode; `mvn -f desktop/app/pom.xml test -Dtest=UsageSourceTest,ClaudeUsageSourceTest,OpenCodeUsageSourceTest`.
- **Prueba manual:** no aplica; se observa en TASK-019.

### TASK-006 Publicador: estados de OpenAI y sesión caducada

- **Ficheros:** `usage/UsageSnapshotPublisher.java`, `usage/UsageProviderState.java`, `usage/UsageSnapshotPublisherTest.java` (modificar).
- **Enfoque:** modelar sesión caducada por proveedor, conservar lectura y hora del último éxito para fallo/caducidad, no crear estado visible por fallo inicial y limpiar ambos avisos tras éxito; mantener la excepción de OpenCode.
- **Pruebas automáticas:** éxito, desactualizado tras 20 %, caducidad con/sin datos, fallo inicial y aislamiento de proveedores sin secretos; `mvn -f desktop/app/pom.xml test -Dtest=UsageSnapshotPublisherTest`.
- **Prueba manual:** no aplica; se verifica visualmente en TASK-019.

### TASK-007 Historial: deduplicación por ventana para OpenAI

- **Ficheros:** `usage/UsageHistory.java`, `usage/UsageHistoryTest.java` (modificar).
- **Enfoque:** aplicar a OpenAI una deduplicación por porcentaje y ventana, independiente de `resetsAt`, conservando orden, límite de 100 puntos y reglas actuales de Claude/OpenCode.
- **Pruebas automáticas:** cambios por ambas ventanas, repetición con distinta hora/reinicio y regresiones existentes; `mvn -f desktop/app/pom.xml test -Dtest=UsageHistoryTest`.
- **Prueba manual:** no aplica; se muestra en TASK-020.

### TASK-008 Cuenta atrás: tiempo mínimo cero para OpenAI

- **Ficheros:** `usage/CountdownEvaluator.java`, `usage/CountdownEvaluatorTest.java`, modelos de presentación si fueran necesarios (modificar).
- **Enfoque:** distinguir la presentación OpenAI para expresar un reinicio pasado como cero sin cambiar la marca de vencimiento que usan Claude y OpenCode.
- **Pruebas automáticas:** reinicio a dos horas, reinicio pasado nunca negativo y regresiones de los otros proveedores; `mvn -f desktop/app/pom.xml test -Dtest=CountdownEvaluatorTest`.
- **Prueba manual:** no aplica; se cubre en TASK-018 y TASK-019.

### TASK-009 Planificador: intervalo, tiempo máximo y reglas de OpenAI por fuente

- **Ficheros:** `usage/UsageSource.java`, `usage/UsagePoller.java`, `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** hacer que cada fuente declare intervalo y timeout; eliminar la decisión por nombre salvo la semántica heredada de publicación OpenCode, usar 60 s para OpenAI y reprogramar tras finalizar sin backoff ni pausa de sus fallos.
- **Pruebas automáticas:** cadencias 60/30 s, ausencia de solape, aborto a 60 s, 401/403/red sin pausa, máximo seis por minuto y regresión Claude/OpenCode; `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest`.
- **Prueba manual:** no aplica; se integra en TASK-017.

### TASK-010 Parseo de la respuesta de cuota de OpenAI

- **Ficheros:** `usage/openai/OpenAiUsageClient.java`, DTOs/registros de respuesta en `usage/openai/` y `usage/openai/OpenAiUsageClientTest.java` (nuevos, propuesta).
- **Enfoque:** aislar un parser puro que asigne primaria a `FIVE_HOUR` y secundaria a `SEVEN_DAY`, redondee porcentaje, calcule el reinicio absoluto y descarte campos desconocidos; JSON inválido o sin `rate_limit` será fallo transitorio.
- **Pruebas automáticas:** ventanas primaria/secundaria, ausencia de secundaria/plan, duración irrelevante, epoch en segundos, respaldo relativo y JSON/esquema inválido; `mvn -f desktop/app/pom.xml test -Dtest=OpenAiUsageClientTest`.
- **Prueba manual:** no aplica; se verifica en TASK-018.

### TASK-011 Petición autenticada al endpoint de cuota y clasificación de respuestas

- **Ficheros:** `usage/openai/OpenAiUsageClient.java`, `usage/openai/OpenAiUsageClientTest.java` (modificar).
- **Enfoque:** usar `java.net.http.HttpClient` con límite conjunto de 30 s y ejecución desde el worker del poller; construir solo las tres cabeceras exigidas, clasificar 200/401/403/resto y registrar únicamente categoría y código.
- **Pruebas automáticas:** servidor simulado para cabeceras y 200; 401/403, 500/429/red/timeout y captura de logs sin valores centinela; comando de TASK-010.
- **Prueba manual:** no aplica; se cubre en TASK-017 y TASK-025.

### TASK-012 Tipo secreto para las credenciales de OpenAI

- **Ficheros:** `usage/openai/OpenAiCredentials.java`, `usage/openai/OpenAiCredentialsTest.java` (nuevos, propuesta).
- **Enfoque:** encapsular token y cuenta con representación fija enmascarada y acceso restringido al constructor de cabeceras, evitando que registros, excepciones o colecciones revelen caracteres reales.
- **Pruebas automáticas:** acceso previsto para cabeceras y `toString`/volcado de contenedor sin centinelas ni fragmentos; `mvn -f desktop/app/pom.xml test -Dtest=OpenAiCredentialsTest`.
- **Prueba manual:** no aplica; se verifica extremo a extremo en TASK-025.

### TASK-013 Lectura de credenciales desde .codex/auth.json

- **Ficheros:** `usage/openai/OpenAiCredentialsReader.java`, DTOs mínimos de lectura, `usage/openai/OpenAiCredentialsReaderTest.java` (nuevos, propuesta).
- **Enfoque:** resolver `user.home/.codex/auth.json`, leer solo los dos campos `tokens` necesarios con Jackson y devolver existencia/disponibilidad sin retener campos descartados ni modificar el fichero.
- **Pruebas automáticas:** completo, incompleto, ausente, JSON inválido, campos extra ignorados e invariancia de contenido/fecha; `mvn -f desktop/app/pom.xml test -Dtest=OpenAiCredentialsReaderTest`.
- **Prueba manual:** no aplica; se integra en TASK-017 y TASK-025.

### TASK-014 Consulta de la ruta de datos de opencode en el ejecutor de CLI

- **Ficheros:** `usage/opencode/OpenCodeCliRunner.java`, `usage/opencode/OpenCodeCliRunnerTest.java` (modificar).
- **Enfoque:** exponer una operación específica para `opencode debug paths data` reutilizando proceso, timeout de 30 s, destrucción y salida limitada; devolver ruta o no disponible sin revelar salida/error.
- **Pruebas automáticas:** fixture con ruta, ejecutable ausente/salida no cero, bloqueo y salida vacía o inválida, más regresión `session.stats`; `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeCliRunnerTest`.
- **Prueba manual:** no aplica; se cubre en TASK-017.

### TASK-015 Orden de fuentes de credenciales de OpenAI

- **Ficheros:** `usage/openai/OpenAiCredentialsReader.java`, `usage/openai/OpenAiCredentialsReaderTest.java` (modificar).
- **Enfoque:** aplicar por ciclo Codex exclusivo si existe; solo ante su ausencia y sección OpenCode presente, pedir la ruta CLI y extraer exclusivamente `openai.access` y `openai.accountId` del `auth.json`, sin caché.
- **Pruebas automáticas:** alternativa OpenCode, prioridad Codex completo/incompleto, sección ausente, CLI fallido, etiqueta ausente y cambio de token entre ciclos; comando de TASK-013.
- **Prueba manual:** no aplica; se verifica en TASK-017 y TASK-025.

### TASK-016 Fuente de uso de OpenAI

- **Ficheros:** `usage/openai/OpenAiUsageSource.java`, `usage/openai/OpenAiUsageSourceTest.java` (nuevos, propuesta).
- **Enfoque:** implementar `UsageSource` con id OpenAI, intervalo validado y timeout de 60 s; releer credenciales, evitar red si faltan y propagar exclusivamente los resultados clasificados del cliente sin registrar secretos.
- **Pruebas automáticas:** 200 con plan/ventanas, ausencia sin petición, 401 y red sin excepción, y declaración de intervalo/timeout; `mvn -f desktop/app/pom.xml test -Dtest=OpenAiUsageSourceTest`.
- **Prueba manual:** no aplica; se integra en TASK-017.

### TASK-017 Integración: planificador con la fuente de OpenAI

- **Ficheros:** `usage/UsagePoller.java`, `usage/openai/OpenAiUsageSource.java`, `usage/UsagePollerTest.java`, `usage/OpenAiPollingIntegrationTest.java` (nuevo, propuesta).
- **Enfoque:** registrar la fuente como bean y entregar datos a historial/publicador, pero también fallos y caducidad al publicador; asegurar recarga de credenciales por ciclo e independencia de planificación y estado.
- **Pruebas automáticas:** servidor 200 a historial/publicador, 401 sin pausa, ausencia sin red con búsqueda repetida, red tras éxito y aislamiento Claude/OpenCode; `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest,OpenAiPollingIntegrationTest`.
- **Prueba manual:** con/sin Codex y tras iniciar sesión en caliente, comprobar tráfico cada 60 s y aparición sin reinicio.

### TASK-018 Ventana: sección de OpenAI con datos

- **Ficheros:** `ui/UsageWindow.java`, `ui/UsageProviderCardPanel.java`, `ui/UsageCardPanel.java`, `ui/UsageWindowTest.java`, `ui/UsageCardPanelTest.java` (modificar).
- **Enfoque:** renderizar OpenAI como tarjeta separada con plan opcional, ventanas reutilizadas, cuenta atrás y hora de último éxito, manteniendo el despacho EDT y la convivencia con otros proveedores.
- **Pruebas automáticas:** 6 %/1 %, Plus/plan ausente, sección independiente y hora conservada tras fallo; `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest,UsageCardPanelTest`.
- **Prueba manual:** con credenciales válidas, abrir la ventana y contrastar plan, porcentajes, tiempos y hora con el servicio.

### TASK-019 Ventana: estados de OpenAI (desactualizado, sesión caducada, sin datos)

- **Ficheros:** `ui/UsageWindow.java`, `ui/UsageProviderCardPanel.java`, `ui/UsageWindowTest.java`, `ui/UsageCardPanelTest.java` (modificar).
- **Enfoque:** traducir el estado publicado a indicador de desactualizado y al texto formal exacto de sesión caducada; permitir aviso sin lectura, ocultar fallo inicial/sin credenciales y limpiar aviso tras éxito.
- **Pruebas automáticas:** texto exacto, red tras 20 %, caducidad con datos y recuperación, y ausencia/respuesta inválida sin sección; comando de TASK-018.
- **Prueba manual:** probar token inválido, restaurarlo y simular pérdida de red, verificando conservación/ocultación correctas y ausencia de texto técnico.

### TASK-020 Ventana: gráfico de evolución de OpenAI

- **Ficheros:** `ui/UsageChartPanel.java`, `ui/UsageWindow.java`, `ui/UsageChartPanelTest.java`, `ui/UsageWindowTest.java` (modificar).
- **Enfoque:** consumir las series OpenAI del historial y repintar ambas ventanas en orden al recibir snapshots, sin persistencia y sin puntos duplicados.
- **Pruebas automáticas:** tres valores, repetición sin punto y serie vacía; `mvn -f desktop/app/pom.xml test -Dtest=UsageChartPanelTest,UsageWindowTest`.
- **Prueba manual:** observar actualizaciones con y sin cambio y reiniciar para confirmar gráfico vacío.

### TASK-021 Contrato: http - consulta de cuota de OpenAI

- **Ficheros:** `docs/contracts/http/desktop/app/openai-usage.md` (nuevo; responsabilidad exclusiva del documentador).
- **Enfoque:** tras TASK-011, documentar endpoint, tres cabeceras con marcadores, esquema de ventanas/plan/reinicio, 200, 401/403 y fallo transitorio conforme al cliente final.
- **Pruebas automáticas:** el documentador valida JSON de ejemplos, cobertura de errores y ausencia de tokens/cuentas reales.
- **Prueba manual:** cotejar el contrato con el cliente implementado.

### TASK-022 Contrato: file-json - .codex/auth.json

- **Ficheros:** `docs/contracts/file-json/desktop/app/codex-auth.md` (nuevo; responsabilidad exclusiva del documentador).
- **Enfoque:** tras TASK-013, describir la lectura de solo lectura y los dos campos permitidos, con fichero ausente o incompleto como no disponible y marcadores seguros.
- **Pruebas automáticas:** el documentador valida el JSON de ejemplo, los campos limitados y la ausencia de secretos.
- **Prueba manual:** cotejar el contrato con el lector Codex implementado.

### TASK-023 Contrato: file-json - auth.json de opencode (etiqueta openai)

- **Ficheros:** `docs/contracts/file-json/desktop/app/opencode-auth.md` (nuevo; responsabilidad exclusiva del documentador).
- **Enfoque:** tras TASK-015, documentar solo `openai.access` y `openai.accountId`, etiquetas ignoradas y etiqueta ausente como no disponible.
- **Pruebas automáticas:** el documentador valida JSON de ejemplo, error de etiqueta y seguridad de marcadores.
- **Prueba manual:** cotejar el contrato con la alternativa OpenCode implementada.

### TASK-024 Contrato: stdio - consulta de la ruta de datos de opencode

- **Ficheros:** `docs/contracts/stdio/desktop/app/opencode-data-path.md` (nuevo; responsabilidad exclusiva del documentador).
- **Enfoque:** tras TASK-014, documentar comando, argumentos, límite de 30 s, salida de una línea y fallo silencioso con finalización.
- **Pruebas automáticas:** el documentador valida ejemplo de salida, timeout y tratamiento de error.
- **Prueba manual:** cotejar el contrato con el runner implementado.

### TASK-025 NFR transversal: protección de credenciales de OpenAI de extremo a extremo

- **Ficheros:** `launcher/OpenAiCredentialsProtectionIntegrationTest.java` (nuevo, propuesta), pruebas de `usage/openai/` y `ui/` si la verificación detecta una fuga (modificar).
- **Enfoque:** ensamblar escenarios con valores centinela y logs capturados para credenciales válidas, 401/403, ausencia y red; inspeccionar textos de vista y metadatos de los ficheros sin imprimir secretos.
- **Pruebas automáticas:** los cinco escenarios, cero ocurrencias de token/cuenta/user/email/`Authorization` y ficheros intactos; `mvn -f desktop/app/pom.xml test -Dtest=OpenAiCredentialsProtectionIntegrationTest`.
- **Prueba manual:** ejecutar con copia de credenciales centinela y revisar UI, log y contenido/fecha del fichero antes y después.

### TASK-026 NFR transversal: recursos y respuesta de la interfaz con OpenAI activo

- **Ficheros:** `launcher/OpenAiResourceAndResponsivenessIntegrationTest.java` (nuevo, propuesta), `pom.xml` (modificar si debe incluirse en `integration-tests`) y correcciones estrictamente necesarias (modificar).
- **Enfoque:** usar servidor lento controlado para demostrar que el trabajo externo queda fuera del EDT; conservar mediciones manuales de CPU y heap como validación dependiente de máquina.
- **Pruebas automáticas:** suite con OpenAI, respuesta de UI menor de un segundo con espera de 6 s; `mvn -f desktop/app/pom.xml test` y `mvn -f desktop/app/pom.xml verify -Pintegration-tests`.
- **Prueba manual:** medir cinco minutos en segundo plano y heap, y abrir la ventana durante una respuesta de más de cinco segundos.

## Orden de implementación

1. TASK-001
2. TASK-005
3. TASK-007
4. TASK-008
5. TASK-010
6. TASK-012
7. TASK-014
8. TASK-002
9. TASK-003
10. TASK-004
11. TASK-006
12. TASK-011
13. TASK-013
14. TASK-009
15. TASK-015
16. TASK-016
17. TASK-017
18. TASK-018
19. TASK-019
20. TASK-020
21. TASK-021
22. TASK-022
23. TASK-023
24. TASK-024
25. TASK-025
26. TASK-026

## Riesgos y dudas técnicas

- El binder de Spring y el parseo manual de duraciones pueden diferir; centralizar la validación sobre los valores efectivos y cubrir formatos inválidos en arranque.
- El timeout de fuente OpenAI incluye CLI y HTTP; mantener cancelación y aislamiento por fuente para que nunca bloquee Claude u OpenCode.
- El endpoint y su esquema no son contractuales; ignorar campos desconocidos y clasificar esquemas incompletos como fallo transitorio.
- CPU, heap y latencia de interfaz dependen del equipo; automatizar solo el servidor lento y registrar las mediciones manuales de TASK-026.
