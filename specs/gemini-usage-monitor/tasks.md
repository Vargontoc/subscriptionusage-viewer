# Tareas: gemini-usage-monitor

Estado: aprobado

## Resumen

Primero se verifica `agy` y se prepara el refactor de `opencode-cli-runner` hacia `cli-runner-base` con su regresión de OpenCode y OpenAI, antes de añadir Gemini. Después se construyen la configuración, los cambios compartidos, el ejecutor, el parser y la fuente de Gemini, su integración con el ciclo de refresco y la vista, y cierran los contratos, la regresión transversal y la verificación de extremo a extremo.

## Tareas

### [x] TASK-001 Verificar agy: argumentos, salida JSON y resolución en Windows

- **Cubre:** REQ-002, DEC-001
- **Componente:** gemini-cli-runner | por definir (analista técnico)
- **Depende de:** -
- **Alcance:** confirmar con el `agy` real instalado que `--output-format json` es aceptado, que la salida tiene la forma esperada y que el ejecutable se localiza por PATH en Windows, también cuando es un script `.cmd`.
- **Hecho cuando:** se ha comprobado que `agy -p "/usage" --output-format json` termina con código 0 y devuelve JSON con `status` y `command.data.groups`, y se ha registrado si `agy` resuelve como `.exe` o como `.cmd`. Si `json` no es aceptado, la tarea no se cierra y se devuelve al router (supuesto S1).
- **Pruebas automáticas:**
  - Caso feliz: no aplica: verificación manual sobre el `agy` instalado, sin código que probar.
  - Caso de error o límite: no aplica: el rechazo del argumento se recoge en la prueba manual.
- **Prueba manual:**
  1. En PowerShell, desde el directorio del proyecto, ejecutar `agy -p "/usage" --output-format json`. Debe terminar con código 0 y la salida debe ser JSON con `status` igual a `SUCCESS` y `command.data.groups` con al menos un grupo. Si el comando rechaza `json`, la tarea no se cierra.
  2. Ejecutar `where agy` y anotar si la resolución es `agy.exe` o `agy.cmd`.
  3. Desde un directorio distinto, repetir el paso 1 y comprobar que el resultado es el mismo, lo que confirma la resolución por PATH.
  4. Repetir el paso 1 desde `cmd.exe` y comprobar que el resultado es el mismo, porque el envoltorio de Windows usa `cmd.exe`.
  5. Anotar solo los nombres de los campos de la salida, sin valores de conversación ni identificadores, para la tarea de contrato TASK-020.

### [x] TASK-002 Inventario de llamadas a liveProcessCount y destroyActiveProcesses

- **Cubre:** DEC-010
- **Componente:** opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java
- **Depende de:** -
- **Alcance:** revisar quién llama a `liveProcessCount` y a `destroyActiveProcesses`, en código de producción, en pruebas y en el cierre ordenado, y decidir en qué clase debe quedar cada método tras la extracción.
- **Hecho cuando:** existe la lista completa de llamadas (fichero y clase de cada una) y, para cada método, la decisión de si queda en la base o en la subclase, anotadas en el resultado de la tarea y sin cambios de código.
- **Pruebas automáticas:**
  - Caso feliz: no aplica: revisión de código sin comportamiento que probar.
  - Caso de error o límite: no aplica: la tarea solo registra el inventario; el efecto se prueba en TASK-003.
- **Prueba manual:**
  1. Buscar en `desktop/app` todas las referencias a `liveProcessCount` y `destroyActiveProcesses`, incluidas las pruebas.
  2. Anotar cada referencia con su fichero, su clase y si es producción o prueba, y la decisión sobre el lugar del método tras el refactor. Comprobar que no queda ninguna referencia sin anotar.

### [x] TASK-003 Refactor de OpenCodeCliRunner para heredar de cli-runner-base

