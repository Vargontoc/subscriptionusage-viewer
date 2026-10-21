# Requisitos: provider-tabs-ui

Estado: aprobado

## Contexto

El usuario (monousuario, entorno local de desarrollo) quiere rediseñar la ventana de uso para que cada proveedor (Claude, OpenAI, OpenCode y Gemini) tenga su propia pestaña con icono y nombre, barras de progreso coloreadas según el nivel de consumo y los datos organizados en tablas sin bordes. Las cuatro pestañas están siempre visibles para dar feedback de qué proveedores se monitorizan; una pestaña sin datos vigentes se muestra en gris y su contenedor muestra el texto formal del error de ese proveedor. El gráfico de evolución queda oculto y fuera de alcance: será objeto de la siguiente especificación.

## Fuera de alcance

- El gráfico de evolución: se oculta en esta feature y se rediseñará en la siguiente especificación. No se modifica la acumulación de datos de evolución ni se elimina ninguno de sus requisitos aprobados.
- Cambios en la obtención de datos, intervalos de refresco, cálculo de porcentajes o tiempos de reinicio de cualquier proveedor.
- Mostrar el plan de OpenAI, el nombre del proveedor dentro del contenedor de la pestaña o un pie (footer) en las tablas.
- Textos de error, aviso o estado distintos de los aceptados en REQ-006 y REQ-018; indicadores de datos desactualizados.
- Quitar la barra de título o el borde de la ventana (descartado: el usuario necesita poder moverla).
- Minimizar, maximizar o redimensionar la ventana.
- Persistir la pestaña seleccionada entre ejecuciones de la aplicación.
- Cambios en el menú de la bandeja del sistema.

## Impacto en especificaciones aprobadas

Al aprobarse esta feature, prevalece sobre las specs aprobadas en lo siguiente:

- **Fallo silencioso y ocultación de proveedores** (claude-usage-monitor REQ-008, REQ-009, REQ-010; opencode-usage-monitor-v2 REQ-003 y REQ-013; openai-usage-monitor REQ-004 y REQ-014; gemini-usage-monitor REQ-013): prevalece esta feature. Claude y OpenCode ya no fallan en silencio: su pestaña pasa a gris y el contenedor muestra el texto formal de REQ-006. Ningún proveedor se oculta; las cuatro pestañas se muestran siempre. Las ventanas individuales caducadas de Claude siguen omitiéndose mientras otra ventana del proveedor tenga datos vigentes.
- **Avisos formales** (openai-usage-monitor REQ-012; gemini-usage-monitor REQ-011): no dejan de mostrarse; se mantienen sus textos y se muestran en el contenedor gris (REQ-006). Prevalece esta feature en que los últimos datos válidos ya no se muestran junto al aviso.
- **Datos desactualizados** (openai-usage-monitor REQ-013; opencode-usage-monitor-v2 REQ-003 y REQ-015): los últimos datos válidos no se muestran tras un fallo; en su lugar la pestaña pasa a gris con el texto de error del proveedor. El indicador de desactualización no existe.
- **Plan de OpenAI** (openai-usage-monitor REQ-008): deja de mostrarse.
- **Secciones por proveedor y gráficos** (claude REQ-006, openai REQ-011, gemini REQ-009, opencode-usage-monitor-v2 REQ-011): la sección única con todos los proveedores y los gráficos dejan de mostrarse en la ventana; los requisitos de acumulación no se retiran.
- **Hora de última actualización** (claude REQ-005, openai REQ-010, gemini REQ-008, opencode-usage-monitor-v2 REQ-010): se mantiene el dato; cambia su ubicación (REQ-009).
- **Fila Total y filas por modelo/grupo** (opencode-usage-monitor-v2 REQ-008, REQ-016; gemini REQ-006): se mantienen, reorganizadas en tablas.

## Requisitos funcionales

### REQ-001 Ventana de tamaño fijo y desplazable por el usuario

