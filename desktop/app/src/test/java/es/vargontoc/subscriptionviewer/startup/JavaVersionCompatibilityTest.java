package es.vargontoc.subscriptionviewer.startup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de compatibilidad con Java 21.
 *
 * <p>Verifica que la propiedad {@code java.version} indica Java 21, garantizando
 * que la compilación y ejecución se realizan sobre la versión declarada en el
 * stack del proyecto.</p>
 */
class JavaVersionCompatibilityTest {

    @Test
    void runtimeJavaVersionIs21() {
        String javaVersion = System.getProperty("java.version");
        assertThat(javaVersion)
                .as("La propiedad java.version debe indicar Java 21")
                .startsWith("21");
    }
}
