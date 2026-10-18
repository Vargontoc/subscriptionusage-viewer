# Tareas: opencode-usage-monitor-v2

Estado: aprobado

## Resumen

Se parte primero de las piezas con lógica propia (configuración, rangos, parseo, ejecución del CLI y cálculo), después se montan el ciclo de OpenCode, el planificador, el historial, la publicación y la vista con tareas de integración por pares de componentes, y al final se verifican los contratos, la regresión de Claude y los NFR transversales. La limpieza de v1 (REQ-014) es una tarea propia y posterior a toda la v2.

## Tareas

### [x] TASK-001 Configuración de topes model-caps y propiedades de OpenCode

- **Cubre:** REQ-004, REQ-007, REQ-009, NFR-005, NFR-006, DEC-005
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** -
- **Alcance:** Carga el prefijo `subscription-viewer.opencode` con día de ciclo, tope por defecto, intervalo de refresco, timeout y la tabla `model-caps` (lista de entradas modelo-tope) en `application.yml`, con entradas marcador comentadas que explican cómo editar una línea.
- **Hecho cuando:** se cumplen los criterios de configuración de REQ-007 y el de día de ciclo inválido de REQ-004, y el `application.yml` entregado contiene la tabla con marcadores explicados.
- **Pruebas automáticas:**
  - Caso feliz: sin propiedades se obtienen día 1, tope por defecto de 60 USD, intervalo de 60 s, timeout de 30 s y tabla sin topes definidos; una entrada con modelo y tope numérico se lee con ambos valores.
  - Caso de error o límite: día 0, 32 o texto no numérico se sustituye por 1 sin impedir el arranque; `cap-usd` vacío se lee como modelo sin tope definido; un intervalo menor de 10 s se aplica como 10 s.
- **Prueba manual:**
  1. Abrir `application.yml`. Resultado: aparecen las entradas marcador con un comentario que indica que añadir o cambiar un modelo es editar una sola línea (modelo y tope en dólares).
  2. Cambiar `cycle-day` a 20, reiniciar la aplicación. Resultado: arranca sin errores.

### [x] TASK-002 Cálculo de rangos de ventanas

- **Cubre:** REQ-004, DEC-003
- **Componente:** usage-window-ranges | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/UsageWindowRanges.java
- **Depende de:** TASK-001
- **Alcance:** Calcula, a partir de un único instante "ahora" y de la zona horaria local, los rangos `from` y `to` de las ventanas de 5 horas, 7 días y mes según el día de ciclo configurado, expresados en ISO-8601 UTC.
- **Hecho cuando:** los ejemplos de REQ-004 producen exactamente los rangos indicados y las tres ventanas salen del mismo instante.
- **Pruebas automáticas:**
  - Caso feliz: con día de ciclo 1 y las 14:00 del 17 de octubre, la ventana de 5 horas va de las 09:00 a las 14:00, la de 7 días del 10 de octubre a las 14:00 al 17 de octubre a las 14:00 y la mensual del 1 de octubre a las 00:00 al 17 de octubre a las 14:00.
  - Caso de error o límite: día de ciclo 20 con 17 de octubre da inicio el 20 de septiembre a las 00:00 y con 25 de octubre da el 20 de octubre a las 00:00; día 31 con 5 de marzo da el 28 de febrero y con 30 de abril da el 30 de abril a las 00:00; si el instante actual coincide exactamente con el inicio de ciclo, ese instante es el inicio.
- **Prueba manual:**
  1. no aplica: cálculo interno sin comportamiento observable por el usuario; su efecto visible se verifica en TASK-020.

### [x] TASK-003 Interpretación del JSON de estadísticas de sesión

- **Cubre:** REQ-002, REQ-003, REQ-005, DEC-001
- **Componente:** opencode-stats-parser | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeStatsParser.java
- **Depende de:** -
- **Alcance:** Interpreta el texto de salida de `session.stats` y extrae de cada elemento de `models` el id, `providerID`, `variant`, `steps`, los tokens y `cost`, descartando los modelos que no son de `opencode-go` y las propiedades desconocidas.
- **Hecho cuando:** REQ-002 (extracción) y REQ-005 se cumplen con los ejemplos de sus criterios y cualquier texto que no es JSON válido o que no contiene `models` se reporta como fallo.
- **Pruebas automáticas:**
  - Caso feliz: una salida con un modelo de `opencode-go` y otro de otro proveedor, con propiedades adicionales, devuelve solo el de `opencode-go` con sus variantes, costes y tokens.
  - Caso de error o límite: texto que no es JSON produce fallo; JSON válido sin la lista `models` produce fallo; una lista `models` sin ningún `opencode-go` produce un resultado sin modelos (no un fallo).