- **Requisito:** El sistema deberá mostrar la ventana de uso con un tamaño fijo, con barra de título y borde que permitan al usuario moverla a cualquier posición de la pantalla, y sin posibilidad de redimensionar, minimizar ni maximizar.
- **Criterios de aceptación:**
  - Dado que la ventana está abierta, cuando el usuario arrastra su barra de título, entonces la ventana se desplaza a la posición elegida.
  - Dado que la ventana está abierta, cuando el usuario arrastra un borde o una esquina, entonces el tamaño de la ventana no cambia.
  - Dado que la ventana está abierta, cuando el usuario observa su barra de título, entonces no existen controles de minimizar ni de maximizar, y la acción de maximizar por doble clic en la barra de título no cambia su tamaño.

### REQ-002 Botón cerrar en la fila de pestañas

- **Requisito:** El sistema deberá mostrar un botón cerrar en la misma fila que las pestañas, alineado a la derecha del todo, que oculte la ventana sin terminar la aplicación, con el mismo efecto que el cierre desde la barra de título.
- **Criterios de aceptación:**
  - Dado que la ventana está abierta, cuando el usuario mira la fila de pestañas, entonces ve el botón cerrar a la derecha de la última pestaña, en el extremo derecho de la fila.
  - Dado que la ventana está abierta, cuando el usuario pulsa el botón cerrar, entonces la ventana desaparece y la aplicación sigue en ejecución con su icono en la bandeja.
  - Dado que la ventana se ha ocultado, cuando el usuario elige mostrar la ventana de uso en el menú de la bandeja, entonces la ventana vuelve a mostrarse.
  - Dado que la ventana está abierta, cuando el usuario la cierra desde la barra de título, entonces el resultado es el mismo que con el botón cerrar.

### REQ-003 Cuatro pestañas siempre visibles, en orden fijo

- **Requisito:** El sistema deberá mostrar siempre cuatro pestañas, una por proveedor, en este orden de izquierda a derecha: Claude, OpenAI, OpenCode y Gemini, cada una con el nombre y el icono del proveedor, con independencia de si hay datos de ese proveedor.
- **Criterios de aceptación:**
  - Dado que la aplicación acaba de arrancar y ningún proveedor ha devuelto datos, cuando el usuario abre la ventana, entonces ve las cuatro pestañas, en el orden Claude, OpenAI, OpenCode, Gemini, cada una con su icono y su nombre.
  - Dado que solo un proveedor tiene datos, cuando el usuario mira la ventana, entonces las cuatro pestañas siguen visibles.
  - Dado cada pestaña, cuando el usuario la observa, entonces muestra el icono PNG correspondiente (Claude, OpenAI, OpenCode o Gemini) tomado de los recursos de iconos de la aplicación.

### REQ-004 Pestaña inicial y memoria de la pestaña seleccionada

- **Requisito:** Cuando se muestra la ventana, el sistema deberá seleccionar la pestaña de Claude la primera vez que se muestra desde el arranque y, en las veces posteriores durante la misma ejecución de la aplicación, la pestaña que estuviera seleccionada la última vez.
- **Criterios de aceptación:**
  - Dado que la aplicación acaba de arrancar, cuando el usuario abre la ventana por primera vez, entonces la pestaña de Claude está seleccionada.
  - Dado que el usuario seleccionó la pestaña de Gemini y ocultó la ventana, cuando la vuelve a mostrar, entonces la pestaña de Gemini está seleccionada.
  - Dado que el usuario seleccionó la pestaña de OpenCode y reinicia la aplicación, cuando abre la ventana, entonces la pestaña de Claude está seleccionada.
  - Dado que el usuario cambia de pestaña varias veces, cuando oculta y muestra la ventana, entonces está seleccionada la última pestaña elegida.

### REQ-005 Pestaña en gris sin datos vigentes

- **Requisito:** Mientras un proveedor no tenga datos vigentes, el sistema deberá mostrar su pestaña con fondo gris. Se considera que un proveedor no tiene datos vigentes cuando todavía no ha devuelto datos, cuando todas sus ventanas han caducado, cuando sus últimos datos están desactualizados por un fallo de consulta o cuando se encuentra en estado de aviso de error.
- **Criterios de aceptación:**
  - Dado que el fichero de estado de Claude no existe, cuando el usuario mira la ventana, entonces la pestaña de Claude aparece en gris.
  - Dado que OpenAI tiene la sesión caducada, cuando el usuario mira la ventana, entonces la pestaña de OpenAI aparece en gris.
  - Dado que `agy` falla en Gemini, cuando el usuario mira la ventana, entonces la pestaña de Gemini aparece en gris.
  - Dado que la última consulta de OpenAI o de OpenCode falló y sus datos están desactualizados, cuando el usuario mira la ventana, entonces la pestaña correspondiente aparece en gris.
  - Dado que una pestaña estaba en gris, cuando una consulta posterior tiene éxito con datos vigentes, entonces la pestaña deja de estar en gris sin reiniciar la aplicación.
  - Dado que un proveedor tiene datos vigentes, cuando el usuario mira la ventana, entonces su pestaña no aparece en gris.

