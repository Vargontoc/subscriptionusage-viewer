# Tareas: provider-tabs-ui

Estado: aprobado

## Resumen

El trabajo se divide en 24 tareas ordenadas por dependencias: primero las piezas puras (nivel de color, textos formales, publicación sin silencios y precisión sin redondear en las fuentes), después el modelo de vista por proveedor, las barras, tablas y contenedores, las pestañas, la ventana y, al final, la integración con el publicador.

## Tareas

### [x] TASK-001 Nivel de color de la barra por umbrales

- **Cubre:** REQ-008, DEC-006
- **Componente:** progress-level | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/ProgressLevel.java
- **Depende de:** -
- **Alcance:** asignar el nivel de color (verde, amarillo, naranja, rojo o negro) a un porcentaje sin redondear según los umbrales de REQ-008.
- **Hecho cuando:** se cumplen los criterios de aceptación de REQ-008 y el porcentaje 99,96 % obtiene rojo y 100 % exacto obtiene negro.
- **Pruebas automáticas:**
  - Caso feliz: 0 %, 25 % y 50 % dan verde; 50,1 % y 75 % dan amarillo; 75,01 % y 90 % dan naranja; 90,01 %, 99 %, 99,5 % y 99,96 % dan rojo; 100 % exacto da negro.
  - Caso de límite: el nivel se calcula sobre el valor sin redondear, de modo que 99,96 % nunca da negro.
- **Prueba manual:**
  1. no aplica: función pura sin interfaz propia; su efecto visible se verifica en TASK-015.

### [x] TASK-002 Catálogo de textos formales de error y de primera consulta

- **Cubre:** REQ-006, REQ-018, NFR-003, DEC-005
- **Componente:** provider-error-texts | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/ProviderErrorTexts.java
- **Depende de:** -
- **Alcance:** catálogo único con los textos exactos de REQ-006 y "Obteniendo datos…" (REQ-018), seleccionados por proveedor y causa clasificada, sin componer texto con datos de respuestas, salidas o credenciales.
- **Hecho cuando:** los textos coinciden carácter a carácter con REQ-006 y REQ-018, y se cumplen los criterios de aceptación de REQ-006 a nivel de texto.
- **Pruebas automáticas:**
  - Caso feliz: para cada proveedor y causa de REQ-006, el texto devuelto es exactamente el de REQ-006; OpenAI con 401 o 403 devuelve el aviso de sesión caducada; el resto de causas de OpenAI devuelve el texto de conexión y sesión.
  - Caso de error: el texto de "Obteniendo datos…" no contiene las palabras de error, fallo ni aviso (REQ-018).
- **Prueba manual:**
  1. Comparar el catálogo con REQ-006 y REQ-018 línea a línea (tildes, puntuación y signo "…"); resultado esperado: coinciden todos.

### [x] TASK-003 Gemini: el parser entrega el porcentaje sin redondear

- **Cubre:** REQ-008, DEC-006
- **Componente:** gemini-usage-parser | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini
- **Depende de:** -
- **Alcance:** el parser entrega el porcentaje consumido acotado a 0-100 con todos sus decimales, sin redondear a un decimal.
- **Hecho cuando:** el valor 1,84 llega sin redondear al resto del sistema y el redondeo en origen ya no existe.
- **Pruebas automáticas:**
  - Caso feliz: la salida de ejemplo de `agy` con 1,84 % produce el valor 1,84.
  - Caso de límite: 99,96 se entrega como 99,96; los valores fuera de 0-100 se acotan a 0 o 100.
- **Prueba manual:**
  1. Revisar que las pruebas del parser aprobadas antes de la feature siguen en verde salvo las que asumían el redondeo, que deben haberse actualizado; resultado esperado: ninguna prueba en rojo sin justificación.

### [x] TASK-004 Historial de Gemini: deduplicación con un decimal

- **Cubre:** REQ-015, DEC-006, DEC-011
- **Componente:** usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java
- **Depende de:** TASK-003
- **Alcance:** la deduplicación del historial de Gemini compara el valor redondeado a un decimal, de modo que su acumulación no cambia; el resto de proveedores no cambia.
- **Hecho cuando:** el historial de Gemini acumula las mismas entradas que antes del cambio en origen y sigue acumulando en memoria.
- **Pruebas automáticas:**
  - Caso feliz: lecturas de Gemini de 1,84 y 1,76 (ambas 1,8 a un decimal) se deduplican en una sola entrada.
  - Caso de límite: lecturas de 1,86 y 1,84 (1,9 y 1,8 a un decimal) no se deduplican.