- **Prueba manual:**
  1. Ejecutar en una terminal `opencode api experimental.session.stats --param from=<inicio> --param to=<fin> --param tools=none` con un rango real. Resultado: la salida es JSON con `models`, y los campos que aparecen coinciden con los que extrae el parser en las pruebas.

### [x] TASK-004 Ejecución del CLI con timeout y fallo silencioso

- **Cubre:** REQ-002, REQ-003, NFR-002, NFR-004, NFR-006, DEC-001
- **Componente:** opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java
- **Depende de:** TASK-001
- **Alcance:** Ejecuta `opencode` localizado por el PATH con los argumentos separados, lee stdout mientras el proceso corre, descarta stderr, aplica el timeout de 30 s y devuelve el texto o un fallo sin mensaje para el usuario.
- **Hecho cuando:** la invocación lleva `api experimental.session.stats`, `--param from`, `--param to` y `--param tools=none`, y todo fallo (no encontrado, código distinto de 0, timeout, excepción, salida que supera el tope de tamaño de DEC-001) se resuelve como consulta fallida con un único registro de bajo nivel sin salida del CLI ni argumentos.
- **Pruebas automáticas:**
  - Caso feliz: un ejecutable de prueba que escribe JSON en stdout devuelve ese texto, y los tres parámetros llegan como argumentos separados y no concatenados.
  - Caso de error o límite: ejecutable ausente devuelve fallo sin excepción no controlada; código de salida 1 devuelve fallo; un proceso que no termina se aborta a los 30 s, se destruye y devuelve fallo; una salida mayor que el tope de tamaño devuelve fallo; stderr no aparece en el registro.
- **Prueba manual:**
  1. Con `opencode` instalado, lanzar la aplicación. Resultado: no aparece ningún mensaje de error.
  2. Quitar `opencode` del PATH de la terminal que lanza la aplicación y arrancarla. Resultado: la aplicación sigue en ejecución sin ningún mensaje de error.

### [x] TASK-005 Contrato: stdio - invocación de session.stats y salida JSON

- **Cubre:** REQ-002, REQ-003, DEC-001
- **Componente:** opencode-cli-runner | por definir (analista técnico)
- **Depende de:** TASK-004
- **Alcance:** Crear el contrato stdio entre `opencode-cli-runner` y el CLI `opencode` en `docs/contracts/stdio/` (nombre del fichero: por definir (analista técnico)), con la invocación y sus parámetros, la salida JSON (`models` con id, providerID, variant, steps, tokens y cost), la regla de propiedades extra ignoradas y los fallos silenciosos (salida no JSON, código distinto de 0, timeout de 30 s).
- **Hecho cuando:** el contrato existe en `docs/contracts/stdio/` y describe la interfaz tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: el contrato es válido según las reglas de su formato y su estructura cubre todos los campos de salida que lee el parser.
  - Caso de error o límite: el contrato recoge los tres fallos (salida no JSON, código distinto de 0, timeout) y señala que no se muestra ningún mensaje al usuario.
- **Prueba manual:**
  1. Comparar el contrato con la invocación de TASK-004. Resultado: el nombre del comando, los tres parámetros y `tools=none` coinciden.
  2. Comparar los campos de salida del contrato con los que extrae TASK-003. Resultado: coinciden todos.

### [x] TASK-006 Pruebas del CLI en Windows (.cmd)

- **Cubre:** REQ-002, REQ-003, NFR-006, DEC-001
- **Componente:** opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java
- **Depende de:** TASK-004
- **Alcance:** Validar en Windows que el runner localiza y ejecuta `opencode` cuando el ejecutable del PATH es un envoltorio `.cmd` (y `.ps1` si es el caso), que los argumentos ISO-8601 (dos puntos, `+`, `T`, `Z`) llegan sin alterar y que, si el envoltorio no existe, el fallo es silencioso.
- **Hecho cuando:** en Windows el runner obtiene la salida de un `.cmd` de prueba situado en el PATH y los argumentos llegan intactos al envoltorio.
- **Pruebas automáticas:**
  - Caso feliz: un `.cmd` de prueba en el PATH que escribe JSON devuelve ese texto al runner y recibe las fechas `from` y `to` sin alteración.
  - Caso de error o límite: un `.cmd` de prueba que sale con código 1 produce fallo; sin ningún `.cmd` ni ejecutable en el PATH produce fallo sin excepción.
