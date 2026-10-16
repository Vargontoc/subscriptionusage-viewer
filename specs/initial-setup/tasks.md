# Tareas: initial-setup

Estado: aprobado

## Resumen

Se divide la estructura base ejecutable en una tarea de andamiaje del proyecto Spring Boot non-web, una por cada uno de los seis componentes nuevos del diseño y una tarea final de verificación del NFR de tiempo de inicio. El orden sigue el flujo de arranque: configuración, contexto, coordinador de ciclo de vida, icono de bandeja, menú contextual y notificación de listo.

## Tareas

### [x] TASK-001 Crear el esqueleto del proyecto Spring Boot non-web

- **Cubre:** REQ-001, NFR-002, DEC-001
- **Componente:** application-launcher | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java
- **Depende de:** -
- **Alcance:** Crear la estructura mínima del proyecto (fichero de build, clase con método `main` que arranque Spring Boot en modo `WebApplicationType.NONE`) y declarar Java 21 como versión de compilación y ejecución.
- **Hecho cuando:** existe un módulo compilable que, al ejecutarse, lanza el método `main` sin errores de configuración ni de versión de Java, según el criterio de aceptación de REQ-001.
- **Pruebas automáticas:**
  - Caso feliz: el comando de build compila sin advertencias de deprecación por versión de Java y la tarea de empaquetado genera el artefacto ejecutable.
  - Caso de error o límite: si se fuerza una versión de Java distinta a 21, la build falla con un mensaje claro de incompatibilidad.
- **Prueba manual:**
  1. Ejecutar el comando de build sobre el proyecto.
  2. Comprobar que la build finaliza sin errores y el artefacto queda generado.

### [x] TASK-002 Configurar el contenedor Spring Boot como aplicación non-web

- **Cubre:** REQ-001, REQ-005, DEC-001
- **Componente:** application-context | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/context/ApplicationContextConfig.java
- **Depende de:** TASK-001
- **Alcance:** Configurar `application-context` para que se inicie como aplicación non-web (sin servidor web embebido) y permanezca registrado en el contexto de Spring hasta que se le ordene cerrarse.
- **Hecho cuando:** al lanzar el ejecutable, el contexto de Spring Boot se inicializa, queda accesible para el resto de componentes y no expone ningún puerto de red.
- **Pruebas automáticas:**
  - Caso feliz: un test de contexto de Spring levanta `application-context` y obtiene los beans principales sin excepción.
  - Caso de error o límite: si se intenta exponer un puerto HTTP, la aplicación no abre ningún socket de red y los logs reflejan la configuración non-web.
- **Prueba manual:**
  1. Ejecutar el artefacto de TASK-001.
  2. Comprobar que no aparece un mensaje de "Tomcat started on port" ni equivalente y que el proceso no queda a la escucha de conexiones.

### [x] TASK-003 Cargar configuración externa con valores por defecto

- **Cubre:** REQ-004, DEC-001
- **Componente:** configuration-provider | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/config
- **Depende de:** TASK-002
- **Alcance:** Hacer que `configuration-provider` lea un fichero de configuración externo opcional y exponga sus propiedades al resto de componentes a través de `application-context`, aplicando valores por defecto documentados si el fichero no existe.
- **Hecho cuando:** existe un fichero de configuración externo de ejemplo y, al iniciarse sin él, la aplicación registra al menos una propiedad con su valor por defecto.
- **Pruebas automáticas:**
  - Caso feliz: con un fichero de configuración presente que define una propiedad, un test recupera esa propiedad desde el `Environment` de Spring con el valor indicado.
  - Caso de error o límite: sin fichero de configuración, un test recupera esa misma propiedad y obtiene el valor por defecto documentado.
- **Prueba manual:**
  1. Ejecutar el artefacto sin fichero de configuración externo.
  2. Comprobar en los logs que las propiedades aparecen con su valor por defecto.
  3. Repetir la ejecución colocando un fichero externo con un valor distinto.
  4. Comprobar en los logs que la propiedad adopta el valor del fichero.

### [x] TASK-004 Mantener el proceso vivo y orquestar el cierre ordenado

- **Cubre:** REQ-001, REQ-003, DEC-003, DEC-004
- **Componente:** lifecycle-coordinator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/lifecycle/LifecycleCoordinator.java
- **Depende de:** TASK-002
- **Alcance:** Implementar `lifecycle-coordinator` para que bloquee el hilo principal mientras la aplicación está activa y, al recibir la señal de cierre, cierre `application-context` de forma ordenada y libere los recursos gestionados.
- **Hecho cuando:** el proceso permanece en segundo plano tras el arranque y, al invocarse el cierre, termina devolviendo un código de salida limpio en menos de 5 segundos.
- **Pruebas automáticas:**
  - Caso feliz: un test que solicita el apagado al coordinador verifica que `application-context` queda cerrado y el método de espera retorna.
  - Caso de error o límite: una solicitud de cierre durante la inicialización del contexto no produce interbloqueo y el proceso termina con un código de salida distinto de cero.
- **Prueba manual:**
  1. Ejecutar el artefacto y comprobar que el proceso sigue vivo tras unos segundos (no termina al acabar el arranque).
  2. Provocar el cierre desde el mecanismo que conectará el menú de bandeja (por ejemplo, llamando al método de apagado mediante reflexión o punto de prueba).
  3. Comprobar que el proceso termina en menos de 5 segundos y no queda ningún proceso hijo huérfano.

### [x] TASK-005 Mostrar el icono de la aplicación en la bandeja del sistema

