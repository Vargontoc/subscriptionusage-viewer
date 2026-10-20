# Sprint 06: gemini-usage-monitor - desktop/app

Estado: aprobado

## Alcance

- Tareas: TASK-001 a TASK-024 (de `specs/gemini-usage-monitor/tasks.md`).
- Dependencias externas: Ninguna; todas las tareas pertenecen a `desktop/app`. TASK-020 a TASK-022 son responsabilidad del documentador.

## Enfoque técnico

Primero se validará manualmente la interfaz real de `agy` y se inventariará el ciclo de vida actual de procesos de OpenCode. Después se extraerá la mecánica segura de ejecución de CLI a `usage/cli/AbstractCliRunner`, conservando sin cambio la API pública y la semántica silenciosa de OpenCode.
Gemini se implementará en el paquete `usage/gemini`: un runner con argumentos fijos, parser JSON tolerante y fuente que convierta cualquier error en el nuevo estado visible de consulta fallida, sin propagar salida, argumentos, códigos ni trazas.
Los modelos compartidos pasarán a usar porcentaje decimal redondeado a una cifra; el historial, publicador, planificador y UI conservarán la compatibilidad observable de Claude, OpenCode y OpenAI. La fuente declarará intervalo y timeout para mantener los ciclos aislados y sin solapamiento.

## Cambios por tarea

### TASK-001 Verificar agy: argumentos, salida JSON y resolución en Windows

- **Ficheros:** ninguno (verificación manual previa, sin código).
- **Enfoque:** ejecutar los cuatro pasos prescritos con la instalación real; registrar únicamente el tipo de ejecutable localizado y los nombres de campos de la respuesta. Si `json` es rechazado, detener el sprint y devolver el supuesto S1 al router.
- **Pruebas automáticas:** no aplica.
- **Prueba manual:** en PowerShell y `cmd.exe`, desde la raíz y otro directorio, ejecutar `agy -p "/usage" --output-format json`; confirmar código 0, `status: SUCCESS`, `command.data.groups` y la resolución de `where agy`.

### TASK-002 Inventario de llamadas a liveProcessCount y destroyActiveProcesses

- **Ficheros:** ninguno (revisión manual previa, sin código).
- **Enfoque:** inventariar antes de extraer la base las referencias actuales: `OpenCodeCliRunnerTest` llama a `liveProcessCount`; `OpenCodeUsageSource` delega ambos métodos y los usa al cerrar; `OpenCodeResourceAndResponsivenessV2IntegrationTest` llama al contador mediante la fuente. Decidir y anotar que ambos quedan accesibles en la subclase por compatibilidad, delegando internamente en la base si procede.
- **Pruebas automáticas:** no aplica; la regresión se ejecuta en TASK-003.
- **Prueba manual:** repetir la búsqueda de ambos símbolos en `desktop/app`, incluyendo producción, pruebas y cierre, y confirmar que no queda referencia sin inventariar.

### TASK-003 Refactor de OpenCodeCliRunner para heredar de cli-runner-base

- **Ficheros:** `usage/cli/AbstractCliRunner.java`, `usage/cli/AbstractCliRunnerTest.java` (nuevos, propuesta); `usage/opencode/OpenCodeCliRunner.java`, `usage/opencode/OpenCodeCliRunnerTest.java`, `usage/opencode/OpenCodeUsageSource.java` y pruebas de integración consumidoras (modificar).
- **Enfoque:** mover a la base abstracta proceso por PATH y envoltorio Windows, stdin/stderr, lector limitado, timeout, destrucción del árbol, registro de procesos y resultado con salida/código. La subclase aporta comando, timeout, límite, hilo y etiqueta; conserva constructores, `run`, `execute`, `runDataPath`, fallo silencioso y los accesos de ciclo de vida decididos en TASK-002.
- **Pruebas automáticas:** stats y data path sin cambio; ejecutable ausente/código no cero, timeout con descendientes, corte a 1 MB, ruta multilínea y `.cmd` en Windows; verificar que el resultado interno conserva código de salida. Ejecutar `mvn -f desktop/app/pom.xml test -Dtest=AbstractCliRunnerTest,OpenCodeCliRunnerTest,OpenCodeUsageSourceTest`.
- **Prueba manual:** con OpenCode activo, comprobar actualización idéntica en la ventana.

### TASK-004 Regresión de credenciales de OpenAI tras el refactor del ejecutor

