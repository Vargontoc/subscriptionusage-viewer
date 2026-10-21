# Sprint 07: provider-tabs-ui - desktop/app

Estado: aprobado

## Alcance

- Tareas: TASK-001 a TASK-024 (de `specs/provider-tabs-ui/tasks.md`).
- Dependencias externas: Ninguna; todas las tareas pertenecen a `desktop/app`.

## Enfoque técnico

La vista Swing se reorganizará alrededor de modelos inmutables y comparables por valor, calculados sin E/S desde el `UsageSnapshot`, el reloj y `CountdownEvaluator`. Los siete componentes nuevos se ubicarán en `src/main/java/es/vargontoc/subscriptionviewer/ui/` (propuesta), separados entre lógica de modelo/presentación y pintado Swing.
Primero se preservará el porcentaje sin redondear en las fuentes y el cálculo; Gemini mantendrá su deduplicación histórica al redondear exclusivamente para comparar. El publicador y el sondeador pasarán a comunicar siempre el resultado clasificado de cada ciclo, conservando internamente la última lectura correcta pero sin exponerla durante errores.
La antigua composición de tarjetas y gráfico se sustituirá por un diálogo fijo con barra propia de pestañas y panel de proveedor; no se eliminarán `UsageHistory` ni `UsageChartPanel`, pero dejarán de instanciarse desde `UsageWindow`. Todas las notificaciones y el refresco de un segundo se procesarán en EDT; solo se reconstruirán paneles cuyo modelo cambie para conservar selección y desplazamiento.

## Cambios por tarea

### TASK-001 Nivel de color de la barra por umbrales

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProgressLevel.java` (nuevo, propuesta); `src/test/java/es/vargontoc/subscriptionviewer/ui/ProgressLevelTest.java` (nuevo, propuesta).
- **Enfoque:** definir una función pura y un tipo de nivel para clasificar el `double` sin formato ni redondeo: verde hasta 50, amarillo hasta 75, naranja hasta 90, rojo por debajo de 100 y negro solo en 100 exacto; acotar o validar entradas fuera del rango de forma coherente con las lecturas ya acotadas.
- **Pruebas automáticas:** comprobar 0, 25, 50, 50,1, 75, 75,01, 90, 90,01, 99, 99,5, 99,96 y 100, incluido que 99,96 es rojo; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProgressLevelTest`.
- **Prueba manual:** no aplica; se verifica visualmente en TASK-015.

### TASK-002 Catálogo de textos formales de error y de primera consulta

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderErrorTexts.java` (nuevo, propuesta); `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderErrorTextsTest.java` (nuevo, propuesta).
- **Enfoque:** centralizar los literales exactos de REQ-006 y `Obteniendo datos…`, seleccionados por identificador canónico de proveedor y `UsageProviderState.Estado`; distinguir solo la sesión caducada de OpenAI y no aceptar ni interpolar datos externos.
- **Pruebas automáticas:** verificar carácter a carácter los textos de Claude, OpenCode, Gemini y las dos causas de OpenAI, y que el texto de primera consulta no contiene error, fallo ni aviso; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderErrorTextsTest`.
- **Prueba manual:** cotejar los cinco textos y el signo de elipsis con REQ-006 y REQ-018.

### TASK-003 Gemini: el parser entrega el porcentaje sin redondear

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiUsageParser.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiUsageParserTest.java` (modificar).
- **Enfoque:** eliminar el `setScale` del cálculo de porcentaje, conservando aritmética decimal, finitud, saturación 0--100 y el resto de validación segura del sobre JSON; el formateo a un decimal pasa a la vista.
- **Pruebas automáticas:** adaptar fixtures para que 1,84 y 99,96 se conserven, y mantener los límites de fracciones fuera de rango a 0/100; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=GeminiUsageParserTest`.
- **Prueba manual:** ejecutar la regresión del parser y confirmar que solo cambian expectativas que asumían redondeo en origen.

