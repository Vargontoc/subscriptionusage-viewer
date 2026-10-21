# Diseño: provider-tabs-ui

Estado: aprobado

## Resumen

`usage-window` pasa de una lista de tarjetas con gráfico a una ventana fija (diálogo sin minimizar ni maximizar) con una fila propia de cuatro pestañas fijas (Claude, OpenAI, OpenCode, Gemini) y el botón cerrar a la derecha. Cada pestaña se pinta a partir de un modelo de vista inmutable por proveedor, calculado en el hilo de interfaz con el último estado publicado y el reloj. Ese modelo es uno de tres: "obteniendo datos", error formal (pestaña y contenedor en gris) o tablas sin bordes con barras de progreso coloreadas. Para que el gris sea fiable, `usage-snapshot-publisher` deja de silenciar fallos y ausencias, y el gráfico sale de la composición de la ventana sin tocar la acumulación del historial.

## Supuestos sobre el sistema actual

- Confirmado en código (referenciado por el mapa): `UsageWindow` es hoy un `JFrame` de 400x300 con `HIDE_ON_CLOSE`, un `CardLayout` neutro/tarjetas, `UsageProviderCardPanel` por proveedor (clases `UsageCardPanel`, `UsageProviderCardPanel` y `UsageChartPanel` en el paquete `ui`, que el mapa no lista como módulos) y un temporizador de 1 s.
- Confirmado: `UsageSnapshotPublisher` hoy (a) descarta sin publicar los fallos y ausencias de OpenCode, (b) oculta el fallo transitorio inicial de OpenAI, (c) elimina la entrada del proveedor cuando una lectura llega vacía (caso de Claude) y (d) conserva el último dato válido tras un fallo. `UsageProviderState` ya tiene los estados correcto, desactualizado, sin datos, sesión caducada y consulta fallida.
- Confirmado: `UsageWindowReading` guarda el porcentaje como `double`. No se ha abierto `usage-poller` ni las fuentes de Claude, OpenAI y OpenCode; se asume que entregan al publisher un resultado clasificado en cada ciclo, incluida la ausencia de datos de Claude, y que no redondean el porcentaje antes de entregarlo (a verificar, ver Riesgos).
- Se asume que `gemini-usage-parser` redondea hoy el porcentaje a un decimal en origen (según su diseño aprobado, DEC-004); esto choca con REQ-008 y se corrige aquí (DEC-006).
- Se asume que el formato textual del tiempo restante y la precisión actual de cada proveedor (Claude, OpenAI y OpenCode en enteros; Gemini con un decimal) se reutilizan tal cual; el diseño no los redefine.
- Se asume que "hora de última actualización" mantiene el criterio actual de cada proveedor (Claude: hora de escritura del fichero de estado; el resto: hora del último éxito).
- Se asume que, salvo Claude y OpenAI, las ventanas no caducan por reloj en la vista (OpenCode no tiene reinicio; Gemini muestra tiempo restante con mínimo 0, como hoy).
- Los iconos PNG de `desktop/app/src/main/resources/icons/` están sin versionar en git en este momento; deben entrar en el repositorio con la feature.
- El menú de la bandeja solo usa mostrar y ocultar de `usage-window`; se asume que esa API se mantiene.

## Componentes