- **Cubre:** REQ-002, DEC-002
- **Componente:** tray-indicator | desktop/app/src/main/java/es/vargontoc/subscriptionviewer/tray/TrayIndicator.java
- **Depende de:** TASK-004
- **Alcance:** Hacer que `tray-indicator` inicialice un icono en `java.awt.SystemTray` cuando el sistema lo soporte, registre un shutdown hook que lo libere en caso de cierre abrupto y, si la bandeja no está disponible, emita un aviso sin abortar la aplicación.
- **Hecho cuando:** tras el arranque con soporte de bandeja, aparece un icono representando la aplicación y el icono se elimina de la bandeja al cerrarse la aplicación.
- **Pruebas automáticas:**
  - Caso feliz: un test de integración en un entorno con `SystemTray` soportado verifica que el icono queda añadido a la bandeja tras el arranque.
  - Caso de error o límite: un test que simula `SystemTray.isSupported() == false` verifica que la aplicación no aborta y registra el aviso correspondiente en los logs.
- **Prueba manual:**
  1. Ejecutar el artefacto en un sistema operativo con bandeja del sistema.
  2. Comprobar que aparece un icono de la aplicación en la bandeja.
  3. Cerrar la aplicación y comprobar que el icono desaparece de la bandeja.

### [ ] TASK-006 Añadir el menú contextual con la opción de cierre

- **Cubre:** REQ-002, REQ-003, DEC-004
- **Componente:** tray-menu | por definir (analista técnico)
- **Depende de:** TASK-005
- **Alcance:** Implementar `tray-menu` como menú contextual del icono de bandeja con, al menos, una opción de cierre que delegue en `lifecycle-coordinator` para iniciar el apagado ordenado.
- **Hecho cuando:** al hacer clic derecho sobre el icono de bandeja se muestra la opción de cierre y al seleccionarla la aplicación termina de forma ordenada.
- **Pruebas automáticas:**
  - Caso feliz: un test construye el menú y verifica que contiene un elemento cuyo texto es el de la opción de cierre.
  - Caso de error o límite: un test que invoca la acción de cierre del menú verifica que se inicia la secuencia de apagado de `lifecycle-coordinator`.
- **Prueba manual:**
  1. Ejecutar el artefacto y localizar el icono en la bandeja del sistema.
  2. Hacer clic derecho sobre el icono y comprobar que aparece una opción de cierre.
  3. Seleccionar la opción de cierre y comprobar que la aplicación termina sin dejar procesos huérfanos.

### [ ] TASK-007 Registrar el mensaje de aplicación lista al finalizar el arranque

- **Cubre:** REQ-005, DEC-001
- **Componente:** startup-reporter | por definir (analista técnico)
- **Depende de:** TASK-005
- **Alcance:** Hacer que `startup-reporter` escriba un mensaje de log indicando que la aplicación está lista una vez completados el contexto Spring, el coordinador de ciclo de vida y el icono de bandeja.
- **Hecho cuando:** tras un arranque correcto, los logs contienen un mensaje inequívoco de que la aplicación está lista.
- **Pruebas automáticas:**
  - Caso feliz: un test que arranca `startup-reporter` con un appender de captura verifica que el mensaje de listo aparece en el log.
  - Caso de error o límite (propuesto): si el icono de bandeja no se inicializa, el mensaje de listo no se emite hasta que `startup-reporter` reciba la señal correspondiente, evitando falsos positivos de aplicación lista.
- **Prueba manual:**
  1. Ejecutar el artefacto y abrir el fichero o salida de log configurada.
  2. Comprobar que aparece un mensaje que indica que la aplicación está lista.
  3. Cerrar la aplicación y comprobar que se registra el mensaje de cierre.

### [ ] TASK-008 Verificar el tiempo de inicio y la compatibilidad con Java 21

- **Cubre:** NFR-001, NFR-002
- **Componente:** application-context, lifecycle-coordinator | por definir (analista técnico)
- **Depende de:** TASK-007
- **Alcance:** Medir de extremo a extremo el tiempo entre el lanzamiento del ejecutable y la aparición del mensaje de listo, y comprobar que la ejecución se realiza sobre Java 21 sin errores de versión.
- **Hecho cuando:** existe un mecanismo repetible que cronometra el arranque y, en el entorno de desarrollo local, el tiempo medido es inferior a 30 segundos.
- **Pruebas automáticas:**
  - Caso feliz: un test automatizado ejecuta el arranque en un proceso hijo y mide el tiempo transcurrido hasta el mensaje de listo, verificando que es inferior a 30 segundos.
  - Caso de error o límite (propuesto): un test que simula una carga lenta del contexto (por ejemplo, introduciendo un `BeanFactoryPostProcessor` que retrase la inicialización) hace fallar la comprobación si el tiempo supera los 30 segundos.
- **Prueba manual:**
  1. Ejecutar el artefacto con `java -version` mostrando Java 21.
  2. Cronometrar con un reloj externo el tiempo entre el comando de arranque y la aparición del mensaje de listo en los logs.
  3. Comprobar que el tiempo medido es inferior a 30 segundos.

## Cobertura

| Requisito | Tareas |
|---|---|
| REQ-001 | TASK-001, TASK-002, TASK-004 |
| REQ-002 | TASK-005, TASK-006 |
| REQ-003 | TASK-004, TASK-006 |
| REQ-004 | TASK-003 |
| REQ-005 | TASK-002, TASK-007 |
| NFR-001 | TASK-008 |
| NFR-002 | TASK-001, TASK-008 |

| Decisión | Tareas |
|---|---|
| DEC-001 | TASK-001, TASK-002, TASK-003, TASK-007 |
| DEC-002 | TASK-005 |
| DEC-003 | TASK-004 |
| DEC-004 | TASK-004, TASK-006 |