- **Prueba manual:**
  1. Ejecutar las pruebas de regresión existentes del historial; resultado esperado: en verde, con la misma acumulación de Gemini que la versión aprobada.

### [x] TASK-005 Claude: la fuente entrega el porcentaje sin redondear

- **Cubre:** REQ-008, DEC-006
- **Componente:** claude-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude
- **Depende de:** -
- **Alcance:** verificar que la fuente de Claude entrega el porcentaje de cada ventana sin redondear; si lo redondea, quitar el redondeo.
- **Hecho cuando:** un fichero de estado con 50,1 entrega 50,1 y con 99,96 entrega 99,96.
- **Pruebas automáticas:**
  - Caso feliz: un fichero de estado con 40 entrega 40.
  - Caso de límite: 50,1 y 99,96 llegan sin redondear.
  - Caso de error: un fichero ilegible o corrupto se clasifica como ausencia de datos y no como porcentaje.
- **Prueba manual:**
  1. Si existe el fichero de estado real de Claude, leer su valor y comprobar que conserva sus decimales; si solo contiene enteros, anotarlo en el informe de la tarea. Resultado esperado: el valor leído coincide con el del fichero.

### [x] TASK-006 OpenAI: el cliente entrega el porcentaje sin redondear

- **Cubre:** REQ-008, REQ-006, DEC-006
- **Componente:** openai-usage-client | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai
- **Depende de:** -
- **Alcance:** verificar que el cliente de OpenAI entrega el porcentaje de cada ventana sin redondear; si lo redondea, quitar el redondeo.
- **Hecho cuando:** una respuesta con 50,1 entrega 50,1 y con 99,96 entrega 99,96.
- **Pruebas automáticas:**
  - Caso feliz: una respuesta con 40 % entrega 40.
  - Caso de límite: 50,1 y 99,96 llegan sin redondear.
  - Caso de error: una respuesta 401 o 403 se entrega como sesión caducada, no como fallo de red ni como porcentaje.
- **Prueba manual:**
  1. Revisar en el informe de la tarea que las respuestas de prueba con decimales llegan sin redondear; resultado esperado: coincidencia con el valor de la respuesta.

### [x] TASK-007 OpenCode: el cálculo entrega el porcentaje sin redondear

- **Cubre:** REQ-008, REQ-013, DEC-006
- **Componente:** opencode-usage-calculator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeUsageCalculator.java
- **Depende de:** -
- **Alcance:** verificar que el cálculo de porcentajes de OpenCode entrega cada ventana y el total sin redondear, manteniendo la saturación a 100 %; si redondea, quitar el redondeo.
- **Hecho cuando:** un total de 31,25 % llega como 31,25 y un uso que supera el tope llega como 100.
- **Pruebas automáticas:**
  - Caso feliz: un total de 31,25 % se entrega como 31,25.
  - Caso de límite: un valor de 99,96 % se entrega como 99,96, no como 100.
  - Caso de error: un uso que supera el tope se satura a 100 y no supera ese valor.
- **Prueba manual:**
  1. Ejecutar las pruebas del cálculo aprobadas; resultado esperado: en verde, con los casos de saturación intactos.

### [x] TASK-008 Publicador: publicar siempre el resultado de cada consulta

- **Cubre:** REQ-005, REQ-006, REQ-018, DEC-004
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java
- **Depende de:** -
- **Alcance:** publicar el resultado de cada consulta de cualquier proveedor (fallos, sesión caducada, consulta fallida y ausencia de datos de Claude) sin silenciarlo ni eliminar la entrada; conservar el último dato válido solo internamente; un proveedor sin entrada publicada significa primera consulta pendiente.
- **Hecho cuando:** ningún resultado se descarta ni elimina la entrada del proveedor, y el último dato válido sigue disponible internamente.
- **Pruebas automáticas:**
  - Caso feliz: un resultado correcto se publica tal cual.
  - Caso de límite: una ausencia de datos de Claude se publica como estado distinto de correcto y no elimina la entrada anterior.
  - Caso de error: el fallo inicial de OpenAI se publica y no se oculta; tras un fallo, el último dato válido se conserva internamente.
