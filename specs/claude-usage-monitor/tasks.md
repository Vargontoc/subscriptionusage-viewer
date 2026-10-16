# Tareas: claude-usage-monitor

Estado: aprobado

## Resumen

El trabajo se divide en 19 tareas: el hook de status line en Python (TASK-001 y TASK-002), la configuración, la abstracción por proveedor y la lectura del fichero de estado (TASK-003 a TASK-005), la lógica de la aplicación (TASK-006 a TASK-010), la ventana, el menú de bandeja y el gráfico (TASK-011 a TASK-014), dos tareas de integración (TASK-015 y TASK-016), los contratos del fichero de estado y de la status line (TASK-017 y TASK-018) y la verificación transversal de NFR-002 (TASK-019).

## Tareas

### [x] TASK-001 Persistir rate_limits desde el hook

- **Cubre:** REQ-011, DEC-001, DEC-002, DEC-003
- **Componente:** statusline-hook | por definir (analista técnico)
- **Depende de:** -
- **Alcance:** el hook lee el JSON de stdin y, si trae `rate_limits`, escribe en el fichero de estado solo `used_percentage` y `resets_at` de `five_hour` y `seven_day`, más `written_at`, de forma atómica, en una ruta configurable y con reintento acotado.
- **Hecho cuando:** se cumplen los criterios de aceptación de REQ-011 relativos a persistencia (campos, atomicidad, reintento, no sobrescribir sin `rate_limits` y última escritura válida con varias sesiones) y el fichero generado respeta el formato de la sección "Fichero de estado" del requisito.
- **Pruebas automáticas:**
  - Caso feliz: con un JSON de entrada que incluye `rate_limits.five_hour` y `rate_limits.seven_day`, el fichero contiene `written_at`, `used_percentage` y `resets_at` de ambas ventanas, y ningún otro dato del JSON (rutas, identificadores de sesión, nombre de repositorio, costes).
  - Caso de error o límite: con un JSON sin `rate_limits`, el fichero existente no se modifica; si el renombrado falla de forma transitoria, el hook reintenta un número limitado de veces y, si sigue fallando, termina sin error visible; tras dos escrituras consecutivas, el fichero contiene la última.
- **Prueba manual:**
  1. Configurar en `~/.claude/settings.json` la entrada `statusLine` para que ejecute el hook con ruta absoluta y `python`, abrir una sesión de Claude Code con suscripción y enviar un mensaje. Resultado: aparece `~/.claude/statusline-state.json` con `written_at` y las dos ventanas con valores. Si el JSON real de Claude Code no incluye `rate_limits`, la prueba falla y la viabilidad de la feature debe revisarse (supuesto S1).
  2. Abrir el fichero generado. Resultado: solo contiene `written_at`, `five_hour` y `seven_day`, y dentro de ellos `used_percentage` y `resets_at`; no aparecen rutas, identificadores de sesión ni costes.

### [x] TASK-002 Encadenar la status line original y emitir la salida

- **Cubre:** REQ-011, REQ-012, DEC-002
- **Componente:** statusline-hook | por definir (analista técnico)
- **Depende de:** TASK-001
- **Alcance:** el hook reenvía el mismo JSON al comando de status line original y devuelve su salida sin cambios; si no hay comando original, imprime una línea mínima con los porcentajes de ambas ventanas.
- **Hecho cuando:** se cumplen los criterios de aceptación de REQ-012 y el requisito de REQ-011 de no alterar la salida de la status line.
- **Pruebas automáticas:**
  - Caso feliz: con comando original configurado, el hook lee stdin una sola vez, pasa el mismo JSON al original y devuelve su salida tal cual; sin comando original, imprime una línea con los porcentajes de `five_hour` y `seven_day`.
  - Caso de error o límite: si el comando original falla o no responde, el hook termina (no queda bloqueado) y el fichero de estado se actualiza igualmente; si la escritura del fichero falla, la salida del comando original se devuelve con normalidad.
