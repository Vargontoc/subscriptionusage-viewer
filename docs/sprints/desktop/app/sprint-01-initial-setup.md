# Sprint 01: initial-setup - desktop/app

Estado: aprobado

## Alcance

- Tareas: TASK-001, TASK-002, TASK-003, TASK-004, TASK-005, TASK-006, TASK-007, TASK-008 (de `specs/initial-setup/tasks.md`)
- Dependencias externas: Ninguna.

## Enfoque técnico

Aplicación de escritorio Spring Boot en modo non-web (`WebApplicationType.NONE`), mantenida viva por un `lifecycle-coordinator` basado en una primitiva de sincronización (`CountDownLatch`) y con interfaz mínima en la bandeja del sistema mediante `java.awt.SystemTray`/`TrayIcon`. La configuración se externaliza aprovechando el mecanismo nativo de Spring Boot (`application.yml` interno más ubicación externa opcional). El módulo `desktop/app` agrupa internamente los paquetes `launcher`, `context`, `config`, `lifecycle`, `tray` y `startup`; no se crean submódulos Maven. Las pruebas usan JUnit 5, AssertJ, Mockito y `spring-boot-starter-test`; el NFR de tiempo de inicio se verifica con un test de integración que lanza el proceso hijo.

## Cambios por tarea

### TASK-001 Crear el esqueleto del proyecto Spring Boot non-web

