# Sprint 03: opencode-usage-monitor - desktop/app

Estado: aprobado

## Alcance

- Tareas: TASK-001, TASK-002, TASK-003, TASK-005, TASK-006, TASK-007, TASK-008, TASK-009, TASK-010, TASK-011, TASK-013, TASK-014, TASK-017, TASK-018, TASK-019, TASK-021, TASK-022, TASK-023 y TASK-024 (de `specs/opencode-usage-monitor/tasks.md`)
- Dependencias externas: Ninguna.

## Enfoque técnico

Se extenderá el modelo de lectura para devolver un resultado clasificado, conservando la compatibilidad funcional de Claude. OpenCode será una fuente HTTP independiente, con tipos de ventana `ROLLING`, `WEEKLY` y `MONTHLY`, normalización en la fuente y una clave encapsulada que solo se revela al construir la cabecera Bearer.
El poller conservará la ejecución fuera del EDT, pero planificará cada fuente con política propia: Claude mantiene 10 s y OpenCode usará retardo desde el final, backoff y pausa según el resultado. Publisher, historial y Swing consumirán estado por proveedor, nunca credenciales.
La configuración local se resolverá una vez al inicio con precedencia explícita sobre el entorno. El lanzador y las verificaciones transversales se incorporarán después de ensamblar la ruta completa.

## Cambios por tarea

### TASK-001 Propiedades de configuración de OpenCode

- **Ficheros:** `config/AppProperties.java`, `config/ConfigurationProvider.java`, `src/main/resources/application.yml` y `config/ConfigurationProviderTest.java` (modificar): añadir URL de uso, intervalo OpenCode y máximo de backoff, con acceso tipado y mínimo efectivo de 10 s.
- **Enfoque:** separar propiedades de Claude y OpenCode bajo `subscription-viewer.opencode`; mantener los valores actuales de Claude y centralizar la normalización del intervalo en el proveedor.
- **Pruebas automáticas:** valores por defecto 60 s, 300 s y endpoint documentado; 5 s se limita a 10 s y Claude sigue en 10 s. Comando: `mvn -f desktop/app/pom.xml test -Dtest=ConfigurationProviderTest`.
- **Prueba manual:** arrancar sin propiedades OpenCode y después con intervalo 5; confirmar arranque y mínimo efectivo de 10 s.

### TASK-022 Valor secreto de la API key

- **Ficheros:** `(propuesta) desktop/app/src/main/java/es/vargontoc/subscriptionviewer/config/OpenCodeApiKey.java` y pruebas de `config` (nuevo); `AppProperties.java` y `ConfigurationProvider.java` (modificar).
- **Enfoque:** encapsular el valor en un tipo con representación fija enmascarada y acceso restringido al adaptador HTTP; evitar que propiedades o registros lo serialicen o impriman.
- **Pruebas automáticas:** el acceso autorizado entrega el valor; `toString` y el volcado de propiedades no contienen ningún carácter de una clave centinela. Comando: `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeApiKeyTest,ConfigurationProviderTest`.
- **Prueba manual:** no aplica; se cubre extremo a extremo en TASK-024.

### TASK-023 Exclusión git y fichero de ejemplo de la API key

- **Ficheros:** `(propuesta) desktop/app/.gitignore` (nuevo o modificar): ignorar `config/application.yml`; `(propuesta) desktop/app/config/application.yml.example` (nuevo): marcador y guía de copia; configuración de arranque (modificar si es necesario): cargar `config/application.yml` local con prioridad sobre el recurso empaquetado.
- **Enfoque:** mantener el ejemplo versionable separado del fichero local y usar un marcador que TASK-021 considere ausente; no introducir una clave en recursos ni fixtures.
- **Pruebas automáticas:** comprobar mediante proceso `git check-ignore` que el fichero local centinela se ignora, que el ejemplo solo tiene marcador y que el fichero local prevalece. Comando: `mvn -f desktop/app/pom.xml test -Dtest=LocalOpenCodeConfigurationTest`.
- **Prueba manual:** crear el fichero local, comprobar que Git lo ignora y que el log solo informa de su origen.

### TASK-021 Resolución de la API key desde application.yml o entorno

- **Ficheros:** `config/AppProperties.java`, `config/ConfigurationProvider.java`, `src/main/resources/application.yml` y `config/ConfigurationProviderTest.java` (modificar).
- **Enfoque:** resolver una sola vez, en orden fichero local, entorno `OPENCODE_API_KEY` y ausencia; tratar vacío y marcador como no definidos y registrar solo disponibilidad y origen. Exponer presencia al poller y el secreto únicamente a la fuente.
- **Pruebas automáticas:** cubrir cada origen, precedencia del YAML, vacío/marcador/sin origen y captura de log sin centinela. Comando: `mvn -f desktop/app/pom.xml test -Dtest=ConfigurationProviderTest`.
- **Prueba manual:** probar ambos orígenes, después solo entorno y finalmente ninguno, verificando que no aparece diálogo ni clave en logs.