- **Prueba manual:**
  1. Configurar en `~/.claude/settings.json` el hook encadenado con la status line original del usuario y enviar un mensaje en Claude Code. Resultado: la status line muestra la misma salida que mostraba antes de instalar el hook.
  2. Quitar el comando original de la configuración del hook y enviar un mensaje. Resultado: la status line muestra la línea mínima con los porcentajes de sesión y semanal.
  3. Sustituir temporalmente el comando original por una ruta inexistente y enviar un mensaje. Resultado: `~/.claude/statusline-state.json` se actualiza tras el mensaje (REQ-012, criterio de fallo del original).

### [x] TASK-003 Propiedades de ruta del fichero e intervalo de lectura

- **Cubre:** REQ-004, DEC-004
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** -
- **Alcance:** añadir a la configuración externa la ruta del fichero de estado, con valor por defecto `~/.claude/statusline-state.json`, y el intervalo de lectura, con valor por defecto de 10 segundos.
- **Hecho cuando:** la ruta y el intervalo están disponibles desde la configuración con sus valores por defecto y pueden sobrescribirse en `application.yml`.
- **Pruebas automáticas:**
  - Caso feliz: sin configuración explícita, la ruta resuelta es `~/.claude/statusline-state.json` del usuario y el intervalo es 10 segundos; con valores definidos en `application.yml`, se devuelven esos valores.
  - Caso de error o límite: sin configuración explícita, la aplicación arranca con los valores por defecto y no requiere ninguna propiedad.
- **Prueba manual:** no aplica: la configuración no tiene comportamiento observable por sí sola; su efecto se verifica en TASK-009 y en la prueba manual de TASK-016.

### [x] TASK-004 Abstracción de fuente de uso por proveedor

- **Cubre:** REQ-010, DEC-006
- **Componente:** usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage
- **Depende de:** -
- **Alcance:** definir el contrato interno que cada proveedor implementa para entregar una lectura de uso (por ventana, porcentaje, `resets_at` y hora de registro) o ausencia de datos, convirtiendo cualquier fallo en ausencia.
- **Hecho cuando:** existe la abstracción y una implementación de prueba que lanza un error o devuelve ausencia no afecta a otra implementación registrada.
- **Pruebas automáticas:**
  - Caso feliz: una implementación de prueba devuelve una lectura con sus ventanas, y cada ventana expone porcentaje, `resets_at` y hora de registro.
  - Caso de error o límite: una implementación que lanza una excepción o devuelve ausencia no provoca excepción en el consumidor, que recibe ausencia de datos para ese proveedor.
- **Prueba manual:** no aplica: abstracción interna sin interfaz propia; su efecto se verifica en TASK-009 y en TASK-016.

### [x] TASK-005 Lectura y validación del fichero de estado de Claude

- **Cubre:** REQ-001, REQ-002, REQ-005, REQ-008, DEC-003, DEC-006
- **Componente:** claude-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude
- **Depende de:** TASK-003, TASK-004
- **Alcance:** implementar la fuente de Claude que lee el fichero de estado en la ruta configurada, valida su contenido y devuelve por ventana el porcentaje, `resets_at` y `written_at`, o ausencia si el fichero falta, es ilegible o no es utilizable.
- **Hecho cuando:** se cumplen los criterios de aceptación de REQ-001 (valores iguales a los del fichero), REQ-002 (40 % y 15 % con el fichero de ejemplo), REQ-005 (hora de registro leída) y REQ-008 (fichero ausente, incompleto o con formato inválido devuelve ausencia sin error visible).
- **Pruebas automáticas:**
  - Caso feliz: con un fichero válido con `five_hour` al 40 % y `seven_day` al 15 %, la lectura devuelve 40 y 15, con sus `resets_at` y `written_at`; un `used_percentage` entero se devuelve tal cual.
  - Caso de error o límite: fichero inexistente, ilegible, con JSON mal formado, con una ventana incompleta, con `used_percentage` fuera de 0 a 100 o no numérico devuelve ausencia para la ventana afectada, sin excepción ni mensaje, y la otra ventana válida se sigue devolviendo. Ausencia de `written_at` o valor no numérico se trata como contenido no utilizable (propuesto).
