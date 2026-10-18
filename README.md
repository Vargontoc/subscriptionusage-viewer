
<!-- sdd:start -->
<!-- Bloque generado por el agente docs. Se reescribe entero: escribe fuera de las marcas. -->

## Qué es

Subscription Usage Viewer es una pequeña aplicación de escritorio que muestra, en tiempo real, cuánto se ha usado de las suscripciones a distintos proveedores de inteligencia artificial. Funciona en un solo equipo, pensada para una sola persona y para uso local durante el desarrollo.

## Qué incluye y qué no

**Incluye:**
- Uso en un solo equipo, para una sola persona, en un entorno de desarrollo local.

**No incluye:**
- Uso por varias personas a la vez o en servidores.

## Con qué está hecho

- Java: el lenguaje con el que está escrita la aplicación.
- Spring Boot: la base sobre la que se construye y organiza la aplicación.

## Cómo está organizado

Es una sola aplicación de escritorio, formada por estas partes:
- Un icono en la bandeja del sistema, con un menú para abrir la ventana de uso o cerrar la aplicación.
- Una ventana con el consumo de cada proveedor y su evolución mientras la aplicación está abierta.
- Un lector de los datos de Claude, que toma la información que deja un pequeño script ejecutado por Claude Code.
- Un lector de los datos de OpenCode, que consulta el programa opencode instalado en el equipo.
- Un planificador que actualiza los datos de Claude cada 10 segundos y los de OpenCode cada minuto, en segundo plano y sin bloquear la ventana.
- Un lanzador que deja la aplicación en segundo plano y devuelve el control a la terminal.
- Una estructura preparada para añadir otros proveedores de inteligencia artificial en el futuro, cada uno mostrado por separado.

## Qué puede hacer hoy

### Aplicación

La aplicación arranca sola y se queda en segundo plano, con un icono en la bandeja del sistema. Desde ese icono se puede cerrar, y el cierre se completa sin dejar nada a medias. Si existe un fichero de configuración externo, lo usa; si no existe, funciona con valores por defecto. Al terminar el arranque deja un aviso en el registro indicando que está lista.

### Claude Usage Monitor

Muestra cuánto de la suscripción a Claude se ha consumido en la sesión de 5 horas y en la semana, cuánto falta para que cada límite se reinicie y cómo ha evolucionado el consumo mientras la aplicación está abierta. Los datos proceden de Claude Code mientras se usa, sin pedir credenciales, y se actualizan solos cada 10 segundos. Si no hay datos vigentes, la tarjeta de Claude se oculta sin mostrar errores.

### Opencode Usage Monitor

Muestra el consumo de la suscripción a OpenCode Go por modelo y en total, en las ventanas de 5 horas, semana y mes, con la hora de la última actualización y la evolución mientras la aplicación está abierta. Obtiene los datos con el programa opencode instalado en el equipo, sin pedir ninguna clave, y los refresca cada minuto. Si opencode falla, conserva los últimos datos válidos sin mostrar errores.
<!-- sdd:end -->

## Guía de uso

### Requisitos

- Windows con entorno gráfico (la aplicación vive en la bandeja del sistema).
- Java 21 y Maven 3.9+ (solo para compilar).

### Compilar

Desde la raíz del repositorio:

```powershell
mvn -f desktop/app/pom.xml clean package
```

Genera `desktop/app/target/app-0.0.1-SNAPSHOT.jar`, un JAR ejecutable con todo incluido. Para saltarte los tests: añade `-DskipTests`.

### Lanzar

Con consola (verás el registro; `Ctrl+C` la cierra):

```powershell
java -jar desktop/app/target/app-0.0.1-SNAPSHOT.jar
```

Sin consola, en segundo plano:

```powershell
javaw -jar desktop/app/target/app-0.0.1-SNAPSHOT.jar
```

Al arrancar aparece un icono en la bandeja del sistema (puede quedar oculto en el desbordamiento de iconos, junto al reloj). Desde su menú puedes abrir la ventana de uso o cerrar la aplicación.

### Configuración opcional

Sin configuración usa valores por defecto. Para cambiarlos, crea un `config/application.yml` en el directorio desde el que lanzas la aplicación (Spring Boot lo carga solo) con solo las propiedades que quieras sobrescribir. Por ejemplo:

```yaml
app:
  usage-poll-interval: "30s"
```

También puedes pasarlas por línea de comandos, p. ej. `--app.usage-poll-interval=30s`.

| Propiedad | Por defecto | Qué hace |
|---|---|---|
| `app.name` | `Subscription Usage Viewer` | Nombre mostrado. |
| `app.tray-icon-path` | `icons/app-icon.png` | Icono de la bandeja. |
| `app.usage-state-file` | `~/.claude/statusline-state.json` | Fichero del que se leen los datos de uso. |
| `app.usage-poll-interval` | `10s` | Frecuencia de lectura. |

### Alimentar los datos

La aplicación no pide credenciales: lee el fichero `~/.claude/statusline-state.json`, que debe escribir un pequeño script (hook) ejecutado por la *status line* de Claude Code, configurada en `~/.claude/settings.json`:

```json
"statusLine": {
  "type": "command",
  "command": "python <ruta-absoluta-al-hook>"
}
```

El hook debe guardar el bloque `rate_limits` que Claude Code recibe por stdin. El comportamiento esperado está en [docs/contracts/stdio/desktop/app/statusline.md](docs/contracts/stdio/desktop/app/statusline.md) y el formato del fichero en [docs/contracts/file-json/desktop/app/statusline-state.md](docs/contracts/file-json/desktop/app/statusline-state.md). El hook no forma parte de este repositorio.

Hasta que Claude Code escriba datos vigentes (basta con enviar un mensaje en una sesión), la ventana de Claude permanece oculta.
