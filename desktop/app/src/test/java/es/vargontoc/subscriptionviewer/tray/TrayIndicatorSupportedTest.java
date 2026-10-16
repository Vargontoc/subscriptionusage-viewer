package es.vargontoc.subscriptionviewer.tray;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.awt.GraphicsEnvironment;
import java.awt.SystemTray;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pruebas del indicador de bandeja en entornos con soporte de {@link SystemTray}.
 *
 * <p>El test se omite automáticamente si el entorno es headless o si
 * {@link SystemTray#isSupported()} devuelve {@code false}.</p>
 *
 * <p>Se marca el contexto como {@link DirtiesContext} para que Spring lo cierre
 * durante la limpieza de la prueba, antes de que Surefire invoque
 * {@code System.exit}. Así la liberación del icono ocurre en el hilo de
 * pruebas y no en un shutdown hook de la JVM, donde
 * {@link SystemTray#remove(java.awt.TrayIcon)} puede bloquear en algunas
 * plataformas.</p>
 */
@SpringBootTest(classes = ApplicationContextConfig.class)
@DisabledIf("isHeadlessOrUnsupported")
@DirtiesContext
class TrayIndicatorSupportedTest {

    @Autowired
    private TrayIndicator trayIndicator;

    static boolean isHeadlessOrUnsupported() {
        return GraphicsEnvironment.isHeadless() || !SystemTray.isSupported();
    }

    @Test
    void iconIsAddedToSystemTrayAfterStartup() {
        assumeTrue(SystemTray.isSupported());
        assumeFalse(GraphicsEnvironment.isHeadless());

        assertThat(trayIndicator.isRunning())
                .as("El indicador debe estar en ejecución tras el arranque")
                .isTrue();

        assertThat(trayIndicator.getTrayIcon())
                .as("El TrayIcon debe haberse creado")
                .isNotNull();

        assertThat(SystemTray.getSystemTray().getTrayIcons())
                .as("El icono debe estar presente en la bandeja del sistema")
                .contains(trayIndicator.getTrayIcon());
    }
}