| Componente | Responsabilidad | Existente o nuevo |
|---|---|---|
| usage-window | Aloja el diálogo de tamaño fijo, la fila de pestañas y el contenido; recibe cada estado publicado, ejecuta el refresco de 1 s, recuerda la pestaña seleccionada y ya no compone tarjetas ni gráfico | existente (modificado) |
| provider-tab-bar | Fila con las cuatro pestañas fijas en orden Claude, OpenAI, OpenCode, Gemini (icono y nombre), pestaña gris, resaltado de la seleccionada y botón cerrar alineado a la derecha | nuevo |
| provider-view-model | Convierte, por proveedor, el último estado publicado, la hora actual y countdown-evaluator en un modelo inmutable: obteniendo datos, error con texto o tablas (cabeceras, filas, celdas de texto, barra o N/A) más la hora de actualización y la bandera de gris; aplica vigencia, omisión de ventanas caducadas y precisión de cada proveedor | nuevo |
| provider-error-texts | Catálogo único de los textos formales de REQ-006 y de "Obteniendo datos…" (REQ-018); elige el texto por proveedor y causa, sin usar contenido de respuestas ni salidas | nuevo |
| provider-content-panel | Pinta el modelo de un proveedor: contenedor gris con texto, o la hora arriba a la izquierda y las tablas en fila a la misma altura, con separación ajustable y desplazamiento vertical conjunto; sin título, plan ni pie | nuevo |
| usage-table | Tabla sin bordes, cabecera negra con texto blanco, filas alternas gris y blanco, alto de fila común, celdas de texto o barra | nuevo |
| usage-progress-bar | Barra con fondo blanco y borde negro, relleno proporcional con el color del nivel y porcentaje centrado (blanco al 100 %, negro en el resto) | nuevo |
| progress-level | Función pura que asigna el nivel de color (verde, amarillo, naranja, rojo, negro) al porcentaje sin redondear según los umbrales de REQ-008 | nuevo |
| usage-snapshot-publisher | Publica siempre el resultado de cada consulta de cualquier proveedor (sin silencios); la ausencia de entrada significa "primera consulta no completada"; conserva internamente el último dato válido | existente (modificado) |
| usage-poller | Entrega al publisher el resultado clasificado de cada ciclo de todos los proveedores, incluidas las ausencias de Claude y los ciclos descartados de OpenCode | existente (modificado) |
| gemini-usage-parser | Entrega el porcentaje acotado a 0-100 sin redondear a un decimal (el redondeo pasa a la presentación) | existente (modificado) |
| usage-history | Sigue acumulando en memoria; para Gemini compara el valor redondeado a un decimal al deduplicar, de modo que su comportamiento no cambia | existente (modificado) |
| countdown-evaluator | Tiempo restante y vigencia de cada ventana frente a la hora actual; lo usa provider-view-model | existente |
| tray-menu | Sin cambios; pide mostrar la ventana | existente |

## Flujo

1. Al arrancar, `usage-poller` lanza la primera consulta de cada proveedor en segundo plano. Mientras un proveedor no ha publicado nada, `provider-view-model` genera "obteniendo datos" para él: pestaña gris y texto "Obteniendo datos…" (REQ-018).
2. Cada fuente termina su consulta y `usage-poller` entrega el resultado clasificado (datos, fallo transitorio, sesión caducada, consulta fallida o ausencia) a `usage-snapshot-publisher`, que lo publica siempre y conserva el último dato válido solo internamente.
3. `usage-snapshot-publisher` notifica a `usage-window` en el hilo de interfaz. Cada segundo, y en cada notificación, `usage-window` pide a `provider-view-model` un modelo por proveedor con el último estado y la hora actual.
4. `provider-view-model` decide por proveedor: sin entrada publicada, "obteniendo datos"; estado distinto de correcto, o correcto sin ninguna ventana vigente (Claude y OpenAI, vía `countdown-evaluator`), error con el texto de `provider-error-texts` (el aviso de sesión caducada si es OpenAI con 401/403; el texto de conexión y sesión en el resto de fallos de OpenAI); en otro caso, tablas.
5. En las tablas, el modelo aplica la precisión del proveedor, omite las ventanas caducadas de Claude y OpenAI mientras otra siga vigente, muestra "N/A" en las celdas de la ventana ausente de un grupo de Gemini y añade la fila "Total" en OpenCode.
6. `provider-tab-bar` pinta cada pestaña en gris si su modelo es "obteniendo datos" o error, y resalta la seleccionada. Si el modelo de un proveedor no ha cambiado respecto al anterior, no se repinta su contenido (conserva el desplazamiento).
7. `provider-content-panel` pinta el modelo de la pestaña seleccionada: contenedor gris con texto, o hora de actualización arriba a la izquierda y tablas en fila a la misma altura. Las barras usan `usage-progress-bar`, que obtiene el color de `progress-level` con el porcentaje sin redondear.
8. El usuario cambia de pestaña: `usage-window` muestra el contenido ya calculado del proveedor elegido sin consultar nada (NFR-001) y guarda la selección en memoria.
9. El usuario cierra con el botón de la fila de pestañas o con la barra de título: ambos ocultan el diálogo y la aplicación sigue en la bandeja. Al volver a mostrarlo desde `tray-menu`, `usage-window` conserva la pestaña seleccionada; tras reiniciar la aplicación parte de Claude.