### TASK-004 Historial de Gemini: deduplicación con un decimal

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/UsageHistoryTest.java` (modificar).
- **Enfoque:** conservar el valor original en la lectura y aplicar el redondeo a un decimal exclusivamente al comparar/guardar puntos Gemini, sin cambiar las reglas de Claude, OpenAI u OpenCode ni la retención en memoria.
- **Pruebas automáticas:** comprobar que 1,84 y 1,76 se deduplican, que 1,86 y 1,84 generan dos puntos, y ejecutar regresiones de las demás series; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageHistoryTest`.
- **Prueba manual:** comprobar que las pruebas históricas conservan la acumulación aprobada de Gemini.

### TASK-005 Claude: la fuente entrega el porcentaje sin redondear

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/claude/ClaudeUsageSource.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/claude/ClaudeUsageSourceTest.java` (modificar).
- **Enfoque:** retirar el redondeo de `toWindow`, mantener validación de rango, reinicio y clasificación de fichero ausente, ilegible o corrupto como resultado sin datos/fallo clasificado.
- **Pruebas automáticas:** cubrir 40, 50,1 y 99,96 sin pérdida decimal y el fichero inválido como ausencia de porcentaje; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ClaudeUsageSourceTest`.
- **Prueba manual:** si existe estado real, contrastar sus decimales con los expuestos por la fuente.

### TASK-006 OpenAI: el cliente entrega el porcentaje sin redondear

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/openai/OpenAiUsageClient.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/openai/OpenAiUsageClientTest.java` (modificar).
- **Enfoque:** conservar el `double` validado de `used_percent` al construir ventanas, sin conversión entera; mantener 401/403 como `SESION_CADUCADA` y los demás problemas como fallo clasificado seguro.
- **Pruebas automáticas:** comprobar 40, 50,1 y 99,96, además de 401/403 sin porcentaje ni reclasificación como red; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=OpenAiUsageClientTest`.
- **Prueba manual:** revisar las respuestas fixture y los valores recibidos por el cliente sin redondeo.

### TASK-007 OpenCode: el cálculo entrega el porcentaje sin redondear

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeUsageCalculator.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeUsageCalculatorTest.java` (modificar).
- **Enfoque:** verificar el flujo `BigDecimal` hasta las lecturas y retirar cualquier redondeo prematuro si apareciera, preservando mínimo histórico y saturación a 100 de modelos y total.
- **Pruebas automáticas:** cubrir total 31,25, valor 99,96 y saturación por encima de tope a 100; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=OpenCodeUsageCalculatorTest`.
- **Prueba manual:** ejecutar la suite del cálculo y confirmar que los casos de saturación permanecen intactos.

### TASK-008 Publicador: publicar siempre el resultado de cada consulta

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisherTest.java` (modificar).
- **Enfoque:** eliminar las ramas que silencian OpenCode, el fallo inicial de OpenAI y ausencias; publicar un estado por resultado, conservar por separado la última lectura correcta y su hora, y no borrar el estado visible en un fallo/ausencia.
- **Pruebas automáticas:** éxito publicado, ausencia Claude publicada sin borrar la lectura interna, fallo inicial OpenAI visible y recuperación posterior; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageSnapshotPublisherTest`.
- **Prueba manual:** ejecutar secuencia datos, fallo y ausencia, comprobando un estado publicado tras cada paso.

### TASK-009 Sondeador: entregar todos los resultados de cada ciclo

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/UsagePollerTest.java` (modificar).
- **Enfoque:** retirar el filtro especial de OpenCode en la entrega al publisher, registrar historial solo para datos correctos y garantizar exactamente una publicación clasificada por ciclo de cada fuente, incluida ausencia Claude y ciclo OpenCode inválido o agotado.
- **Pruebas automáticas:** datos OpenCode, consulta OpenCode descartada como fallo, ausencia Claude y fallo inicial OpenAI entregados al publisher; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest`.
- **Prueba manual:** iniciar sin `opencode` y confirmar en la traza controlada del publicador que el primer ciclo se publica como fallo.