- **Ficheros:** `usage/openai/OpenAiCredentialsReaderTest.java`, `usage/openai/OpenAiUsageSourceTest.java` (modificar; producción solo si revela una incompatibilidad).
- **Enfoque:** confirmar que el lector sigue inyectando la implementación concreta de OpenCode y que `runDataPath` mantiene la ruta única y su semántica de no disponibilidad.
- **Pruebas automáticas:** ruta válida, runner fallido y salida multilínea, además de la suite de fuente OpenAI; `mvn -f desktop/app/pom.xml test -Dtest=OpenAiCredentialsReaderTest,OpenAiUsageSourceTest`.
- **Prueba manual:** con credenciales OpenCode disponibles y después no disponibles, comprobar respectivamente valores OpenAI y aviso de sesión caducada sin cambio.

### TASK-005 Configuración de subscription-viewer.gemini.usage-poll-interval

- **Ficheros:** `src/main/resources/application.yml`, `config/AppProperties.java`, `config/ConfigurationProvider.java`, `config/ConfigurationProviderTest.java` (modificar).
- **Enfoque:** añadir subsección Gemini y acceso tipado; usar 60 s para valor ausente/vacío, mínimo específico 30 s y el validador uniforme que identifica la propiedad para formatos inválidos, cero y negativos, sin alterar proveedores existentes.
- **Pruebas automáticas:** `60s`, ausente, vacío, `5s`, `abc`, `0s` y `-5s`, más regresión Claude/OpenCode/OpenAI; `mvn -f desktop/app/pom.xml test -Dtest=ConfigurationProviderTest`.
- **Prueba manual:** revisar el YAML y arrancar temporalmente con `abc`, confirmando el nombre de propiedad en el error y restaurando `60s`.

### TASK-006 Porcentaje con un decimal en la lectura de ventana

- **Ficheros:** `usage/UsageWindowReading.java`, `usage/UsageHistoryPoint.java`, consumidores de porcentaje, y `usage/UsageHistoryTest.java`, `ui/UsageCardPanelTest.java` (modificar).
- **Enfoque:** sustituir la representación entera por decimal apropiado para escala 0--100 y conservar los enteros sin decimales espurios en proveedores existentes; adaptar igualdad, límites y formato posteriores para no perder precisión.
- **Pruebas automáticas:** conservar 1,8; presentar 0 y 100 como enteros; regresión de lecturas enteras Claude/OpenCode/OpenAI; `mvn -f desktop/app/pom.xml test -Dtest=UsageHistoryTest,UsageCardPanelTest`.
- **Prueba manual:** no aplica; se observa en TASK-017.

### TASK-007 Estado consulta fallida y tiempos declarados por fuente en usage-source

- **Ficheros:** `usage/UsageSource.java`, `usage/UsageReadResult.java`, `usage/UsageProviderState.java`, `usage/UsageSourceTest.java` y pruebas de fuentes existentes (modificar).
- **Enfoque:** añadir el estado clasificado y su constructor sin causa técnica expuesta; preservar los valores por defecto actuales para fuentes que no declaren cadencia/timeout y permitir sobrescrituras explícitas.
- **Pruebas automáticas:** fuente con intervalo/timeout propios, fuente heredada y preservación de datos, fallo transitorio y sesión caducada; `mvn -f desktop/app/pom.xml test -Dtest=UsageSourceTest,ClaudeUsageSourceTest,OpenCodeUsageSourceTest,OpenAiUsageSourceTest`.
- **Prueba manual:** no aplica; se integra en TASK-014 y TASK-016.

### TASK-008 Ejecutor gemini-cli-runner para agy

- **Ficheros:** `usage/gemini/GeminiCliRunner.java`, `usage/gemini/GeminiCliRunnerTest.java` (nuevos, propuesta); `context/ApplicationContextConfig.java` y prueba de contexto (modificar si se requiere declarar o cualificar beans).
- **Enfoque:** subclase concreta de `AbstractCliRunner` con `agy`, argumentos inmutables, 60 s y registro independiente. Exponer un resultado interno con salida y código, usar solo inyección concreta y registrar categorías sin salida, argumentos ni códigos.
- **Pruebas automáticas:** argumentos exactos y código 0, ausente/no cero, timeout y descendientes, límite de salida, aislamiento de procesos OpenCode, centinela ausente de logs, `.cmd` y arranque de contexto con ambos runners; `mvn -f desktop/app/pom.xml test -Dtest=GeminiCliRunnerTest,ApplicationContextNonWebTest`.
- **Prueba manual:** anteponer un `agy.cmd` bloqueante temporal al PATH en Windows, provocar una consulta y comprobar ejecución y limpieza a los 60 s.

