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

**No incluye:**
- Uso simultáneo por varias personas ni funcionamiento en servidores: es una aplicación monousuario de desarrollo local.

## Con qué está hecho

- Java: el lenguaje con el que está escrita la aplicación.
- Spring Boot: la base sobre la que se construye y organiza la aplicación.

## Cómo está organizado

Es una sola aplicación de escritorio, formada por estas partes:
- Un icono en la bandeja del sistema, con un menú para abrir la ventana de uso o cerrar la aplicación.
- Una ventana que muestra el consumo de cada límite de Claude, el tiempo que falta para su reinicio y la evolución del consumo.
- Un lector que consulta los datos cada 10 segundos en segundo plano, sin bloquear la ventana.
- Un pequeño script que Claude Code ejecuta y que guarda en un fichero local los datos de uso que recibe. La aplicación lee ese fichero.
- Una estructura preparada para añadir otros proveedores de AI en el futuro, cada uno mostrado por separado.

## Qué puede hacer hoy

### Initial setup

La aplicación arranca de forma autónoma y se queda activa en segundo plano, mostrando un icono en la bandeja del sistema. Haciendo clic en el icono aparece una opción para cerrar la aplicación, que se detiene de forma ordenada sin dejar nada a medias. Además, admite un fichero de configuración externo opcional: si falta, usa valores por defecto, y cuando el arranque termina deja un mensaje en el registro indicando que está lista.

### claude-usage-monitor

Muestra cuánto de la suscripción a Claude se ha consumido en la sesión de 5 horas y en la semana, cuánto falta para que cada límite se reinicie y cómo ha evolucionado el consumo mientras la aplicación está abierta. Los datos se leen de lo que Claude Code comunica durante su uso y se refrescan solos cada 10 segundos. Si no hay datos vigentes, la ventana de Claude se oculta sin mostrar errores.
<!-- sdd:end -->