- **Cubre:** REQ-013, NFR-001, NFR-002, NFR-003, DEC-001, DEC-010
- **Componente:** opencode-cli-runner ↔ cli-runner-base | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java ↔ desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/cli/AbstractCliRunner.java
- **Depende de:** TASK-002
- **Alcance:** extraer la mecánica común (proceso hijo por PATH con envoltorio en Windows, cierre de stdin, descarte de stderr, lectura de stdout con límite, espera con plazo, destrucción del árbol de procesos, registro de procesos activos y resultado con salida y código de salida) a una base abstracta, de modo que `opencode-cli-runner` herede de ella. La API pública de OpenCode no cambia: métodos de estadísticas y de ruta de datos, tipo de resultado con fallo silencioso, constructores usados por pruebas y por Spring, timeout de 30 s, límite de 1 MB y validación de la ruta de una sola línea. El código de salida se añade al resultado sin que los consumidores existentes lo usen. Los métodos de TASK-002 quedan donde se decidió allí.
- **Hecho cuando:** `opencode-cli-runner` hereda de la base, sus pruebas existentes pasan sin cambiar sus expectativas, los consumidores compilan sin modificarse y cada método queda en la clase decidida en TASK-002.
- **Pruebas automáticas:**
  - Caso feliz: las consultas de estadísticas y de ruta de datos devuelven el mismo resultado que antes del refactor.
  - Caso de error: un ejecutable inexistente o un código distinto de cero dan el fallo silencioso de siempre; el código de salida se devuelve en el resultado interno.
  - Caso de límite: el timeout de 30 s destruye el proceso y sus hijos; una salida superior a 1 MB se corta sin bloquear; una ruta de datos de más de una línea se rechaza como antes.
  - Regresión: las tres consultas de `opencode-usage-source` por ciclo conservan su timeout y su fallo silencioso; en Windows, la ejecución de `.cmd` sigue funcionando.
- **Prueba manual:**
  1. Ejecutar la aplicación con OpenCode activo y comprobar que sus datos se muestran y se actualizan igual que antes del refactor.

### [x] TASK-004 Regresión de credenciales de OpenAI tras el refactor del ejecutor

- **Cubre:** REQ-013, DEC-010
- **Componente:** openai-credentials-reader | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/openai
- **Depende de:** TASK-003
- **Alcance:** comprobar que `openai-credentials-reader` sigue obteniendo la ruta de datos de OpenCode mediante `runDataPath` sobre la implementación concreta, sin cambios de comportamiento.
- **Hecho cuando:** las pruebas existentes de `openai-credentials-reader` y de `openai-usage-source` pasan sin cambiar sus expectativas y la lectura de credenciales de opencode funciona igual que antes.
- **Pruebas automáticas:**
  - Caso feliz: con una ruta de datos válida, las credenciales de opencode se leen y OpenAI obtiene su consulta.
  - Caso de error: si `runDataPath` falla o no devuelve una ruta válida, OpenAI se comporta como antes del refactor (sin credenciales o con sesión caducada, según corresponda).
  - Caso de límite: una ruta de datos de más de una línea se rechaza como antes.
- **Prueba manual:**
  1. Con OpenAI activo mediante credenciales de opencode, abrir la ventana de uso y comprobar que la sección OpenAI muestra los mismos valores que antes del refactor.
  2. Con las credenciales no disponibles, comprobar que OpenAI muestra la sesión caducada como antes.

### [x] TASK-005 Configuración de subscription-viewer.gemini.usage-poll-interval

- **Cubre:** REQ-007, NFR-002, DEC-007
- **Componente:** configuration-provider | src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** -
- **Alcance:** añadir la subsección `subscription-viewer.gemini` con `usage-poll-interval` (60 s por defecto si está ausente o vacío, mínimo 30 s, y validación uniforme que falla el arranque nombrando la propiedad) y la entrada `usage-poll-interval: "60s"` en `desktop/app/src/main/resources/application.yml`.
- **Hecho cuando:** `application.yml` contiene `subscription-viewer.gemini.usage-poll-interval: "60s"` y la configuración resuelve ausente o vacío, `5s`, `abc`, `0s` y `-5s` exactamente como indica REQ-007.
- **Pruebas automáticas:**
  - Caso feliz: con `60s`, el intervalo de Gemini es de 60 segundos.
  - Caso de límite: ausente o vacío da 60 s; `5s` da 30 s.
  - Caso de error: `abc`, `0s` y `-5s` impiden el arranque con un error que nombra `subscription-viewer.gemini.usage-poll-interval`.
  - Regresión: las propiedades de claude, opencode y openai se validan igual que antes.
- **Prueba manual:**
  1. Abrir `application.yml` y comprobar que `subscription-viewer.gemini` contiene `usage-poll-interval: "60s"`, con el mismo formato que `openai`.
  2. Sustituir temporalmente el valor por `abc`, lanzar la aplicación y comprobar que no arranca y que el mensaje de error nombra la propiedad. Restaurar `"60s"`.

### [x] TASK-006 Porcentaje con un decimal en la lectura de ventana

- **Cubre:** REQ-004, DEC-004
- **Componente:** UsageWindowReading | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageWindowReading.java
- **Depende de:** -
- **Alcance:** ampliar la lectura de ventana para que el porcentaje admita un decimal, sin cambiar los valores que producen Claude, OpenCode y OpenAI, que siguen siendo enteros.
- **Hecho cuando:** una lectura conserva un porcentaje como 1,8 con su decimal, y las lecturas enteras existentes se representan igual que antes del cambio.
- **Pruebas automáticas:**
  - Caso feliz: un porcentaje de 1,8 se conserva con su decimal.
  - Caso de límite: 0 y 100 se representan como enteros, sin decimales espurios.
  - Regresión: las lecturas de Claude, OpenCode y OpenAI con valores enteros producen el mismo resultado que antes.