### REQ-006 Contenedor gris con el texto formal de error

- **Requisito:** Mientras la pestaña de un proveedor está en gris y no se está realizando su primera consulta tras el arranque (REQ-018), el sistema deberá mostrar en su contenedor un fondo gris con el texto formal de error de ese proveedor, sin tablas, sin barras de progreso, sin hora de última actualización y sin los últimos datos válidos. Los textos son:
  - **Claude** (fichero de estado inexistente, ilegible o corrupto, o todas las ventanas caducadas): "No se han podido obtener los datos de uso de Claude. Compruebe que Claude Code está en uso y que el hook de la barra de estado está configurado, y vuelva a intentarlo más tarde."
  - **OpenCode** (falla `opencode`, salida inválida o ciclo descartado): "No se ha podido obtener el consumo de OpenCode. Por favor, compruebe que la herramienta opencode está instalada y vuelva a intentarlo más tarde."
  - **OpenAI** con respuesta 401 o 403: el aviso formal de openai-usage-monitor REQ-012: "La sesión de OpenAI ha caducado o no es válida. Por favor, vuelva a iniciar sesión mediante el CLI de Codex u opencode para continuar consultando el consumo de su suscripción."
  - **OpenAI** sin credenciales, con fallo de red o con respuesta no válida: "No se ha podido obtener el consumo de OpenAI. Por favor, compruebe su conexión y que ha iniciado sesión en el CLI de Codex u opencode, y vuelva a intentarlo más tarde."
  - **Gemini** (cualquier condición de gemini-usage-monitor REQ-011): "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde."
  - **Cualquier proveedor sin datos y sin error** (por ejemplo, todas las ventanas de Claude caducadas sin fallo de lectura): el mismo texto de error de ese proveedor.
- **Criterios de aceptación:**
  - Dado que el fichero de estado de Claude no existe, está corrupto o todas sus ventanas han caducado, cuando el usuario selecciona la pestaña de Claude, entonces ve el contenedor gris con exactamente el texto de Claude y ninguna tabla, barra ni hora.
  - Dado que OpenCode falla (CLI ausente, salida inválida o ciclo descartado), cuando el usuario selecciona su pestaña, entonces ve el contenedor gris con exactamente el texto de OpenCode.
  - Dado que OpenAI responde 401 o 403, cuando el usuario selecciona su pestaña, entonces ve exactamente el aviso de sesión caducada y no el texto de red o credenciales.
  - Dado que OpenAI no tiene credenciales, falla por red o recibe una respuesta no válida, cuando el usuario selecciona su pestaña, entonces ve exactamente el texto de conexión y sesión de OpenAI.
  - Dado que Gemini no puede obtener datos, cuando el usuario selecciona su pestaña, entonces ve exactamente el aviso formal de Gemini, sin salida del comando ni detalles técnicos.
  - Dado que se habían obtenido datos válidos antes del fallo, cuando el proveedor pasa a gris, entonces el contenedor no muestra esos últimos datos ni su hora de actualización.
  - Dado que la pestaña seleccionada pasa de tener datos a estar en gris, cuando se completa el ciclo siguiente, entonces el contenedor sustituye las tablas por el contenedor gris con el texto, sin intervención del usuario.
  - Dado que el proveedor se recupera, cuando una consulta posterior tiene éxito con datos vigentes, entonces el texto desaparece y se muestran las tablas.

### REQ-007 Barra de progreso por ventana

