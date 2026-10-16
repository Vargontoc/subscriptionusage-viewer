# Tareas: opencode-usage-monitor

Estado: aprobado

## Resumen

El trabajo se divide en cimientos compartidos (configuración, clasificación de resultados, historial, cuenta atrás, publicador y planificador, con regresión sobre Claude), protección y resolución de la API key desde `application.yml` o `OPENCODE_API_KEY`, componentes de OpenCode (normalización y consulta HTTP), integraciones, interfaz, lanzador, el contrato http y dos NFR transversales (protección de la clave y recursos). Retiradas TASK-004, TASK-012, TASK-015, TASK-016 y TASK-020 por el cambio de requisitos (almacén, diálogo, opción de bandeja y contrato file-json); sus IDs no se reutilizan.

## Tareas

### [ ] TASK-001 Propiedades de configuración de OpenCode

- **Cubre:** REQ-006, NFR-005, DEC-003, DEC-004
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** -
- **Alcance:** Exponer el intervalo de OpenCode (60 s por defecto, mínimo 10 s), el tope de backoff (300 s) y la URL base de uso, sin cambiar los valores de Claude.
- **Hecho cuando:** se cumplen los criterios de NFR-005 relativos al intervalo y las propiedades de OpenCode se leen de `application.yml` con sus valores por defecto.
- **Pruebas automáticas:**
  - Caso feliz: sin configuración explícita, el intervalo de OpenCode es 60 s, el tope de backoff es 300 s, la URL base es la de uso documentada y el intervalo de Claude sigue siendo 10 s.
  - Caso de límite: un intervalo de OpenCode configurado por debajo de 10 s se aplica como 10 s.
- **Prueba manual:**
  1. Eliminar las propiedades de OpenCode de `application.yml` y arrancar la aplicación. Resultado: arranca sin error con los valores por defecto.
  2. Configurar un intervalo de OpenCode de 5 y arrancar la aplicación. Resultado: arranca sin error y el intervalo efectivo no baja de 10 s.

### [ ] TASK-022 Valor secreto de la API key

- **Cubre:** NFR-006, DEC-010
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** -
- **Alcance:** Introducir un tipo propio para la API key cuya representación textual sea un valor enmascarado fijo, sin ningún carácter de la clave, y que solo entregue el valor real en el punto donde se construye la cabecera de autenticación. Los objetos de propiedades que contengan la clave tampoco la imprimen.
- **Hecho cuando:** ninguna representación textual del tipo ni de los objetos de propiedades contiene la clave, y el valor real solo es accesible desde el punto de uso.
- **Pruebas automáticas:**
  - Caso feliz: el valor real se obtiene mediante el acceso previsto para construir la cabecera.
  - Caso de límite: la representación textual del tipo y el volcado de propiedades con una clave centinela no contienen el centinela ni ninguno de sus caracteres.
- **Prueba manual:**
  1. no aplica: tipo interno sin comportamiento observable por sí solo; su efecto se verifica en TASK-024.

### [ ] TASK-021 Resolución de la API key desde application.yml o entorno

- **Cubre:** REQ-002, REQ-010, REQ-012, NFR-006, DEC-007, DEC-010
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** TASK-001, TASK-022
- **Alcance:** Resolver una vez al arrancar la API key desde la propiedad `subscription-viewer.opencode.api-key` de `application.yml`; si no está definida, desde la variable de entorno `OPENCODE_API_KEY`; si ninguna, queda "sin clave". Un valor vacío o igual al marcador del ejemplo cuenta como no definido. Solo se registra si hay clave y su origen, nunca el valor. Entrega la clave a opencode-usage-source y la indicación "con clave / sin clave" a usage-poller.
- **Hecho cuando:** la precedencia de REQ-002 se cumple en todos sus casos y la aplicación arranca sin pedir la clave en la interfaz.
- **Pruebas automáticas:**
  - Caso feliz: clave solo en `application.yml` produce esa clave con origen `application.yml`.
  - Caso feliz: clave solo en `OPENCODE_API_KEY` produce esa clave con origen entorno.
  - Caso de límite: valores distintos en ambos orígenes producen el de `application.yml`.
  - Caso de límite: ningún origen definido, o propiedad vacía o igual al marcador sin variable de entorno, produce "sin clave" sin lanzar excepción.
  - Seguridad: el registro de arranque contiene solo "clave configurada: sí/no" y el origen, nunca el valor.