- **Prueba manual:** no aplica: modelo interno sin comportamiento observable propio; la regresión visual se verifica en TASK-023.

### [x] TASK-007 Estado consulta fallida y tiempos declarados por fuente en usage-source

- **Cubre:** REQ-007, REQ-011, REQ-013, DEC-005, DEC-006
- **Componente:** usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java
- **Depende de:** -
- **Alcance:** añadir a los resultados clasificados el estado "consulta fallida" y permitir que cada fuente declare su intervalo y su tiempo máximo. La selección actual por nombre queda como valor por defecto para Claude, OpenCode y OpenAI.
- **Hecho cuando:** existe el cuarto estado, cada fuente puede declarar sus tiempos y los tres proveedores existentes conservan sus valores y estados.
- **Pruebas automáticas:**
  - Caso feliz: una fuente que declara intervalo y tiempo máximo propios los devuelve.
  - Caso de error: una fuente sin declaración recibe los valores por defecto actuales, sin cambios.
  - Caso de límite: los estados existentes (datos, fallo transitorio, sesión caducada) conservan su significado.
- **Prueba manual:** no aplica: contrato interno sin comportamiento observable propio; se verifica en TASK-014 y TASK-016.

### [x] TASK-008 Ejecutor gemini-cli-runner para agy

- **Cubre:** REQ-002, REQ-011, NFR-001, NFR-003, DEC-001, DEC-008, DEC-009
- **Componente:** gemini-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiCliRunner.java
- **Depende de:** TASK-001, TASK-003
- **Alcance:** implementar la clase base de ejecución de CLI para `agy`, que aporta el comando `agy`, los argumentos `-p`, `/usage`, `--output-format` y `json`, un timeout fijo de 60 s y sus propiedades propias (límite de salida, nombre del hilo lector y etiqueta de log). Devuelve salida y código de salida, y los fallos no llevan detalle técnico. No acepta argumentos del usuario. Mantiene su propio registro de procesos, separado del de OpenCode.
- **Hecho cuando:** el ejecutor lanza `agy` con esos cuatro argumentos, devuelve salida y código, destruye el proceso y sus hijos al cumplirse 60 s, y con los dos ejecutores registrados los consumidores reciben su implementación concreta sin ambigüedad de beans.
- **Pruebas automáticas:**
  - Caso feliz: un `agy` de prueba que termina con código 0 recibe exactamente `-p`, `/usage`, `--output-format` y `json`, y devuelve su salida y el código 0.
  - Caso de error: un ejecutable inexistente devuelve fallo sin detalle; un código distinto de cero se devuelve como código.
  - Caso de límite: un `agy` que no termina se destruye al cumplirse 60 s junto con sus hijos y devuelve fallo por tiempo agotado; la salida que supera el límite se corta sin bloquear el ejecutor.
  - Aislamiento: destruir `agy` no afecta a los procesos de `opencode` ni a su registro.
  - Protección: la salida del comando no aparece en ningún mensaje de log, con una cadena centinela en la salida de prueba.
  - Inyección: el contexto arranca con los dos ejecutores y `openai-credentials-reader`, `opencode-usage-source` y `gemini-usage-source` reciben la implementación concreta, nunca la base.
- **Prueba manual:**
  1. En Windows, crear en una carpeta temporal un `agy.cmd` de prueba que no termine, añadir esa carpeta al PATH de la sesión y provocar una consulta desde la aplicación. Comprobar que se ejecuta el `.cmd`.
  2. Comprobar que el proceso desaparece a los 60 s y que no quedan procesos sobrantes en el Administrador de tareas. Quitar la carpeta del PATH.

### [x] TASK-009 Validar el sobre de la respuesta de agy

- **Cubre:** REQ-002, REQ-011, DEC-002, DEC-009
- **Componente:** gemini-usage-parser | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiUsageParser.java
- **Depende de:** TASK-001
- **Alcance:** validar que la salida es JSON, que `status` es `SUCCESS` y que existe `command.data.groups`. Ignorar las propiedades no reconocidas y el texto `response`. Devolver un motivo de invalidez que nunca contenga la salida del comando.
- **Hecho cuando:** una salida válida pasa a la extracción de grupos y cualquier salida inválida se clasifica como inválida sin contenido de la salida.
- **Pruebas automáticas:**
  - Caso feliz: un JSON con `status` igual a `SUCCESS` y `command.data.groups` se acepta, ignorando campos adicionales.
  - Caso de error: texto que no es JSON, `status` distinto de `SUCCESS` o ausencia de `command.data.groups` se clasifican como inválidos.
  - Caso de límite: si están `command.data.groups` y `response`, solo se usan los grupos.
  - Protección: el motivo de invalidez no contiene una cadena centinela incluida en una salida inválida.