- **Requisito:** Cuando un proveedor tiene datos vigentes, el sistema deberá mostrar cada porcentaje consumido como una barra de progreso con fondo blanco y borde negro, cuyo relleno sea proporcional al porcentaje y coloreado según REQ-008, con el valor numérico del porcentaje, con la precisión que ya usa ese proveedor, centrado sobre la barra (en mitad de la barra); el texto será blanco cuando el porcentaje sea 100 % y negro en cualquier otro caso.
- **Criterios de aceptación:**
  - Dado un porcentaje del 40 %, cuando el usuario mira la barra, entonces el 40 % de su longitud aparece relleno, el resto es blanco, tiene borde negro y el texto "40 %" aparece negro en el centro de la barra.
  - Dado un porcentaje del 0 %, cuando el usuario mira la barra, entonces aparece sin relleno con el texto en el centro.
  - Dado un porcentaje del 100 %, cuando el usuario mira la barra, entonces aparece completamente rellena de negro y su texto es blanco y legible.
  - Dado que Gemini informa de 1,8 % consumido, cuando el usuario mira la barra, entonces el valor numérico mostrado es 1,8 % y, si Claude informa de 40 %, se muestra 40 % sin decimales añadidos.

### REQ-008 Umbrales de color de la barra de progreso

- **Requisito:** El sistema deberá colorear el relleno de cada barra de progreso según el porcentaje consumido sin redondear: verde hasta 50 % inclusive; amarillo por encima de 50 % y hasta 75 % inclusive; naranja por encima de 75 % y hasta 90 % inclusive; rojo por encima de 90 % y por debajo de 100 %; negro únicamente cuando el porcentaje es exactamente 100 %.
- **Criterios de aceptación:**
  - Dado un porcentaje de 0 %, 25 % o 50 %, cuando se muestra la barra, entonces es verde.
  - Dado un porcentaje de 50,1 %, cuando se muestra la barra, entonces es amarilla aunque el valor mostrado sea 50 %.
  - Dado un porcentaje de 75 %, cuando se muestra la barra, entonces es amarilla; con 75,01 % es naranja.
  - Dado un porcentaje de 90 %, cuando se muestra la barra, entonces es naranja; con 90,01 % es roja.
  - Dado un porcentaje de 99 % o de 99,5 %, cuando se muestra la barra, entonces es roja (el tramo entre 99 % y 100 % exclusivos se pinta rojo; ver supuesto S8).
  - Dado un porcentaje de 99,96 % que se muestra redondeado a un decimal como 100,0 %, cuando se muestra la barra, entonces es roja y no negra.
  - Dado un porcentaje de exactamente 100 %, cuando se muestra la barra, entonces es negra.

### REQ-009 Hora de última actualización arriba a la izquierda

- **Requisito:** Cuando un proveedor tiene datos vigentes, el sistema deberá mostrar en su contenedor, en la esquina superior izquierda, el texto "última actualización" con la hora de la última actualización de ese proveedor, calculada con el mismo criterio que usa hoy cada proveedor.
- **Criterios de aceptación:**
  - Dado que el hook de Claude escribió el fichero a las 10:32:15, cuando el usuario selecciona la pestaña de Claude, entonces ve el texto "última actualización" con 10:32:15 arriba a la izquierda del contenedor.
  - Dado que la última consulta exitosa de OpenAI se completó a las 14:10:05, cuando el usuario selecciona su pestaña, entonces ve 14:10:05 arriba a la izquierda.
  - Dado que dos proveedores tienen horas de actualización distintas, cuando el usuario cambia de pestaña, entonces cada contenedor muestra la hora de su propio proveedor.
  - Dado que el proveedor está en gris, cuando el usuario selecciona su pestaña, entonces no aparece la hora.

### REQ-010 Tablas de Claude