- **Prueba manual:**
  1. Colocar en la ruta configurada un fichero con JSON mal formado y arrancar la aplicación. Resultado: no aparece ningún cuadro de error ni aviso visible.
  2. Sustituir el fichero por uno válido con `five_hour` al 40 % y `seven_day` al 15 %. Resultado: en la siguiente lectura la aplicación ya no lo considera inválido (se verifica visualmente en TASK-011).

### [x] TASK-006 Cálculo de tiempo restante y vigencia de cada ventana

- **Cubre:** REQ-003, REQ-008, DEC-005
- **Componente:** countdown-evaluator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage
- **Depende de:** TASK-005
- **Alcance:** calcular, frente a la hora actual, el tiempo restante hasta `resets_at` de cada ventana y si la ventana sigue vigente, sin depender de nuevas lecturas.
- **Hecho cuando:** se cumplen el criterio de REQ-003 (2 h 10 min con `resets_at` a esa distancia y disminución de un minuto sin lecturas) y el de REQ-008 sobre ventanas caducadas.
- **Pruebas automáticas:**
  - Caso feliz: con `resets_at` a 2 h 10 min en el futuro, el tiempo restante es 2 h 10 min; con el reloj simulado avanzado un minuto y sin nueva lectura, el tiempo restante es 2 h 9 min.
  - Caso de error o límite: con `resets_at` ya pasado, la ventana de sesión no está vigente aunque `written_at` sea reciente; la ventana semanal con `resets_at` futuro sigue vigente en el mismo instante.
- **Prueba manual:** no aplica: cálculo interno sin interfaz propia; su resultado visible se verifica en TASK-012.

### [x] TASK-007 Historial en memoria de la evolución por ventana

- **Cubre:** REQ-006, REQ-007, DEC-007
- **Componente:** usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage
- **Depende de:** TASK-005
- **Alcance:** acumular en memoria, por proveedor y ventana, un punto con porcentaje y hora de registro solo cuando la hora de registro es posterior a la última representada, con un tope de puntos que descarta los más antiguos.
- **Hecho cuando:** se cumplen los criterios de REQ-006 (un punto por actualización y ventana, ordenados en el tiempo, sin duplicados si el fichero no cambia) y el de REQ-007 (una instancia nueva empieza vacía).
- **Pruebas automáticas:**
  - Caso feliz: tres lecturas con `written_at` crecientes producen tres puntos por ventana, en orden temporal.
  - Caso de error o límite: una lectura con el mismo `written_at` o uno anterior no añade ningún punto; al superar el tope, se descartan los puntos más antiguos y se conservan los más recientes; una instancia nueva no contiene puntos.
- **Prueba manual:** no aplica: historial sin interfaz propia; su resultado visible se verifica en TASK-013.

### [x] TASK-008 Estado publicado por proveedor y notificación a la vista

- **Cubre:** REQ-002, REQ-009, REQ-010, NFR-001, DEC-006
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage
- **Depende de:** TASK-004
- **Alcance:** guardar el último estado conocido de cada proveedor y notificar cada cambio a la vista en el hilo de interfaz, sin incluir en el estado a los proveedores sin datos.
- **Hecho cuando:** se cumplen el criterio de REQ-010 de aislamiento (un proveedor sin datos no afecta al otro), el de REQ-009 (ninguna lectura vigente produce un estado sin proveedores) y NFR-001 en lo relativo a la notificación en el hilo de interfaz.
- **Pruebas automáticas:**
  - Caso feliz: al publicar una lectura de un proveedor, la vista recibe el último estado de ese proveedor, notificado en el hilo de interfaz.
  - Caso de error o límite: una fuente que devuelve ausencia en una lectura posterior deja a ese proveedor sin datos en el estado publicado y no altera el estado del otro proveedor; sin ningún proveedor con datos, el estado publicado está vacío.
- **Prueba manual:** no aplica: estado interno sin interfaz propia; su resultado visible se verifica en TASK-011 y en TASK-016.

### [x] TASK-009 Planificador de lecturas en hilo de fondo

