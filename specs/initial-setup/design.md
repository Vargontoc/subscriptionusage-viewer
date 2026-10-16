# Diseño: initial-setup

Estado: aprobado

## Resumen

La aplicación se implementará como un proceso Java de escritorio basado en Spring Boot en modo non-web, manteniéndose activo mediante un coordinador de ciclo de vida mientras expone un icono en la bandeja del sistema con un menú contextual mínimo que permite cerrarla ordenadamente. La configuración se externaliza aprovechando el mecanismo nativo de Spring Boot y el inicio se notifica mediante el registro de logs estándar.

## Supuestos sobre el sistema actual

- No existe código previo: la feature construye la estructura base ejecutable desde cero.
- El entorno de ejecución soporta `java.awt.SystemTray` (Windows, macOS o Linux con bandeja del sistema).
- No hay persistencia, autenticación ni integraciones con terceros en esta fase.

## Componentes

| Componente | Responsabilidad | Existente o nuevo |
|---|---|---|
| application-launcher | Punto de entrada que arranca el contexto de Spring Boot. | nuevo |
| application-context | Contenedor IoC de Spring Boot configurado como aplicación non-web. | nuevo |
| lifecycle-coordinator | Mantiene el proceso vivo y orquesta el cierre ordenado. | nuevo |
| configuration-provider | Resuelve propiedades desde fichero externo o valores por defecto. | nuevo |
| tray-indicator | Crea y muestra el icono de la aplicación en la bandeja del sistema. | nuevo |
| tray-menu | Menú contextual asociado al icono con la opción de cierre. | nuevo |
| startup-reporter | Emite el mensaje de log cuando la aplicación ha finalizado el arranque. | nuevo |

## Flujo

1. El usuario ejecuta `application-launcher`.
2. `application-launcher` inicia `application-context` en modo non-web.
3. `configuration-provider` carga la configuración externalizada o los valores por defecto.
4. `lifecycle-coordinator` se registra y mantiene el proceso en ejecución.
5. `tray-indicator` inicializa el icono en la bandeja del sistema.
6. `tray-menu` se asocia al icono con la opción de cerrar la aplicación.
7. `startup-reporter` escribe el mensaje indicando que la aplicación está lista.
8. El usuario abre `tray-menu` desde el icono y selecciona la opción de cierre.
9. `lifecycle-coordinator` libera el icono de bandeja, cierra `application-context` y termina el proceso.

## Interfaces entre componentes

| Origen | Destino | Qué se comunica | Protocolo |
|---|---|---|---|
| application-launcher | application-context | Solicitud de arranque del contenedor Spring Boot. | invocación interna |
| lifecycle-coordinator | application-context | Señal de cierre ordenado del contenedor. | invocación interna |
| tray-menu | lifecycle-coordinator | Petición de iniciar el apagado de la aplicación. | invocación interna |
| configuration-provider | application-context | Propiedades resueltas para el resto de componentes. | Spring Environment |
| tray-indicator | tray-menu | Asociación del menú contextual al icono de bandeja. | invocación interna |
| startup-reporter | application-context | Mensaje de log de aplicación lista. | SLF4J/Logback |

## Modelo de datos lógico

Ninguno: la feature no persiste información.

## Decisiones

### DEC-001 Tipo de aplicación Spring Boot

- **Decisión:** Usar Spring Boot en modo non-web (`WebApplicationType.NONE`).
- **Alternativas descartadas:**
  - Aplicación web con servlet embebido: descartada porque los requisitos excluyen endpoints HTTP y añadiría un puerto innecesario.
  - Aplicación reactiva con WebFlux: descartada por complejidad adicional sin beneficio para un proceso de escritorio.
- **Cubre:** REQ-001, REQ-003, NFR-001, NFR-002

### DEC-002 Biblioteca para la bandeja del sistema

- **Decisión:** Utilizar `java.awt.SystemTray` incluido en el JDK.
- **Alternativas descartadas:**
  - Bibliotecas de terceros como dorkbox SystemTray o FXTrayIcon: descartadas para minimizar dependencias externas en la fase inicial.
  - JavaFX: descartada porque requiere módulos y configuración adicionales no justificados para un menú contextual mínimo.
- **Cubre:** REQ-002

### DEC-003 Mecanismo para mantener el proceso vivo

- **Decisión:** El `lifecycle-coordinator` bloquea el hilo principal mediante una primitiva de sincronización (por ejemplo, `CountDownLatch`) hasta recibir la señal de cierre.
- **Alternativas descartadas:**
  - Bucle infinito con `Thread.sleep`: descartado por menor claridad y control del ciclo de vida.
  - Depender únicamente del contexto Spring Boot sin bloqueo explícito: descartado porque el proceso terminaría inmediatamente al finalizar el arranque.
- **Cubre:** REQ-001, REQ-003

### DEC-004 Estrategia de cierre de la aplicación

- **Decisión:** La opción de cierre del `tray-menu` invoca al `lifecycle-coordinator`, que libera el icono de bandeja y cierra el `application-context` de Spring Boot.
- **Alternativas descartadas:**
  - Cierre únicamente por señal del sistema (Ctrl+C): descartado porque REQ-003 exige cerrar desde el indicador visual.
  - `System.exit(0)` directo: descartado porque no permite liberar recursos gestionados por Spring Boot.
- **Cubre:** REQ-002, REQ-003

## Riesgos

- `java.awt.SystemTray` puede no estar disponible en entornos Linux sin bandeja o en macOS con restricciones de accesibilidad. **Mitigación:** detectar soporte en tiempo de arranque y, si no está disponible, registrar un aviso y permitir el cierre mediante señal del sistema como fallback.
- Un cierre abrupto puede dejar el icono en la bandeja hasta que el sistema lo refresque. **Mitigación:** registrar un shutdown hook que invoque la liberación del icono y asegurar que `tray-indicator` exponga una operación de limpieza.
- El inicio puede superar los 30 segundos si se añaden dependencias pesadas más adelante. **Mitigación:** mantener el árbol de componentes mínimo en esta fase y medir el tiempo de inicio en los tests.

## Trazabilidad

| Requisito | Componentes | Decisiones |
|---|---|---|
| REQ-001 | application-launcher, application-context, lifecycle-coordinator | DEC-001, DEC-003 |
| REQ-002 | tray-indicator, tray-menu | DEC-002 |
| REQ-003 | lifecycle-coordinator, tray-menu, tray-indicator | DEC-003, DEC-004 |
| REQ-004 | configuration-provider | DEC-001 |
| REQ-005 | startup-reporter | DEC-001 |
| NFR-001 | application-context, lifecycle-coordinator | DEC-001, DEC-003 |
| NFR-002 | application-launcher, application-context | DEC-001 |

## Contratos afectados

Ninguno: la feature no expone ni consume contratos externos.

## Cambios sugeridos al mapa

- Módulo: `launcher | src/main/java/com/example/subscriptionusageviewer/launcher | Punto de entrada de la aplicación | ->`
- Módulo: `spring-context | src/main/java/com/example/subscriptionusageviewer/context | Contenedor IoC y configuración Spring Boot | launcher`
- Módulo: `lifecycle | src/main/java/com/example/subscriptionusageviewer/lifecycle | Coordinación de arranque y parada | spring-context, tray`
- Módulo: `tray | src/main/java/com/example/subscriptionusageviewer/tray | Indicador visual en bandeja del sistema | spring-context`
- Módulo: `config | src/main/java/com/example/subscriptionusageviewer/config | Carga de propiedades externalizadas | spring-context`
- Convención: `application-type: non-web`
- Convención: `config-format: application.yml`