- **Requisito:** Cuando Claude tiene datos vigentes, el sistema deberá mostrar en su pestaña dos tablas: la tabla de uso de 5 horas, con cabeceras "Uso 5h" y "Reinicia en" y una fila con la barra de progreso y el tiempo restante hasta el reinicio; y la tabla de uso semanal, con cabeceras "Uso semanal" y "Reinicia en" y una fila con la barra y el tiempo restante.
- **Criterios de aceptación:**
  - Dado que ambas ventanas de Claude están vigentes, cuando el usuario selecciona la pestaña de Claude, entonces ve dos tablas con las cabeceras indicadas, cada una con una barra con su porcentaje y el tiempo restante.
  - Dado que la ventana de 5 horas ha caducado y la semanal sigue vigente, cuando el usuario selecciona la pestaña de Claude, entonces ve solo la tabla semanal y la pestaña no está en gris.
  - Dado que no se recibe ninguna lectura nueva, cuando transcurre un minuto, entonces el tiempo restante mostrado disminuye en un minuto.
  - Dado que se muestran los datos de Claude, cuando el usuario mira el contenedor, entonces no aparece el texto "Claude" como título.

### REQ-011 Tablas de OpenAI

- **Requisito:** Cuando OpenAI tiene datos vigentes, el sistema deberá mostrar en su pestaña dos tablas con el mismo contenido que las de Claude (REQ-010): tabla de uso de 5 horas ("Uso 5h" | "Reinicia en") y tabla de uso semanal ("Uso semanal" | "Reinicia en"), cada una con la barra de progreso con su porcentaje y el tiempo restante, sin indicar el plan.
- **Criterios de aceptación:**
  - Dado que OpenAI devuelve ambas ventanas, cuando el usuario selecciona la pestaña de OpenAI, entonces ve las dos tablas con las cabeceras indicadas, barra, porcentaje y tiempo restante.
  - Dado que la respuesta no incluye ventana semanal, cuando el usuario selecciona la pestaña, entonces ve solo la tabla de 5 horas.
  - Dado que la respuesta incluye `plan_type`, cuando el usuario selecciona la pestaña, entonces no aparece ninguna indicación de plan.

### REQ-012 Tablas de Gemini

- **Requisito:** Cuando Gemini tiene datos vigentes, el sistema deberá mostrar en su pestaña tres tablas: (1) tabla de modelos, con la cabecera "Modelo" y una fila por grupo ("Gemini Models", "Claude and GPT models" y "Gemini 4 Argon"); (2) tabla de uso de 5 horas, con cabeceras "Uso 5h" y "Reinicia en", con una fila por grupo que contiene la barra de progreso y el tiempo restante; (3) tabla de uso semanal, con cabeceras "Uso semanal" y "Reinicia en", con una fila por grupo que contiene la barra de progreso y el tiempo restante. Para un grupo sin ventana de 5 horas ("Gemini 4 Argon"), la fila de la tabla 2 deberá mostrar "N/A" en cada celda.
- **Criterios de aceptación:**
  - Dado que existen los tres grupos de Gemini, cuando el usuario selecciona la pestaña de Gemini, entonces la tabla de modelos lista "Gemini Models", "Claude and GPT models" y "Gemini 4 Argon", y las tablas de 5 horas y semanal tienen una fila por cada uno, en el mismo orden.
  - Dado que "Gemini 4 Argon" solo tiene ventana semanal, cuando el usuario mira la tabla de 5 horas, entonces su fila muestra "N/A" en la celda de uso y en la de reinicio, sin barra ni valor inventado.
  - Dado que la respuesta incluye un grupo con nombre no previsto, cuando el usuario selecciona la pestaña, entonces el grupo aparece con su nombre en las tres tablas.
  - Dado que un grupo no tiene cubos o está ausente de la respuesta, cuando el usuario selecciona la pestaña, entonces el grupo no aparece en ninguna tabla.
  - Dado que "Claude and GPT models" tiene 1,8 % en 5 horas y 0,9 % en la semana, cuando el usuario mira las tablas, entonces cada tabla muestra el valor propio de esa ventana con un decimal.

### REQ-013 Tablas de OpenCode