- **Prueba manual:**
  1. Definir claves distintas en `application.yml` y en `OPENCODE_API_KEY` y arrancar. Resultado: el log indica origen `application.yml` y no muestra ningún valor.
  2. Quitar la clave de `application.yml`, dejar la variable y arrancar. Resultado: el log indica origen entorno; la aplicación arranca sin pedir clave en la interfaz.
  3. Quitar ambas y arrancar. Resultado: la aplicación arranca sin error y sin ningún campo para introducir la clave.

### [ ] TASK-023 Exclusión git y fichero de ejemplo de la API key

- **Cubre:** REQ-002, NFR-006, DEC-010
- **Componente:** secret-config-files | por definir (analista técnico)
- **Depende de:** -
- **Alcance:** Excluir del control de versiones el `application.yml` local que contiene la clave real y versionar solo un fichero de ejemplo con la propiedad `subscription-viewer.opencode.api-key` y un marcador de posición evidente, documentando dónde colocar la clave local. Confirmar que la aplicación carga el fichero local con prioridad sobre el versionado.
- **Hecho cuando:** git ignora el fichero local, el ejemplo versionado contiene solo el marcador y la clave del fichero local tiene prioridad sobre el versionado.
- **Pruebas automáticas:**
  - Caso feliz: un fichero local de prueba con una clave centinela figura como ignorado por git.
  - Caso de error: el fichero de ejemplo versionado no contiene ninguna clave real ni con forma de clave, solo el marcador.
  - Caso de límite: la clave del fichero local prevalece sobre el valor del fichero versionado al arrancar.
- **Prueba manual:**
  1. Ejecutar la comprobación de ignorado de git sobre el fichero local. Resultado: git lo reporta como ignorado y no aparece en el estado del repositorio.
  2. Abrir el ejemplo versionado. Resultado: solo contiene el marcador, sin clave.
  3. Crear el fichero local con una clave de prueba y arrancar. Resultado: el log indica origen `application.yml` local.

### [ ] TASK-002 Resultado clasificado en usage-source

- **Cubre:** REQ-010, REQ-011, REQ-012, DEC-005
- **Componente:** usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java
- **Depende de:** -
- **Alcance:** Ampliar la abstracción de fuente para devolver datos o una causa clasificada (clave ausente, clave inválida, sin suscripción, fallo transitorio) sin propagar excepciones al llamador.
- **Hecho cuando:** las causas de REQ-010 y REQ-011 se pueden expresar como resultados distintos y las fuentes existentes compilan contra la nueva abstracción.
- **Pruebas automáticas:**
  - Caso feliz: una fuente simulada que entrega datos produce un resultado de datos con las ventanas recibidas.
  - Caso de error: una fuente simulada que lanza una excepción de red produce un resultado de fallo transitorio, sin excepción hacia el llamador.
  - Caso de límite: las causas de clave ausente, clave inválida y sin suscripción producen resultados distintos entre sí.
  - Regresión Claude: las pruebas existentes de la fuente de Claude siguen en verde sin cambiar sus expectativas.
- **Prueba manual:**
  1. no aplica: cambio interno de abstracción sin comportamiento observable por sí solo; su efecto se verifica en TASK-011 y TASK-013.

### [ ] TASK-003 Adaptación de claude-usage-source al resultado clasificado