- **Cubre:** REQ-004, REQ-010, NFR-001, DEC-004, DEC-006
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage
- **Depende de:** TASK-003, TASK-004, TASK-007, TASK-008
- **Alcance:** planificador en hilo de fondo que, con retardo fijo de 10 segundos contado desde el fin de una lectura hasta el inicio de la siguiente, pide a cada fuente su lectura de forma aislada y entrega los resultados a usage-history y a usage-snapshot-publisher, sin solapar lecturas.
- **Hecho cuando:** se cumplen el criterio de REQ-004 (lecturas repetidas cada 10 segundos sin acción del usuario), el de REQ-010 de aislamiento entre fuentes y NFR-001 (las lecturas no se ejecutan en el hilo de interfaz).
- **Pruebas automáticas:**
  - Caso feliz: con una fuente de prueba, tras tres ciclos se han realizado tres lecturas en el hilo de fondo, cada una iniciada 10 segundos después del fin de la anterior.
  - Caso de error o límite: una fuente que lanza una excepción o tarda más de 5 segundos no impide la lectura de la otra fuente; si una lectura dura más que el intervalo, la siguiente no se solapa con ella.
- **Prueba manual:** no aplica: planificador sin interfaz propia; su resultado visible se verifica en TASK-016.

### [x] TASK-010 Parada del planificador en el cierre ordenado

- **Cubre:** REQ-004, REQ-007, DEC-004
- **Componente:** lifecycle-coordinator | src/main/java/es/vargontoc/subscriptionviewer/lifecycle/LifecycleCoordinator.java
- **Depende de:** TASK-009
- **Alcance:** registrar en el cierre ordenado la parada del planificador de lecturas, de forma que la aplicación no inicie nuevas lecturas al cerrarse y el historial en memoria se descarte con el proceso.
- **Hecho cuando:** al cerrar la aplicación no queda ningún planificador de lecturas activo y el proceso termina.
- **Pruebas automáticas:**
  - Caso feliz: al ejecutar el cierre ordenado, el planificador se detiene y no se inicia ninguna lectura posterior.
  - Caso de error o límite (propuesto): cierre con una lectura en curso; el planificador finaliza esa lectura o la cancela sin iniciar otras y el cierre se completa.
- **Prueba manual:**
  1. Arrancar la aplicación y cerrarla desde el menú de la bandeja. Resultado: el proceso deja de aparecer en el Administrador de tareas.
  2. Arrancar de nuevo la aplicación. Resultado: el historial de evolución empieza vacío (REQ-007), lo que se confirma en TASK-013.

### [x] TASK-011 Ventana de uso: tarjetas por proveedor y texto neutro

- **Cubre:** REQ-002, REQ-005, REQ-009, REQ-010, DEC-008
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui
- **Depende de:** TASK-008
- **Alcance:** ventana de escritorio Swing con una tarjeta por proveedor que muestra el porcentaje de la ventana de sesión y de la semanal, la hora de datos y, si ningún proveedor tiene datos, un texto informativo neutro.
- **Hecho cuando:** se cumplen los criterios de REQ-002 (40 % y 15 % en sus ventanas), REQ-005 (hora de registro visible), REQ-009 (texto neutro sin términos de error, fallo o advertencia) y REQ-010 (solo se muestran proveedores con datos, sin secciones vacías).
- **Pruebas automáticas:**
  - Caso feliz: con un estado que contiene 40 % de sesión, 15 % semanal y `written_at` a las 10:32:15 local, la vista asigna esos valores a sus ventanas y muestra la hora 10:32:15.
  - Caso de error o límite: con un estado sin proveedores con datos, la vista muestra solo el texto neutro, sin palabras de error, fallo o advertencia; con un único proveedor, no aparece ninguna sección de otro proveedor.
- **Prueba manual:**
  1. Con la aplicación en ejecución y un fichero de estado de prueba con 40 % de sesión, 15 % semanal y `written_at` a las 10:32:15 local, abrir la ventana desde la bandeja (opción de TASK-014). Resultado: se ven 40 % y 15 % en sus ventanas y la hora de datos 10:32:15.
  2. Sustituir el fichero por uno sin datos vigentes y esperar 10 segundos. Resultado: la ventana muestra un texto informativo neutro, sin términos de error, fallo o advertencia.