- **Prueba manual:** no aplica: componente interno sin comportamiento observable; se verifica en TASK-013 y TASK-024.

### [x] TASK-010 Extraer grupos, ventanas y porcentajes de command.data.groups

- **Cubre:** REQ-003, REQ-004, REQ-005, REQ-012, DEC-002, DEC-003, DEC-004
- **Componente:** gemini-usage-parser | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiUsageParser.java; desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiGroup.java; desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiWindow.java
- **Depende de:** TASK-006, TASK-009
- **Alcance:** de `command.data.groups` extraer cada grupo por `name` y sus cubos por `window` (`weekly` y `5h`), `remaining_fraction` y `reset_time`. Calcular el porcentaje consumido acotado entre 0 y 100 y redondeado a un decimal, interpretar `reset_time` como UTC y omitir cubos y grupos inválidos. Si no queda ningún grupo válido, devolver consulta fallida.
- **Hecho cuando:** una respuesta con los tres grupos produce cinco ventanas con los porcentajes de REQ-004, y las respuestas incompletas producen solo lo presente, o consulta fallida si no queda nada válido.
- **Pruebas automáticas:**
  - Caso feliz: tres grupos producen cinco ventanas; una fracción de 0,9818568229675293 produce 1,8 %; 1 produce 0 %, 0 produce 100 % y 0,5 produce 50 %.
  - Caso de límite: una fracción fuera de 0-1 se acota a 0 o a 100; un `reset_time` con sufijo `Z` se interpreta como UTC; un grupo sin `5h` muestra solo `weekly`; un grupo con `buckets` vacío o ausente se omite; un grupo con nombre no previsto se incluye; una ventana distinta de `weekly` y `5h` se ignora.
  - Caso de error: un cubo con `remaining_fraction` ausente o no numérico se ignora sin error; un `reset_time` ausente o no ISO-8601 deja la ventana sin reinicio; una respuesta sin ningún cubo válido produce consulta fallida.
- **Prueba manual:** no aplica: componente interno sin comportamiento observable propio; se verifica en TASK-017.

### [x] TASK-011 Tiempo restante de las ventanas de Gemini en countdown-evaluator

- **Cubre:** REQ-005, DEC-002
- **Componente:** countdown-evaluator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/CountdownEvaluator.java
- **Depende de:** -
- **Alcance:** verificar y, si hace falta, ajustar el cálculo del tiempo restante para que las ventanas de Gemini cumplan REQ-005: mínimo 0 y ausencia de tiempo cuando no hay instante de reinicio.
- **Hecho cuando:** un reinicio situado 2 horas por delante da un tiempo restante de 2 horas, un reinicio pasado da 0 y una ventana sin reinicio no muestra tiempo, sin error.
- **Pruebas automáticas:**
  - Caso feliz: un reinicio 2 horas por delante da un tiempo restante equivalente a 2 horas.
  - Caso de límite: un reinicio ya pasado da 0, nunca un valor negativo.
  - Caso de error: la ausencia de reinicio devuelve "sin tiempo restante" sin lanzar excepción.
- **Prueba manual:** no aplica: el tiempo restante se ve en la ventana; se verifica en TASK-017.

### [x] TASK-012 Historial de Gemini con un decimal y sin persistencia

- **Cubre:** REQ-004, REQ-009, REQ-010, DEC-003, DEC-004
- **Componente:** usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java
- **Depende de:** TASK-006
- **Alcance:** añadir un punto por grupo y ventana solo si el valor redondeado a un decimal cambia, manteniendo las series por grupo y ventana solo en memoria.
- **Hecho cuando:** dos lecturas con el mismo valor redondeado no añaden puntos, un cambio de un decimal sí añade uno y un historial nuevo empieza vacío.
- **Pruebas automáticas:**
  - Caso feliz: lecturas de Gemini con valores distintos añaden un punto por grupo y ventana, en orden temporal.
  - Caso de límite: dos lecturas consecutivas iguales no añaden puntos; 1,81 y 1,8 se consideran iguales tras el redondeo; un historial nuevo no carga datos de ejecuciones anteriores (propuesto: REQ-010 no tiene caso de error).
  - Regresión: Claude, OpenCode y OpenAI con valores enteros siguen deduplicando como antes.
- **Prueba manual:** no aplica: el comportamiento visible del historial se verifica en TASK-019 y TASK-024.

### [x] TASK-013 Fuente gemini-usage-source