- **Requisito:** Cuando OpenCode tiene datos vigentes, el sistema deberá mostrar en su pestaña dos tablas: (1) tabla de modelos, con la cabecera "Modelo" y una fila por cada modelo de `opencode-go` con consumo, más una fila final "Total"; (2) tabla de usos, con cabeceras "Uso 5h", "Uso semanal" y "Uso mensual", y por cada fila de la tabla 1 una fila con tres barras de progreso (una por ventana). Las filas se añaden según los modelos recibidos, sin repetir las cabeceras, y la tabla de usos no tiene columnas de tiempo restante ni de reinicio.
- **Criterios de aceptación:**
  - Dado que hay dos modelos con consumo, cuando el usuario selecciona la pestaña de OpenCode, entonces la tabla de modelos tiene tres filas (los dos modelos y "Total") y la tabla de usos tiene tres filas con tres barras cada una, con una sola fila de cabeceras en cada tabla.
  - Dado el modelo A con 50 % en 5 horas, cuando el usuario mira la fila de A, entonces el 50 % de 5 horas aparece en la fila de la tabla de usos alineada con A, junto a sus valores semanal y mensual.
  - Dado que el modelo B aparece en una consulta posterior, cuando se actualiza la ventana, entonces se añade su fila a ambas tablas sin repetir cabeceras.
  - Dado que el total de una ventana es 31,25 %, cuando el usuario mira la fila "Total", entonces la barra y el valor reflejan ese porcentaje.
  - Dado que la pestaña de OpenCode tiene datos, cuando el usuario la observa, entonces no aparece ninguna columna ni texto de tiempo restante o reinicio.

### REQ-014 Desplazamiento vertical del contenido

- **Requisito:** Si el contenido de una pestaña excede el alto fijo de su contenedor, entonces el sistema deberá permitir desplazarse verticalmente dentro del contenedor sin cambiar el tamaño de la ventana; el alto de la ventana deberá ser suficiente para que los contenidos de Claude, OpenAI y Gemini con todos sus grupos y ventanas se vean sin desplazamiento.
- **Criterios de aceptación:**
  - Dado que OpenCode tiene más modelos de los que caben en el contenedor, cuando el usuario usa la rueda del ratón o la barra de desplazamiento, entonces ve el resto de filas sin que la ventana cambie de tamaño.
  - Dado que Claude, OpenAI o Gemini muestran todos sus grupos y ventanas con datos, cuando el usuario selecciona su pestaña, entonces no aparece barra de desplazamiento vertical.
  - Dado que el contenido cabe en el contenedor, cuando el usuario selecciona la pestaña, entonces no aparece barra de desplazamiento.
  - Dado que las tablas de OpenCode se desplazan, cuando el usuario se desplaza, entonces ambas tablas se desplazan juntas y sus filas siguen alineadas.

### REQ-015 Gráfico oculto

- **Requisito:** El sistema no deberá mostrar el gráfico de evolución en la ventana de uso mientras no se defina su nuevo diseño.
- **Criterios de aceptación:**
  - Dado que la aplicación lleva abierta varias actualizaciones, cuando el usuario recorre las cuatro pestañas, entonces no aparece ningún gráfico de evolución.
  - Dado que los datos de evolución se siguen acumulando en memoria, cuando se muestra la ventana, entonces la interfaz es la misma que sin ellos.

### REQ-016 Contenido sin elementos retirados

- **Requisito:** El sistema no deberá mostrar en el contenedor de una pestaña el nombre del proveedor como título, el plan de OpenAI ni un pie (footer) bajo las tablas.
- **Criterios de aceptación:**
  - Dado que la pestaña de OpenAI tiene datos, cuando el usuario la selecciona, entonces el contenedor no repite el nombre "OpenAI" como título ni muestra el plan.
  - Dado cualquier pestaña con tablas, cuando el usuario mira bajo las tablas, entonces no hay ningún texto de pie.

### REQ-017 Actualización automática de la pestaña mostrada

- **Requisito:** Mientras la ventana está visible, el sistema deberá actualizar sin intervención del usuario las barras, los porcentajes, los tiempos restantes, la hora de última actualización, el contenido gris o con datos y el color de las pestañas cuando cambien los datos del proveedor.
- **Criterios de aceptación:**
  - Dado que la ventana está abierta en la pestaña de Claude y el consumo cambia de 40 % a 60 %, cuando se completa la siguiente lectura, entonces la barra y el color pasan de verde a amarillo sin que el usuario haga nada.
  - Dado que la ventana está abierta en la pestaña de Claude y OpenAI deja de tener datos vigentes, cuando se completa el ciclo siguiente, entonces la pestaña de OpenAI pasa a gris sin que el usuario cambie de pestaña.
  - Dado que el usuario está en una pestaña, cuando se actualizan los datos, entonces la pestaña seleccionada no cambia.