## Interfaces entre componentes

| Origen | Destino | Qué se comunica | Protocolo |
|---|---|---|---|
| usage-poller | usage-snapshot-publisher | Resultado clasificado de cada consulta de cada proveedor, sin silencios, y hora de fin | - |
| usage-snapshot-publisher | usage-window | Notificación de cambio del estado por proveedor (hilo de interfaz) | - |
| usage-window | provider-view-model | Último estado por proveedor y hora actual; devuelve un modelo por proveedor | - |
| provider-view-model | countdown-evaluator | Vigencia y tiempo restante de cada ventana | - |
| provider-view-model | provider-error-texts | Proveedor y causa; devuelve el texto exacto | - |
| provider-view-model | progress-level | Porcentaje sin redondear; devuelve el nivel de color | - |
| usage-window | provider-tab-bar | Estado gris y selección de las cuatro pestañas; evento de cambio de pestaña y de cierre | - |
| usage-window | provider-content-panel | Modelo del proveedor seleccionado | - |
| provider-content-panel | usage-table | Cabeceras y filas de cada tabla | - |
| usage-table | usage-progress-bar | Porcentaje, nivel y precisión para la celda de barra | - |
| tray-menu | usage-window | Petición de mostrar la ventana | - |
| usage-history | (sin consumidor en la vista) | Acumulación en memoria que continúa; el gráfico ya no la lee | - |

## Modelo de datos lógico

- Proveedor: servicio de AI (Claude, OpenAI, OpenCode, Gemini); siempre exactamente cuatro. Relaciones: tiene una Pestaña y un Estado de visualización.
- Pestaña: entrada de la fila con icono y nombre; puede estar en gris y seleccionada. Relaciones: pertenece a un Proveedor; muestra su Estado de visualización.
- Estado de visualización: obteniendo datos, error o con datos. Relaciones: se deriva del Estado de lectura publicado del Proveedor y de la hora actual; si es error, referencia un Texto formal; si es con datos, contiene Tablas y la hora de actualización.
- Texto formal: mensaje fijo de usuario por proveedor y causa. Relaciones: pertenece al catálogo; lo usa el Estado de visualización de tipo error.
- Tabla: cabeceras y filas. Relaciones: pertenece a un Estado de visualización con datos; sus filas homólogas están alineadas con las de las otras tablas de la pestaña.
- Fila y celda: la celda es texto, "N/A" o barra de progreso. Relaciones: la celda de barra tiene un Porcentaje y un Nivel de color.
- Nivel de color: verde, amarillo, naranja, rojo o negro. Relaciones: lo determina el Porcentaje sin redondear.
- Estado de lectura publicado y Lectura: sin cambios respecto a las specs aprobadas.

## Decisiones

### DEC-001 Fila de pestañas propia en lugar de un contenedor de pestañas estándar

- **Decisión:** `provider-tab-bar` es un componente propio (una fila de botones de pestaña con icono y nombre, un hueco elástico y el botón cerrar a la derecha) sobre un conmutador de contenido por proveedor.
- **Alternativas descartadas:** contenedor de pestañas estándar de Swing (el pintado de fondo gris y de la pestaña seleccionada depende del aspecto del sistema y no admite un botón alineado a la derecha en la misma fila sin soluciones frágiles); una barra de botones de otro tipo (lo mismo que la elegida, pero sin ventaja).
- **Cubre:** REQ-002, REQ-003, REQ-004, REQ-005, REQ-020