### TASK-010 Modelo de vista: estado, error y hora por proveedor

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModel.java` (nuevo, propuesta); `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModelTest.java` (nuevo, propuesta).
- **Enfoque:** definir records inmutables comparables por valor para proveedor, estado de presentación, tablas, filas y celdas; construir desde snapshot/estado y reloj: falta de entrada equivale a obteniendo, estado no correcto equivale a gris/error sin lectura expuesta, y datos correctos incluyen hora propia.
- **Pruebas automáticas:** datos correctos con hora y sin gris, proveedor sin entrada con `Obteniendo datos…`, y fallo que oculta última lectura/hora y usa TASK-002; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderViewModelTest`.
- **Prueba manual:** no aplica; se comprueba en TASK-017 y TASK-019.

### TASK-011 Modelo de vista: vigencia de Claude y OpenAI

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModel.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModelTest.java` (modificar).
- **Enfoque:** invocar `CountdownEvaluator` al construir cada modelo de Claude/OpenAI; omitir tablas de ventanas caducadas, mantener datos si queda alguna vigente y derivar error formal si no queda ninguna. Ajustar la semántica actual del evaluador de OpenAI si impide aplicar DEC-010.
- **Pruebas automáticas:** ambas ventanas vigentes, solo semanal vigente y ambas caducadas con texto específico Claude/OpenAI; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderViewModelTest,CountdownEvaluatorTest`.
- **Prueba manual:** tras TASK-022, abrir Claude con 5 h caducada y semanal vigente y comprobar que solo aparece la semanal.

### TASK-012 Modelo de vista: tablas de Claude y OpenAI

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModel.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModelTest.java` (modificar).
- **Enfoque:** producir tablas independientes `Uso 5h` y `Uso semanal`, con columna `Reinicia en`, barra con valor sin redondear y texto presentado con la precisión del proveedor; excluir título, pie y `planType`.
- **Pruebas automáticas:** dos tablas Claude con 40 % y tiempos, OpenAI solo con 5 h cuando falta semanal, y ausencia de plan para respuesta con `plan_type`; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderViewModelTest`.
- **Prueba manual:** cotejar las cabeceras exactas y ausencia de plan/título/pie.

### TASK-013 Modelo de vista: tablas de Gemini

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModel.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModelTest.java` (modificar).
- **Enfoque:** generar tres tablas alineables a partir de `seriesReadings`: modelo, 5 h y semanal; ordenar primero los tres nombres establecidos, añadir desconocidos conservando nombre, omitir grupos sin cubos y representar cada ventana faltante con dos celdas `N/A`; formatear Gemini a un decimal.
- **Pruebas automáticas:** tres grupos en orden, Argon sin 5 h con `N/A`, valores 1,8/0,9 por ventana, grupos vacíos omitidos y grupo desconocido añadido; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderViewModelTest`.
- **Prueba manual:** revisar que las tres tablas tienen filas y orden idénticos.

### TASK-014 Modelo de vista: tablas de OpenCode

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModel.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderViewModelTest.java` (modificar).
- **Enfoque:** modelar tabla `Modelo` y tabla de tres usos a partir de las series de OpenCode, añadiendo `Total` al final y manteniendo una fila por modelo en ambas; no crear columnas de reinicio/tiempo y usar el porcentaje sin redondear para barra aunque el texto siga la precisión vigente del proveedor.
- **Pruebas automáticas:** dos modelos más total, total 31,25 en las tres ventanas, alineación de A con 50 y adición posterior de B sin duplicar cabeceras; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderViewModelTest`.
- **Prueba manual:** comprobar dos tablas con mismo número de filas y sin tiempo restante.

### TASK-015 Barra de progreso propia

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/UsageProgressBar.java` (nuevo, propuesta); `src/test/java/es/vargontoc/subscriptionviewer/ui/UsageProgressBarTest.java` (nuevo, propuesta).
- **Enfoque:** crear componente Swing de pintado determinista que recibe porcentaje sin redondear y texto del modelo, pinta borde negro/fondo blanco/relleno proporcional con `ProgressLevel` y centra texto negro salvo negro completo al 100, donde será blanco.
- **Pruebas automáticas:** inspeccionar estado/pintado o propiedades para 40, 0, 100 y 99,96 mostrado como 100,0 pero rojo; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageProgressBarTest`.
- **Prueba manual:** en ventana temporal, comprobar 0, 40, 99,96 y 100, incluido contraste del texto.