### TASK-002 Resultado clasificado en usage-source

- **Ficheros:** `usage/UsageSource.java`, `(propuesta) usage/UsageReadResult.java`, `UsageReading.java` y pruebas de `usage` (modificar o nuevo).
- **Enfoque:** sustituir la ausencia opcional por resultado inmutable con datos, clave ausente, clave inválida, sin suscripción o fallo transitorio; la envoltura de la fuente convierte excepciones en transitorio.
- **Pruebas automáticas:** datos conservan ventanas; excepción de red no escapa; cada causa es distinguible; regresión de Claude. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageSourceTest,ClaudeUsageSourceTest`.
- **Prueba manual:** no aplica; se observa en TASK-011 y TASK-013.

### TASK-003 Adaptación de claude-usage-source al resultado clasificado

- **Ficheros:** `usage/claude/ClaudeUsageSource.java` y `usage/claude/ClaudeUsageSourceTest.java` (modificar).
- **Enfoque:** adaptar las salidas de lectura local al nuevo resultado, preservando los porcentajes, reinicios y tolerancia existentes; los problemas de fichero o parseo pasan a transitorio o sin datos conforme al contrato común.
- **Pruebas automáticas:** fixture válido sin cambios de valores y contenido inválido sin excepción ni datos publicados. Comando: `mvn -f desktop/app/pom.xml test -Dtest=ClaudeUsageSourceTest`.
- **Prueba manual:** validar tarjeta Claude con fichero válido e inválido sin detener la aplicación.

### TASK-005 Normalización de las dos variantes del contrato de OpenCode

- **Ficheros:** `(propuesta) desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeUsageSource.java`, DTOs privados del paquete y `usage/opencode/OpenCodeUsageSourceTest.java` (nuevo); `UsageWindowType.java` (modificar).
- **Enfoque:** deserializar tolerando propiedades adicionales, convertir cada ventana presente a la lectura común y calcular `resetInSec` desde la finalización de la consulta. Omitir únicamente ventanas ilegibles, no toda la respuesta.
- **Pruebas automáticas:** ambas variantes, mezcla por ventanas, ausencia de `weekly` y porcentaje no numérico con las demás ventanas válidas. Comando: `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeUsageSourceTest`.
- **Prueba manual:** procesar una respuesta real anonimizada y confirmar ventanas presentes sin fallo por campos desconocidos.

### TASK-006 Consulta HTTP y clasificación de fallos de OpenCode

- **Ficheros:** `usage/opencode/OpenCodeUsageSource.java` y `usage/opencode/OpenCodeUsageSourceTest.java` (modificar).
- **Enfoque:** usar `java.net.http.HttpClient` de Java 21 con límites de conexión y petición; construir Bearer en el único punto autorizado, sin registrar solicitud, cuerpo, cabecera ni mensaje de excepción. Mapear 401, 403, 429, 5xx, red y timeout al resultado clasificado.
- **Pruebas automáticas:** servidor HTTP simulado verifica Bearer y 200; 401/403, sin clave sin solicitud, timeout/conexión/429/5xx transitorios y logs sin centinela. Comando: `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeUsageSourceTest`.
- **Prueba manual:** consultar con clave válida, falsa y sin red; comprobar clasificación sin excepción visible.

### TASK-007 Historial: deduplicación por valores para OpenCode

- **Ficheros:** `usage/UsageHistory.java`, `usage/UsageHistoryPoint.java` y `usage/UsageHistoryTest.java` (modificar).
- **Enfoque:** para OpenCode comparar porcentaje e instante de reinicio contra el último punto de la misma serie antes de añadir; conservar la regla cronológica actual de Claude y el límite en memoria.
- **Pruebas automáticas:** tres cambios ordenados, lectura repetida a distinta hora sin punto, expulsión del más antiguo y regresión Claude. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageHistoryTest`.
- **Prueba manual:** no aplica; se visualiza en TASK-014.

### TASK-008 Cuenta atrás: ventanas variables, días y reinicio vencido