- **Prueba manual:**
  1. Con las pruebas del publicador, ejecutar la secuencia correcto, fallo y ausencia; resultado esperado: tras cada paso, el estado publicado es el que corresponde y ningún paso deja el proveedor sin entrada.

### [ ] TASK-009 Sondeador: entregar todos los resultados de cada ciclo

- **Cubre:** REQ-006, REQ-018, DEC-004
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java
- **Depende de:** TASK-008
- **Alcance:** entregar al publicador exactamente un resultado clasificado por cada ciclo de cada proveedor, incluidas la ausencia de datos de Claude y los ciclos descartados de OpenCode, sin silenciarlos.
- **Hecho cuando:** cada ciclo de cada proveedor produce una entrega al publicador, incluidos los casos de fallo y ausencia.
- **Pruebas automáticas:**
  - Caso feliz: un ciclo correcto de OpenCode llega al publicador como dato.
  - Caso de límite: un ciclo de OpenCode descartado (salida inválida o tiempo agotado) llega como consulta fallida clasificada.
  - Caso de error: la ausencia de datos de Claude y el fallo inicial de OpenAI llegan al publicador y no se descartan.
- **Prueba manual:**
  1. Arrancar la aplicación con `opencode` no disponible y revisar el registro o la traza del publicador; resultado esperado: el primer ciclo de OpenCode aparece como fallo y no como silencio.

### [ ] TASK-010 Modelo de vista: estado, error y hora por proveedor

- **Cubre:** REQ-005, REQ-006, REQ-009, REQ-018, DEC-003
- **Componente:** provider-view-model | por definir (analista técnico)
- **Depende de:** TASK-002, TASK-008
- **Alcance:** a partir del último estado publicado y de la hora actual, producir por proveedor un modelo inmutable que sea de tres tipos: obteniendo datos, error (con su texto) o con datos (con la hora de actualización y la bandera de gris desactivada); las tablas las completan TASK-011 a TASK-014. Sin E/S y sin componentes gráficos.
- **Hecho cuando:** para cada proveedor, el modelo cumple REQ-005, REQ-006, REQ-009 y REQ-018 según el estado publicado.
- **Pruebas automáticas:**
  - Caso feliz: un estado correcto con datos produce un modelo con datos, sin gris, con la hora de ese proveedor.
  - Caso de límite: sin entrada publicada produce "obteniendo datos" con gris.
  - Caso de error: un estado de fallo produce el modelo de error con el texto de TASK-002, sin hora y sin los últimos datos válidos, aunque existieran.
- **Prueba manual:**
  1. no aplica: componente de lógica sin comportamiento visible propio; su efecto se verifica en TASK-017 y TASK-019.

### [ ] TASK-011 Modelo de vista: vigencia de Claude y OpenAI

- **Cubre:** REQ-005, REQ-006, REQ-010, REQ-011, DEC-010
- **Componente:** provider-view-model | por definir (analista técnico)
- **Depende de:** TASK-010
- **Alcance:** aplicar la vigencia de las ventanas de Claude y OpenAI con countdown-evaluator en cada refresco: se omite la tabla de una ventana caducada mientras la otra siga vigente, y el proveedor pasa a error si ninguna ventana está vigente.
- **Hecho cuando:** se cumplen los criterios de vigencia de REQ-005, REQ-006, REQ-010 y REQ-011.
- **Pruebas automáticas:**
  - Caso feliz: con ambas ventanas vigentes, el modelo conserva las dos ventanas y no está en gris.
  - Caso de límite: con la ventana de 5 horas caducada y la semanal vigente, solo queda la semanal y la pestaña no está en gris.
  - Caso de error: con ambas ventanas caducadas, Claude produce su texto de error, y OpenAI produce el texto de conexión y sesión.
- **Prueba manual:**
  1. Tras TASK-022, con el fichero de estado de prueba cuya ventana de 5 horas ya ha caducado, abrir la ventana y seleccionar Claude; resultado esperado: solo aparece la tabla semanal y la pestaña no está en gris.