- **Prueba manual:**
  1. En Windows, con `opencode` disponible solo como `.cmd` en el PATH, ejecutar la prueba de integración con el CLI real (marcada para ejecución manual) desde la terminal. Resultado: devuelve el JSON con `models`.

### [x] TASK-007 Cálculo de porcentajes por modelo y total

- **Cubre:** REQ-005, REQ-006, REQ-007, REQ-016, DEC-004
- **Componente:** opencode-usage-calculator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeUsageCalculator.java
- **Depende de:** TASK-001
- **Alcance:** Suma el coste por modelo y ventana (todas sus `variant`), resuelve el tope del modelo (tabla o por defecto), calcula el porcentaje por modelo y ventana y el total agregado, saturados a 100 y sin redondear antes de presentar.
- **Hecho cuando:** los ejemplos de REQ-006 y REQ-016 producen los porcentajes indicados.
- **Pruebas automáticas:**
  - Caso feliz: con tope de 60 USD, 6 USD en 5 horas dan 50 %, 15 USD en 7 días dan 50 % y 45 USD en el mes dan 75 %; el modelo A (60 USD, 6 USD) y el modelo B (100 USD, 4 USD) dan un total de 5 horas de 31,25 %.
  - Caso de error o límite: 15 USD con límite de 12 USD dan 100 %; gasto igual al límite da 100 %; sin coste da 0 %; un modelo sin consumo no entra en el denominador del total; sin consumo en ningún modelo el total es 0 %; un modelo con marcador o no listado usa 60 USD; un coste negativo cuenta como 0.
- **Prueba manual:**
  1. no aplica: cálculo puro sin pantalla propia; su resultado visible se verifica en TASK-015 y TASK-020.

### [x] TASK-008 Ciclo de tres consultas en paralelo

- **Cubre:** REQ-002, REQ-003, REQ-015, NFR-004, NFR-005, NFR-006, DEC-002
- **Componente:** opencode-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode
- **Depende de:** TASK-002, TASK-003, TASK-004
- **Alcance:** Orquesta un ciclo: toma un instante, pide los tres rangos, lanza las tres consultas en un pool fijo de 3 hilos demonio, espera a las tres y devuelve el gasto por modelo y ventana, o "sin datos" si alguna falla. No llama al endpoint HTTP; el código HTTP de v1 queda sin uso hasta la limpieza (TASK-021).
- **Hecho cuando:** REQ-015 se cumple y un ciclo con alguna consulta fallida devuelve "sin datos" sin modificar nada publicado.
- **Pruebas automáticas:**
  - Caso feliz: con tres consultas simuladas de 4 s, el ciclo termina en menos de 8 s, nunca hay más de 3 en ejecución a la vez y devuelve el gasto de las tres ventanas.
  - Caso de error o límite: una consulta que falla hace que el ciclo devuelva "sin datos" aunque las otras dos tengan éxito; una consulta más lenta que las demás hace que el ciclo no devuelve resultado hasta que termina la última; una consulta que supera 30 s se considera fallida.
- **Prueba manual:**
  1. Con el CLI real, abrir el Administrador de tareas durante un ciclo y contar los procesos `opencode`. Resultado: no hay más de 3 simultáneos.

### [x] TASK-009 Integración: ciclo de OpenCode con el cálculo de porcentajes

- **Cubre:** REQ-006, REQ-007, REQ-016, DEC-004
- **Componente:** opencode-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode ; opencode-usage-calculator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeUsageCalculator.java
- **Depende de:** TASK-007, TASK-008
- **Alcance:** El ciclo de la fuente entrega a los consumidores los porcentajes por modelo y total calculados con los topes de la configuración, en lugar del gasto crudo.
- **Hecho cuando:** un ciclo con las tres consultas válidas devuelve porcentajes saturados por modelo y total, con el tope de la tabla o el de por defecto.
- **Pruebas automáticas:**
  - Caso feliz: un ciclo con dos modelos devuelve los porcentajes del ejemplo de REQ-016 (total de 5 horas de 31,25 %).
  - Caso de error o límite: un ciclo con una consulta fallida devuelve "sin datos" y ningún porcentaje parcial; un modelo sin entrada en la tabla usa 60 USD; un gasto superior al límite se devuelve como 100 %.