### TASK-016 Tabla de datos sin bordes

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/UsageTable.java` (nuevo, propuesta); `src/test/java/es/vargontoc/subscriptionviewer/ui/UsageTableTest.java` (nuevo, propuesta).
- **Enfoque:** representar el modelo de tabla mediante paneles/celdas Swing sin bordes, cabecera negra de texto blanco, alternancia gris/blanco y constantes compartidas de alto; delegar celdas de porcentaje a `UsageProgressBar` y tratar `N/A` como texto.
- **Pruebas automáticas:** cabecera y tres filas alternas, tabla vacía sin pie y celda N/A sin barra; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageTableTest`.
- **Prueba manual:** revisar visualmente ausencia de bordes y pie.

### TASK-017 Contenedor de pestaña: estado gris y hora de actualización

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderContentPanel.java` (nuevo, propuesta); `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderContentPanelTest.java` (nuevo, propuesta).
- **Enfoque:** pintar exclusivamente el `ProviderViewModel`: fondo gris y mensaje sin otro componente para obteniendo/error, o línea `última actualización` arriba a la izquierda para datos; no introducir nombre de proveedor, plan ni pie.
- **Pruebas automáticas:** hora para datos, error Claude gris sin hora y primera consulta sin términos prohibidos; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderContentPanelTest`.
- **Prueba manual:** tras TASK-022, ocultar estado Claude y comprobar solo el texto formal sobre gris.

### TASK-018 Contenedor de pestaña: tablas en fila y desplazamiento conjunto

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderContentPanel.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderContentPanelTest.java` (modificar).
- **Enfoque:** alojar un panel horizontal de `UsageTable` en un único `JScrollPane` vertical, con borde superior común y una constante única de separación; reutilizar alturas de TASK-016 para conservar alineación de filas homólogas.
- **Pruebas automáticas:** tres tablas con misma coordenada superior y separación configurada, contenido alto con un único scroll compartido y contenido corto sin barra; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderContentPanelTest,UsageTableTest`.
- **Prueba manual:** tras TASK-022, desplazar OpenCode y confirmar movimiento conjunto y alineación.

### TASK-019 Fila de pestañas fijas

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderTabBar.java` (nuevo, propuesta); `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderTabBarTest.java` (nuevo, propuesta); `src/main/resources/icons/claude.png`, `openai.png`, `opencode.png`, `gemini.png` (verificar que están versionados).
- **Enfoque:** construir cuatro controles en orden canónico, cargar PNG desde recursos tolerando icono ausente, aplicar estado gris y resaltado independiente de selección, y exponer callbacks de selección; mantener los identificadores de proveedor centralizados.
- **Pruebas automáticas:** orden, nombre e icono de las cuatro pestañas, degradación a nombre ante icono faltante y selección distinguible con todas grises; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderTabBarTest`.
- **Prueba manual:** ejecutar `git ls-files desktop/app/src/main/resources/icons` y verificar los cuatro PNG requeridos.

### TASK-020 Botón cerrar en la fila de pestañas

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderTabBar.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/ProviderTabBarTest.java` (modificar).
- **Enfoque:** añadir tras un hueco elástico un control de cierre en el extremo derecho que solo emita una solicitud de ocultación, sin acoplar la barra a terminación de proceso o a `JDialog`.
- **Pruebas automáticas:** comprobar posición final tras Gemini y callback de ocultar, no de cierre de aplicación; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=ProviderTabBarTest`.
- **Prueba manual:** tras TASK-021, abrir la ventana y comprobar alineación a la derecha.

