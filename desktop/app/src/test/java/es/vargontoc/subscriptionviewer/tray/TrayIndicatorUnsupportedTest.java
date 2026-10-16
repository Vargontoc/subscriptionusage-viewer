package es.vargontoc.subscriptionviewer.tray;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas del indicador de bandeja cuando {@link java.awt.SystemTray} no está
 * soportado.
 *
 * <p>Verifica que la aplicación no aborta, que no se intenta crear el icono y
 * que se registra un aviso en los logs.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class TrayIndicatorUnsupportedTest {

    @Test
    void whenSystemTrayIsNotSupported_logsWarningAndContinuesWithoutAborting(CapturedOutput output) {
        ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
        when(configurationProvider.getAppName()).thenReturn("Subscription Usage Viewer");
        when(configurationProvider.getTrayIconPath()).thenReturn("icons/app-icon.png");

        TrayIconFactory trayIconFactory = mock(TrayIconFactory.class);

        TrayIndicator indicator = new TrayIndicator(configurationProvider, trayIconFactory) {
            @Override
            boolean isSystemTraySupported() {
                return false;
            }
        };

        assertThatNoException()
                .as("El arranque del indicador no debe abortar cuando la bandeja no está soportada")
                .isThrownBy(indicator::start);

        assertThat(indicator.isRunning())
                .as("El indicador no debe estar en ejecución si la bandeja no está soportada")
                .isFalse();

        assertThat(indicator.getTrayIcon())
                .as("No debe haberse creado ningún TrayIcon")
                .isNull();

        verifyNoInteractions(trayIconFactory);

        assertThat(output.getOut())
                .as("Debe registrarse un aviso indicando que la bandeja no está disponible")
                .contains("no está disponible");
    }
}