- **Prueba manual:**
  1. no aplica: integración interna; el resultado visible se verifica en TASK-015.

### [x] TASK-010 Planificador de refresco fijo sin solapamiento

- **Cubre:** REQ-009, REQ-013, REQ-015, NFR-005, DEC-006
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java
- **Depende de:** TASK-009
- **Alcance:** Repite el ciclo de OpenCode con un retardo fijo de 60 s entre el fin de un ciclo y el inicio del siguiente, con el primer ciclo al arrancar, en un hilo de fondo, sin backoff ni pausa, y aislado de otras fuentes. El cambio afecta a todas las fuentes y su verificación con Claude está en TASK-019.
- **Hecho cuando:** se cumplen los criterios de REQ-009 y el intervalo sigue la configuración con un mínimo de 10 s.
- **Pruebas automáticas:**
  - Caso feliz: con una fuente simulada y reloj inyectable, la segunda consulta llega 60 s después del fin de la primera.
  - Caso de error o límite: no se inicia un ciclo mientras el anterior está en curso; una fuente que falla repetidamente sigue reintentando a su intervalo sin backoff; el fallo de una fuente no detiene a la otra; un intervalo configurado de 5 s se aplica como 10 s, y con ese mínimo el máximo es 18 ejecuciones por minuto (3 consultas por ciclo), por debajo de NFR-005.
- **Prueba manual:**
  1. Con la aplicación en marcha y el CLI real, observar en el Administrador de tareas los procesos `opencode`. Resultado: aparecen en tandas de tres cada 60 s aproximadamente.

### [x] TASK-011 Historial en memoria por modelo y total

- **Cubre:** REQ-011, REQ-012, DEC-009
- **Componente:** usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java
- **Depende de:** TASK-007
- **Alcance:** Guarda en memoria una serie por proveedor, por cada serie (modelo o total) y por ventana, añade un punto solo si el porcentaje cambia respecto al último de esa serie, mantiene el tope de puntos por serie de v1, y no persiste nada.
- **Hecho cuando:** REQ-011 y REQ-012 se cumplen a nivel de datos, sin ningún fichero escrito.
- **Pruebas automáticas:**
  - Caso feliz: dos lecturas con valores distintos generan dos puntos por serie y ventana, ordenados en el tiempo.
  - Caso de error o límite: dos lecturas iguales no añaden puntos; un modelo que aparece en la segunda lectura empieza su serie en ese momento; ningún punto supera 100; una nueva instancia (reinicio simulado) tiene las series vacías y no existe ningún fichero escrito.
- **Prueba manual:**
  1. no aplica: componente de datos sin pantalla propia; su efecto visible se verifica en TASK-016.

### [x] TASK-012 Último estado de OpenCode y hora de último ciclo exitoso

- **Cubre:** REQ-003, REQ-008, REQ-010, DEC-007
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java
- **Depende de:** TASK-009
- **Alcance:** Guarda el último resultado de OpenCode (filas por modelo y total con sus tres ventanas) y la hora del último ciclo exitoso; ante "sin datos" no cambia nada; para OpenCode no existe indicador de desactualización (S9).
- **Hecho cuando:** REQ-010 se cumple y el estado guardado contiene los datos por modelo y total de REQ-008.
- **Pruebas automáticas:**
  - Caso feliz: un ciclo con datos guarda los valores por modelo y total y la hora de fin del ciclo (reloj fijado a 14:10:05 debe dar "14:10:05").
  - Caso de error o límite: un "sin datos" tras un ciclo válido conserva los datos y la hora anteriores sin estado de error; sin ningún ciclo válido desde el arranque no hay estado de OpenCode.
- **Prueba manual:**
  1. no aplica: estado interno; su efecto visible se verifica en TASK-015.

### [x] TASK-013 Integración: entrega del resultado del ciclo al publicador

- **Cubre:** REQ-003, REQ-010, DEC-007
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java ; usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java
- **Depende de:** TASK-010, TASK-012
- **Alcance:** El planificador entrega al publicador el resultado de cada ciclo con datos y la hora de fin del ciclo; un ciclo sin datos no entrega nada.
- **Hecho cuando:** un ciclo con datos actualiza el estado publicado y un ciclo sin datos lo deja intacto.
- **Pruebas automáticas:**
  - Caso feliz: un ciclo válido deja en el publicador los datos y la hora de fin del ciclo.
  - Caso de error o límite: un ciclo sin datos no modifica el publicador; un ciclo fallido justo después de uno válido no cambia la hora de última actualización.