### DEC-002 Diálogo sin propietario en lugar de ventana principal

- **Decisión:** `usage-window` aloja su contenido en un diálogo de tamaño fijo y no redimensionable, con barra de título y borde del sistema, cierre que solo oculta. El diálogo no ofrece minimizar ni maximizar, por lo que la acción por doble clic tampoco cambia el tamaño. Mantiene mostrar, ocultar y estado de visibilidad para `tray-menu`.
- **Alternativas descartadas:** ventana principal no redimensionable (en Windows mantiene el control de minimizar, contra REQ-001); ventana principal que restaura su estado al minimizarse (el control seguiría visible y parpadearía); ventana sin decoración con barra propia (descartado por el usuario, necesita moverla con la barra de título).
- **Cubre:** REQ-001, REQ-002

### DEC-003 Modelo de vista inmutable separado del pintado

- **Decisión:** `provider-view-model` es lógica pura (sin Swing) que, dado el estado publicado y el reloj, produce el modelo del proveedor; los componentes de pintado solo lo representan. El cálculo y el pintado ocurren en el hilo de interfaz sobre el último estado ya en memoria, sin E/S ni espera de ninguna consulta.
- **Alternativas descartadas:** que cada panel interprete el estado publicado (duplica reglas de gris, vigencia y precisión en cuatro sitios y las hace difíciles de probar); calcular el modelo en el publisher (acopla el estado de dominio a decisiones de presentación).
- **Cubre:** REQ-005, REQ-006, REQ-009, REQ-017, REQ-018, NFR-001

### DEC-004 Publicar siempre el resultado de cada consulta; ausencia de entrada = primera consulta pendiente

- **Decisión:** `usage-poller` y `usage-snapshot-publisher` dejan de descartar o silenciar resultados: los ciclos descartados de OpenCode, la ausencia de datos de Claude y el fallo inicial de OpenAI pasan a publicarse con un estado distinto de correcto. Un proveedor sin ninguna entrada publicada es, por definición, uno que no ha completado su primera consulta ("Obteniendo datos…"). Una segunda consulta en curso no altera el estado publicado, así que el texto de error persiste (REQ-018). El último dato válido se sigue conservando dentro del estado, pero la vista lo ignora (S16).
- **Alternativas descartadas:** un indicador explícito "consulta en curso" por proveedor (más estado y no necesario: la primera consulta se distingue por la ausencia de entrada); mantener los silencios actuales y que la vista deduzca el fallo por tiempo (impreciso y frágil, y contradice REQ-006 para Claude y OpenCode).
- **Cubre:** REQ-005, REQ-006, REQ-018

### DEC-005 Catálogo único de textos de error

- **Decisión:** `provider-error-texts` contiene todos los textos exactos de REQ-006 y REQ-018 y es la única fuente de texto del estado gris. La causa llega como estado clasificado (no como texto): sesión caducada de OpenAI usa el aviso de 401/403; el resto de estados sin datos de OpenAI, el de conexión y sesión; Claude, OpenCode y Gemini tienen un texto único. Nunca se compone un texto a partir de respuestas, salidas, códigos ni credenciales.
- **Alternativas descartadas:** textos repartidos en los paneles (riesgo de variantes y de filtrar contenido técnico); texto recibido desde las fuentes (rompería NFR-003).
- **Cubre:** REQ-006, REQ-018, NFR-003

### DEC-006 Porcentaje sin redondear hasta la presentación

