package es.vargontoc.subscriptionviewer.usage.claude;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Contrato para cargar el contenido crudo de un fichero de estado.
 *
 * <p>Permite sustituir la lectura real del sistema de ficheros en pruebas.</p>
 */
public interface StateFileLoader {

    /**
     * Carga el contenido del fichero indicado.
     *
     * @param path ruta del fichero de estado
     * @return contenido y timestamp de registro, o {@link Optional#empty()} si no se puede leer
     */
    Optional<StateFileContent> load(Path path);
}