- **Prueba manual:**
  1. Con la aplicación en marcha, anotar la hora de última actualización. Resultado: tras cada ciclo correcto cambia; tras un ciclo fallido (p. ej. con `opencode` fuera del PATH) no cambia.

### [x] TASK-014 Integración: entrega de puntos del ciclo al historial

- **Cubre:** REQ-011, DEC-009
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java ; usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java
- **Depende de:** TASK-010, TASK-011
- **Alcance:** En cada ciclo con datos, el planificador envía al historial los porcentajes por serie y ventana; un ciclo sin datos no envía nada.
- **Hecho cuando:** el historial recibe un punto por serie y ventana solo cuando cambia el valor.
- **Pruebas automáticas:**
  - Caso feliz: dos ciclos con datos distintos añaden puntos a las series correspondientes.
  - Caso de error o límite: un ciclo sin datos no añade puntos; dos ciclos con valores iguales no añaden puntos duplicados.
- **Prueba manual:**
  1. no aplica: entrega interna; su efecto visible se verifica en TASK-016.

### [x] TASK-015 Integración: publicación del estado a la ventana (filas por modelo y Total)

- **Cubre:** REQ-003, REQ-005, REQ-008, REQ-010, REQ-013, REQ-016, DEC-007
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java ; usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-012
- **Alcance:** La ventana muestra para OpenCode una fila por modelo con sus tres ventanas, una fila "Total" y la hora de última actualización; no muestra cuenta atrás ni mensajes de error; oculta la sección OpenCode si no hay datos o no hay modelos con consumo; la notificación se entrega en el hilo de interfaz.
- **Hecho cuando:** REQ-008, REQ-010 y REQ-016 se ven en la ventana, y sin datos la sección OpenCode no aparece mientras los demás proveedores siguen mostrándose.
- **Pruebas automáticas:**
  - Caso feliz: un estado con dos modelos produce dos filas con tres ventanas cada una y una fila "Total" con sus valores.
  - Caso de error o límite: sin datos no aparece la sección OpenCode y no hay texto de error; un modelo sin gasto en ninguna ventana no aparece en la tabla (propuesto); una notificación desde otro hilo se aplica en el hilo de interfaz.
- **Prueba manual:**
  1. Con el CLI real, lanzar la aplicación y esperar un ciclo. Resultado: aparece la sección OpenCode con cada modelo, sus tres ventanas, la fila "Total" y la hora en que se completó el ciclo.
  2. Quitar `opencode` del PATH y reiniciar la aplicación. Resultado: no aparece ningún mensaje ni la sección OpenCode, y no se muestra cuenta atrás en OpenCode.

### [x] TASK-016 Integración: historial a gráfico de evolución

- **Cubre:** REQ-011, REQ-012
- **Componente:** usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java ; usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-011, TASK-015
- **Alcance:** El gráfico de la ventana muestra la evolución de las tres ventanas por modelo y del total, añade nuevos puntos sin recargar y arranca vacío en cada ejecución.
- **Hecho cuando:** REQ-011 y REQ-012 se cumplen visualmente.
- **Pruebas automáticas:**
  - Caso feliz: una serie con tres puntos se dibuja con esos tres puntos en su serie.
  - Caso de error o límite: un historial vacío muestra el gráfico vacío sin error; un nuevo punto se añade sin reiniciar la ventana.
- **Prueba manual:**
  1. Dejar la aplicación abierta varios ciclos con consumo cambiante. Resultado: el gráfico muestra un punto por cambio y ventana, por modelo y total, con valores no superiores a 100 %.
  2. Cerrar la aplicación y volver a abrirla. Resultado: el gráfico empieza vacío.

### [x] TASK-017 Integración: cierre con consulta en curso