- **Cubre:** REQ-001, REQ-002, REQ-011, REQ-013, DEC-001, DEC-006, DEC-008, DEC-009
- **Componente:** gemini-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/gemini/GeminiUsageSource.java
- **Depende de:** TASK-005, TASK-007, TASK-008, TASK-010
- **Alcance:** pedir al ejecutor la consulta `agy -p "/usage" --output-format json`, pasar la salida al parser y clasificar el resultado como datos o como consulta fallida, sin detalle del error. Declarar como intervalo el valor de la configuración y como tiempo máximo un valor algo mayor que 60 s.
- **Hecho cuando:** una consulta correcta devuelve lecturas por grupo, cualquier error devuelve consulta fallida, y la fuente declara el intervalo configurado y un tiempo máximo superior a 60 s.
- **Pruebas automáticas:**
  - Caso feliz: con salida válida, el ejecutor recibe exactamente los argumentos `-p`, `/usage`, `--output-format` y `json`, y la fuente devuelve datos.
  - Caso de error: ejecutable inexistente, código distinto de cero o tiempo agotado devuelven consulta fallida sin salida ni código en el resultado.
  - Caso de límite: el tiempo máximo declarado es mayor que 60 s y el intervalo declarado es el configurado.
  - Protección: ningún mensaje de log contiene la cadena centinela de una salida de prueba.
- **Prueba manual:** no aplica: el comportamiento visible se verifica en TASK-016 y TASK-024.

### [x] TASK-014 Ciclo de Gemini en usage-poller

- **Cubre:** REQ-001, REQ-007, REQ-011, REQ-013, NFR-002, NFR-003, DEC-006, DEC-008
- **Componente:** usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java
- **Depende de:** TASK-007
- **Alcance:** programar el siguiente ciclo de cada fuente al terminar el anterior, sin reintento inmediato tras un error; publicar los fallos de Gemini; mantener sin cambios la lógica de OpenCode; ejecutar cada fuente en su propio hilo con su tiempo máximo.
- **Hecho cuando:** un ciclo fallido de Gemini se publica como consulta fallida, el siguiente ciclo espera al intervalo y los fallos de OpenCode siguen sin publicarse.
- **Pruebas automáticas:**
  - Caso feliz: con una fuente simulada que devuelve datos, cada ciclo se programa tras terminar el anterior.
  - Caso de límite: el siguiente ciclo no empieza mientras el anterior sigue en curso, aunque haya pasado el intervalo.
  - Caso de error: una fuente que devuelve consulta fallida se publica y no se reintenta de inmediato.
  - Aislamiento: un ciclo de Gemini que tarda más de 8 s no retrasa a una fuente simulada de otro proveedor.
  - Regresión: los fallos de OpenCode siguen sin publicarse y los de Claude y OpenAI se publican como hasta ahora.
- **Prueba manual:** no aplica: se verifica en TASK-016 y TASK-024.

### [x] TASK-015 Publicación de la consulta fallida en usage-snapshot-publisher

- **Cubre:** REQ-008, REQ-011, DEC-005
- **Componente:** usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java
- **Depende de:** TASK-007
- **Alcance:** publicar el estado "consulta fallida" conservando la última lectura válida y la hora del último éxito. Un éxito posterior retira el aviso y actualiza la hora.
- **Hecho cuando:** tras un fallo se mantienen la última lectura y la hora del último éxito junto al estado de fallo, y tras un éxito el estado vuelve a datos.
- **Pruebas automáticas:**
  - Caso feliz: tras un éxito, el estado publicado tiene datos y la hora de fin de consulta.
  - Caso de error: tras un fallo con datos previos se conservan los datos y la hora del último éxito, y el estado es consulta fallida.
  - Caso de límite: un fallo sin datos previos publica consulta fallida sin lecturas; un éxito posterior retira el aviso.
  - Regresión: la desactualización de Claude y la sesión caducada de OpenAI siguen igual.
- **Prueba manual:** no aplica: se verifica en TASK-018.

### [x] TASK-016 Integración de usage-poller con gemini-usage-source

- **Cubre:** REQ-001, REQ-007, REQ-013, NFR-002
- **Componente:** usage-poller ↔ gemini-usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java ↔ por definir (analista técnico)
- **Depende de:** TASK-005, TASK-013, TASK-014
- **Alcance:** comprobar la comunicación entre el poller y la fuente de Gemini: el poller registra la fuente con el intervalo de configuración y lanza la primera lectura en segundo plano.
- **Hecho cuando:** con `agy` disponible, la primera lectura se hace sin bloquear el arranque y la frecuencia respeta el intervalo mínimo de 30 s.
- **Pruebas automáticas:**
  - Caso feliz: con `60s` configurado, la fuente de Gemini se consulta en la primera lectura y después cada 60 s (con reloj simulado).
  - Caso de límite: con `1s` configurado se usan 30 s y en un minuto no hay más de 2 ejecuciones.
  - Caso de error: con la fuente de Gemini fallando, el poller sigue sus ciclos y no detiene a los demás proveedores.
