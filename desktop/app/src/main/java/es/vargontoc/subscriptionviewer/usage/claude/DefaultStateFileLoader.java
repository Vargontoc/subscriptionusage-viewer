package es.vargontoc.subscriptionviewer.usage.claude;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Cargador por defecto que lee el fichero de estado desde el sistema de ficheros.
 */
@Component
public class DefaultStateFileLoader implements StateFileLoader {

    @Override
    public Optional<StateFileContent> load(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Instant writtenAt = Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis());
            return Optional.of(new StateFileContent(text, writtenAt));
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }
}