### TASK-021 Ventana de tamaño fijo que solo se oculta

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/UsageWindowTest.java` (modificar).
- **Enfoque:** reemplazar el `JFrame` actual por diálogo sin propietario con decoración de sistema, tamaño constante no redimensionable y política de cierre que llama a `hide`; conservar API `show`, `hide` e `isVisible` para `TrayMenu`, conectando el evento de TASK-020.
- **Pruebas automáticas:** mostrar/ocultar y cierre de título que conservan proceso/visibilidad, propiedades sin resize/minimizar/maximizar y doble clic sin cambio de tamaño cuando sea automatizable; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest,TrayMenuTest`.
- **Prueba manual:** mover por título, intentar redimensionar/doble clic, cerrar por ambos mecanismos y reabrir desde bandeja.

### TASK-022 Composición de la ventana: pestañas, contenido y sin gráfico

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/UsageWindowTest.java` (modificar).
- **Enfoque:** sustituir tarjetas, etiqueta neutra y gráfico por `ProviderTabBar` y conmutador de cuatro `ProviderContentPanel`; inicializar Claude, guardar selección solo en memoria, usar tamaño derivado para tres tablas Gemini y no instanciar `UsageChartPanel` ni consumir `UsageHistory` desde la ventana.
- **Pruebas automáticas:** Claude inicial, Gemini conservada tras ocultar/mostrar, y composición idéntica con historial vacío/datos sin gráfico; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest`.
- **Prueba manual:** recorrer pestañas con Gemini de tres grupos y OpenCode, verificar sin gráfico ni scroll en Claude/OpenAI/Gemini.

### TASK-023 Ventana: refresco automático y repintado solo al cambiar

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java`, `src/test/java/es/vargontoc/subscriptionviewer/ui/UsageWindowTest.java` (modificar).
- **Enfoque:** en notificación y temporizador de 1 s, recomputar los cuatro modelos en EDT y compararlos por valor con el último mapa; actualizar siempre estado de la barra de pestañas, reemplazar solo el panel alterado y preservar scroll/selección. Ninguna consulta se hará desde la interfaz.
- **Pruebas automáticas:** cambio Claude 40→60 actualiza color, consulta lenta Gemini no bloquea cambio de pestaña, y modelo OpenCode idéntico no reconstruye ni pierde posición; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest`.
- **Prueba manual:** desplazar OpenCode durante más de un minuto, cambiar durante consulta lenta y comprobar decremento de tiempo restante.

### TASK-024 Integración: publicador a ventana, cambio de estado sin reiniciar

- **Ficheros:** `src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java`, `src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java`, `src/test/java/es/vargontoc/subscriptionviewer/usage/UsageWindowLiveUpdateIntegrationTest.java` (modificar o ampliar); `src/test/java/es/vargontoc/subscriptionviewer/ui/UsageWindowTest.java` (modificar si procede).
- **Enfoque:** verificar extremo a extremo el dispatcher EDT ya utilizado por publisher y el consumo de snapshots por ventana: cada estado sustituye el modelo de pestaña/contenido sin reinicio, mientras la segunda consulta en curso no crea un estado de primera consulta.
- **Pruebas automáticas:** fallo seguido de datos vigentes quita gris, primera consulta Gemini pasa de obteniendo a error, y proveedor ya en error conserva el texto durante siguiente consulta; ejecutar `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowLiveUpdateIntegrationTest,UsageWindowTest,UsageSnapshotPublisherTest`.
- **Prueba manual:** renombrar/restaurar temporalmente el estado Claude y probar `agy` lento, verificando las tres transiciones de REQ-018.

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
22. TASK-022
23. TASK-023
24. TASK-024

## Riesgos y dudas técnicas

- La semántica actual de `CountdownEvaluator` declara vigentes ventanas OpenAI vencidas; TASK-011 debe alinearla con DEC-010 sin cambiar Gemini.
- La sustitución de `JFrame` por diálogo puede requerir adaptar pruebas no headless y accesos internos que hoy exponen tarjetas/gráfico; mantener la API pública de bandeja y retirar o reemplazar solo ayudas de prueba obsoletas.
- Los PNG existen en el árbol de recursos, pero TASK-019 debe confirmar que están versionados antes de cerrar la tarea.
- La precisión textual de OpenCode sigue el supuesto aprobado S4: barra con valor exacto y texto con la precisión actual; si se exige mostrar 31,25 literalmente, deberá aclararse antes de modificar el formato.