### [ ] TASK-012 Modelo de vista: tablas de Claude y OpenAI

- **Cubre:** REQ-007, REQ-010, REQ-011, REQ-016, DEC-006
- **Componente:** provider-view-model | por definir (analista técnico)
- **Depende de:** TASK-011
- **Alcance:** construir las tablas de Claude y OpenAI, "Uso 5h" y "Uso semanal", cada una con cabecera "Reinicia en" y una fila con porcentaje y tiempo restante; el porcentaje se muestra con la precisión del proveedor y el texto de la barra es el valor mostrado. No se incluye plan, título ni pie.
- **Hecho cuando:** los criterios de contenido de REQ-010 y REQ-011 se cumplen a nivel de modelo.
- **Pruebas automáticas:**
  - Caso feliz: Claude con ambas ventanas produce las dos tablas con sus cabeceras, porcentaje 40 % (sin decimales añadidos) y tiempo restante.
  - Caso de límite: OpenAI sin ventana semanal produce solo la tabla de 5 horas.
  - Caso de error: una respuesta de OpenAI con `plan_type` no produce ninguna indicación de plan en el modelo.
- **Prueba manual:**
  1. Revisar en el informe de la prueba del modelo que las cabeceras son exactamente "Uso 5h", "Uso semanal" y "Reinicia en", y que no hay plan ni título; resultado esperado: coincide con REQ-010 y REQ-011.

### [ ] TASK-013 Modelo de vista: tablas de Gemini

- **Cubre:** REQ-007, REQ-012, DEC-006
- **Componente:** provider-view-model | por definir (analista técnico)
- **Depende de:** TASK-003, TASK-010
- **Alcance:** construir las tres tablas de Gemini: modelos (cabecera "Modelo"), uso de 5 horas y uso semanal (con "Reinicia en"), con una fila por grupo en el mismo orden; "N/A" en las celdas de una ventana ausente; el porcentaje se muestra con un decimal; los grupos con nombre no previsto se añaden con su nombre y los grupos sin cubos se omiten.
- **Hecho cuando:** los criterios de REQ-012 se cumplen a nivel de modelo.
- **Pruebas automáticas:**
  - Caso feliz: tres grupos producen tres filas en cada tabla, en el orden "Gemini Models", "Claude and GPT models" y "Gemini 4 Argon".
  - Caso de límite: "Gemini 4 Argon" sin ventana de 5 horas muestra "N/A" en su celda de uso y en la de reinicio; "Claude and GPT models" con 1,8 % en 5 horas y 0,9 % en la semana muestra cada valor en su tabla.
  - Caso de error: un grupo sin cubos no aparece en ninguna tabla; un grupo con nombre no previsto aparece con su nombre en las tres tablas.
- **Prueba manual:**
  1. Revisar en el informe de la prueba que las tres tablas tienen las mismas filas en el mismo orden y que aparece "N/A" donde corresponde; resultado esperado: coincide con REQ-012.

### [ ] TASK-014 Modelo de vista: tablas de OpenCode

- **Cubre:** REQ-013
- **Componente:** provider-view-model | por definir (analista técnico)
- **Depende de:** TASK-007, TASK-010
- **Alcance:** construir la tabla de modelos (cabecera "Modelo", una fila por modelo de `opencode-go` con consumo, y una fila final "Total") y la tabla de usos ("Uso 5h", "Uso semanal" y "Uso mensual"), con una fila por cada fila de modelos y sin columnas de tiempo restante ni de reinicio.
- **Hecho cuando:** los criterios de REQ-013 se cumplen a nivel de modelo.
- **Pruebas automáticas:**
  - Caso feliz: dos modelos producen tres filas en cada tabla (dos modelos y "Total"), con una sola fila de cabeceras.
  - Caso de límite: un total de 31,25 % produce la fila "Total" con su valor en las tres ventanas; el modelo A con 50 % en 5 horas queda alineado con su fila.
  - Caso de error: un modelo B que aparece en una consulta posterior se añade a ambas tablas sin repetir cabeceras.
- **Prueba manual:**
  1. Revisar en el informe de la prueba que las dos tablas tienen la misma cantidad de filas y ninguna columna de tiempo; resultado esperado: coincide con REQ-013.

### [ ] TASK-015 Barra de progreso propia