- **Decisión:** el modelo de lectura lleva el porcentaje sin redondear (acotado a 0-100). El nivel de color se calcula con ese valor en `progress-level`; el redondeo para mostrar se hace en `provider-view-model` según la precisión de cada proveedor. Para ello `gemini-usage-parser` deja de redondear a un decimal y `usage-history` compara, para Gemini, el valor redondeado a un decimal, conservando la deduplicación aprobada. El texto de la barra siempre es el valor mostrado.
- **Alternativas descartadas:** conservar el redondeo en origen (99,96 % pasaría a 100,0 % y la barra sería negra, contra el criterio de REQ-008); calcular el color con el texto mostrado (mismo defecto); añadir a la lectura un segundo valor redondeado (duplica el dato sin necesidad).
- **Cubre:** REQ-007, REQ-008, NFR-002

### DEC-007 Barra de progreso pintada por un componente propio

- **Decisión:** `usage-progress-bar` se dibuja a medida (fondo blanco, borde negro, relleno proporcional, texto centrado) en lugar de usar la barra estándar de Swing. El color del relleno y del texto los fija `progress-level` y la regla del 100 % (relleno negro y texto blanco).
- **Alternativas descartadas:** barra estándar de Swing (según el aspecto del sistema ignora el color de relleno y el color del texto centrado, y no garantiza el texto legible sobre negro); imagen precalculada por nivel (no admite todos los porcentajes ni el texto variable).
- **Cubre:** REQ-007, REQ-008, NFR-002

### DEC-008 Tablas en una sola fila horizontal con alto de fila común y un único desplazamiento

- **Decisión:** `provider-content-panel` coloca las tablas de la pestaña en horizontal, con el mismo borde superior, separadas por un valor ajustable definido en un único sitio, y todas con el mismo alto de fila y de cabecera. Como las tablas de un proveedor tienen siempre el mismo número de filas homólogas (grupos de Gemini, modelos y "Total" de OpenCode), las filas coinciden en altura. Las tablas se alojan juntas en una sola zona de desplazamiento vertical, de modo que se mueven a la vez. La cabecera negra y las filas alternas gris y blanco se aplican en `usage-table`, sin bordes ni pie.
- **Alternativas descartadas:** un área de desplazamiento por tabla con sincronización (puede desalinear filas y añade lógica); una única tabla con columnas agrupadas (impide la separación entre tablas y la ausencia de bordes entre ellas).
- **Cubre:** REQ-012, REQ-013, REQ-014, REQ-019, REQ-021

### DEC-009 Repintar solo cuando cambia el modelo

- **Decisión:** `usage-window` compara el modelo de cada proveedor con el anterior (igualdad de valores) y solo reconstruye el contenido que cambió, tanto en las notificaciones como en el refresco de 1 s. Así el tiempo restante se actualiza en el minuto que cambia, y el desplazamiento y la pestaña seleccionada se conservan; el color de todas las pestañas se actualiza aunque no estén seleccionadas.
- **Alternativas descartadas:** reconstruir todo cada segundo (hoy se hace; perdería el desplazamiento de OpenCode y parpadearía); refrescar solo la pestaña visible (dejaría pestañas con el color desfasado, contra REQ-017).
- **Cubre:** REQ-014, REQ-017, NFR-001

### DEC-010 Vigencia por proveedor mediante countdown-evaluator

- **Decisión:** para Claude y OpenAI, `provider-view-model` descarta las ventanas caducadas y pasa a error si no queda ninguna vigente; se omite la tabla completa de una ventana caducada mientras la otra siga vigente (S13). OpenCode y Gemini no caducan por reloj. Se evalúa en cada refresco, no al leer.
- **Alternativas descartadas:** evaluar la vigencia en la fuente (la caducidad no se vería hasta la siguiente lectura); aplicar caducidad a Gemini (cambiaría su comportamiento aprobado de mostrar tiempo restante mínimo 0).
- **Cubre:** REQ-005, REQ-006, REQ-010, REQ-011

### DEC-011 Gráfico fuera de la ventana, acumulación intacta

