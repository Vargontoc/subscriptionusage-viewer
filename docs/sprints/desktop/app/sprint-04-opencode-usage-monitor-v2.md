# Sprint 04: opencode-usage-monitor-v2 - desktop/app

Estado: aprobado

## Alcance

- Tareas: TASK-001 a TASK-021 (de `specs/opencode-usage-monitor-v2/tasks.md`).
- Dependencias externas: Ninguna; todas las tareas y contratos pertenecen a `desktop/app`.

## Enfoque técnico

Se sustituirá la lectura HTTP de OpenCode por un ciclo atómico de tres procesos CLI, aislado del EDT y limitado por un pool demonio de tres hilos. Se ampliará el modelo común para representar porcentajes por serie (modelo y Total), preservando el flujo y la cuenta atrás de Claude.
La configuración validará los valores de v2 al arrancar; los rangos se derivarán de un único instante y los cálculos se mantendrán puros y sin redondeo previo. El publicador conservará exclusivamente el último ciclo OpenCode correcto y la vista ocultará el proveedor cuando no existan modelos con consumo.
Las tareas `Contrato:` son responsabilidad documental del documentador: este sprint solo ordena el trabajo y el desarrollador no crea ni modifica contratos. La retirada definitiva de v1 se realizará al final, después de la verificación funcional y transversal.

## Cambios por tarea

### TASK-001 Configuración de topes model-caps y propiedades de OpenCode

- **Ficheros:** `config/AppProperties.java`, `config/ConfigurationProvider.java`, `src/main/resources/application.yml`, `config/ConfigurationProviderTest.java` (modificar).
- **Enfoque:** reemplazar URL, API key y backoff por `cycle-day`, `default-cap-usd`, intervalo y timeout; enlazar `model-caps` como lista modelo/tope, normalizar día inválido a 1 e intervalo menor de 10 s, y dejar marcadores comentados editables por línea.
- **Pruebas automáticas:** valores por defecto, entrada válida, tope vacío, días 0/32/texto e intervalo mínimo; `mvn -f desktop/app/pom.xml test -Dtest=ConfigurationProviderTest`.
- **Prueba manual:** editar los marcadores y reiniciar con `cycle-day: 20`; la aplicación arranca y acepta el cambio.

### TASK-002 Cálculo de rangos de ventanas

- **Ficheros:** `usage/opencode/UsageWindowRanges.java` y `usage/opencode/UsageWindowRangesTest.java` (nuevos, propuesta).
- **Enfoque:** recibir `Clock`/instante, zona y día ya validado; devolver los tres rangos UTC desde un solo ahora, con días locales para 7 días y el candidato mensual ajustado al último día del mes.
- **Pruebas automáticas:** ejemplos de octubre, transición de ciclo, febrero, abril e igualdad exacta con inicio de ciclo; `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowRangesTest`.
- **Prueba manual:** no aplica; se cubre visible en TASK-020.

### TASK-003 Interpretación del JSON de estadísticas de sesión

- **Ficheros:** `usage/opencode/OpenCodeStatsParser.java`, DTOs/package records de estadísticas y `usage/opencode/OpenCodeStatsParserTest.java` (nuevos, propuesta).
- **Enfoque:** usar Jackson para validar `models`, extraer los campos requeridos, ignorar desconocidos y filtrar exactamente `providerID=opencode-go`; distinguir JSON/esquema inválido de lista válida sin modelos objetivo.
- **Pruebas automáticas:** modelo objetivo con propiedades extra, otro proveedor, JSON inválido, ausencia de `models` y lista sin OpenCode; `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeStatsParserTest`.
- **Prueba manual:** ejecutar el comando real con un rango y contrastar los campos con la salida JSON.

### TASK-004 Ejecución del CLI con timeout y fallo silencioso

- **Ficheros:** `usage/opencode/OpenCodeCliRunner.java` y `usage/opencode/OpenCodeCliRunnerTest.java` (nuevos, propuesta).
- **Enfoque:** construir argumentos separados, consumir stdout concurrentemente con límite de tamaño, descartar stderr y entrada, aplicar el timeout configurado y destruir proceso/descendientes; registrar una sola categoría sin argumentos, stdout ni stderr.
- **Pruebas automáticas:** ejecutable fixture correcto y argumentos separados; ausente, salida 1, bloqueo/timeout, exceso de salida y ausencia de stderr en log; `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeCliRunnerTest`.
- **Prueba manual:** arrancar con y sin `opencode` en PATH; no se muestra error ni se detiene la aplicación.

### TASK-005 Contrato: stdio - invocación de session.stats y salida JSON