### REQ-018 Estado "Obteniendo datos…" en la primera consulta

- **Requisito:** Mientras un proveedor no ha completado su primera consulta tras el arranque de la aplicación, el sistema deberá mostrar su pestaña en gris y, en su contenedor, el texto "Obteniendo datos…", sin términos de error, fallo ni aviso, sin tablas y sin hora de actualización. Cuando esa primera consulta termine, el sistema deberá mostrar el contenido que corresponda según REQ-005 y REQ-006.
- **Criterios de aceptación:**
  - Dado que la aplicación acaba de arrancar y la consulta de Gemini lleva 5 segundos en curso, cuando el usuario selecciona la pestaña de Gemini, entonces ve el contenedor gris con "Obteniendo datos…" y no el aviso formal.
  - Dado que la primera consulta de un proveedor termina con fallo, cuando el usuario selecciona su pestaña, entonces el contenedor pasa a mostrar el texto formal de error de ese proveedor.
  - Dado que la primera consulta de un proveedor termina con datos vigentes, cuando el usuario selecciona su pestaña, entonces ve las tablas y la pestaña no está en gris.
  - Dado que una segunda consulta de un proveedor ya en estado de error está en curso, cuando el usuario selecciona su pestaña, entonces sigue viendo el texto formal de error y no "Obteniendo datos…".

### REQ-019 Estilo de las tablas

- **Requisito:** El sistema deberá mostrar las tablas de datos sin bordes, sin pie, con filas alternadas en gris y blanco, y con la fila de cabecera de fondo negro.
- **Criterios de aceptación:**
  - Dado cualquier tabla con datos, cuando el usuario la mira, entonces no tiene bordes entre celdas ni alrededor y la cabecera tiene fondo negro con texto legible.
  - Dado una tabla con varias filas de datos, cuando el usuario la mira, entonces las filas alternan gris y blanco.
  - Dado cualquier tabla, cuando el usuario mira bajo la última fila, entonces no hay fila de pie.

### REQ-020 Resaltado de la pestaña seleccionada

- **Requisito:** El sistema deberá resaltar visualmente la pestaña seleccionada de modo que se distinga de las demás, también cuando esté en gris.
- **Criterios de aceptación:**
  - Dado que la pestaña de OpenAI está seleccionada y con datos, cuando el usuario mira la fila de pestañas, entonces la de OpenAI se distingue de las otras tres.
  - Dado que la pestaña seleccionada está en gris y hay otras pestañas en gris, cuando el usuario mira la fila, entonces la seleccionada se distingue de las otras grises.
  - Dado que el usuario selecciona otra pestaña, cuando cambia la selección, entonces el resaltado pasa a la nueva pestaña.

### REQ-021 Disposición de las tablas en una misma altura

- **Requisito:** El sistema deberá disponer las tablas de una pestaña una al lado de la otra, alineadas a la misma altura (borde superior común) y con una separación de varios píxeles entre ellas, de modo que las filas correspondientes del mismo grupo o modelo queden a la misma altura en todas las tablas.
- **Criterios de aceptación:**
  - Dado que Gemini muestra tres tablas, cuando el usuario las mira, entonces las tres empiezan a la misma altura, están separadas entre sí y la fila de cada grupo coincide en altura en las tres.
  - Dado que OpenAI o Claude muestran dos tablas, cuando el usuario las mira, entonces ambas empiezan a la misma altura y están separadas entre sí.
  - Dado que OpenCode muestra dos tablas, cuando el usuario las mira, entonces la fila de cada modelo y la de "Total" coinciden en altura en ambas.
  - Dado que la separación entre tablas es un valor de diseño que el usuario ajustará, cuando se cambie, entonces el resto de requisitos de disposición no cambian.

## Requisitos no funcionales

### NFR-001 Interfaz receptiva al cambiar de pestaña

- **Requisito:** El sistema deberá mostrar el contenido de la pestaña elegida en menos de 1 segundo desde que el usuario la selecciona, incluso mientras se ejecuta una consulta de cualquier proveedor.
- **Criterios de aceptación:**
  - Dado que una consulta de Gemini lleva más de 8 segundos en curso, cuando el usuario cambia de pestaña, entonces el contenido de la nueva pestaña aparece en menos de 1 segundo.