- **Cubre:** REQ-012, DEC-005
- **Componente:** claude-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude
- **Depende de:** TASK-002
- **Alcance:** Adaptar la fuente de Claude a la nueva abstracción sin cambiar la lectura, la validación ni los valores que muestra.
- **Hecho cuando:** el fichero de estado de Claude produce los mismos valores que antes del cambio y la fuente devuelve datos o fallo transitorio según corresponda.
- **Pruebas automáticas:**
  - Caso feliz (regresión): con un fichero de estado válido, los valores devueltos son idénticos a los de la versión anterior.
  - Caso de límite (regresión): con un fichero de estado de contenido inválido se mantiene la tolerancia actual, es decir, no hay excepción y no hay datos.
- **Prueba manual:**
  1. Con Claude configurado y un fichero de estado válido, arrancar la aplicación. Resultado: la tarjeta de Claude muestra los mismos porcentajes y tiempos que antes del cambio.
  2. Sustituir el fichero de estado por contenido inválido y esperar un ciclo de lectura. Resultado: la tarjeta de Claude no falla y la aplicación sigue en ejecución.

### [ ] TASK-005 Normalización de las dos variantes del contrato de OpenCode

- **Cubre:** REQ-003, REQ-004, REQ-005, DEC-009
- **Componente:** opencode-usage-source | por definir (analista técnico)
- **Depende de:** TASK-002
- **Alcance:** Convertir una respuesta de uso en ventanas `rolling`, `weekly` y `monthly` con porcentaje e instante de reinicio, aceptando la variante `percent`/`resetsAt` y la variante `usagePercent`/`resetInSec`, ignorando propiedades desconocidas y omitiendo las ventanas ausentes.
- **Hecho cuando:** los criterios de aceptación de REQ-004 se cumplen para las dos variantes y la ausencia de `weekly` no produce fallo.
- **Pruebas automáticas:**
  - Caso feliz: una respuesta con `percent` y `resetsAt` produce el porcentaje y el instante indicados.
  - Caso feliz: una respuesta con `usagePercent` y `resetInSec` produce como instante de reinicio la hora de finalización de la consulta más los segundos indicados.
  - Caso de límite: una respuesta sin `weekly` produce solo `rolling` y `monthly`, sin error.
  - Caso de límite: una respuesta con las dos variantes en ventanas distintas interpreta cada ventana según la variante que tiene.
  - Caso de error (propuesto): una ventana con porcentaje no numérico se omite y las demás ventanas se procesan; las propiedades desconocidas se ignoran. Avisos: REQ-004 no define este caso.
- **Prueba manual:**
  1. Guardar una respuesta real de OpenCode (obtenida con una clave propia) como fichero de prueba y procesarla con la fuente. Resultado: se obtienen las ventanas presentes con porcentaje e instante de reinicio.
  2. Anotar las propiedades desconocidas de la respuesta real, si las hay. Resultado: no provocan fallo y el resultado sigue siendo válido.

### [ ] TASK-006 Consulta HTTP y clasificación de fallos de OpenCode

- **Cubre:** REQ-002, REQ-003, REQ-010, REQ-011, NFR-002, NFR-004, NFR-006, DEC-002, DEC-004, DEC-010
- **Componente:** opencode-usage-source | por definir (analista técnico)
- **Depende de:** TASK-001, TASK-005, TASK-021, TASK-022
- **Alcance:** Hacer la petición `GET` de uso con la cabecera `Authorization: Bearer` usando la clave que entrega configuration-provider, con tiempos de espera acotados, y clasificar el resultado como datos, clave inválida o ausente (401), sin suscripción (403) o fallo transitorio (red, tiempo de espera, 5xx y 429). No registra petición, cabeceras ni cuerpo, y los fallos se registran con categoría y código de estado, nunca con el mensaje de excepción del cliente.
- **Hecho cuando:** los criterios de aceptación de REQ-010 y REQ-011 se cumplen a nivel de resultado de la fuente y la petición lleva la cabecera con la clave resuelta.
- **Pruebas automáticas:**
  - Caso feliz: con un servidor simulado que responde 200 y datos válidos, la petición incluye `Authorization: Bearer <clave>` y el resultado son datos.
  - Caso de error: respuesta 401 produce "clave inválida o ausente"; respuesta 403 produce "sin suscripción Go activa".
  - Caso de límite: sin clave resuelta, la fuente devuelve "clave ausente" sin realizar ninguna petición de red.
  - Caso de límite: tiempo de espera agotado, error de conexión, 5xx y 429 producen fallo transitorio, sin excepción hacia el llamador.
  - Seguridad: con una clave centinela, los mensajes de error y los logs capturados no contienen la clave ni la cabecera.
  - Compilación: el módulo compila con Java 21 sin errores ni advertencias de deprecación (NFR-002).