- **Ficheros:** `docs/contracts/stdio/desktop/app/opencode-session-stats.md` (nuevo; responsabilidad exclusiva del documentador).
- **Enfoque:** tras TASK-004, el documentador describirá comando, parámetros `from`/`to`/`tools=none`, schema `models`, propiedades ignoradas y fallos silenciosos; debe cotejar runner y parser finales.
- **Pruebas automáticas:** validación documental de formato, campos y tres fallos por el documentador.
- **Prueba manual:** comparar contrato con TASK-003 y TASK-004; todos los campos y parámetros coinciden.

### TASK-006 Pruebas del CLI en Windows (.cmd)

- **Ficheros:** `usage/opencode/OpenCodeCliRunnerTest.java` (modificar) y fixture `.cmd` de prueba (nuevo, propuesta).
- **Enfoque:** adaptar el runner a `cmd.exe` cuando corresponda y comprobar que PATH y los argumentos ISO llegan sin reinterpretación; contemplar `.ps1` solo si el entorno lo requiere.
- **Pruebas automáticas:** `.cmd` correcto, fechas con `:`, `+`, `T`, `Z`, salida 1 y comando ausente; `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeCliRunnerTest` en Windows.
- **Prueba manual:** ejecutar la integración marcada manual con el CLI real disponible solo como `.cmd`.

### TASK-007 Cálculo de porcentajes por modelo y total

- **Ficheros:** `usage/opencode/OpenCodeUsageCalculator.java` y `usage/opencode/OpenCodeUsageCalculatorTest.java` (nuevos, propuesta).
- **Enfoque:** agrupar variantes por id y ventana, sanear costes, resolver topes y calcular porcentajes y Total con denominador de modelos con gasto; saturar a 100 sin redondear el dato de dominio.
- **Pruebas automáticas:** fórmulas 60 USD, Total 31,25 %, saturación, cero/negativo/ausente, marcador y modelo no listado; `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeUsageCalculatorTest`.
- **Prueba manual:** no aplica; se verifica en TASK-015 y TASK-020.

### TASK-008 Ciclo de tres consultas en paralelo

- **Ficheros:** `usage/opencode/OpenCodeUsageSource.java`, modelos de resultado OpenCode y `usage/opencode/OpenCodeUsageSourceTest.java` (modificar o nuevos).
- **Enfoque:** reescribir la fuente para obtener rangos, enviar exactamente tres tareas al pool fijo demonio, combinar runner y parser y devolver datos crudos solo si las tres terminan correctamente; exponer cierre del pool y procesos vivos.
- **Pruebas automáticas:** tres fixtures de 4 s en menos de 8 s y máximo tres; fallo parcial, espera de la más lenta y timeout; `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeUsageSourceTest`.
- **Prueba manual:** observar como máximo tres procesos `opencode` durante un ciclo real.

### TASK-009 Integración: ciclo de OpenCode con el cálculo de porcentajes

- **Ficheros:** `usage/opencode/OpenCodeUsageSource.java`, `usage/opencode/OpenCodeUsageSourceTest.java` y/o `usage/OpenCodePollingIntegrationTest.java` (modificar).
- **Enfoque:** conectar el resultado atómico de TASK-008 al calculador con la tabla de configuración y entregar un único resultado porcentual, sin publicar datos parciales.
- **Pruebas automáticas:** dos modelos y Total 31,25 %, tope por defecto, saturación y fallo de una ventana; `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeUsageSourceTest,OpenCodePollingIntegrationTest`.
- **Prueba manual:** no aplica; se muestra en TASK-015.

### TASK-010 Planificador de refresco fijo sin solapamiento