- **Cubre:** REQ-007, REQ-008, NFR-002, DEC-007
- **Componente:** usage-progress-bar | por definir (analista técnico)
- **Depende de:** TASK-001
- **Alcance:** barra con fondo blanco y borde negro, relleno proporcional al porcentaje con el color del nivel de TASK-001, y texto con el valor mostrado centrado (blanco al 100 %, negro en el resto de casos).
- **Hecho cuando:** se cumplen los criterios de REQ-007 y REQ-008 y el texto es legible en todos los colores (NFR-002).
- **Pruebas automáticas:**
  - Caso feliz: 40 % produce un relleno del 40 % de la longitud, el resto blanco, y el texto "40 %" negro en el centro.
  - Caso de límite: 0 % produce la barra sin relleno con texto centrado; 100 % produce relleno negro completo con texto blanco.
  - Caso de error: un porcentaje de 99,96 con texto mostrado "100,0 %" produce relleno rojo, no negro.
- **Prueba manual:**
  1. En una ventana de prueba temporal (no entregable), mostrar barras de 0 %, 40 %, 99,96 % y 100 %; resultado esperado: colores y textos según REQ-007 y REQ-008, y texto legible en la barra negra.

### [ ] TASK-016 Tabla de datos sin bordes

- **Cubre:** REQ-019, REQ-021, REQ-012, REQ-013, DEC-008
- **Componente:** usage-table | por definir (analista técnico)
- **Depende de:** TASK-015
- **Alcance:** tabla sin bordes entre celdas ni alrededor, cabecera de fondo negro con texto blanco, filas alternas gris y blanco, sin pie, con celdas de texto, "N/A" o barra de progreso, y con alto de fila y de cabecera común.
- **Hecho cuando:** se cumple REQ-019 y las celdas de barra usan usage-progress-bar.
- **Pruebas automáticas:**
  - Caso feliz: una tabla con cabecera y tres filas tiene cabecera negra y filas alternas gris y blanco.
  - Caso de límite: una tabla sin filas de datos muestra solo la cabecera, sin pie.
  - Caso de error: una celda "N/A" se muestra como texto, sin barra ni valor.
- **Prueba manual:**
  1. Revisar en el informe de la prueba visual de la tabla que no hay bordes ni fila de pie; resultado esperado: coincide con REQ-019.

### [ ] TASK-017 Contenedor de pestaña: estado gris y hora de actualización

- **Cubre:** REQ-006, REQ-009, REQ-016, REQ-018, NFR-003
- **Componente:** provider-content-panel | por definir (analista técnico)
- **Depende de:** TASK-002, TASK-010
- **Alcance:** pintar el modelo de un proveedor sin tablas: si está en gris, un contenedor de fondo gris con el texto formal o "Obteniendo datos…", sin hora; si tiene datos, el texto "última actualización" con la hora en la esquina superior izquierda. Sin título con el nombre del proveedor y sin pie.
- **Hecho cuando:** se cumplen los criterios de REQ-006 y REQ-009 a nivel de contenedor.
- **Pruebas automáticas:**
  - Caso feliz: un modelo con datos muestra "última actualización" con su hora arriba a la izquierda.
  - Caso de límite: un modelo de error de Claude muestra el contenedor gris con el texto exacto de Claude y no muestra hora.
  - Caso de error: un modelo de "obteniendo datos" muestra "Obteniendo datos…" y no contiene términos de error, fallo ni aviso.
- **Prueba manual:**
  1. Tras TASK-022, con el fichero de estado de Claude ausente, seleccionar Claude; resultado esperado: contenedor gris con el texto de Claude y nada más.

### [ ] TASK-018 Contenedor de pestaña: tablas en fila y desplazamiento conjunto

- **Cubre:** REQ-014, REQ-021, REQ-012, REQ-013, DEC-008
- **Componente:** provider-content-panel | por definir (analista técnico)
- **Depende de:** TASK-016, TASK-017
- **Alcance:** colocar las tablas de la pestaña en horizontal, con el mismo borde superior y una separación definida en un único valor ajustable; todas dentro de una única zona de desplazamiento vertical, de modo que se muevan juntas.
- **Hecho cuando:** se cumplen los criterios de REQ-021 y REQ-014 de disposición y desplazamiento.
- **Pruebas automáticas:**
  - Caso feliz: tres tablas empiezan a la misma altura, separadas por el valor configurado.
  - Caso de límite: un contenido más alto que el contenedor muestra una única barra vertical y las tablas se desplazan juntas con sus filas alineadas.
  - Caso de error: un contenido que cabe en el contenedor no muestra barra de desplazamiento.