- **Prueba manual:**
  1. Con una clave válida y suscripción Go en `application.yml`, ejecutar la consulta real de la fuente. Resultado: se obtienen las ventanas `rolling`, `weekly` (si viene) y `monthly` con porcentaje e instante de reinicio.
  2. Poner una clave falsa en `application.yml` y ejecutar la consulta. Resultado: el resultado es "clave inválida o ausente".
  3. Desconectar la red y ejecutar la consulta. Resultado: el resultado es fallo transitorio, sin excepción visible.

### [ ] TASK-019 Contrato: http - consumo de uso de OpenCode Go

- **Cubre:** REQ-002, REQ-003, REQ-004, REQ-010, NFR-006, DEC-002, DEC-009
- **Componente:** opencode-usage-source | por definir (analista técnico)
- **Ubicación del contrato:** `docs/contracts/http/`, con el nombre del fichero por definir (analista técnico).
- **Depende de:** TASK-006
- **Alcance:** Crear el contrato de la petición `GET` de uso: cabecera Bearer, respuesta con ventanas en sus dos variantes, propiedades adicionales ignoradas, respuestas 401 y 403 y comportamiento ante fallo transitorio. Los ejemplos usan solo el marcador, nunca una clave real. Antes de crearlo, el documentador revisa el contenido sin versionar de `docs/contracts/opencode/` y decide con el usuario si se integra o se descarta; no se usa como fuente de requisitos.
- **Hecho cuando:** el contrato existe en `docs/contracts/http/` y describe la interfaz tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: los ejemplos de respuesta de cada variante son JSON válido.
  - Caso de error: el contrato recoge las respuestas 401 y 403 que exige REQ-010 y el comportamiento ante fallo transitorio.
  - Seguridad: ningún ejemplo del contrato contiene una clave real ni con forma de clave.
- **Prueba manual:**
  1. Repasar que aparecen la petición, la respuesta en la variante `percent`/`resetsAt`, la respuesta en la variante `usagePercent`/`resetInSec`, el 401 y el 403, y que cada una coincide con lo implementado en TASK-006.

### [ ] TASK-007 Historial: deduplicación por valores para OpenCode

- **Cubre:** REQ-008, REQ-009, DEC-006
- **Componente:** usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java
- **Depende de:** -
- **Alcance:** Para OpenCode, añadir un punto por ventana solo si el porcentaje o el instante de reinicio cambian respecto al último punto de esa ventana, manteniendo el tope de puntos por serie y sin persistir nada.
- **Hecho cuando:** dos lecturas consecutivas con los mismos valores no añaden puntos y un cambio añade un punto por cada ventana afectada.
- **Pruebas automáticas:**
  - Caso feliz: tres lecturas con valores distintos producen tres puntos por ventana, ordenados en el tiempo.
  - Caso de límite: dos lecturas con valores iguales pero distinta hora de consulta producen un único punto por ventana.
  - Caso de límite: al superar el tope de puntos de una serie se descartan los más antiguos.
  - Regresión Claude: la deduplicación por hora de registro sigue funcionando igual que antes (pruebas existentes en verde).
- **Prueba manual:**
  1. no aplica: lógica en memoria sin interfaz propia; su efecto visible se comprueba en TASK-014.

### [ ] TASK-008 Cuenta atrás: ventanas variables, días y reinicio vencido

