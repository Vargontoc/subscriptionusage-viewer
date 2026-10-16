# Sprint 02: claude-usage-monitor - desktop/app

Estado: aprobado

## Alcance

- Tareas: TASK-001, TASK-002, TASK-003, TASK-004, TASK-005, TASK-006, TASK-007, TASK-008, TASK-009, TASK-010, TASK-011, TASK-012, TASK-013, TASK-014, TASK-015, TASK-016, TASK-017, TASK-018, TASK-019 (de `specs/claude-usage-monitor/tasks.md`)
- Dependencias externas: Ninguna.

## Enfoque técnico

El hook de status line ya existe y queda fuera de implementación y pruebas de construcción de este sprint; TASK-001 y TASK-002 validan exclusivamente el fichero de estado y el comportamiento observable que produce. La aplicación incorpora los componentes Java bajo los paquetes `usage` y `ui`, conservando la inyección de dependencias de Spring y la UI Swing/AWT ya usada por la bandeja. Modela proveedor, ventana, lectura y punto de historial mediante tipos inmutables internos; cada fuente entrega ausencia ante fallos y el poller las aísla en un ejecutor de fondo de un solo hilo. El estado para la ventana se publica en el EDT, mientras que la caducidad se recalcula periódicamente en la propia vista a partir de `resets_at`, sin esperar al sondeo. La configuración amplía `AppProperties` con la ruta expandida respecto al directorio de usuario y un `Duration` de 10 segundos; el JSON se deserializa con las facilidades ya transitivas de Spring Boot, sin clientes ni conexiones de red.

## Cambios por tarea

### TASK-001 Persistir rate_limits desde el hook

- **Ficheros:** `~/.claude/statusline-state.json` (generado por el hook existente; solo verificar): fichero de estado resultante.
- **Enfoque:** no modificar ni construir el hook. Ejecutarlo únicamente mediante su configuración existente y validar que el fichero aparece en la ruta configurada, contiene exclusivamente `written_at`, `five_hour` y `seven_day`, y que cada ventana contiene `used_percentage` y `resets_at`. Verificar sobre ejecuciones observables que sin `rate_limits` no se reemplaza el último estado y que las escrituras sucesivas dejan JSON completo y más reciente.
- **Pruebas automáticas:** no se crean pruebas ni fixtures de construcción del hook. Validación de formato del fichero real o de una copia generada: parseo JSON y comprobación exacta de campos, tipos y rango 0–100; repetir lectura mientras Claude Code actualiza el estado y aceptar únicamente documento completo o fichero temporalmente no disponible. Comando de comprobación local: `python -m json.tool <ruta-del-fichero-de-estado>`.
- **Prueba manual:** con la configuración existente, enviar un mensaje y comprobar el fichero resultante y la ausencia de rutas, sesión, repositorio, costes, tokens o credenciales.

### TASK-002 Encadenar la status line original y emitir la salida

- **Ficheros:** `~/.claude/statusline-state.json` (generado por el hook existente; solo verificar): estado que debe mantenerse independiente de la salida de status line.
- **Enfoque:** no modificar ni probar la construcción del hook. Validar con la configuración existente que una ejecución que encadena la status line conserva la salida observable y produce/actualiza el estado; sin comando original, comprobar la línea mínima y su coherencia con el fichero. Ante un fallo observable del comando original, confirmar que el hook termina y que el fichero resultante sigue siendo utilizable.
- **Pruebas automáticas:** no se crean pruebas del proceso ni de stdout del hook. Usar la validación JSON de TASK-001 sobre el fichero producido en ejecuciones de encadenado, sin comando original y con fallo controlado del comando original, cuando el entorno permita dichas configuraciones.
- **Prueba manual:** comprobar salida original intacta; retirar el comando para observar la línea mínima; usar una ruta inválida para el comando original y confirmar que el estado resultante sigue válido.