- **Prueba manual:**
  1. Lanzar la aplicación con `start-background` y comprobar que la terminal se devuelve al usuario y que la ventana de uso se abre sin esperar a Gemini.
  2. Mientras la primera consulta de Gemini sigue en curso, comprobar que la ventana responde y que los demás proveedores ya muestran datos.

### [x] TASK-017 Sección Gemini con grupos y ventanas en usage-window

- **Cubre:** REQ-003, REQ-004, REQ-005, REQ-006, REQ-008, REQ-012, DEC-003, DEC-004
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-006, TASK-010, TASK-011, TASK-015
- **Alcance:** mostrar Gemini como sección diferenciada con un bloque por grupo que contenga solo sus ventanas presentes, el porcentaje con un decimal, el tiempo restante y la hora de la última consulta exitosa.
- **Hecho cuando:** con los tres grupos aparece la sección con tres bloques diferenciados, Gemini 4 Argon muestra solo la ventana semanal y la hora mostrada es la de la última consulta exitosa.
- **Pruebas automáticas:**
  - Caso feliz: con los tres grupos, cada ventana muestra su propio valor; "Claude and GPT models" con 1,8 % en 5 horas y 0,9 % en la semana muestra esos dos valores por separado.
  - Caso de límite: "Gemini 4 Argon" no muestra ventana de 5 horas ni valor inventado; un grupo nuevo aparece sin reiniciar la aplicación; un grupo con nombre no previsto se muestra con su nombre; un bloque no se mezcla con otro aunque coincidan los nombres de ventana (propuesto: REQ-006 no tiene caso de error).
  - Caso de error: una ventana sin reinicio se muestra con su porcentaje y sin tiempo restante, sin error visible.
  - Regresión: las secciones de Claude, OpenCode y OpenAI se muestran como antes.
- **Prueba manual:**
  1. Con `agy` disponible, abrir la ventana de uso y comprobar que hay una sección Gemini con cada grupo devuelto y sus ventanas.
  2. Comparar los porcentajes con la salida de `agy -p "/usage" --output-format json` en el mismo momento: deben coincidir con un decimal.
  3. Comprobar que la hora de actualización coincide con la hora de la última consulta completada.

### [ ] TASK-018 Aviso formal de consulta fallida en usage-window

- **Cubre:** REQ-011, REQ-013, DEC-005, DEC-009
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-015, TASK-017
- **Alcance:** mostrar en la sección Gemini el texto formal exacto de REQ-011 cuando el estado es consulta fallida, junto con los últimos datos válidos si existen, y retirarlo tras un éxito. La vista no muestra salida, trazas ni códigos.
- **Hecho cuando:** el aviso aparece con el texto exacto en todos los escenarios de fallo, desaparece tras un éxito y ningún texto de la vista contiene la salida del comando.
- **Pruebas automáticas:**
  - Caso feliz: tras un éxito no hay aviso.
  - Caso de error: una consulta fallida muestra el texto exacto de REQ-011 y no muestra la cadena centinela de una salida de prueba, ni trazas ni códigos.
  - Caso de límite: un fallo con datos previos muestra los datos y el aviso, sin cambiar la hora; un fallo sin datos previos muestra la sección con el aviso y sin grupos; un éxito posterior retira el aviso.
  - Regresión: la sesión caducada de OpenAI y el fallo silencioso de OpenCode se muestran como antes.
- **Prueba manual:**
  1. Ocultar `agy` del PATH de forma temporal y reiniciar la aplicación. La sección Gemini debe mostrar exactamente el texto formal de REQ-011, sin datos, y el resto de proveedores debe seguir visible. Restaurar `agy`.
  2. Con `agy` restaurado, esperar un ciclo de refresco. El aviso debe desaparecer y aparecer los datos de Gemini.

### [ ] TASK-019 Gráfico de evolución de Gemini en usage-window

- **Cubre:** REQ-009, DEC-003, DEC-004
- **Componente:** usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-012, TASK-017
- **Alcance:** mostrar en el gráfico de evolución una serie por grupo y ventana de Gemini, con los puntos del historial.
- **Hecho cuando:** cada lectura distinta añade puntos sin recargar la aplicación, y ningún punto queda fuera de 0-100.
- **Pruebas automáticas:**
  - Caso feliz: tras varias lecturas distintas, el gráfico muestra un punto por lectura y ventana, ordenados en el tiempo y distinguiendo grupo y ventana.
  - Caso de límite: dos lecturas iguales no añaden puntos; los valores 0 y 100 se muestran dentro del rango.
  - Caso de error: un valor fuera de 0-100 no genera un punto fuera del rango.
  - Regresión: el gráfico de OpenCode mantiene sus series.