- **Cubre:** REQ-005
- **Componente:** countdown-evaluator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/CountdownEvaluator.java
- **Depende de:** -
- **Alcance:** Calcular el tiempo restante en días para ventanas largas, aceptar la ausencia de una ventana opcional y marcar como vencida una ventana cuyo reinicio ya ha pasado, hasta la siguiente lectura.
- **Hecho cuando:** un reinicio dentro de 2 días produce "2 días" y una ventana con reinicio vencido no muestra tiempo restante.
- **Pruebas automáticas:**
  - Caso feliz: un reinicio dentro de 2 días produce "2 días".
  - Caso de límite (regresión): un reinicio dentro de 3 horas mantiene el formato actual de horas y minutos de Claude.
  - Caso de error (propuesto): un instante de reinicio ya pasado marca la ventana como vencida y nunca produce un tiempo negativo. Avisos: REQ-005 no define este caso.
- **Prueba manual:**
  1. no aplica: cálculo sin interfaz propia; su efecto visible se comprueba en TASK-013.

### [ ] TASK-009 Publicador: estado por proveedor y desactualización

- **Cubre:** REQ-007, REQ-009, REQ-010, REQ-011, REQ-012, NFR-004, NFR-006, DEC-005, DEC-008
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java
- **Depende de:** TASK-002
- **Alcance:** Mantener por proveedor el último dato válido, la hora de la última consulta exitosa y el estado de lectura (correcto, desactualizado, clave ausente, clave inválida, sin suscripción, sin datos), y notificar los cambios a la vista en el hilo de interfaz. No recibe ni almacena la clave.
- **Hecho cuando:** tras un fallo de red el último dato se conserva marcado como desactualizado, y tras una lectura exitosa el estado vuelve a correcto con su hora.
- **Pruebas automáticas:**
  - Caso feliz: una lectura exitosa deja el estado correcto y registra la hora de la consulta.
  - Caso de error: un fallo transitorio tras un dato previo de 20 % conserva el 20 % y deja el estado desactualizado; una lectura exitosa posterior limpia el indicador.
  - Caso de error: una respuesta de clave inválida conserva el último dato válido si lo hay y deja el estado de clave inválida.
  - Caso de límite: el estado de clave ausente se publica sin dato ni hora de última consulta.
  - Caso de límite (propuesto): un fallo sin dato previo deja el estado "sin datos" y no registra hora de última consulta. Avisos: REQ-011 solo lo cubre para la vista.
  - Regresión Claude: los cambios de estado de Claude no cambian, y un fallo de OpenCode no altera el estado de Claude.
  - Seguridad: los estados publicados no contienen la clave ni ninguna parte de ella.
- **Prueba manual:**
  1. no aplica: estado interno; su efecto visible se comprueba en TASK-013.

### [ ] TASK-010 Planificador: intervalo por fuente, backoff y pausa

- **Cubre:** REQ-003, REQ-006, REQ-010, REQ-011, REQ-012, NFR-003, NFR-004, NFR-005, DEC-003, DEC-004, DEC-007
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java
- **Depende de:** TASK-001, TASK-002, TASK-003, TASK-007, TASK-009, TASK-021
- **Alcance:** Planificar las lecturas por fuente en hilo de fondo: OpenCode cada 60 s contados desde el fin de cada lectura, backoff ante fallos transitorios hasta el tope, pausa ante 401 y 403 hasta el reinicio de la aplicación, sin consulta sin clave, sin solapamiento y con aislamiento entre fuentes. Claude mantiene 10 s y sin backoff.
- **Hecho cuando:** OpenCode no inicia ninguna lectura sin clave ni mientras está pausada, y no se inicia una lectura mientras otra está en curso.
- **Pruebas automáticas:**
  - Caso feliz: con una fuente simulada que responde al instante y un reloj simulado, las lecturas de OpenCode se producen cada 60 s medidos desde el fin de cada lectura.
  - Caso de error: tras una respuesta 401 o 403 no hay más lecturas durante la ejecución.
  - Caso de límite: fallos transitorios sucesivos producen retardos de 60, 120, 240 y 300 s, y el tope se mantiene; tras una lectura exitosa el retardo vuelve a 60 s.
  - Caso de límite: una lectura lenta impide iniciar la siguiente hasta que termine.
  - Caso de límite (NFR-005): en un minuto con respuestas inmediatas no se realizan más de 6 peticiones a OpenCode.
  - Caso de límite: sin clave resuelta no se realiza ninguna petición.
  - Aislamiento (REQ-012): un fallo de OpenCode no retrasa ni bloquea las lecturas de Claude.
  - Regresión Claude: Claude mantiene el intervalo de 10 s, no aplica backoff ante fallos y sus lecturas no cambian.