### NFR-002 Legibilidad del color y del estado

- **Requisito:** El sistema deberá hacer distinguibles las barras sin depender solo del color, mostrando siempre el valor numérico del porcentaje en el centro de cada barra.
- **Criterios de aceptación:**
  - Dado cualquier barra con datos, cuando el usuario la mira, entonces su porcentaje numérico aparece en el centro de la barra y es legible en todos los colores, incluido el negro.

### NFR-003 Textos de error sin datos sensibles

- **Requisito:** El sistema deberá mostrar en el estado gris únicamente los textos de REQ-006 y REQ-018, sin credenciales, identificadores de cuenta, salida de comandos, trazas ni códigos de error.
- **Criterios de aceptación:**
  - Dado cualquier fallo de cualquier proveedor, cuando el usuario inspecciona el contenedor gris, entonces el texto coincide exactamente con el definido para ese caso y no contiene valores de token, cuenta, correo ni salida de comandos.

## Supuestos

- S1: El gráfico queda suspendido (oculto) y los requisitos de acumulación en memoria de las specs aprobadas se mantienen intactos; la siguiente especificación decide su nueva presentación.
- S2: En "Gemini 4 Argon" la ventana de 5 horas muestra "N/A" en cada celda de su fila de la tabla de 5 horas, en lugar de omitir la fila. Por extensión, cualquier grupo al que le falte la ventana de 5 horas muestra "N/A" en esa tabla y, si le falta la semanal, en la tabla semanal.
- S3: Las tablas de Gemini tienen una fila por grupo (la de modelos, la de 5 horas y la semanal), en el mismo orden.
- S4: Cada proveedor mantiene la precisión de porcentaje que usa hoy (Claude y OpenAI según el valor recibido; Gemini con un decimal; OpenCode con la que ya muestra); los umbrales de color se aplican sobre el valor sin redondear.
- S5: La hora de última actualización se muestra por proveedor y solo en pestañas con datos vigentes.
- S6: Desaparecen el plan de OpenAI y el nombre del proveedor como título en el contenedor; la identificación del proveedor la da la pestaña.
- S7: Los iconos son los PNG de `desktop/app/src/main/resources/icons/` (`claude.png`, `gemini.png`, `openai.png`, `opencode.png`).
- S8: Los valores de porcentaje entre 99 % y 100 % exclusivos no están definidos en la especificación del usuario (rojo hasta 99 % inclusive; negro en 100 %); se pintan en rojo.
- S9: El cierre (botón de la fila de pestañas y cierre desde la barra de título) solo oculta la ventana; la aplicación termina únicamente desde el menú de la bandeja.
- S10: El tamaño exacto de la ventana lo decide el diseño, de modo que se cumpla REQ-014.
- S11: La fila "Total" de OpenCode (opencode-usage-monitor-v2 REQ-016) se conserva, sin columnas de reinicio, como última fila de la tabla de modelos y de la tabla de usos.
- S12: El texto de la cabecera negra de las tablas es blanco.
- S13: En Claude y OpenAI, si una ventana no está presente o ha caducado, se omite su tabla completa (cabecera incluida) mientras la otra tenga datos vigentes.
- S14: "Alineadas a la misma altura" se interpreta como tablas dispuestas una al lado de la otra en horizontal, con el mismo borde superior y filas homólogas a la misma altura; la separación se define en diseño como un valor ajustable.
- S15: "Obteniendo datos…" se muestra por proveedor hasta que termina su primera consulta (con éxito o con fallo); en Claude, la primera lectura del fichero de estado cuenta como primera consulta.
- S16: Los últimos datos válidos se conservan internamente (para la acumulación del gráfico) pero no se muestran en la ventana durante el estado gris.
- S17: El estilo concreto del resaltado de la pestaña seleccionada y el tono de gris lo decide el diseño, siempre que cumpla REQ-020.
- S18: Esta feature prevalece sobre claude-usage-monitor REQ-008 y opencode-usage-monitor-v2 REQ-003 (fallo silencioso): Claude y OpenCode pasan a mostrar su texto formal de error en el contenedor gris.

## Preguntas abiertas

Ninguna.