### TASK-003 Propiedades de ruta del fichero e intervalo de lectura

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/config/AppProperties.java` (modificar): propiedades de ruta e intervalo; `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/config/ConfigurationProvider.java` (modificar): accesores tipados y expansión de `~`; `desktop/app/src/main/resources/application.yml` (modificar): valores por defecto; `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/config/ConfigurationProviderTest.java` (modificar): valores por defecto y externos.
- **Enfoque:** añadir `app.usage-state-file` y `app.usage-poll-interval`, con ruta por defecto `${user.home}/.claude/statusline-state.json` e intervalo de diez segundos, exponiendo `Path` y `Duration` validados al consumidor.
- **Pruebas automáticas:** comprobar ruta resuelta del usuario e intervalo por defecto, sobrescritura desde YAML y arranque sin propiedades nuevas. Comando: `mvn -f desktop/app/pom.xml test -Dtest=ConfigurationProviderTest`.
- **Prueba manual:** no aplica; se comprueba al configurar una ruta alternativa en TASK-016.

### TASK-004 Abstracción de fuente de uso por proveedor

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java` (nuevo, propuesta): contrato de lectura; `UsageReading.java`, `UsageWindowReading.java` y `UsageWindowType.java` en el mismo paquete (nuevos, propuesta): modelo inmutable; `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/usage/UsageSourceTest.java` (nuevo, propuesta).
- **Enfoque:** representar proveedor identificado, ventanas y hora de registro; el contrato devuelve resultado opcional y establece que la fuente encapsula sus fallos. Mantener el consumidor independiente de Claude para registrar otras fuentes después.
- **Pruebas automáticas:** fuente de prueba devuelve ambas ventanas con porcentaje, reinicio y registro; una fuente que devuelve ausencia o lanza no propaga error al adaptador/consumidor de fuentes. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageSourceTest`.
- **Prueba manual:** no aplica.

### TASK-005 Lectura y validación del fichero de estado de Claude

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude/ClaudeUsageSource.java` (nuevo, propuesta): fuente de fichero JSON; DTOs privados o de paquete en `usage/claude` (nuevos, propuesta); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/usage/claude/ClaudeUsageSourceTest.java` (nuevo, propuesta).
- **Enfoque:** inyectar configuración y lector de ficheros para poder simular I/O; validar independientemente cada ventana (número 0–100 y epoch utilizable) y exigir `written_at` válido para publicar la lectura. Cualquier ausencia, JSON inválido, permisos o I/O se convierte en ausencia sin alerta visible.
- **Pruebas automáticas:** fixture 40/15 devuelve porcentajes, resets y registro; enteros se preservan; fichero ausente, ilegible o malformado devuelve ausencia; una ventana incompleta, no numérica o fuera de rango se omite y la válida se conserva; `written_at` ausente o inválido invalida el contenido. Comando: `mvn -f desktop/app/pom.xml test -Dtest=ClaudeUsageSourceTest`.
- **Prueba manual:** arrancar con JSON malformado y sustituirlo por uno válido; no hay aviso visible y la siguiente lectura queda disponible.

### TASK-006 Cálculo de tiempo restante y vigencia de cada ventana

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/CountdownEvaluator.java` (nuevo, propuesta); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/usage/CountdownEvaluatorTest.java` (nuevo, propuesta).
- **Enfoque:** inyectar `Clock`; calcular duración no negativa y vigencia estricta frente a `resets_at`, sin consultar el fichero ni depender del poller.
- **Pruebas automáticas:** a 2 h 10 min devuelve esa duración y, al avanzar un minuto el reloj, 2 h 9 min; ventana expirada no es vigente y otra futura sí. Comando: `mvn -f desktop/app/pom.xml test -Dtest=CountdownEvaluatorTest`.
- **Prueba manual:** no aplica.

### TASK-007 Historial en memoria de la evolución por ventana

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java` (nuevo, propuesta); `UsageHistoryPoint.java` (nuevo, propuesta); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/usage/UsageHistoryTest.java` (nuevo, propuesta).
- **Enfoque:** indexar por proveedor y tipo de ventana, añadir solo si `written_at` es estrictamente posterior y aplicar un máximo documentado por serie, eliminando los puntos más antiguos. No persistir ni reutilizar el historial tras crear una instancia nueva.
- **Pruebas automáticas:** tres registros crecientes forman tres puntos ordenados; registro igual o anterior no duplica; al exceder el límite se retienen los recientes; instancia nueva está vacía. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageHistoryTest`.
- **Prueba manual:** no aplica.