- **Prueba manual:**
  1. Con clave válida, arrancar la aplicación y contar durante 3 minutos las peticiones a `opencode.ai` con un proxy o herramienta de red del desarrollador. Resultado: como máximo 6 peticiones por minuto.
  2. Con clave inválida en `application.yml`, arrancar la aplicación y contar las peticiones durante 3 minutos. Resultado: una sola petición inicial y ninguna después.

### [ ] TASK-011 Integración: usage-poller con opencode-usage-source

- **Cubre:** REQ-003, REQ-010, REQ-011, DEC-004, DEC-005
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java; opencode-usage-source | por definir (analista técnico)
- **Depende de:** TASK-006, TASK-010
- **Alcance:** Conectar el planificador con la fuente real de OpenCode para que cada lectura llegue clasificada al historial y al publicador, y para que, sin clave, el publicador reciba el estado "clave ausente" sin peticiones.
- **Hecho cuando:** una lectura real de OpenCode llega al publicador y al historial con su causa clasificada, y sin clave el estado "clave ausente" llega al publicador sin ninguna petición.
- **Pruebas automáticas:**
  - Caso feliz: con un servidor simulado que responde 200, los datos llegan al historial y al publicador.
  - Caso de error: con un servidor simulado que responde 401, la fuente queda pausada y no hay reintento.
  - Caso de límite: con un error de red simulado, el planificador aplica el backoff.
  - Caso de límite: sin clave resuelta, el publicador recibe "clave ausente" y no se realiza ninguna petición.
- **Prueba manual:**
  1. Con clave válida, arrancar la aplicación y observar el tráfico hacia `opencode.ai`. Resultado: una petición cada 60 s, con la cabecera `Authorization`.

### [ ] TASK-013 Ventana: tarjetas, estados y hora de actualización

- **Cubre:** REQ-002, REQ-005, REQ-006, REQ-007, REQ-010, REQ-011, REQ-012, DEC-007, DEC-008
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-007, TASK-008, TASK-009
- **Alcance:** Mostrar la tarjeta de OpenCode con sus ventanas, porcentaje, cuenta atrás, hora de la última consulta exitosa, indicador de desactualización, mensajes de autenticación, texto neutro sin datos y, sin clave, un aviso de que falta la API key indicando dónde definirla (`application.yml` o `OPENCODE_API_KEY`), sin campo de entrada. Muestra solo proveedores con información o con un estado que comunicar y refresca al recibir cambios sin intervención del usuario.
- **Hecho cuando:** los criterios de aceptación de REQ-005 y REQ-007 se cumplen en la interfaz y los mensajes de REQ-010 y REQ-011 aparecen con el texto no técnico definido.
- **Pruebas automáticas:**
  - Caso feliz: con `rolling` al 30 % y `monthly` con reinicio en 2 días, la tarjeta muestra 30 % y "2 días".
  - Caso feliz (regresión): la tarjeta de Claude muestra los mismos valores que antes del cambio.
  - Caso de error: respuesta 401 muestra un mensaje de clave inválida o ausente; 403 muestra el mensaje de suscripción no activa.
  - Caso de error: un fallo de red tras un dato previo de 20 % muestra 20 % con indicador de desactualizado.
  - Caso de límite: sin datos y con fallo de red se muestra texto neutro sin términos técnicos.
  - Caso de límite: sin clave configurada, la tarjeta de OpenCode aparece con el aviso de falta de API key y dónde definirla, y no hay ningún campo para introducirla.