- **Cubre:** REQ-012, DEC-001, DEC-002
- **Componente:** lifecycle-coordinator | src/main/java/es/vargontoc/subscriptionviewer/lifecycle/LifecycleCoordinator.java ; usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java
- **Depende de:** TASK-009, TASK-010
- **Alcance:** Al cerrar la aplicación, el coordinador detiene el planificador, que cierra el pool de consultas y destruye los procesos `opencode` que sigan en ejecución.
- **Hecho cuando:** al cerrar no queda ningún proceso `opencode` lanzado por la aplicación ni hilo del pool activo.
- **Pruebas automáticas:**
  - Caso feliz: un cierre sin ciclo en curso detiene el planificador y cierra el pool.
  - Caso de error o límite: un cierre con una consulta en curso (proceso simulado que no termina) destruye ese proceso y completa el cierre sin excepción.
- **Prueba manual:**
  1. Con el CLI real, lanzar la aplicación y cerrarla desde el menú de la bandeja justo durante un ciclo. Resultado: en el Administrador de tareas no quedan procesos `opencode` ni de la aplicación.

### [x] TASK-018 Contrato: http - retirada del endpoint de uso de OpenCode

- **Cubre:** REQ-014, DEC-008
- **Componente:** opencode-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode
- **Depende de:** TASK-008
- **Alcance:** Retirar el contrato HTTP de OpenCode `docs/contracts/http/desktop/app/opencode-usage.md`, que describe el endpoint `/zen/go/v1/usage` y las variantes `percent`/`resetsAt` y `usagePercent`/`resetInSec`, ya no usados por la fuente. Las referencias del mapa y del README las actualiza el documentador tras TASK-021.
- **Hecho cuando:** el fichero del contrato HTTP de OpenCode ya no existe en `docs/contracts/http/desktop/app/` y ningún contrato vigente lo enlaza.
- **Pruebas automáticas:**
  - Caso feliz: el contrato ya no figura en `docs/contracts/http/` y ningún otro contrato enlaza con él.
  - Caso de error o límite: la búsqueda de `/zen/go/v1/usage` en `docs/contracts/` no devuelve ninguna referencia activa.
- **Prueba manual:**
  1. Repasar `docs/contracts/http/`. Resultado: no aparece el contrato de uso de OpenCode.

### [x] TASK-019 Regresión de Claude tras los cambios en componentes compartidos

- **Cubre:** REQ-013, DEC-006, DEC-007
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java ; claude-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/claude
- **Depende de:** TASK-016, TASK-017
- **Alcance:** Verificar que el flujo de Claude (lectura del fichero de estado, refresco, historial, publicación, ventana con cuenta atrás) se comporta como antes tras los cambios de poller, usage-source, usage-history, usage-snapshot-publisher y usage-window.
- **Hecho cuando:** las pruebas existentes de Claude pasan y las de regresión describen el flujo previo a v2.
- **Pruebas automáticas:**
  - Caso feliz: un fichero de estado válido de Claude produce el estado correcto, su historial y su cuenta atrás como antes de v2.
  - Caso de error o límite: un fichero de estado inválido se trata como antes y no afecta a OpenCode; sin datos de OpenCode, Claude se muestra con normalidad.
- **Prueba manual:**
  1. Con Claude y OpenCode configurados, arrancar la aplicación. Resultado: ambas secciones se muestran y Claude mantiene su cuenta atrás.
  2. Quitar `opencode` del PATH y reiniciar. Resultado: Claude sigue actualizándose y OpenCode no aparece.

### [x] TASK-020 Verificación transversal de arranque, recursos y receptividad

- **Cubre:** REQ-001, NFR-001, NFR-002, NFR-003, NFR-004
- **Componente:** transversal (proceso completo) | no aplica (sin componente único)
- **Depende de:** TASK-019
- **Alcance:** Medir de extremo a extremo el arranque de fondo, el consumo de CPU y heap en reposo, la receptividad de la interfaz durante las consultas y la compilación con Java 21.
- **Hecho cuando:** los umbrales de NFR-001, NFR-003 y NFR-004 se cumplen en la máquina del usuario, y el arranque de fondo de REQ-001 funciona sin cambios.
- **Pruebas automáticas:**
  - Caso feliz: la compilación y la suite de pruebas con Java 21 terminan sin errores de versión ni advertencias de deprecación que impidan el funcionamiento (NFR-002).
  - Caso de error o límite: con `opencode` ausente, el arranque de fondo completa y la interfaz aparece (propuesto).
