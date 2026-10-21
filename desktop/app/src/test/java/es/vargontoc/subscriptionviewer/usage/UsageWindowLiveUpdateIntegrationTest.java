package es.vargontoc.subscriptionviewer.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.GraphicsEnvironment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import es.vargontoc.subscriptionviewer.ui.ProviderViewModel.PresentationState;
import es.vargontoc.subscriptionviewer.ui.UsageWindow;

/** Verifica la entrega en caliente de estados publicados a la ventana Swing. */
@EnabledIf("notHeadless")
class UsageWindowLiveUpdateIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    static boolean notHeadless() {
        return !GraphicsEnvironment.isHeadless();
    }

    @Test
    void shouldTransitionFromErrorToData() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageWindow window = createWindow(publisher);

        publisher.publish("claude", UsageReadResult.consultaFallida());
        drainEdt();
        assertThat(window.getTabBar().isProviderGrey("claude")).isTrue();

        publisher.publish("claude", UsageReadResult.datos(reading("claude")));
        drainEdt();

        assertThat(window.getTabBar().isProviderGrey("claude")).isFalse();
        assertThat(window.getContentPanel("claude").getState()).isEqualTo(PresentationState.DATA);
        window.hide();
        drainEdt();
    }

    @Test
    void shouldTransitionFromFetchingToError() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageWindow window = createWindow(publisher);

        assertThat(window.getTabBar().isProviderGrey("gemini")).isTrue();
        assertThat(window.getContentPanel("gemini").getState()).isEqualTo(PresentationState.FETCHING);
        assertThat(window.getContentPanel("gemini").getText()).contains("Obteniendo datos");

        publisher.publish("gemini", UsageReadResult.consultaFallida());
        drainEdt();

        assertThat(window.getContentPanel("gemini").getState()).isEqualTo(PresentationState.ERROR);
        assertThat(window.getContentPanel("gemini").getText())
                .contains("No se ha podido obtener el consumo de Gemini");
        window.hide();
        drainEdt();
    }

    @Test
    void shouldMaintainErrorDuringSecondQuery() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageWindow window = createWindow(publisher);

        publisher.publish("openai", UsageReadResult.falloTransitorio(null));
        drainEdt();
        publisher.publish("openai", UsageReadResult.falloTransitorio(null));
        drainEdt();

        assertThat(window.getContentPanel("openai").getState()).isEqualTo(PresentationState.ERROR);
        assertThat(window.getContentPanel("openai").getText()).doesNotContain("Obteniendo datos");
        assertThat(window.getContentPanel("openai").getText())
                .contains("No se ha podido obtener el consumo de OpenAI");
        window.hide();
        drainEdt();
    }

    private UsageWindow createWindow(UsageSnapshotPublisher publisher) throws Exception {
        Clock clock = Clock.fixed(NOW, ZONE);
        UsageWindow window = new UsageWindow(publisher, new CountdownEvaluator(clock), clock);
        window.subscribe();
        window.show();
        drainEdt();
        return window;
    }

    private UsageReading reading(String provider) {
        return new UsageReading(provider, NOW, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 40, NOW.plusSeconds(3600)),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 15, NOW.plusSeconds(86400))));
    }

    private void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