### [x] TASK-012 Ventana de uso: cuenta atrás y ocultación de ventanas caducadas

- **Cubre:** REQ-003, REQ-008, DEC-005, DEC-008
- **Componente:** usage-window | por definir (analista técnico)
- **Depende de:** TASK-006, TASK-011
- **Alcance:** mostrar en cada ventana el tiempo restante calculado por countdown-evaluator, actualizado en la vista sin nuevas lecturas, y ocultar las ventanas caducadas así como el proveedor que no tenga ventanas vigentes.
- **Hecho cuando:** se cumplen el criterio de REQ-003 (2 h 10 min junto a la ventana de sesión, disminuye un minuto sin lecturas) y los de REQ-008 (ventana caducada oculta sin mensaje, Claude oculto si ninguna ventana es vigente y de nuevo visible con datos vigentes).
- **Pruebas automáticas:**
  - Caso feliz: con `resets_at` a 2 h 10 min en el futuro, la vista muestra 2 h 10 min junto a la ventana de sesión.
  - Caso de error o límite: una ventana con `resets_at` pasado no aparece; si ninguna ventana de Claude es vigente, Claude no aparece en la vista y se muestra el texto neutro; al llegar datos vigentes, Claude vuelve a mostrarse.
- **Prueba manual:**
  1. Con la ventana abierta y un fichero de prueba con `resets_at` a 2 h 10 min en el futuro, anotar el tiempo mostrado, cerrar Claude Code para que el fichero no cambie y esperar un minuto. Resultado: el tiempo restante ha disminuido un minuto.
  2. Editar el fichero para poner `resets_at` de la sesión en una hora pasada y esperar 2 segundos. Resultado: la ventana de sesión desaparece sin ningún mensaje de error.

### [x] TASK-013 Ventana de uso: gráfico de evolución

- **Cubre:** REQ-006, DEC-007, DEC-008
- **Componente:** usage-window | por definir (analista técnico)
- **Depende de:** TASK-007, TASK-011
- **Alcance:** dibujar en la ventana, con un componente de pintado propio, la serie de usage-history por ventana, con puntos ordenados en el tiempo que se añaden al recibir nuevas lecturas.
- **Hecho cuando:** se cumplen los criterios de REQ-006 (un punto por actualización y ventana, ordenados en el tiempo y sin duplicados).
- **Pruebas automáticas:**
  - Caso feliz: con tres puntos por ventana en la serie, el gráfico dibuja tres puntos por ventana en orden temporal.
  - Caso de error o límite: con una serie sin puntos, el gráfico aparece vacío sin mensaje de error.
- **Prueba manual:**
  1. Abrir la ventana justo después de arrancar la aplicación. Resultado: el gráfico aparece vacío y sin errores.
  2. Mantener Claude Code en uso durante varias actualizaciones del fichero sin reiniciar la aplicación. Resultado: aparece un punto nuevo por ventana en cada actualización, ordenados en el tiempo.

### [x] TASK-014 Opción de mostrar el uso en el menú de bandeja

- **Cubre:** REQ-002, DEC-008
- **Componente:** tray-menu | src/main/java/es/vargontoc/subscriptionviewer/tray/TrayMenu.java
- **Depende de:** TASK-011
- **Alcance:** añadir al menú contextual de la bandeja la opción de mostrar la ventana de uso, junto a la opción de cierre existente.
- **Hecho cuando:** la opción abre la ventana de uso y la opción de cierre sigue funcionando como antes.
- **Pruebas automáticas:**
  - Caso feliz: activar la opción de mostrar uso con la ventana cerrada hace que la ventana se muestre.
  - Caso de error o límite (propuesto): activar la opción con la ventana ya abierta no crea una segunda ventana.
- **Prueba manual:**
  1. Abrir el menú de la bandeja y elegir la opción de mostrar uso. Resultado: se abre la ventana de uso.
  2. Repetir con la ventana ya abierta. Resultado: no aparece una segunda ventana (propuesto).
  3. Elegir la opción de cierre. Resultado: la aplicación se cierra como antes.