- **Prueba manual:**
  1. Lanzar la aplicación con `start-background`. Resultado: la terminal recupera el control y la interfaz aparece en menos de 30 s.
  2. Medir la CPU de la aplicación durante 5 minutos en reposo, sin contar procesos `opencode`. Resultado: menos de 5 % en un núcleo.
  3. Consultar el heap de la JVM (p. ej. con `jcmd <pid> GC.heap_info`). Resultado: menos de 256 MB.
  4. Con un CLI lento o un ciclo en curso, interactuar con el menú de la bandeja. Resultado: responde en menos de 1 s.

### [x] TASK-021 Limpieza del código, configuración y pruebas de v1

- **Cubre:** REQ-014, DEC-007, DEC-008
- **Componente:** opencode-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode ; configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config ; secret-config-files | desktop/app/config ; usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java ; usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java ; usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java ; usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-001, TASK-002, TASK-003, TASK-004, TASK-005, TASK-006, TASK-007, TASK-008, TASK-009, TASK-010, TASK-011, TASK-012, TASK-013, TASK-014, TASK-015, TASK-016, TASK-017, TASK-018, TASK-019, TASK-020
- **Alcance:** Retirar el cliente HTTP y la normalización `percent`/`usagePercent` de la fuente de OpenCode; la propiedad `api-key`, `OPENCODE_API_KEY`, su precedencia y su enmascarado en configuration-provider; `secret-config-files` (ejemplo de configuración y reglas de exclusión de la clave); la propiedad de backoff; el tratamiento de 401/403 y los estados de clave ausente, clave inválida y sin suscripción en usage-source, usage-poller, usage-snapshot-publisher y usage-window; los mensajes de autenticación; y las pruebas asociadas, incluida la verificación con clave centinela. Esta tarea es la única de limpieza de v1 (REQ-014) y se ejecuta cuando la v2 y la regresión de Claude están terminadas. Las referencias de README, mapa y contratos las actualiza el documentador después de esta tarea.
- **Hecho cuando:** la búsqueda de `OPENCODE_API_KEY`, `Authorization: Bearer` y `/zen/go/v1/usage` en código y configuración no devuelve referencias activas, se compila y las pruebas pasan sin depender de código retirado, y la regresión de Claude (TASK-019) sigue en verde.
- Retirar también todas las pruebas marcadas con `@Disabled("v1 - retirar en TASK-021")` en la suite, verificando que ninguna sigue presente tras la limpieza.
- **Pruebas automáticas:**
  - Caso feliz: la compilación con Java 21 y la suite completa terminan en verde.
  - Caso de error o límite: una búsqueda de los tres literales en código y configuración no devuelve referencias activas; el arranque sin variable `OPENCODE_API_KEY` ni propiedad `api-key` funciona sin pedir ninguna clave; una propiedad `api-key` residual en `application.yml` no impide el arranque.
- **Prueba manual:**
  1. Buscar en el repositorio las tres cadenas. Resultado: sin referencias activas.
  2. Arrancar la aplicación sin clave, con OpenCode y Claude. Resultado: no aparece ningún mensaje de autenticación ni de clave.

## Cobertura

| Requisito | Tareas |
|---|---|
| REQ-001 | TASK-020 |
| REQ-002 | TASK-003, TASK-004, TASK-005, TASK-006, TASK-008 |
| REQ-003 | TASK-003, TASK-004, TASK-005, TASK-006, TASK-008, TASK-012, TASK-013, TASK-015 |
| REQ-004 | TASK-001, TASK-002 |
| REQ-005 | TASK-003, TASK-007, TASK-015 |
| REQ-006 | TASK-007, TASK-009 |
| REQ-007 | TASK-001, TASK-007, TASK-009 |
| REQ-008 | TASK-012, TASK-015 |
| REQ-009 | TASK-001, TASK-010 |
| REQ-010 | TASK-012, TASK-013, TASK-015 |
| REQ-011 | TASK-011, TASK-014, TASK-016 |
| REQ-012 | TASK-011, TASK-016, TASK-017 |
| REQ-013 | TASK-010, TASK-015, TASK-019 |
| REQ-014 | TASK-018, TASK-021 |
| REQ-015 | TASK-008, TASK-010 |
| REQ-016 | TASK-007, TASK-009, TASK-015 |
| NFR-001 | TASK-020 |
| NFR-002 | TASK-004, TASK-020 |
| NFR-003 | TASK-020 |
| NFR-004 | TASK-004, TASK-008, TASK-020 |
| NFR-005 | TASK-001, TASK-008, TASK-010 |
| NFR-006 | TASK-001, TASK-004, TASK-006, TASK-008 |