- **Prueba manual:**
  1. Con Claude y OpenCode configurados, abrir la ventana de uso. Resultado: ambas tarjetas muestran sus datos; la hora de última actualización coincide con la última consulta exitosa.
  2. Desconectar la red tras una lectura correcta y esperar un ciclo. Resultado: se muestra el último porcentaje con indicador de desactualizado, sin mensaje técnico.
  3. Restablecer la red y esperar la siguiente lectura. Resultado: el indicador de desactualizado desaparece.
  4. Quitar la clave de `application.yml` y de `OPENCODE_API_KEY` y arrancar. Resultado: la tarjeta de OpenCode indica que falta la API key y dónde definirla; no hay campo para introducirla.
  5. Pulsar "mostrar uso" en el menú contextual del icono. Resultado: la ventana de uso se abre como antes.

### [ ] TASK-014 Ventana: gráfico de evolución

- **Cubre:** REQ-008, REQ-009, DEC-006
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-007, TASK-013
- **Alcance:** Dibujar en el gráfico un punto por actualización y por ventana, ordenados en el tiempo, que aparecen en vivo y se descartan al cerrar la aplicación.
- **Hecho cuando:** los criterios de aceptación de REQ-008 y REQ-009 se cumplen en la interfaz.
- **Pruebas automáticas:**
  - Caso feliz: tres lecturas con valores distintos muestran tres puntos por ventana en orden temporal.
  - Caso de límite: dos lecturas con valores iguales no añaden un punto duplicado.
  - Caso de límite: sin lecturas, el gráfico está vacío.
- **Prueba manual:**
  1. Con OpenCode configurado, esperar tres actualizaciones con valores distintos. Resultado: aparece un punto nuevo por actualización y ventana, sin recargar la aplicación.
  2. Esperar un ciclo sin cambios en el consumo. Resultado: no aparecen puntos nuevos.
  3. Cerrar la aplicación y volver a abrirla. Resultado: el gráfico empieza vacío.

### [ ] TASK-017 Lanzador de arranque en segundo plano

- **Cubre:** REQ-001, NFR-001, NFR-002, DEC-001
- **Componente:** background-launcher | por definir (analista técnico)
- **Depende de:** -
- **Alcance:** Lanzador que inicia la aplicación desacoplada de la terminal y devuelve el control al usuario de inmediato.
- **Hecho cuando:** la terminal queda libre, el proceso sigue en ejecución y la interfaz está disponible en menos de 30 s.
- **Pruebas automáticas:**
  - Caso feliz: ejecutar el lanzador devuelve el control sin esperar al cierre de la aplicación y el proceso queda vivo.
  - Caso de error (propuesto): si la aplicación no puede iniciarse (por ejemplo, Java 21 no disponible), el lanzador termina con código de error y un mensaje claro. Avisos: REQ-001 no define este caso.
- **Prueba manual:**
  1. Ejecutar el lanzador desde la terminal. Resultado: la terminal vuelve al prompt.
  2. Medir el tiempo hasta que aparece el icono de bandeja. Resultado: menos de 30 s.
  3. Cerrar la terminal. Resultado: la aplicación sigue en ejecución.

### [ ] TASK-024 Protección de la API key de extremo a extremo (NFR transversal)