### [x] TASK-015 Integración: hook y fuente de Claude a través del fichero de estado

- **Cubre:** REQ-001, REQ-011, DEC-001, DEC-003
- **Componente:** statusline-hook | ~/.claude/statusline-dump.py; claude-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude
- **Depende de:** TASK-001, TASK-005
- **Alcance:** verificar que el fichero escrito por el hook es leído por la fuente de Claude con los mismos valores y que una lectura concurrente con una escritura nunca obtiene contenido parcial.
- **Hecho cuando:** se cumplen el criterio de REQ-001 (los valores mostrados coinciden con los `rate_limits` de la última ejecución del hook) y el de REQ-011 de atomicidad (la aplicación nunca obtiene contenido parcial).
- **Pruebas automáticas:**
  - Caso feliz: el hook escribe a partir de un JSON de entrada de prueba y la fuente de Claude devuelve los mismos `used_percentage` y `resets_at`.
  - Caso de error o límite: con lecturas repetidas mientras el hook escribe en bucle, la fuente devuelve siempre los valores de una escritura completa o ausencia, nunca valores mezclados.
- **Prueba manual:**
  1. Ejecutar el hook desde una terminal, pasando por stdin un JSON de prueba con `rate_limits`, y apuntar la ruta de estado a un fichero de prueba. Resultado: el fichero contiene esos valores y `written_at`.
  2. Abrir la aplicación con esa misma ruta de estado. Resultado: la fuente de Claude lo lee sin errores visibles (los valores concretos se confirman en la ventana de TASK-011).

### [x] TASK-016 Integración: actualización en vivo de la ventana de uso

- **Cubre:** REQ-004, REQ-005, REQ-006, DEC-004, DEC-007
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java; usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-009, TASK-012, TASK-013
- **Alcance:** verificar que los cambios que el planificador publica llegan a la ventana abierta y se reflejan sin acción del usuario, incluidos los nuevos puntos del gráfico y la ocultación de ventanas sin datos vigentes.
- **Hecho cuando:** se cumplen el criterio de REQ-004 (los valores reflejan el nuevo consumo tras 10 segundos sin acción), el criterio de REQ-006 de aparición de un nuevo punto sin reiniciar y el de REQ-005 (hora de datos actualizada).
- **Pruebas automáticas:**
  - Caso feliz: con la ventana abierta y una fuente de prueba que cambia su valor, tras un ciclo de lectura la ventana muestra el nuevo porcentaje y añade un punto al gráfico.
  - Caso de error o límite: una fuente que devuelve ausencia durante un ciclo oculta la ventana afectada sin error, y al volver a tener datos vigentes la ventana se muestra de nuevo.
- **Prueba manual:**
  1. Con la aplicación y su ventana abiertas, y Claude Code en uso con la status line configurada, esperar 10 segundos tras un mensaje. Resultado: el porcentaje y el gráfico se actualizan sin tocar la aplicación.
  2. Sustituir el fichero por uno sin datos vigentes y esperar 10 segundos. Resultado: la ventana de Claude se oculta y aparece el texto neutro.
  3. Restaurar un fichero con datos vigentes y esperar 10 segundos. Resultado: Claude vuelve a mostrarse con esos datos.

### [ ] TASK-017 Contrato: file-json - estado de límites de Claude

- **Cubre:** REQ-001, REQ-008, REQ-011, DEC-001, DEC-003
- **Componente:** statusline-hook | por definir (analista técnico)
- **Ubicación del contrato:** docs/contracts/file-json/ (fichero: por definir (analista técnico))
- **Depende de:** TASK-001, TASK-005
- **Alcance:** crear el contrato del fichero de estado: campos, tipos, marcas de tiempo en epoch de segundos, ubicación por defecto configurable, escritura atómica y lectura tolerante a contenido no utilizable.
- **Hecho cuando:** el contrato existe en docs/contracts/file-json/ y describe la interfaz tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: el ejemplo del contrato es JSON válido y contiene exactamente `written_at`, `five_hour` y `seven_day`, con `used_percentage` y `resets_at` en cada ventana.
  - Caso de error o límite: el contrato recoge el comportamiento ante fichero ausente, incompleto o con formato inválido (REQ-008) y ante un fallo de escritura del hook (REQ-011).