- **Prueba manual:**
  1. Tras TASK-022, con OpenCode con varios modelos, desplazar la rueda del ratón; resultado esperado: ambas tablas se desplazan a la vez y las filas siguen alineadas.

### [ ] TASK-019 Fila de pestañas fijas

- **Cubre:** REQ-003, REQ-005, REQ-020, DEC-001
- **Componente:** provider-tab-bar | por definir (analista técnico)
- **Depende de:** TASK-010
- **Alcance:** fila con cuatro pestañas fijas en el orden Claude, OpenAI, OpenCode y Gemini, cada una con su icono PNG (`claude.png`, `openai.png`, `opencode.png` y `gemini.png` de `desktop/app/src/main/resources/icons`, que entran en el repositorio) y su nombre; cada pestaña se pinta en gris según su modelo; la seleccionada se distingue también si está en gris; emite el evento de cambio de pestaña.
- **Hecho cuando:** se cumplen los criterios de REQ-003, REQ-005 (color de la pestaña) y REQ-020, y los cuatro iconos están en el repositorio.
- **Pruebas automáticas:**
  - Caso feliz: las cuatro pestañas aparecen en orden, con icono y nombre.
  - Caso de límite: si falta un icono, la pestaña se pinta con su nombre y no impide pintar las demás (riesgo de DEC-001 y de la especificación de diseño).
  - Caso de error: con las cuatro pestañas en gris, las cuatro siguen visibles y la seleccionada se distingue de las demás.
- **Prueba manual:**
  1. Ejecutar `git ls-files desktop/app/src/main/resources/icons` y comprobar que aparecen los cuatro PNG; resultado esperado: los cuatro están versionados.

### [ ] TASK-020 Botón cerrar en la fila de pestañas

- **Cubre:** REQ-002, DEC-001
- **Componente:** provider-tab-bar | por definir (analista técnico)
- **Depende de:** TASK-019
- **Alcance:** botón cerrar en la misma fila de pestañas, alineado a la derecha del todo, que al pulsarlo emite la petición de ocultar la ventana.
- **Hecho cuando:** el botón está a la derecha de la última pestaña y emite la petición de ocultar.
- **Pruebas automáticas:**
  - Caso feliz: el botón cerrar ocupa la última posición a la derecha de la fila, tras la pestaña de Gemini.
  - Caso de error: pulsar el botón emite la petición de ocultar y no la de terminar la aplicación.
- **Prueba manual:**
  1. Tras TASK-021, abrir la ventana y comprobar que el botón cerrar está en el extremo derecho de la fila; resultado esperado: visible y alineado a la derecha.

### [ ] TASK-021 Ventana de tamaño fijo que solo se oculta

- **Cubre:** REQ-001, REQ-002, DEC-002, S9 de la especificación
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-020
- **Alcance:** alojar el contenido en un diálogo de tamaño fijo y no redimensionable, con barra de título y borde del sistema, sin minimizar ni maximizar; el cierre (barra de título y botón de la fila) solo oculta la ventana; se conservan mostrar, ocultar y el estado de visibilidad para tray-menu.
- **Hecho cuando:** se cumplen los criterios de REQ-001 y REQ-002, y la API pública usada por tray-menu no cambia.
- **Pruebas automáticas:**
  - Caso feliz: mostrar y ocultar cambian la visibilidad de la ventana.
  - Caso de límite: cerrar desde la barra de título solo oculta la ventana y la aplicación sigue en ejecución.
  - Caso de error: no existen controles de minimizar ni de maximizar, y el doble clic en la barra de título no cambia el tamaño.