- **Ficheros:**
  - `desktop/app/pom.xml` (nuevo): declarar Java 21, parent `spring-boot-starter-parent`, `spring-boot-maven-plugin`, packaging `jar` y propiedad `java.version=21`.
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java` (nuevo): clase con `public static void main(String[] args)` que arranque Spring Boot mediante `SpringApplicationBuilder` sobre la configuración del módulo.
  - `(propuesta)` `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/context/ApplicationContextConfig.java` (nuevo): clase `@Configuration` raíz del contexto.
- **Enfoque:** usar `SpringApplicationBuilder` explícito para controlar el tipo de aplicación. Declarar `maven.compiler.release=21` (o `source`/`target` 21) y forzar `JAVA_HOME` correcto en la build.
- **Pruebas automáticas:**
  - `ApplicationLauncherBuildTest`: ejecuta `mvn -f desktop/app/pom.xml clean package -DskipTests` y verifica que el JAR se genera y que no hay advertencias de versión de Java.
  - Caso de error o límite: si se fuerza `JAVA_HOME` a una versión distinta de 21, la build falla con un mensaje claro de incompatibilidad.
  - Comando: `mvn -f desktop/app/pom.xml clean verify`.
- **Prueba manual:**
  1. Ejecutar `mvn -f desktop/app/pom.xml clean package`.
  2. Comprobar que el artefacto se genera en `desktop/app/target/`.
  3. Ejecutar `java -jar desktop/app/target/app-*.jar` y verificar que no hay errores de versión.

### TASK-002 Configurar el contenedor Spring Boot como aplicación non-web

- **Ficheros:**
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java` (modificar): construir la aplicación con `.web(WebApplicationType.NONE)`.
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/context/ApplicationContextConfig.java` (modificar): anotar como `@Configuration` y, opcionalmente, deshabilitar autoconfiguración web.
  - `desktop/app/src/main/resources/application.yml` (nuevo): `spring.main.web-application-type: none` y `spring.main.banner-mode: off`.
- **Enfoque:** configurar el tipo non-web tanto por código como por propiedad; no incluir `spring-boot-starter-web` en las dependencias para evitar Tomcat embebido.
- **Pruebas automáticas:**
  - `ApplicationContextNonWebTest`: test de contexto de Spring (`@SpringBootTest`) que verifica que `ConfigurableApplicationContext` está activo y que no existe ningún bean de servidor web (por ejemplo, `TomcatServletWebServerFactory`).
  - Caso de error o límite: forzar `spring.main.web-application-type=servlet` y comprobar que no se abre socket de red ni aparece el log de servidor iniciado.
  - Comando: `mvn -f desktop/app/pom.xml test -Dtest=ApplicationContextNonWebTest`.
- **Prueba manual:**
  1. Ejecutar el artefacto.
  2. Comprobar que no aparece "Tomcat started on port" ni equivalente.
  3. Verificar con `netstat`/`lsof` que no hay puertos en escucha.

### TASK-003 Cargar configuración externa con valores por defecto

- **Ficheros:**
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/config/AppProperties.java` (nuevo): `@ConfigurationProperties(prefix = "app")` con propiedades documentadas y valores por defecto (por ejemplo, `app.name`, `app.tray-icon-path`).
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/config/ConfigurationProvider.java` (nuevo): bean `@Component` que inyecte `AppProperties`/`Environment` y exponga las propiedades al resto de componentes.
  - `desktop/app/src/main/resources/application.yml` (modificar): valores por defecto documentados.
  - `(propuesta)` `config/application.yml` (nuevo, fuera del classpath): ejemplo de fichero externo.
- **Enfoque:** aprovechar `@EnableConfigurationProperties(AppProperties.class)` y el mecanismo de ubicaciones de Spring Boot (`--spring.config.location` o fichero junto al JAR). Los valores por defecto residen en `application.yml` empaquetado.
- **Pruebas automáticas:**
  - `ConfigurationProviderTest`: con `ApplicationContextRunner`, verifica que con un `application-external.yml` que defina `app.name=External` se lee ese valor; sin fichero externo se obtiene el valor por defecto documentado.
  - Comando: `mvn -f desktop/app/pom.xml test -Dtest=ConfigurationProviderTest`.
- **Prueba manual:**
  1. Ejecutar el artefacto sin fichero de configuración externo y comprobar en los logs el valor por defecto.
  2. Colocar un `application.yml` externo con valores distintos y reejecutar.
  3. Comprobar que las propiedades adoptan los valores del fichero.

### TASK-004 Mantener el proceso vivo y orquestar el cierre ordenado

- **Ficheros:**
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/lifecycle/LifecycleCoordinator.java` (nuevo): bean `@Component` con un `CountDownLatch` de cierre, métodos `waitForShutdown()` y `requestShutdown()`, e integración con `SmartLifecycle`/`DisposableBean` para cerrar el contexto.
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/launcher/ApplicationLauncher.java` (modificar): tras arrancar el contexto, invocar `lifecycleCoordinator.waitForShutdown()` y, al retornar, cerrar el contexto de forma ordenada.
- **Enfoque:** el hilo principal queda bloqueado hasta la señal de cierre. Al recibirla se libera el latch, se invoca `context.close()` y se termina el proceso con código de salida limpio. Manejar interrupciones y evitar `System.exit` brusco.
- **Pruebas automáticas:**
  - `LifecycleCoordinatorTest`: inyecta el coordinador, llama a `requestShutdown()` desde otro hilo y verifica que `waitForShutdown()` retorna y que `ConfigurableApplicationContext.isActive()` pasa a `false`.
  - Caso de error o límite: simular una solicitud de cierre durante la inicialización del contexto y verificar que no se produce interbloqueo y que el proceso termina con código de salida distinto de cero.
  - Comando: `mvn -f desktop/app/pom.xml test -Dtest=LifecycleCoordinatorTest`.
- **Prueba manual:**
  1. Ejecutar el artefacto y comprobar que el proceso sigue vivo tras unos segundos.
  2. Provocar el cierre desde el mecanismo que conectará el menú de bandeja (por ejemplo, invocando el método de apagado mediante reflexión o punto de prueba).
  3. Comprobar que el proceso termina en menos de 5 segundos y sin procesos hijo huérfanos.

### TASK-005 Mostrar el icono de la aplicación en la bandeja del sistema

- **Ficheros:**
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/tray/TrayIndicator.java` (nuevo): bean `@Component` que, en `@PostConstruct` o `SmartLifecycle.start()`, verifique `SystemTray.isSupported()`, cree un `TrayIcon` con una imagen por defecto, lo añada a la bandeja y exponga `removeIcon()`.
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/tray/TrayIconFactory.java` (nuevo, propuesta): encapsula la carga de la imagen y la creación del `TrayIcon`.
  - `desktop/app/src/main/resources/icons/app-icon.png` (nuevo): icono por defecto (placeholder si no existe recurso gráfico).
- **Enfoque:** usar `java.awt.SystemTray` y `TrayIcon` del JDK. Registrar un shutdown hook que invoque `removeIcon()`. Si la bandeja no está disponible, registrar un aviso y continuar sin abortar.
- **Pruebas automáticas:**
  - `TrayIndicatorSupportedTest`: test condicional que, si `SystemTray.isSupported()` es true, verifica que el icono se añade a la bandeja tras el arranque.
  - `TrayIndicatorUnsupportedTest`: con un envoltorio inyectable o mediante reflexión, simula `SystemTray.isSupported() == false` y verifica que la aplicación no aborta y que se registra el aviso correspondiente.
  - Comando: `mvn -f desktop/app/pom.xml test -Dtest=TrayIndicator*Test`.
- **Prueba manual:**
  1. Ejecutar el artefacto en un sistema operativo con bandeja del sistema.
  2. Comprobar que aparece un icono de la aplicación en la bandeja.
  3. Cerrar la aplicación y comprobar que el icono desaparece.

### TASK-006 Añadir el menú contextual con la opción de cierre

- **Ficheros:**
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/tray/TrayMenu.java` (nuevo): construye un `PopupMenu` de AWT con al menos un `MenuItem` de cierre; al activarse, delega en `LifecycleCoordinator.requestShutdown()`.
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/tray/TrayIndicator.java` (modificar): asociar `TrayMenu.getPopupMenu()` al `TrayIcon`.
- **Enfoque:** mantener la estrategia de cierre centralizada en `LifecycleCoordinator`. Opcionalmente externalizar los textos del menú a un `messages.properties`.
- **Pruebas automáticas:**
  - `TrayMenuTest`: construye el menú y verifica que contiene un `MenuItem` cuyo texto corresponde a la opción de cierre.
  - Caso de error o límite: invoca la acción del ítem de cierre y verifica que se llama a `LifecycleCoordinator.requestShutdown()` (mediante un mock).
  - Comando: `mvn -f desktop/app/pom.xml test -Dtest=TrayMenuTest`.
- **Prueba manual:**
  1. Ejecutar el artefacto y localizar el icono en la bandeja del sistema.
  2. Hacer clic derecho sobre el icono y comprobar que aparece una opción de cierre.
  3. Seleccionar la opción de cierre y comprobar que la aplicación termina sin procesos huérfanos.

### TASK-007 Registrar el mensaje de aplicación lista al finalizar el arranque

- **Ficheros:**
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/startup/StartupReporter.java` (nuevo): bean `@Component` que escuche `ApplicationReadyEvent` y un evento personalizado `TrayReadyEvent`; una vez completados ambos, escriba el mensaje de log "Aplicación lista".
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/tray/TrayIndicator.java` (modificar): publicar `TrayReadyEvent` cuando el icono se haya añadido correctamente a la bandeja.
  - `desktop/app/src/main/java/es/vargontoc/subscriptionviewer/startup/TrayReadyEvent.java` (nuevo): evento de Spring para indicar que la bandeja está lista.
- **Enfoque:** evitar falsos positivos esperando tanto el contexto Spring como el indicador de bandeja. Usar SLF4J/Logback para el mensaje.
- **Pruebas automáticas:**
  - `StartupReporterTest`: usa `OutputCaptureExtension` o un appender de Logback; arranca el contexto y verifica que aparece el mensaje de listo tras la señal de bandeja.
  - Caso de error o límite: si el icono de bandeja no se inicializa, verificar que el mensaje de listo no se emite hasta que `StartupReporter` reciba la señal correspondiente.
  - Comando: `mvn -f desktop/app/pom.xml test -Dtest=StartupReporterTest`.
- **Prueba manual:**
  1. Ejecutar el artefacto y abrir la salida de log configurada.
  2. Comprobar que aparece un mensaje inequívoco de que la aplicación está lista.
  3. Cerrar la aplicación y comprobar que se registra el mensaje de cierre.

### TASK-008 Verificar el tiempo de inicio y la compatibilidad con Java 21

- **Ficheros:**
  - `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/startup/StartupTimeIntegrationTest.java` (nuevo): test de integración que lance el proceso hijo con `ProcessBuilder`, capture `stdout`/`stderr` y mida el tiempo hasta el mensaje de listo.
  - `(propuesta)` `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/startup/SlowContextBeanFactoryPostProcessor.java` (nuevo): `BeanFactoryPostProcessor` de prueba que retrase la inicialización para validar el caso límite.
  - `desktop/app/src/test/java/es/vargontoc/subscriptionviewer/startup/JavaVersionCompatibilityTest.java` (nuevo): verifica que la propiedad `java.version` indica Java 21.
- **Enfoque:** medir de extremo a extremo el tiempo entre el lanzamiento de `java -jar` y la aparición del mensaje de listo en los logs. Ejecutar en un proceso hijo para no depender del entorno de test actual.
- **Pruebas automáticas:**
  - `StartupTimeIntegrationTest`: caso feliz: arranca el artefacto y verifica que el tiempo hasta el mensaje de listo es inferior a 30 segundos.
  - Caso de error o límite: con un post-processor que retrase la carga del contexto por encima de 30 segundos, el test falla por exceso de tiempo.
  - `JavaVersionCompatibilityTest`: verifica que la ejecución se realiza sobre Java 21.
  - Comando: `mvn -f desktop/app/pom.xml verify -Pintegration-tests`.
- **Prueba manual:**
  1. Ejecutar el artefacto con `java -version` mostrando Java 21.
  2. Cronometrar con un reloj externo el tiempo entre el comando de arranque y la aparición del mensaje de listo en los logs.
  3. Comprobar que el tiempo medido es inferior a 30 segundos.

## Orden de implementación

1. TASK-001
2. TASK-002
3. TASK-003
4. TASK-004
5. TASK-005
6. TASK-006
7. TASK-007
8. TASK-008

## Riesgos y dudas técnicas

- `java.awt.SystemTray` puede no estar disponible en entornos Linux sin bandeja o en CI headless. **Mitigación:** detectar soporte en tiempo de arranque, hacer los tests condicionales a `GraphicsEnvironment.isHeadless()` y permitir el cierre por señal del sistema como fallback.
- Un cierre abrupto puede dejar el icono residual en la bandeja hasta que el sistema lo refresque. **Mitigación:** registrar un shutdown hook que invoque la liberación del icono y exponer una operación de limpieza en `TrayIndicator`.
- El inicio puede superar los 30 segundos si se añaden dependencias pesadas más adelante. **Mitigación:** mantener el árbol de componentes mínimo en esta fase y medir el tiempo de inicio en los tests.
- La subdivisión interna en paquetes (`launcher`, `context`, `config`, `lifecycle`, `tray`, `startup`) es una propuesta; como `team.md` registra un único módulo `desktop|app`, el desarrollador debe mantener todos los paquetes dentro de `desktop/app/src/main/java/...` sin crear submódulos Maven.
