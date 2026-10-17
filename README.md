# Subscription Usage Viewer

<!-- sdd:start -->
<!-- Bloque generado por el agente docs. Se reescribe entero: escribe fuera de las marcas. -->

## Qué es

Subscription Usage Viewer es una pequeña aplicación de escritorio que muestra al usuario, en tiempo real, las suscripciones a distintos proveedores de AI. Funciona en un solo equipo, pensado para uso local de una única persona.

## Qué incluye y qué no

**Incluye:**
- Una aplicación de escritorio que arranca por sí sola y permanece en segundo plano mientras el usuario la necesite.
- Una ventana con el uso de la suscripción a Claude: el porcentaje consumido en la sesión de 5 horas y en la semana, y el tiempo que falta para que cada límite se reinicie.
- Un gráfico con la evolución de ese consumo mientras la aplicación está abierta.
- Lectura de los datos de uso que Claude Code entrega durante su funcionamiento, sin pedir credenciales al usuario.
- Una tarjeta con el uso de la suscripción a OpenCode Go: el porcentaje consumido en las ventanas de 5 horas, semanal y mensual, y el tiempo que falta para cada reinicio.

**No incluye:**
- Uso simultáneo por varias personas ni funcionamiento en servidores: es una aplicación monousuario de desarrollo local.

## Con qué está hecho

- Java: el lenguaje con el que está escrita la aplicación.
- Spring Boot: la base sobre la que se construye y organiza la aplicación.

## Cómo está organizado

Es una sola aplicación de escritorio, formada por estas partes:
- Un icono en la bandeja del sistema, con un menú para abrir la ventana de uso o cerrar la aplicación.
- Una ventana que muestra el consumo de cada límite de Claude, el tiempo que falta para su reinicio y la evolución del consumo.
- Un pequeño script que Claude Code ejecuta y que guarda en un fichero local los datos de uso que recibe. La aplicación lee ese fichero.
- Una estructura preparada para añadir otros proveedores de AI en el futuro, cada uno mostrado por separado.
- Un lector que consulta los datos de Claude cada 10 segundos y los de OpenCode cada minuto, en segundo plano y sin bloquear la ventana." Y añadir: "Un lanzador que deja la aplicación en segundo plano y devuelve el control a la terminal.

## Qué puede hacer hoy

### Initial setup

La aplicación arranca de forma autónoma y se queda activa en segundo plano, mostrando un icono en la bandeja del sistema. Haciendo clic en el icono aparece una opción para cerrar la aplicación, que se detiene de forma ordenada sin dejar nada a medias. Además, admite un fichero de configuración externo opcional: si falta, usa valores por defecto, y cuando el arranque termina deja un mensaje en el registro indicando que está lista.

### claude-usage-monitor

Muestra cuánto de la suscripción a Claude se ha consumido en la sesión de 5 horas y en la semana, cuánto falta para que cada límite se reinicie y cómo ha evolucionado el consumo mientras la aplicación está abierta. Los datos se leen de lo que Claude Code comunica durante su uso y se refrescan solos cada 10 segundos. Si no hay datos vigentes, la ventana de Claude se oculta sin mostrar errores.

### opencode-usage-monitor

Muestra el consumo de la suscripción a OpenCode Go en las ventanas de 5 horas, semanal y mensual, con el tiempo que falta para cada reinicio. Se actualiza sola cada minuto y el gráfico recoge su evolución mientras la aplicación está abierta. La clave de acceso se configura en el equipo y no en la ventana; si falta o no es válida, la ventana lo indica, y si no hay conexión muestra los últimos datos marcados como desactualizados.
<!-- sdd:end -->

## Guía de uso

### Requisitos

- Windows con entorno gráfico (la aplicación vive en la bandeja del sistema).
- Java 21 y Maven 3.9+ (solo para compilar).
- Python 3 y Claude Code, para alimentar los datos de uso (ver [Alimentar los datos](#alimentar-los-datos)).

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