- **Prueba manual:**
  1. Dejar la aplicación abierta durante varios ciclos con cambios de consumo y comprobar que aparecen puntos nuevos sin reiniciar.
  2. Comprobar que cada grupo y ventana de Gemini tiene su serie diferenciada.

### [ ] TASK-020 Contrato: stdio - ejecución de agy-usage

- **Cubre:** REQ-002, REQ-011, DEC-001, DEC-002, DEC-009
- **Componente:** gemini-cli-runner | por definir (analista técnico). Ubicación del contrato: `docs/contracts/stdio/`, con nombre de fichero por definir (analista técnico), por ser contrato nuevo.
- **Depende de:** TASK-008, TASK-010
- **Alcance:** crear el contrato de la interfaz stdio con la CLI `agy`: invocación con `-p "/usage" --output-format json` por PATH, salida JSON con `status` y `command.data.groups` (grupos, cubos, ventana, fracción restante e instante de reinicio), código de salida, límite de 60 s y propiedades ignoradas. Sin datos reales de conversación en los ejemplos.
- **Hecho cuando:** el contrato existe en `docs/contracts/stdio/` y describe la interfaz tal como quedó implementada.
- **Pruebas automáticas:**
  - Caso feliz: no aplica: el formato stdio no admite validación automática de su contrato; se verifica en la prueba manual.
  - Caso de error: el contrato recoge las respuestas de error de REQ-011: ejecutable no encontrado, código distinto de cero, tiempo agotado de 60 s, salida que no es JSON, `status` distinto de `SUCCESS` y respuesta sin grupos válidos.
- **Prueba manual:**
  1. Repasar que el contrato incluye la invocación con los cuatro argumentos y coincide con lo que hace el ejecutor.
  2. Repasar que los campos descritos coinciden con los que lee el parser (`status`, `command.data.groups`, `buckets`, `window`, `remaining_fraction`, `reset_time`) y que se indica que el resto se ignora.
  3. Repasar que el contrato no contiene datos reales de conversación.

### [ ] TASK-021 Contrato: stdio - revisión de opencode-session-stats

- **Cubre:** REQ-013, NFR-003, DEC-010
- **Componente:** opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java. Contrato existente: `docs/contracts/stdio/desktop/app/opencode-session-stats.md`.
- **Depende de:** TASK-003
- **Alcance:** revisar el contrato existente para que refleje que el origen es `opencode-cli-runner` como implementación de la base común. Confirmar que lo documentado (argumentos, timeout, límite de salida y fallo silencioso) sigue siendo cierto, sin cambio funcional.
- **Hecho cuando:** el contrato describe la interfaz tal como quedó tras el refactor y no contiene ninguna afirmación que el código ya no cumpla.
- **Pruebas automáticas:**
  - Caso feliz: no aplica: el formato stdio no admite validación automática de su contrato; se verifica en la prueba manual.
  - Caso de error: el contrato recoge el fallo silencioso ante ejecutable inexistente, código distinto de cero y tiempo agotado.
- **Prueba manual:**
  1. Repasar que los argumentos, el timeout de 30 s y el límite de 1 MB documentados coinciden con la subclase tras el refactor.
  2. Repasar que el contrato describe el fallo silencioso y no menciona la base como interfaz pública.

### [ ] TASK-022 Contrato: stdio - revisión de opencode-data-path

- **Cubre:** REQ-013, DEC-010
- **Componente:** opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java. Contrato existente: `docs/contracts/stdio/desktop/app/opencode-data-path.md`.
- **Depende de:** TASK-003, TASK-004
- **Alcance:** revisar el contrato existente de la consulta de ruta de datos para que refleje la implementación tras el refactor, sin cambio funcional, incluida la validación de una sola línea.
- **Hecho cuando:** el contrato describe la consulta tal como quedó implementada y coincide con lo que usa `openai-credentials-reader`.
- **Pruebas automáticas:**
  - Caso feliz: no aplica: el formato stdio no admite validación automática de su contrato; se verifica en la prueba manual.
  - Caso de error: el contrato recoge el comportamiento ante fallo y ante una ruta de más de una línea.
- **Prueba manual:**
  1. Repasar que el comando, los argumentos y la validación de una línea documentados coinciden con la subclase.
  2. Repasar que el contrato indica que su consumidor es `openai-credentials-reader`, y que no se describen cambios funcionales.

### [ ] TASK-023 Regresión transversal de Claude, OpenCode y OpenAI