### TASK-009 Validar el sobre de la respuesta de agy

- **Ficheros:** `usage/gemini/GeminiUsageParser.java`, `usage/gemini/GeminiUsageParserTest.java` (nuevos, propuesta).
- **Enfoque:** encapsular el parseo Jackson en un resultado de validación privado/puro: exigir JSON, `SUCCESS` y `command.data.groups`; ignorar `response` y campos desconocidos; devolver solo categorías de invalidez seguras.
- **Pruebas automáticas:** JSON válido con extras, texto inválido, estado distinto, grupos ausentes, coexistencia de `response` y centinela que no aparezca en el motivo; `mvn -f desktop/app/pom.xml test -Dtest=GeminiUsageParserTest`.
- **Prueba manual:** no aplica; se cubre con TASK-013 y TASK-024.

### TASK-010 Extraer grupos, ventanas y porcentajes de command.data.groups

- **Ficheros:** `usage/gemini/GeminiUsageParser.java`, `usage/gemini/GeminiUsageParserTest.java` (modificar).
- **Enfoque:** mapear `weekly` y `5h` a tipos existentes, crear una serie por nombre de grupo y calcular `(1-fracción)*100` acotado y redondeado mitad-arriba a un decimal; interpretar `Z` como UTC y omitir datos inválidos sin perder grupos válidos.
- **Pruebas automáticas:** tres grupos/cinco ventanas, 1,8/0/50/100, fracciones fuera de rango, reinicio UTC o ausente, grupo desconocido, cubos ausentes/desconocidos y resultado fallido sin cubos válidos; comando de TASK-009.
- **Prueba manual:** no aplica; se observa en TASK-017.

### TASK-011 Tiempo restante de las ventanas de Gemini en countdown-evaluator

- **Ficheros:** `usage/CountdownEvaluator.java`, `usage/CountdownEvaluatorTest.java` (modificar).
- **Enfoque:** tratar Gemini como OpenAI para conservar ventanas con reinicio vencido en cero, pero añadir el caso sin reinicio para que la vista no fabrique una cuenta atrás; no cambiar la regla de vencimiento de Claude/OpenCode.
- **Pruebas automáticas:** dos horas futuras, pasado a cero y reinicio nulo sin tiempo, con regresión existente; `mvn -f desktop/app/pom.xml test -Dtest=CountdownEvaluatorTest`.
- **Prueba manual:** no aplica; se observa en TASK-017.

### TASK-012 Historial de Gemini con un decimal y sin persistencia

- **Ficheros:** `usage/UsageHistory.java`, `usage/UsageHistoryPoint.java`, `usage/UsageHistoryTest.java` (modificar).
- **Enfoque:** indexar por proveedor, grupo y ventana ya existentes; para Gemini comparar el porcentaje ya redondeado a una cifra y conservar solo memoria, orden cronológico y límite de serie.
- **Pruebas automáticas:** una serie por grupo/ventana, cambios de decimal, 1,81 frente a 1,8, repetición, instancia nueva vacía y regresión entera; `mvn -f desktop/app/pom.xml test -Dtest=UsageHistoryTest`.
- **Prueba manual:** no aplica; se verifica en TASK-019 y TASK-024.

### TASK-013 Fuente gemini-usage-source

- **Ficheros:** `usage/gemini/GeminiUsageSource.java`, `usage/gemini/GeminiUsageSourceTest.java` (nuevos, propuesta).
- **Enfoque:** inyectar runner, parser y configuración; aceptar solo resultado ejecutado con código cero y parser con grupos, construir lectura compuesta Gemini y clasificar toda otra condición como consulta fallida sin retener detalles. Declarar intervalo configurado y timeout mayor de 60 s.
- **Pruebas automáticas:** argumentos fijos, datos válidos, ausente/no cero/timeout/inválido como fallo, declaración de tiempos y centinela ausente de logs/resultados; `mvn -f desktop/app/pom.xml test -Dtest=GeminiUsageSourceTest`.
- **Prueba manual:** no aplica; se integra en TASK-016 y TASK-024.

### TASK-014 Ciclo de Gemini en usage-poller