- **Ficheros:** `usage/UsagePoller.java`, `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** eliminar política OpenCode de backoff/pausa y programar primer ciclo inmediato y siguientes con retardo desde fin; mantener ejecución de cada fuente aislada, sin solapamiento y fuera de interfaz.
- **Pruebas automáticas:** cadencia de 60 s con reloj/planificador controlado, no solape, reintento tras fallo, aislamiento de Claude y mínimo de 10 s/máximo 18 ejecuciones; `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest`.
- **Prueba manual:** comprobar tandas de tres procesos aproximadamente cada 60 s.

### TASK-011 Historial en memoria por modelo y total

- **Ficheros:** `usage/UsageHistory.java`, `usage/UsageHistoryPoint.java`, clave de serie/modelos de lectura y `usage/UsageHistoryTest.java` (modificar).
- **Enfoque:** indexar por proveedor, identificador de serie y ventana; añadir solo cambios de porcentaje, mantener el límite de 100 puntos y no persistir, sin alterar la semántica previa de Claude.
- **Pruebas automáticas:** cambios ordenados, repetidos, modelo tardío, límites ≤100, reinicio vacío y regresión Claude; `mvn -f desktop/app/pom.xml test -Dtest=UsageHistoryTest`.
- **Prueba manual:** no aplica; se comprueba en TASK-016.

### TASK-012 Último estado de OpenCode y hora de último ciclo exitoso

- **Ficheros:** `usage/UsageSnapshotPublisher.java`, `usage/UsageSnapshot.java`, `usage/UsageProviderState.java`, modelos de lectura y `usage/UsageSnapshotPublisherTest.java` (modificar).
- **Enfoque:** simplificar OpenCode a datos o ausencia, conservar lectura y hora previas ante ausencia, y representar ausencia inicial sin estados de error ni desactualización; preservar estados necesarios para Claude hasta TASK-021.
- **Pruebas automáticas:** almacenamiento de modelos/Total y hora de fin, fallo tras éxito y fallo inicial; notificación en dispatcher de UI; `mvn -f desktop/app/pom.xml test -Dtest=UsageSnapshotPublisherTest`.
- **Prueba manual:** no aplica; se muestra en TASK-015.

### TASK-013 Integración: entrega del resultado del ciclo al publicador

- **Ficheros:** `usage/UsagePoller.java`, `usage/UsageSnapshotPublisher.java`, `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** publicar solo el resultado completo y su hora de fin; no invocar al publicador cuando la fuente devuelve sin datos.
- **Pruebas automáticas:** éxito actualiza lectura/hora; ausencia no cambia una publicación previa; `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest,UsageSnapshotPublisherTest`.
- **Prueba manual:** retirar el CLI tras un éxito y confirmar que la última hora no cambia.

### TASK-014 Integración: entrega de puntos del ciclo al historial