- **Decisión:** `usage-window` deja de crear y de mostrar el gráfico y las tarjetas. `usage-history` sigue acumulando y el componente de gráfico existente se conserva sin instanciar para la siguiente especificación. Nada en la ventana depende del historial.
- **Alternativas descartadas:** borrar el gráfico y la acumulación (contra el alcance, el siguiente diseño lo necesita); ocultarlo con visibilidad en la misma ventana (reservaría espacio y mantendría dependencia del historial).
- **Cubre:** REQ-015, REQ-016

### DEC-012 Tamaño fijo derivado del contenido más alto

- **Decisión:** el tamaño se fija como constante en `usage-window`, calculado para que, sin desplazamiento, quepan fila de pestañas, línea de actualización, cabecera y tres filas de grupo de Gemini (el caso más alto de Claude, OpenAI y Gemini) y el ancho de las tres tablas de Gemini con su separación. Se valida a mano; el valor orientativo de partida es de unos 720x360 píxeles.
- **Alternativas descartadas:** tamaño ajustado al contenido de la pestaña activa (cambiaría el tamaño al cambiar de pestaña, contra REQ-001); tamaño muy holgado (desperdicia espacio en una ventana pequeña de bandeja).
- **Cubre:** REQ-001, REQ-014

## Riesgos

- Las fuentes de Claude, OpenAI y OpenCode podrían redondear el porcentaje en origen (no se ha abierto su código): el criterio de REQ-008 (50,1 % amarillo mostrando 50 %) fallaría. Mitigación: tarea de verificación en cada fuente, con prueba de los umbrales con valores decimales.
- Cambiar el publisher y el poller (DEC-004) afecta a Claude, OpenCode y OpenAI y deja obsoletas partes de sus specs aprobadas (declarado en "Impacto en especificaciones aprobadas" de los requisitos). Mitigación: pruebas de regresión de cada proveedor, ahora con el estado gris como resultado esperado.
- Cambiar el diálogo (DEC-002) puede romper pruebas y accesos que asumen un marco de ventana principal. Mitigación: conservar la API pública de `usage-window` y revisar las pruebas existentes.
- Un fallo persistente de Gemini u OpenCode ya no es silencioso: queda visible en gris con un texto fijo y sin detalles técnicos (NFR-003), aceptado por la feature.
- Los iconos están sin versionar; si faltan, las pestañas mostrarían solo el nombre. Mitigación: incluirlos en el repositorio y definir que un icono ausente no impide pintar la pestaña.
- Cuatro modelos recalculados cada segundo: coste despreciable con DEC-009; si OpenCode tiene muchos modelos, solo se repinta cuando cambian.
- Dejar de redondear en `gemini-usage-parser` obliga a revisar su deduplicación y sus pruebas aprobadas (DEC-006).
- El tamaño fijo puede quedar corto si Gemini añade grupos nuevos (REQ-012 admite nombres no previstos): en ese caso aparece el desplazamiento vertical, que ya está previsto.

## Trazabilidad