### TASK-008 Estado publicado por proveedor y notificación a la vista

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java` (nuevo, propuesta); `UsageSnapshot.java` y listener de vista en el mismo paquete (nuevos, propuesta); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisherTest.java` (nuevo, propuesta).
- **Enfoque:** sustituir el último resultado por proveedor, eliminar el proveedor al recibir ausencia y entregar snapshots inmutables. Encapsular el despacho con `SwingUtilities.invokeLater`/EDT para que los listeners de UI nunca se ejecuten desde el poller.
- **Pruebas automáticas:** publicación con Claude notifica su último estado desde EDT; una ausencia posterior lo elimina sin cambiar otro proveedor; sin lecturas el snapshot es vacío. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageSnapshotPublisherTest`.
- **Prueba manual:** no aplica.

### TASK-009 Planificador de lecturas en hilo de fondo

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java` (nuevo, propuesta); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/usage/UsagePollerTest.java` (nuevo, propuesta).
- **Enfoque:** usar un único ejecutor programado con retardo fijo tras finalizar el ciclo; consultar cada fuente de forma aislada, actualizar historial con lecturas presentes y publicar resultado por fuente. Definir límite de espera por fuente para que una lenta no impida a las demás ni cree solapamiento, y arrancar/parar como bean de ciclo de vida.
- **Pruebas automáticas:** con reloj/ejecutor controlado, tres ciclos quedan separados diez segundos tras cada fin y corren fuera del EDT; fallo o demora superior a cinco segundos de una fuente permite la otra; ciclo largo no se solapa. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsagePollerTest`.
- **Prueba manual:** no aplica; se verifica en TASK-016.

### TASK-010 Parada del planificador en el cierre ordenado

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/lifecycle/LifecycleCoordinator.java` (modificar): coordinar el cese del poller; `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java` (modificar): parada idempotente; `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/lifecycle/LifecycleCoordinatorTest.java` (modificar).
- **Enfoque:** conectar el orden de cierre del contexto para detener y esperar/cancelar de forma acotada al ejecutor antes de terminar, sin conservar el historial fuera del proceso.
- **Pruebas automáticas:** tras cierre no se programa otra lectura; con lectura en curso se finaliza o cancela sin bloquear el cierre. Comando: `mvn -f desktop/app/pom.xml test -Dtest=LifecycleCoordinatorTest,UsagePollerTest`.
- **Prueba manual:** cerrar desde bandeja y confirmar que el proceso desaparece; reiniciar y comprobar gráfico vacío.

### TASK-011 Ventana de uso: tarjetas por proveedor y texto neutro

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java` (nuevo, propuesta); paneles de tarjeta en `ui` (nuevos, propuesta); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/ui/UsageWindowTest.java` (nuevo, propuesta).
- **Enfoque:** crear una única ventana Swing reutilizable suscrita al publisher; construir tarjetas solo para proveedores presentes y mostrar porcentajes y hora local de `written_at`. Sin proveedores, reemplazar el contenido por texto informativo neutro.
- **Pruebas automáticas:** snapshot 40/15 con hora 10:32:15 asigna textos correctos; snapshot vacío contiene solo texto neutro sin términos prohibidos; un proveedor no crea secciones ajenas. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest` (condicional en entornos headless).
- **Prueba manual:** abrir desde bandeja con fixture válido y comprobar porcentajes/hora; retirar datos vigentes y esperar el ciclo para ver el texto neutro.