- **Ficheros:** `usage/UsagePoller.java`, `usage/UsageHistory.java`, `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** enviar al historial todos los porcentajes por modelo/Total únicamente tras un ciclo atómico correcto; delegar la deduplicación en TASK-011.
- **Pruebas automáticas:** dos resultados distintos, ausencia y repetición idéntica; `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest,UsageHistoryTest`.
- **Prueba manual:** no aplica; se comprueba en TASK-016.

### TASK-015 Integración: publicación del estado a la ventana (filas por modelo y Total)

- **Ficheros:** `ui/UsageWindow.java`, `ui/UsageProviderCardPanel.java`/`ui/UsageCardPanel.java`, modelos de presentación y `ui/UsageWindowTest.java` (modificar).
- **Enfoque:** renderizar sección OpenCode con filas por modelo, fila Total y hora, sin cuenta atrás ni mensajes; ocultarla sin datos o sin consumo y aplicar cambios recibidos desde fondo en EDT, manteniendo tarjetas Claude.
- **Pruebas automáticas:** dos modelos más Total, ausencia sin texto de error, modelo vacío omitido y dispatcher EDT; `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest`.
- **Prueba manual:** comprobar filas y hora con CLI real; sin PATH, OpenCode no aparece y Claude sigue visible.

### TASK-016 Integración: historial a gráfico de evolución

- **Ficheros:** `ui/UsageWindow.java`, `ui/UsageChartPanel.java`, `ui/UsageChartPanelTest.java`, `ui/UsageWindowTest.java` (modificar).
- **Enfoque:** transportar la clave de serie al gráfico, dibujar las tres ventanas de cada modelo y Total, y repintar ante nuevos puntos sin recarga ni persistencia.
- **Pruebas automáticas:** tres puntos por serie, historial vacío y actualización incremental; `mvn -f desktop/app/pom.xml test -Dtest=UsageChartPanelTest,UsageWindowTest`.
- **Prueba manual:** dejar ciclos con cambios y reiniciar; el gráfico se actualiza y vuelve vacío.

### TASK-017 Integración: cierre con consulta en curso

- **Ficheros:** `lifecycle/LifecycleCoordinator.java`, `usage/UsagePoller.java`, `usage/opencode/OpenCodeUsageSource.java`, pruebas `lifecycle/LifecycleCoordinatorTest.java` y `usage/UsagePollerTest.java` (modificar).
- **Enfoque:** encadenar parada de lifecycle, poller y fuente para cancelar programación, cerrar pool y destruir procesos de consulta antes de completar el cierre.
- **Pruebas automáticas:** parada sin ciclo y proceso fixture bloqueado destruido sin excepción; `mvn -f desktop/app/pom.xml test -Dtest=LifecycleCoordinatorTest,UsagePollerTest,OpenCodeUsageSourceTest`.
- **Prueba manual:** cerrar desde bandeja durante un ciclo y verificar que no quedan procesos ni hilos del pool.

### TASK-018 Contrato: http - retirada del endpoint de uso de OpenCode

- **Ficheros:** `docs/contracts/http/desktop/app/opencode-usage.md` (eliminar; responsabilidad exclusiva del documentador).
- **Enfoque:** después de TASK-008, el documentador retirará el contrato HTTP y comprobará que no quedan enlaces activos; el desarrollador no modifica documentación contractual.
- **Pruebas automáticas:** búsqueda documental sin contrato ni `/zen/go/v1/usage`, realizada por el documentador.
- **Prueba manual:** revisar `docs/contracts/http/` y confirmar la ausencia del contrato.

### TASK-019 Regresión de Claude tras los cambios en componentes compartidos

- **Ficheros:** pruebas `usage/claude/ClaudeUsageSourceTest.java`, `usage/UsagePollerTest.java`, `usage/UsageHistoryTest.java`, `usage/UsageSnapshotPublisherTest.java`, `ui/UsageWindowTest.java` y correcciones compartidas estrictamente necesarias (modificar).
- **Enfoque:** ejecutar y completar la cobertura del flujo Claude sobre contrato, planificación, historial, publicación, cuenta atrás y renderizado, garantizando aislamiento cuando OpenCode no tiene datos.
- **Pruebas automáticas:** fichero Claude válido e inválido, historial y cuenta atrás previos, coexistencia y ausencia OpenCode; `mvn -f desktop/app/pom.xml test`.
- **Prueba manual:** iniciar ambos proveedores y repetir sin CLI OpenCode; Claude permanece operativo con cuenta atrás.

### TASK-020 Verificación transversal de arranque, recursos y receptividad

- **Ficheros:** pruebas `launcher/BackgroundLauncherTest.java`, `launcher/OpenCodeResourceAndResponsivenessIntegrationTest.java`, `startup/JavaVersionCompatibilityTest.java` y scripts existentes (modificar solo si la verificación descubre un incumplimiento).
- **Enfoque:** verificar la ruta ensamblada sin llevar trabajo de CLI al EDT; medir manualmente los umbrales del entorno del usuario y mantener las comprobaciones automatizables de Java 21 y arranque sin CLI.
- **Pruebas automáticas:** `mvn -f desktop/app/pom.xml test` y `mvn -f desktop/app/pom.xml verify -Pintegration-tests`; arranque sin CLI y UI receptiva con fixture lento.
- **Prueba manual:** ejecutar `start-background`, medir <30 s, CPU cinco minutos, heap <256 MB y respuesta de bandeja <1 s durante consulta.

### TASK-021 Limpieza del código, configuración y pruebas de v1

- **Ficheros:** `usage/opencode/OpenCodeUsageSource.java`, `config/AppProperties.java`, `config/ConfigurationProvider.java`, `config/OpenCodeApiKey.java` (eliminar), `config/application.yml.example` (eliminar), `.gitignore`, `usage/UsageSource.java`, `usage/UsageReadResult.java`, `usage/UsagePoller.java`, `usage/UsageSnapshotPublisher.java`, `usage/UsageProviderState.java`, `ui/UsageWindow.java` y pruebas v1 asociadas (modificar o eliminar).
- **Enfoque:** retirar HTTP, Bearer, API key, configuración secreta, variantes antiguas, backoff/pausa y estados/mensajes de autenticación OpenCode, sin dañar Claude; eliminar o reescribir fixtures que dependan de v1 y conservar la propiedad residual tolerada.
- **Pruebas automáticas:** suite Java 21 completa, búsqueda de `OPENCODE_API_KEY`, `Authorization: Bearer` y `/zen/go/v1/usage` sin referencias activas, y arranque sin clave; `mvn -f desktop/app/pom.xml test`.
- **Prueba manual:** buscar los tres literales y arrancar sin clave con Claude y CLI; no aparece autenticación.

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
20. TASK-020
21. TASK-021

## Riesgos y dudas técnicas

- `.cmd`/`.ps1` y el PATH heredado por la JVM pueden variar en Windows. Mitigación: fixture Windows, argumentos separados y prueba manual real de TASK-006.
- La simplificación del modelo común puede afectar a Claude. Mitigación: mantener compatibilidad hasta la limpieza final y ejecutar TASK-019 antes de TASK-021.
- CPU, heap y tiempo de arranque dependen de la máquina del usuario. Mitigación: registrar las mediciones manuales de TASK-020 además de las pruebas repetibles.