- **Cubre:** NFR-006, REQ-002, DEC-010
- **Componente:** application-launcher | src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java (verificación de la aplicación completa)
- **Depende de:** TASK-011, TASK-013, TASK-021, TASK-022, TASK-023
- **Alcance:** Verificar con una clave centinela reconocible, en la aplicación completa, que la clave nunca aparece en los logs ni en la interfaz, en los escenarios de clave válida, ausente, 401, 403 y fallo de red.
- **Hecho cuando:** en todos los escenarios no aparecen ni el valor de la clave ni la cabecera `Authorization` en los logs capturados ni en los textos de la interfaz, y el fichero local está ignorado por git.
- **Pruebas automáticas:**
  - Caso feliz: escenario con clave centinela válida; no aparece el centinela en logs ni en textos de la interfaz.
  - Caso de error: escenarios 401, 403 y fallo de red con clave centinela; no aparece el centinela en logs ni en textos.
  - Caso de límite: clave ausente; no aparece el centinela y no se realiza ninguna petición.
  - Caso de límite: el centinela no aparece parcialmente (ni sus últimos caracteres) en los textos de la interfaz.
- **Prueba manual:**
  1. Con clave válida en `application.yml`, arrancar, usar la ventana y revisar el log. Resultado: no aparecen la clave ni la cabecera con la clave.
  2. Con clave inválida, observar la tarjeta de OpenCode tras el 401. Resultado: el mensaje no contiene la clave ni parte de ella.
  3. Buscar la clave de prueba en el repositorio y en el estado de git. Resultado: no aparece en ningún fichero versionado.

### [ ] TASK-018 Consumo de recursos y respuesta de la interfaz (NFR transversal)

- **Cubre:** NFR-003, NFR-004
- **Componente:** application-launcher | src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java (medición de la aplicación completa)
- **Depende de:** TASK-011, TASK-013, TASK-014, TASK-017
- **Alcance:** Medir, con OpenCode configurado, el consumo de CPU y de heap en segundo plano, y la respuesta de la interfaz mientras una consulta tarda más de 5 s.
- **Hecho cuando:** los criterios de NFR-003 y NFR-004 se cumplen con los valores medidos.
- **Pruebas automáticas:**
  - Caso feliz (regresión): la suite de pruebas del proyecto pasa con la configuración de OpenCode activa.
  - Caso de límite: con un servidor simulado que tarda 6 s, la interfaz responde en menos de 1 s mientras espera.
- **Prueba manual:**
  1. Lanzar la aplicación con el lanzador y dejarla en segundo plano 5 minutos. Resultado: el consumo medio de CPU es inferior al 5 % de un núcleo.
  2. Medir el heap con una herramienta de la JVM tras esos 5 minutos. Resultado: inferior a 256 MB.
  3. Con un servidor lento simulado, pulsar "mostrar uso" durante la consulta. Resultado: la ventana se abre en menos de 1 s.

## Cobertura

| Requisito | Tareas |
|---|---|
| REQ-001 | TASK-017 |
| REQ-002 | TASK-021, TASK-022, TASK-023, TASK-006, TASK-013, TASK-019, TASK-024 |
| REQ-003 | TASK-005, TASK-006, TASK-010, TASK-011, TASK-019 |
| REQ-004 | TASK-005, TASK-019 |
| REQ-005 | TASK-008, TASK-013 |
| REQ-006 | TASK-001, TASK-010, TASK-013 |
| REQ-007 | TASK-009, TASK-013 |
| REQ-008 | TASK-007, TASK-014 |
| REQ-009 | TASK-007, TASK-009, TASK-014 |
| REQ-010 | TASK-002, TASK-006, TASK-009, TASK-010, TASK-011, TASK-013, TASK-019, TASK-021 |
| REQ-011 | TASK-002, TASK-006, TASK-009, TASK-010, TASK-011, TASK-013 |
| REQ-012 | TASK-002, TASK-003, TASK-009, TASK-010, TASK-013, TASK-021 |
| NFR-001 | TASK-017 |
| NFR-002 | TASK-006, TASK-017 |
| NFR-003 | TASK-010, TASK-018 |
| NFR-004 | TASK-006, TASK-009, TASK-010, TASK-018 |
| NFR-005 | TASK-001, TASK-010 |
| NFR-006 | TASK-006, TASK-009, TASK-019, TASK-021, TASK-022, TASK-023, TASK-024 |