- **Ficheros:** `usage/CountdownEvaluator.java`, `usage/CountdownResult.java` y `usage/CountdownEvaluatorTest.java` (modificar).
- **Enfoque:** mantener duración no negativa y añadir formato/presentación suficiente para días completos, preservando horas y minutos para límites cortos; una ventana vencida queda inválida hasta nueva lectura.
- **Pruebas automáticas:** reinicio a 2 días, regresión a 3 horas y reinicio pasado sin duración negativa. Comando: `mvn -f desktop/app/pom.xml test -Dtest=CountdownEvaluatorTest`.
- **Prueba manual:** no aplica; se comprueba en TASK-013.

### TASK-009 Publicador: estado por proveedor y desactualización

- **Ficheros:** `usage/UsageSnapshotPublisher.java`, `UsageSnapshot.java`, `(propuesta) usage/UsageProviderState.java` y `usage/UsageSnapshotPublisherTest.java` (modificar o nuevo).
- **Enfoque:** conservar último dato y última consulta exitosa por proveedor junto a causa y desactualización; publicar snapshots inmutables en EDT sin almacenar ni aceptar la clave.
- **Pruebas automáticas:** éxito, transitorio tras 20 %, 401 conservando dato, clave ausente sin hora, fallo inicial sin datos, aislamiento y ausencia de centinela. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageSnapshotPublisherTest`.
- **Prueba manual:** no aplica; se observa en TASK-013.

### TASK-010 Planificador: intervalo por fuente, backoff y pausa

- **Ficheros:** `usage/UsagePoller.java` y `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** sustituir el ciclo global de retardo fijo por programación por fuente, inyectando reloj/planificador para pruebas. OpenCode usa su intervalo, duplicación limitada 60/120/240/300, pausa en 401/403 y cero solicitudes sin clave; Claude conserva 10 s sin backoff.
- **Pruebas automáticas:** cadencia desde fin, pausa, secuencia de backoff y restablecimiento, no solapamiento, máximo seis solicitudes/minuto, ausencia de clave y aislamiento/regresión Claude. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest`.
- **Prueba manual:** con proxy, comprobar cadencia válida y una única petición con clave inválida durante tres minutos.

### TASK-011 Integración: usage-poller con opencode-usage-source

- **Ficheros:** `usage/UsagePoller.java`, `usage/opencode/OpenCodeUsageSource.java` y `(propuesta) usage/OpenCodePollingIntegrationTest.java` (modificar o nuevo).
- **Enfoque:** registrar la fuente Spring de OpenCode y encadenar su resultado clasificado al historial y publicador; la ruta sin clave publica el estado correspondiente sin llegar a la fuente HTTP.
- **Pruebas automáticas:** servidor simulado 200 llega a historial/publicador; 401 pausa; red activa backoff; sin clave publica ausencia sin petición. Comando: `mvn -f desktop/app/pom.xml test -Dtest=OpenCodePollingIntegrationTest`.
- **Prueba manual:** con clave válida, observar una petición OpenCode cada 60 s con autenticación.

### TASK-013 Ventana: tarjetas, estados y hora de actualización

- **Ficheros:** `ui/UsageWindow.java`, `ui/UsageCardPanel.java`, pruebas `ui/UsageWindowTest.java` y, si simplifica estado, `(propuesta) ui/UsageProviderCardPanel.java` (modificar o nuevo).
- **Enfoque:** renderizar tarjetas desde el estado del proveedor: ventanas variables, días, última consulta, dato desactualizado, mensajes no técnicos para 401/403 y aviso de configuración ausente. Nunca representar ni transportar el secreto; conservar tarjeta Claude y actualización en EDT.
- **Pruebas automáticas:** 30 % y 2 días, regresión Claude, 401/403, dato antiguo desactualizado, primer fallo neutro y ausencia de clave sin campo de entrada. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest`.
- **Prueba manual:** comprobar ambas tarjetas, recuperación de red, falta de clave y apertura desde el menú existente.

### TASK-014 Ventana: gráfico de evolución

