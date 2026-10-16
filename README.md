# Subscription Usage Viewer

<!-- sdd:start -->
<!-- Bloque generado por el agente docs. Se reescribe entero: escribe fuera de las marcas. -->

## Qué es

Subscription Usage Viewer es una pequeña aplicación de escritorio que muestra al usuario, en tiempo real, las suscripciones a distintos proveedores de AI. Funciona en un solo equipo, pensado para uso local de una única persona.

## Qué incluye y qué no

**Incluye:**
- Una aplicación de escritorio que arranca por sí sola y permanece en segundo plano mientras el usuario la necesite.

**No incluye:**
- Uso simultáneo por varias personas ni funcionamiento en servidores: es una aplicación monousuario de desarrollo local.

## Con qué está hecho

- Java: el lenguaje con el que está escrita la aplicación.
- Spring Boot: la base sobre la que se construye y organiza la aplicación.

## Cómo está organizado

Es una sola aplicación de escritorio. Al arrancar, muestra un icono en la bandeja del sistema con un menú para cerrarla, lee una configuración opcional si existe, y se mantiene activa hasta que el usuario elige cerrarla de forma ordenada.

## Qué puede hacer hoy

### Initial setup

La aplicación arranca de forma autónoma y se queda activa en segundo plano, mostrando un icono en la bandeja del sistema. Haciendo clic en el icono aparece una opción para cerrar la aplicación, que se detiene de forma ordenada sin dejar nada a medias. Además, admite un fichero de configuración externo opcional: si falta, usa valores por defecto, y cuando el arranque termina deja un mensaje en el registro indicando que está lista.

<!-- sdd:end -->