### TASK-012 Ventana de uso: cuenta atrás y ocultación de ventanas caducadas

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java` (modificar); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/ui/UsageWindowTest.java` (modificar).
- **Enfoque:** programar refresco de presentación en EDT con `CountdownEvaluator`; cada tarjeta filtra ventanas vencidas, y retirar la tarjeta cuando no queda ninguna, sin alterar el snapshot ni requerir lectura nueva.
- **Pruebas automáticas:** reset futuro a 2 h 10 min se muestra; reset pasado se omite; sin ventanas de Claude queda texto neutro y un snapshot vigente lo restaura. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowTest`.
- **Prueba manual:** con fichero sin cambios, esperar un minuto y verificar decremento; editar un reset pasado y comprobar ocultación silenciosa.

### TASK-013 Ventana de uso: gráfico de evolución

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageChartPanel.java` (nuevo, propuesta); `UsageWindow.java` (modificar); `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/ui/UsageChartPanelTest.java` (nuevo, propuesta).
- **Enfoque:** implementar `JPanel` de pintado propio que reciba series inmutables del historial y escale porcentaje/tiempo; repintar al llegar snapshots, sin librería externa y con serie vacía válida.
- **Pruebas automáticas:** tres puntos por ventana se transforman/dibujan en orden temporal; serie vacía no produce excepción ni mensaje. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageChartPanelTest`.
- **Prueba manual:** abrir recién iniciada para ver gráfico vacío; generar varias actualizaciones y confirmar un punto ordenado por ventana.

### TASK-014 Opción de mostrar el uso en el menú de bandeja

- **Ficheros:** `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/tray/TrayMenu.java` (modificar): ítem de mostrar uso; `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/tray/TrayMenuTest.java` (modificar).
- **Enfoque:** inyectar `UsageWindow`, añadir la acción antes de cierre y delegar la creación/visibilidad única en la ventana, garantizando ejecución en EDT y sin cambiar la acción existente de cierre.
- **Pruebas automáticas:** activar el ítem solicita mostrar la ventana; repetir con ventana visible no crea instancia adicional; el ítem Cerrar mantiene la delegación al coordinador. Comando: `mvn -f desktop/app/pom.xml test -Dtest=TrayMenuTest`.
- **Prueba manual:** elegir Mostrar uso, repetirlo y confirmar una sola ventana; elegir Cerrar y comprobar cierre normal.

### TASK-015 Integración: hook y fuente de Claude a través del fichero de estado

- **Ficheros:** `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/usage/claude/ClaudeUsageStateFileIntegrationTest.java` (nuevo, propuesta): integración de la fuente Java con un fichero resultante del hook existente.
- **Enfoque:** no ejecutar, modificar ni instrumentar el hook. Configurar `ClaudeUsageSource` contra una copia de un estado válido producido por él y verificar la equivalencia de datos; durante actualización real del fichero, comprobar desde el lector que el resultado es una lectura completa o ausencia tolerada, nunca una mezcla.
- **Pruebas automáticas:** fixture que reproduce exactamente un fichero resultante validado por TASK-001 se lee con iguales porcentajes y resets; el lector rechaza documentos parciales o mezclados como ausencia. Comando: `mvn -f desktop/app/pom.xml test -Dtest=ClaudeUsageStateFileIntegrationTest`.
- **Prueba manual:** con el hook existente configurado, copiar/apuntar su fichero de estado en la aplicación y comprobar que la ventana recibe los valores sin errores visibles.

### TASK-016 Integración: actualización en vivo de la ventana de uso

- **Ficheros:** `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/ui/UsageWindowLiveUpdateIntegrationTest.java` (nuevo, propuesta).
- **Enfoque:** ensamblar fuente mutable, poller, historial, publisher y ventana con ejecutor/reloj controlables, comprobando el cruce de hilo hacia EDT y la actualización sin interacción.
- **Pruebas automáticas:** tras un ciclo, porcentaje, hora y gráfico cambian en ventana abierta; ausencia oculta la ventana afectada y una lectura vigente posterior la restaura. Comando: `mvn -f desktop/app/pom.xml test -Dtest=UsageWindowLiveUpdateIntegrationTest`.
- **Prueba manual:** con ventana abierta y hook real, enviar un mensaje y esperar diez segundos; comprobar porcentaje, hora y gráfico; alternar fichero sin/vigente y confirmar ocultación/restauración.

### TASK-017 Contrato: file-json - estado de límites de Claude

- **Ficheros:** `docs/contracts/file-json/statusline-state.md` (nuevo, propuesta; responsabilidad del documentador): contrato del fichero compartido.
- **Enfoque:** documentar el formato observable del fichero ya producido por el hook, sus tipos y epoch en segundos, ruta configurable, comportamiento atómico esperado y tolerancia del lector. No atribuir al desarrollador cambios sobre el hook; el contrato se deriva de las validaciones de TASK-001/TASK-002 y de la lectura de TASK-005.
- **Pruebas automáticas:** validar que el ejemplo contractual es JSON y contiene exactamente los campos permitidos; contrastarlo con una muestra real validada del fichero resultante y verificar que documenta ausencia, contenido inválido e incidencia de escritura. Comando de validación documental definido por documentador.
- **Prueba manual:** revisar campos y ruta frente al fichero existente y la fuente implementada.

### TASK-018 Contrato: stdio - entrada y salida de la status line

- **Ficheros:** `docs/contracts/stdio/statusline.md` (nuevo, propuesta; responsabilidad del documentador): contrato de stdin/stdout del hook.
- **Enfoque:** describir la interfaz observable del hook existente: entrada JSON, encadenado, salida intacta o mínima y casos de fallo. El documento no planifica cambios ni pruebas de construcción del hook; se contrasta con las validaciones operativas de TASK-002.
- **Pruebas automáticas:** validar ejemplos JSON y salida mínima, y que están descritos los casos límite. Comando de validación documental definido por documentador; no se añade prueba de código del hook.
- **Prueba manual:** contrastar entrada, encadenado y salida con la ejecución existente del hook.

### TASK-019 Verificación transversal de NFR-002: sin conexiones propias ni credenciales

- **Ficheros:** `~/.claude/statusline-state.json` (generado; solo revisar); `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude/ClaudeUsageSource.java` (revisar); `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java` (revisar).
- **Enfoque:** no inspeccionar ni modificar la implementación del hook. Verificar su resultado observable para confirmar que no persiste credenciales y observar el proceso existente para descartar conexiones propias; revisar que la aplicación solo usa lectura local y planificación, sin APIs ni dependencias de red propias.
- **Pruebas automáticas:** inspección de dependencias/código Java que no encuentre APIs de red propias y pruebas de `ClaudeUsageSource` que rechacen o ignoren campos no contractuales presentes en muestras de estado. Comando: `mvn -f desktop/app/pom.xml test`.
- **Prueba manual:** observar cinco minutos aplicación y hook existente con Monitor de recursos o Process Monitor, y revisar fichero de estado/configuración para confirmar que no contienen credenciales.

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

## Riesgos y dudas técnicas

- `rate_limits` puede no estar presente en el JSON real de Claude Code; mitigación: realizar la prueba manual inicial de TASK-001 antes de depender de datos reales.
- La sustitución atómica del hook existente puede recibir `PermissionError` en Windows; mitigación: validarla por observación y mantener el lector tolerante a ausencia.
- Swing/AWT requiere entorno gráfico y EDT; mitigación: aislar lógica de presentación en pruebas unitarias y condicionar las de widgets en CI headless.
- Las tareas TASK-017 y TASK-018 son contratos: su fichero lo crea el documentador conforme al protocolo y describe el comportamiento observable del hook existente, no cambios sobre él.