- **Ficheros:** `ui/UsageWindow.java`, `ui/UsageChartPanel.java`, `ui/UsageChartPanelTest.java` y `ui/UsageWindowTest.java` (modificar).
- **Enfoque:** alimentar el gráfico con series OpenCode del historial y repintar al recibir snapshots; mantener vacío válido y sin persistencia entre instancias.
- **Pruebas automáticas:** tres puntos ordenados, repetición sin punto y gráfico vacío. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageChartPanelTest,UsageWindowTest`.
- **Prueba manual:** observar puntos en vivo, ausencia de duplicado y gráfico vacío tras reiniciar.

### TASK-017 Lanzador de arranque en segundo plano

- **Ficheros:** `(propuesta) desktop/app/scripts/start-background.cmd` (nuevo) y `(propuesta) desktop/app/scripts/start-background.sh` (nuevo); pruebas de `launcher` (nuevo).
- **Enfoque:** los lanzadores localizarán Java 21 y el JAR, iniciarán un proceso desacoplado con salida controlada y devolverán de inmediato; reportarán error claro si no pueden iniciar. La aplicación Java no cambia su ciclo de vida.
- **Pruebas automáticas:** proceso hijo confirma retorno antes de cierre y proceso vivo; entorno sin Java devuelve código no nulo y mensaje claro. Comando: `mvn -f desktop/app/pom.xml test -Dtest=BackgroundLauncherTest`.
- **Prueba manual:** ejecutar el lanzador, cerrar terminal y confirmar bandeja disponible antes de 30 s.

### TASK-019 Contrato: http - consumo de uso de OpenCode Go

- **Ficheros:** `(propuesta) docs/contracts/http/desktop/app/opencode-usage.md` (nuevo; responsabilidad del documentador).
- **Enfoque:** tras TASK-006, documentar GET, marcador Bearer, variantes JSON, propiedades ignoradas, 401/403 y fallo transitorio conforme a la implementación. Revisar antes el contenido no versionado indicado por diseño, sin usarlo como requisito ni incorporarlo sin decisión del usuario.
- **Pruebas automáticas:** validar JSON de ambos ejemplos, cobertura de estados de error y ausencia de claves reales o con forma de clave. Comando: validación documental definida por documentador.
- **Prueba manual:** cotejar petición, variantes y errores documentados frente a TASK-006.

### TASK-024 Protección de la API key de extremo a extremo (NFR transversal)

- **Ficheros:** `(propuesta) desktop/app/src/test/java/es/vargontoc/subscriptionviewer/launcher/OpenCodeApiKeyProtectionIntegrationTest.java` (nuevo); fixtures de servidor y captura de logs/UI de `src/test` (nuevos).
- **Enfoque:** ensamblar configuración, fuente, poller, publisher y vista con una clave centinela; inspeccionar logs y textos renderizados sin imprimir el valor durante el diagnóstico.
- **Pruebas automáticas:** clave válida, ausente, 401, 403 y red no filtran clave ni fragmentos ni `Authorization`; sin clave no hay solicitud y el fichero local está ignorado. Comando: `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeApiKeyProtectionIntegrationTest`.
- **Prueba manual:** revisar logs, interfaz, búsqueda de repositorio y estado Git con clave válida e inválida.

### TASK-018 Consumo de recursos y respuesta de la interfaz (NFR transversal)

- **Ficheros:** `(propuesta) desktop/app/src/test/java/es/vargontoc/subscriptionviewer/launcher/OpenCodeResourceAndResponsivenessIntegrationTest.java` (nuevo); utilidades de prueba de servidor lento (nuevo).
- **Enfoque:** medir la ruta ya integrada sin incorporar trabajo de red al EDT; usar servidor simulado con seis segundos de demora para comprobar que abrir la vista no espera a la fuente.
- **Pruebas automáticas:** suite completa con OpenCode activo y apertura/interacción de UI inferior a un segundo durante consulta lenta. Comando: `mvn -f desktop/app/pom.xml test`.
- **Prueba manual:** con el lanzador, medir cinco minutos de CPU y heap, y abrir la ventana durante una consulta lenta.

## Orden de implementación

1. TASK-001
2. TASK-022
3. TASK-023
4. TASK-021
5. TASK-002
6. TASK-003
7. TASK-005
8. TASK-006
9. TASK-007
10. TASK-008
11. TASK-009
12. TASK-010
13. TASK-011
14. TASK-013
15. TASK-014
16. TASK-017
17. TASK-019
18. TASK-024
19. TASK-018

## Riesgos y dudas técnicas

- El cargador actual de Spring puede no priorizar automáticamente `desktop/app/config/application.yml` al ejecutar el JAR desde otra carpeta. **Mitigación:** probar explícitamente la ubicación de TASK-023 y fijar la ubicación adicional y su precedencia en el lanzador/configuración, sin versionar la clave.
- El cambio de `Optional` a resultado clasificado afecta a Claude y a todos los consumidores. **Mitigación:** migrar primero el contrato, conservar pruebas de regresión y completar el ensamblaje antes de modificar la vista.
- La UI Swing y las pruebas de lanzadores son dependientes de plataforma o headless. **Mitigación:** separar pruebas de lógica, condicionar las gráficas y ejecutar las manuales en Windows con entorno gráfico.
- El contrato HTTP es responsabilidad del documentador y no se crea desde este módulo de análisis. **Mitigación:** mantener TASK-019 después de la fuente implementada y validar que refleja su comportamiento final.