- **Prueba manual:**
  1. Abrir la ventana desde el menú de la bandeja y arrastrar la barra de título; resultado esperado: la ventana se mueve.
  2. Arrastrar un borde o una esquina; resultado esperado: el tamaño no cambia.
  3. Observar la barra de título y hacer doble clic en ella; resultado esperado: no hay minimizar ni maximizar y el tamaño no cambia.
  4. Pulsar el botón cerrar; resultado esperado: la ventana desaparece y el icono de la bandeja sigue.
  5. Elegir mostrar la ventana de uso en el menú de la bandeja; resultado esperado: la ventana vuelve a mostrarse.
  6. Cerrar desde la barra de título; resultado esperado: mismo efecto que el botón cerrar.

### [ ] TASK-022 Composición de la ventana: pestañas, contenido y sin gráfico

- **Cubre:** REQ-003, REQ-004, REQ-014, REQ-015, REQ-016, DEC-011, DEC-012
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-012, TASK-013, TASK-014, TASK-018, TASK-019, TASK-021
- **Alcance:** componer en la ventana la fila de pestañas y el contenido de cada proveedor, sin gráfico ni tarjetas (el componente de gráfico existente se conserva sin instanciar); recordar la pestaña seleccionada en memoria, empezando por Claude tras arrancar; tamaño fijo como constante, validado para que Claude, OpenAI y Gemini quepan sin desplazamiento.
- **Hecho cuando:** se cumplen REQ-004 y REQ-015, y el contenido más alto (Gemini con tres grupos) cabe sin barra de desplazamiento.
- **Pruebas automáticas:**
  - Caso feliz: al mostrar la ventana por primera vez desde el arranque, Claude está seleccionada.
  - Caso de límite: si el usuario elige Gemini, oculta la ventana y la vuelve a mostrar, Gemini sigue seleccionada.
  - Caso de error: la composición no instancia el gráfico y su interfaz es la misma con el historial vacío que con datos.
- **Prueba manual:**
  1. Arrancar la aplicación, abrir la ventana; resultado esperado: Claude seleccionada.
  2. Seleccionar Gemini, ocultar y volver a mostrar; resultado esperado: Gemini seleccionada.
  3. Reiniciar la aplicación y abrir la ventana; resultado esperado: Claude seleccionada.
  4. Con Gemini de tres grupos y OpenCode con datos, revisar las cuatro pestañas; resultado esperado: no hay gráfico y no aparece barra de desplazamiento en Claude, OpenAI ni Gemini.

### [ ] TASK-023 Ventana: refresco automático y repintado solo al cambiar

- **Cubre:** REQ-004, REQ-010, REQ-017, NFR-001, DEC-009
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-022
- **Alcance:** refrescar cada segundo y en cada notificación; reconstruir solo el contenido de los proveedores cuyo modelo cambió (comparación por valor), conservando el desplazamiento y la pestaña seleccionada; actualizar el color de todas las pestañas aunque no estén seleccionadas.
- **Hecho cuando:** se cumplen REQ-017 y NFR-001.
- **Pruebas automáticas:**
  - Caso feliz: un cambio de Claude de 40 % a 60 % pasa la barra de verde a amarillo en la siguiente lectura, sin acción del usuario.
  - Caso de límite: con una consulta de Gemini en curso de más de 8 segundos, cambiar de pestaña muestra el contenido nuevo en menos de 1 segundo.
  - Caso de error: si el modelo de un proveedor no cambia, su contenido no se reconstruye y el desplazamiento de OpenCode se conserva.
- **Prueba manual:**
  1. Con OpenCode con muchos modelos, desplazar la lista y esperar más de un minuto; resultado esperado: el desplazamiento se mantiene.
  2. Con Gemini en una consulta lenta, cambiar de pestaña; resultado esperado: el contenido aparece en menos de 1 segundo.
  3. Con Claude mostrado, anotar el tiempo restante y esperar un minuto; resultado esperado: el tiempo restante disminuye en un minuto.

### [ ] TASK-024 Integración: publicador a ventana, cambio de estado sin reiniciar

- **Cubre:** REQ-005, REQ-006, REQ-017, REQ-018
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java y usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-009, TASK-023
- **Alcance:** verificar la comunicación entre usage-snapshot-publisher y usage-window: cada cambio publicado llega a la ventana en el hilo de interfaz y actualiza la pestaña y su contenido (con datos a gris, gris a con datos, obteniendo datos a error).
- **Hecho cuando:** los criterios de REQ-005, REQ-006 y REQ-018 se cumplen en la ventana sin reiniciar la aplicación.
- **Pruebas automáticas:**
  - Caso feliz: tras un fallo, una publicación con datos vigentes deja la pestaña de no estar en gris sin reiniciar.
  - Caso de límite: la primera consulta de Gemini en curso muestra "Obteniendo datos…"; si termina con fallo, muestra el texto de Gemini.
  - Caso de error: una segunda consulta en curso con el proveedor ya en error mantiene el texto de error y no muestra "Obteniendo datos…".