- **Cubre:** REQ-013, DEC-001, DEC-004, DEC-005, DEC-006, DEC-008, DEC-010
- **Componente:** transversal (sin componente concreto): usage-source | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSource.java; usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java; usage-history | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageHistory.java; usage-snapshot-publisher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsageSnapshotPublisher.java; usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java; opencode-cli-runner | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java
- **Depende de:** TASK-003, TASK-004, TASK-006, TASK-007, TASK-012, TASK-014, TASK-015, TASK-018, TASK-019
- **Alcance:** comprobar que Claude, OpenCode y OpenAI no cambian por la ampliación a decimal, por el refactor del ejecutor ni por los cambios en componentes compartidos.
- **Hecho cuando:** la suite existente de Claude, OpenCode y OpenAI pasa sin cambiar sus expectativas y la ventana de uso muestra los mismos valores y estados que antes.
- **Pruebas automáticas:**
  - Caso feliz: la suite existente de Claude, OpenCode y OpenAI pasa sin modificar sus expectativas.
  - Caso de límite: los valores enteros de los tres proveedores se deduplican y se muestran igual que antes.
  - Caso de error: los estados de error ya existentes (sesión caducada de OpenAI, fallo silencioso de OpenCode, desactualización de Claude) se comportan igual que antes.
- **Prueba manual:**
  1. Con los mismos datos de Claude, OpenCode y OpenAI, comparar la ventana de uso de la versión anterior (commit f1e70b8) con la nueva: porcentajes, tiempos restantes y secciones deben ser idénticos.
  2. Comprobar que OpenAI sigue mostrando sesión caducada cuando corresponde.

### [ ] TASK-024 Verificación de Gemini de extremo a extremo

- **Cubre:** REQ-001, REQ-010, REQ-013, NFR-001, NFR-004
- **Componente:** transversal (sin componente concreto): background-launcher | desktop/app/scripts/start-background; usage-poller | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/UsagePoller.java; usage-window | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/ui/UsageWindow.java
- **Depende de:** TASK-016, TASK-018, TASK-019, TASK-023
- **Alcance:** verificar Gemini en la aplicación en segundo plano con el `agy` real: arranque, no persistencia entre ejecuciones, receptividad mientras corre `agy` y consumo de CPU y heap con Gemini activo.
- **Hecho cuando:** los criterios de REQ-001, REQ-010, NFR-001 y NFR-004 se cumplen y las mediciones quedan anotadas.
- **Pruebas automáticas:** no aplica: la tarea mide el sistema completo con el ejecutable real; las pruebas automáticas están en las tareas de componente.
- **Prueba manual:**
  1. Lanzar la aplicación con `start-background` y comprobar que la terminal se libera y el proceso sigue activo tras el arranque, con la interfaz disponible (REQ-001).
  2. Durante cinco minutos con Gemini activo, medir la CPU media en un núcleo (inferior al 5 %) con el Administrador de tareas y el heap de la JVM (inferior a 256 MB) con una herramienta como `jcmd`, sin contar el proceso `agy` (NFR-004).
  3. Durante una consulta de `agy` que dure más de 8 s, interactuar con la ventana de uso y comprobar que responde en menos de 1 s (NFR-001).
  4. Cerrar la aplicación, volver a abrirla y comprobar que el gráfico de Gemini empieza vacío (REQ-010).

## Cobertura

| Requisito | Tareas |
|---|---|
| REQ-001 | TASK-013, TASK-014, TASK-016, TASK-024 |
| REQ-002 | TASK-001, TASK-008, TASK-009, TASK-013, TASK-020 |
| REQ-003 | TASK-010, TASK-017 |
| REQ-004 | TASK-006, TASK-010, TASK-012, TASK-017 |
| REQ-005 | TASK-010, TASK-011, TASK-017 |
| REQ-006 | TASK-017 |
| REQ-007 | TASK-005, TASK-007, TASK-014, TASK-016 |
| REQ-008 | TASK-015, TASK-017 |
| REQ-009 | TASK-012, TASK-019 |
| REQ-010 | TASK-012, TASK-024 |
| REQ-011 | TASK-007, TASK-008, TASK-009, TASK-013, TASK-014, TASK-015, TASK-018, TASK-020 |
| REQ-012 | TASK-010, TASK-017 |
| REQ-013 | TASK-003, TASK-004, TASK-007, TASK-013, TASK-014, TASK-016, TASK-018, TASK-021, TASK-022, TASK-023, TASK-024 |
| NFR-001 | TASK-003, TASK-008, TASK-024 |
| NFR-002 | TASK-003, TASK-005, TASK-014, TASK-016 |
| NFR-003 | TASK-003, TASK-008, TASK-014, TASK-021 |
| NFR-004 | TASK-024 |