- **Ficheros:** `usage/UsagePoller.java`, `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** mantener una programación por fuente desde fin de ciclo, ejecutar cada lectura en worker aislado y publicar explícitamente consulta fallida Gemini, preservando el descarte especial de OpenCode y los comportamientos Claude/OpenAI.
- **Pruebas automáticas:** reprogramación tras éxito/fallo, ausencia de solape, fallo publicado sin reintento inmediato, fuente Gemini lenta aislada y regresiones de los otros estados; `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest`.
- **Prueba manual:** no aplica; se integra en TASK-016 y TASK-024.

### TASK-015 Publicación de la consulta fallida en usage-snapshot-publisher

- **Ficheros:** `usage/UsageSnapshotPublisher.java`, `usage/UsageProviderState.java`, `usage/UsageSnapshotPublisherTest.java` (modificar).
- **Enfoque:** traducir exclusivamente el nuevo resultado a un estado publicado específico, preservar lectura/hora del último éxito y hacerlo visible incluso sin lectura previa para Gemini; un éxito lo sustituye por correcto sin afectar desactualización Claude ni sesión OpenAI.
- **Pruebas automáticas:** éxito, fallo con/sin lectura, recuperación y regresiones de Claude/OpenAI/OpenCode; `mvn -f desktop/app/pom.xml test -Dtest=UsageSnapshotPublisherTest`.
- **Prueba manual:** no aplica; se observa en TASK-018.

### TASK-016 Integración de usage-poller con gemini-usage-source

- **Ficheros:** `usage/UsagePoller.java`, `usage/gemini/GeminiUsageSource.java`, `usage/GeminiPollingIntegrationTest.java` (nuevo, propuesta), `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** ensamblar el bean Gemini en la lista de fuentes y probar primera lectura asíncrona, cadencia configurada/minimizada y continuidad de proveedores ante fallo.
- **Pruebas automáticas:** primera consulta y 60 s con reloj/controlador simulado, `1s` efectivo a 30 s y fallo aislado; `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest,GeminiPollingIntegrationTest`.
- **Prueba manual:** usar `scripts/start-background`; comprobar retorno inmediato de terminal, ventana receptiva y otros datos durante la primera consulta Gemini.

### TASK-017 Sección Gemini con grupos y ventanas en usage-window

- **Ficheros:** `ui/UsageWindow.java`, `ui/UsageProviderCardPanel.java`, `ui/UsageCardPanel.java`, `ui/UsageWindowTest.java`, `ui/UsageCardPanelTest.java` (modificar).
- **Enfoque:** añadir renderizado específico Gemini por filas dinámicas de `seriesReadings`, una fila por grupo y solo ventanas presentes; formatear decimal, cuenta atrás opcional y hora del último éxito sin mezclar series ni modificar tarjetas existentes.
- **Pruebas automáticas:** tres grupos/cinco ventanas, 1,8 y 0,9 separados, Argon sin 5 h, grupo nuevo, reinicio ausente y regresión de otras secciones; `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest,UsageCardPanelTest`.
- **Prueba manual:** comparar grupos, porcentajes y hora mostrada con `agy -p "/usage" --output-format json`.

### TASK-018 Aviso formal de consulta fallida en usage-window

- **Ficheros:** `ui/UsageProviderCardPanel.java`, `ui/UsageCardPanel.java`, `ui/UsageWindowTest.java`, `ui/UsageCardPanelTest.java` (modificar).
- **Enfoque:** asociar solo el estado Gemini de consulta fallida al literal exacto de REQ-011; mantener filas y hora previas, permitir tarjeta sin filas y eliminar el aviso al recuperar, sin presentar información técnica.
- **Pruebas automáticas:** éxito, fallo con/sin datos, recuperación, centinela ausente de textos y regresión OpenAI/OpenCode; comando de TASK-017.
- **Prueba manual:** retirar `agy` del PATH, reiniciar y verificar el literal; restaurarlo y esperar un ciclo para confirmar recuperación.

### TASK-019 Gráfico de evolución de Gemini en usage-window

- **Ficheros:** `ui/UsageChartPanel.java`, `ui/UsageWindow.java`, `ui/UsageChartPanelTest.java`, `ui/UsageWindowTest.java` (modificar).
- **Enfoque:** consumir las claves de historial proveedor/grupo/ventana para etiquetar series Gemini de forma distinta, conservar actualización EDT y acotar la representación a 0--100.
- **Pruebas automáticas:** puntos ordenados por cada grupo/ventana, repetidos omitidos, extremos 0/100 y regresión de series OpenCode; `mvn -f desktop/app/pom.xml test -Dtest=UsageChartPanelTest,UsageWindowTest`.
- **Prueba manual:** dejar la aplicación abierta varios ciclos y comprobar nuevas series y puntos sin reinicio.

### TASK-020 Contrato: stdio - ejecución de agy-usage