- **Prueba manual:**
  1. Renombrar temporalmente el fichero de estado de Claude; resultado esperado: en el ciclo siguiente la pestaña de Claude está en gris y el contenedor muestra el texto de Claude, sin tocar nada.
  2. Restaurar el fichero; resultado esperado: en el ciclo siguiente la pestaña deja de estar en gris y aparecen las tablas.
  3. Arrancar la aplicación con `agy` lento; resultado esperado: Gemini muestra "Obteniendo datos…" hasta que termina su primera consulta.

## Cobertura

| Requisito | Tareas |
|---|---|
| REQ-001 | TASK-021 |
| REQ-002 | TASK-020, TASK-021 |
| REQ-003 | TASK-019, TASK-022 |
| REQ-004 | TASK-022, TASK-023 |
| REQ-005 | TASK-008, TASK-010, TASK-011, TASK-019, TASK-024 |
| REQ-006 | TASK-002, TASK-008, TASK-009, TASK-010, TASK-011, TASK-017, TASK-024 |
| REQ-007 | TASK-012, TASK-013, TASK-015 |
| REQ-008 | TASK-001, TASK-003, TASK-005, TASK-006, TASK-007, TASK-015 |
| REQ-009 | TASK-010, TASK-017 |
| REQ-010 | TASK-011, TASK-012, TASK-018, TASK-023 |
| REQ-011 | TASK-011, TASK-012 |
| REQ-012 | TASK-013, TASK-016, TASK-018 |
| REQ-013 | TASK-007, TASK-014, TASK-016, TASK-018 |
| REQ-014 | TASK-018, TASK-022 |
| REQ-015 | TASK-004, TASK-022 |
| REQ-016 | TASK-012, TASK-017, TASK-022 |
| REQ-017 | TASK-023, TASK-024 |
| REQ-018 | TASK-002, TASK-008, TASK-009, TASK-010, TASK-017, TASK-024 |
| REQ-019 | TASK-016 |
| REQ-020 | TASK-019 |
| REQ-021 | TASK-016, TASK-018 |
| NFR-001 | TASK-023 |
| NFR-002 | TASK-015 |
| NFR-003 | TASK-002, TASK-017 |

Avisos:
- Componentes nuevos sin ruta: provider-tab-bar, provider-view-model, provider-error-texts, provider-content-panel, usage-table, usage-progress-bar y progress-level se marcan "por definir (analista técnico)", aunque el diseño sugiere rutas en "Cambios sugeridos al mapa".
- El componente de gráfico existente (UsageChartPanel) y las tarjetas (UsageCardPanel, UsageProviderCardPanel) no constan en el mapa; no tienen tarea propia, solo se dejan de componer en TASK-022, como pide DEC-011.
- Supuesto: la entrega de ausencia de Claude y de ciclos descartados de OpenCode por las fuentes (claude-usage-source, opencode-usage-source) se asume según el diseño; si no existe hoy, TASK-009 debe cubrirlo. Las tareas TASK-005 a TASK-007 verifican la precisión en las fuentes; si ya no redondean, el desarrollador lo anota y cierra la tarea sin cambios de código.
- Supuesto de precisión de OpenCode: se muestra en enteros, como hoy (S4 y diseño). REQ-013 cita "31,25 %" con "barra y valor reflejan ese porcentaje"; la barra usa el valor sin redondear y el texto muestra el entero. Conviene confirmar con el usuario si el texto debe mostrar dos decimales.
- Los pasos manuales de TASK-010 a TASK-018 se verifican antes de que la ventana esté compuesta (TASK-022); usan informes de prueba o ventanas temporales de desarrollo. Las comprobaciones visuales completas están en TASK-021, TASK-022, TASK-023 y TASK-024.
- REQ-015 no tiene caso de error en la especificación; no se ha propuesto ninguno.
