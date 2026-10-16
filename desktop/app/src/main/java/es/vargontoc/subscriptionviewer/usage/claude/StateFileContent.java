package es.vargontoc.subscriptionviewer.usage.claude;

import java.time.Instant;

/**
 * Contenido crudo del fichero de estado junto con su instante de registro.
 *
 * @param text      contenido textual del fichero
 * @param writtenAt instante en que el fichero fue escrito (del JSON o del sistema de ficheros)
 */
record StateFileContent(String text, Instant writtenAt) {
}