- **Ficheros:** `docs/contracts/stdio/desktop/app/agy-usage.md` (nuevo; responsabilidad exclusiva del documentador).
- **Enfoque:** tras TASK-008 y TASK-010, documentar invocación por PATH, argumentos fijos, 60 s, código de salida, campos consumidos/ignorados y categorías de error sin ejemplos reales de conversación.
- **Pruebas automáticas:** no aplica; el documentador valida esquema de ejemplos y ausencia de datos sensibles.
- **Prueba manual:** cotejar argumento, sobre JSON y cubos descritos contra runner y parser finales.

### TASK-021 Contrato: stdio - revisión de opencode-session-stats

- **Ficheros:** `docs/contracts/stdio/desktop/app/opencode-session-stats.md` (modificar; responsabilidad exclusiva del documentador).
- **Enfoque:** después de TASK-003, reflejar la subclase como origen y confirmar argumentos, 30 s, 1 MB y fallo silencioso, sin presentar la base como API pública.
- **Pruebas automáticas:** no aplica; revisión documental por el documentador.
- **Prueba manual:** cotejar contrato con OpenCodeCliRunner refactorizado.

### TASK-022 Contrato: stdio - revisión de opencode-data-path

- **Ficheros:** `docs/contracts/stdio/desktop/app/opencode-data-path.md` (modificar; responsabilidad exclusiva del documentador).
- **Enfoque:** después de TASK-003 y TASK-004, conservar comando, semántica silenciosa, validación de salida de una línea y consumidor `openai-credentials-reader`.
- **Pruebas automáticas:** no aplica; revisión documental por el documentador.
- **Prueba manual:** cotejar contrato con `runDataPath` y su consumidor final.

### TASK-023 Regresión transversal de Claude, OpenCode y OpenAI

- **Ficheros:** pruebas existentes de `usage/claude/`, `usage/opencode/`, `usage/openai/`, `usage/`, `ui/` y `launcher/` (modificar solo si deben adaptar el tipo decimal sin cambiar expectativas funcionales).
- **Enfoque:** ejecutar y completar la red de regresión tras los cambios compartidos; no introducir ramas específicas de Gemini en fuentes existentes ni alterar los valores/estados publicados de los tres proveedores.
- **Pruebas automáticas:** suite completa de módulo: `mvn -f desktop/app/pom.xml test`.
- **Prueba manual:** comparar porcentajes, tiempos, secciones y sesión caducada con la versión de referencia `f1e70b8` usando los mismos datos.

### TASK-024 Verificación de Gemini de extremo a extremo

- **Ficheros:** `launcher/GeminiResourceAndResponsivenessIntegrationTest.java` (nuevo, propuesta, solo si permite automatizar la latencia con fixture lento); `pom.xml` (modificar si se incorpora al perfil `integration-tests`).
- **Enfoque:** mantener las mediciones reales como evidencia manual; automatizar únicamente aislamiento del EDT ante un runner lento si el fixture no depende de `agy` real. Verificar que el historial nace vacío en una nueva JVM.
- **Pruebas automáticas:** si se crea, respuesta de UI menor de un segundo durante runner simulado lento y `mvn -f desktop/app/pom.xml verify -Pintegration-tests`; las mediciones con `agy` real no aplican a automatización.
- **Prueba manual:** iniciar con `start-background`, medir cinco minutos CPU/heap sin contar `agy`, interactuar durante consulta >8 s y reiniciar para verificar gráfico vacío.

## Orden de implementación

1. TASK-001
2. TASK-002
3. TASK-003
4. TASK-004
5. TASK-005
6. TASK-006
7. TASK-007
8. TASK-008
9. TASK-009
10. TASK-010
11. TASK-011
12. TASK-012
13. TASK-013
14. TASK-014
15. TASK-015
16. TASK-016
17. TASK-017
18. TASK-018
19. TASK-019
20. TASK-023
21. TASK-024
22. TASK-020 (documentador)
23. TASK-021 (documentador)
24. TASK-022 (documentador)

## Riesgos y dudas técnicas

- TASK-001 bloquea el desarrollo: si la CLI real no acepta `--output-format json`, hay que revisar requisitos y diseño antes de crear el runner.
- La extracción de runner puede romper constructores, pruebas o cierre de OpenCode; el inventario de TASK-002 y la regresión de OpenAI preceden a Gemini.
- El tipo decimal afecta modelo, historial, gráfico y UI; centralizar redondeo antes de deduplicar y conservar formato entero para los proveedores existentes.
- CPU, heap y receptividad con `agy` real dependen de la máquina; se automatiza aislamiento y se registran las mediciones manuales de TASK-024.