- **Prueba manual:** repasar que cada campo (`written_at`, `five_hour`, `seven_day`, `used_percentage`, `resets_at`) y la ruta por defecto aparecen en el contrato y coinciden con lo implementado en TASK-001 y TASK-005.

### [ ] TASK-018 Contrato: stdio - entrada y salida de la status line

- **Cubre:** REQ-011, REQ-012, DEC-002
- **Componente:** statusline-hook | por definir (analista técnico)
- **Ubicación del contrato:** docs/contracts/stdio/ (fichero: por definir (analista técnico))
- **Depende de:** TASK-002
- **Alcance:** crear el contrato de la comunicación por stdio entre Claude Code y el hook: JSON de entrada, encadenado con el comando original y salida de texto devuelta.
- **Hecho cuando:** el contrato existe en docs/contracts/stdio/ y describe la interfaz tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: el ejemplo de entrada del contrato es JSON válido con `rate_limits`, y el ejemplo de salida coincide con el formato de la línea mínima.
  - Caso de error o límite: el contrato recoge el comportamiento cuando el comando original falla, cuando no hay `rate_limits` y cuando falla la escritura del fichero de estado.
- **Prueba manual:** repasar que la entrada, la encadenación con el comando original y la salida aparecen en el contrato y coinciden con lo implementado en TASK-002.

### [x] TASK-019 Verificación transversal de NFR-002: sin conexiones propias ni credenciales

- **Cubre:** NFR-002, DEC-001
- **Componente:** transversal | no aplica: NFR transversal que cruza statusline-hook, claude-usage-source y usage-poller
- **Depende de:** TASK-002, TASK-005, TASK-009
- **Alcance:** verificar que ni la aplicación ni el hook abren conexiones de red propias ni almacenan credenciales del usuario.
- **Hecho cuando:** se cumplen los criterios de aceptación de NFR-002 (sin conexiones observadas durante 5 minutos de ejecución y ficheros revisados sin credenciales).
- **Pruebas automáticas:**
  - Caso feliz: la revisión del código del hook y de la aplicación no encuentra llamadas a red propias; el hook solo usa la librería estándar para ficheros y procesos.
  - Caso de error o límite: un JSON de entrada que contiene campos de credenciales o tokens no se persiste en el fichero de estado.
- **Prueba manual:**
  1. Ejecutar la aplicación con su ventana abierta y Claude Code en uso con el hook durante 5 minutos. Resultado: durante ese tiempo se observan actividad y mensajes de Claude Code, pero ninguna conexión de red iniciada por el proceso de la aplicación ni por el proceso del hook; se puede comprobar con el Monitor de recursos de Windows (pestaña Red o conexiones TCP) o con Process Monitor.
  2. Revisar `~/.claude/statusline-state.json` y los ficheros de configuración de la aplicación. Resultado: no contienen credenciales del usuario.

## Cobertura

| Requisito | Tareas |
|---|---|
| REQ-001 | TASK-005, TASK-015, TASK-017 |
| REQ-002 | TASK-005, TASK-008, TASK-011, TASK-014 |
| REQ-003 | TASK-006, TASK-012 |
| REQ-004 | TASK-003, TASK-009, TASK-010, TASK-016 |
| REQ-005 | TASK-005, TASK-011, TASK-016 |
| REQ-006 | TASK-007, TASK-013, TASK-016 |
| REQ-007 | TASK-007, TASK-010 |
| REQ-008 | TASK-005, TASK-006, TASK-012, TASK-017 |
| REQ-009 | TASK-008, TASK-011 |
| REQ-010 | TASK-004, TASK-008, TASK-009, TASK-011 |
| REQ-011 | TASK-001, TASK-002, TASK-015, TASK-017, TASK-018 |
| REQ-012 | TASK-002, TASK-018 |
| NFR-001 | TASK-008, TASK-009 |
| NFR-002 | TASK-019 |