| Requisito | Componentes | Decisiones |
|---|---|---|
| REQ-001 | usage-window | DEC-002, DEC-012 |
| REQ-002 | provider-tab-bar, usage-window, tray-menu | DEC-001, DEC-002 |
| REQ-003 | provider-tab-bar, usage-window | DEC-001 |
| REQ-004 | usage-window, provider-tab-bar | DEC-001 |
| REQ-005 | provider-view-model, provider-tab-bar, usage-snapshot-publisher, usage-poller, countdown-evaluator | DEC-001, DEC-003, DEC-004, DEC-010 |
| REQ-006 | provider-view-model, provider-error-texts, provider-content-panel, usage-snapshot-publisher, usage-poller | DEC-003, DEC-004, DEC-005, DEC-010 |
| REQ-007 | usage-progress-bar, provider-view-model, usage-table | DEC-006, DEC-007 |
| REQ-008 | progress-level, usage-progress-bar, provider-view-model, gemini-usage-parser | DEC-006, DEC-007 |
| REQ-009 | provider-view-model, provider-content-panel | DEC-003 |
| REQ-010 | provider-view-model, provider-content-panel, usage-table, countdown-evaluator | DEC-008, DEC-010 |
| REQ-011 | provider-view-model, provider-content-panel, usage-table, countdown-evaluator | DEC-008, DEC-010 |
| REQ-012 | provider-view-model, provider-content-panel, usage-table | DEC-008 |
| REQ-013 | provider-view-model, provider-content-panel, usage-table | DEC-008 |
| REQ-014 | provider-content-panel, usage-window | DEC-008, DEC-009, DEC-012 |
| REQ-015 | usage-window, usage-history | DEC-011 |
| REQ-016 | provider-content-panel, provider-view-model | DEC-008, DEC-011 |
| REQ-017 | usage-window, provider-view-model, provider-tab-bar, usage-snapshot-publisher | DEC-003, DEC-009 |
| REQ-018 | provider-view-model, provider-error-texts, usage-snapshot-publisher | DEC-004, DEC-005 |
| REQ-019 | usage-table | DEC-008 |
| REQ-020 | provider-tab-bar | DEC-001 |
| REQ-021 | provider-content-panel, usage-table | DEC-008 |
| NFR-001 | usage-window, provider-view-model | DEC-003, DEC-009 |
| NFR-002 | usage-progress-bar, progress-level | DEC-006, DEC-007 |
| NFR-003 | provider-error-texts, provider-view-model | DEC-005 |

## Contratos afectados

Ninguno. La feature no crea ni cambia contratos de comunicación entre procesos; los cambios en publisher, poller y parser de Gemini son internos al módulo `desktop/app`.

## Cambios sugeridos al mapa

- ~ Módulo: usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java | Diálogo fijo con pestañas por proveedor, estado gris, tablas y barras; sin gráfico | usage-snapshot-publisher, provider-view-model, provider-tab-bar, provider-content-panel, countdown-evaluator
- + Módulo: provider-tab-bar | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui | Fila de cuatro pestañas fijas con icono, gris, resaltado y botón cerrar | -
- + Módulo: provider-view-model | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui | Calcula por proveedor el modelo de vista: obteniendo, error o tablas | usage-snapshot-publisher, countdown-evaluator, provider-error-texts, progress-level
- + Módulo: provider-error-texts | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui | Catálogo único de textos formales de error y de obteniendo datos | -
- + Módulo: provider-content-panel | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui | Pinta contenedor gris o tablas alineadas con hora y desplazamiento conjunto | usage-table
- + Módulo: usage-table | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui | Tabla sin bordes, cabecera negra, filas alternas y celdas de barra | usage-progress-bar
- + Módulo: usage-progress-bar | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui | Barra propia con porcentaje centrado y color por nivel | progress-level
- + Módulo: progress-level | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui | Nivel de color según umbrales sobre porcentaje sin redondear | -
- ~ Módulo: usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java | Publica todo resultado de cualquier proveedor; sin entrada es primera consulta pendiente | -
- ~ Módulo: usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java | Entrega a la vista el resultado de cada ciclo de todos los proveedores sin silencios | usage-source, usage-history, usage-snapshot-publisher, configuration-provider, lifecycle-coordinator
- ~ Módulo: gemini-usage-parser | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini | Interpreta command.data.groups; porcentaje consumido acotado y sin redondear | -
- ~ Módulo: usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java | Historial en memoria deduplicado por valor redondeado; la vista ya no lo consume | -
- ~ Módulo: tray-menu | src/main/java/es/vargontoc/subscriptionviewer/tray/TrayMenu.java | Menú contextual; la opción de mostrar abre el diálogo de pestañas | lifecycle-coordinator, usage-window
- + Convención: provider-tab-order: Claude, OpenAI, OpenCode, Gemini
- + Convención: provider-icons-path: desktop/app/src/main/resources/icons
- + Convención: error-texts-source: provider-error-texts es la única fuente de textos del estado gris
