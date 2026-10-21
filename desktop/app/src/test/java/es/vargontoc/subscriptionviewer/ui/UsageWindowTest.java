package es.vargontoc.subscriptionviewer.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.time.Clock;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotPublisher;

class UsageWindowTest {
    private UsageWindow window;

    @BeforeEach
    void setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Clock clock = Clock.systemUTC();
        window = new UsageWindow(new UsageSnapshotPublisher(), new CountdownEvaluator(clock), clock);
    }

    @Test
    void startsWithClaudeAndFourProviderPanels() throws Exception {
        window.show();
        drainEdt();

        assertThat(window.getTabBar().getSelectedProvider()).isEqualTo("claude");
        assertThat(window.getContentPanels()).containsKeys("claude", "openai", "opencode", "gemini");
        assertThat(window.getContentPanels().get("claude").isVisible()).isTrue();
        window.hide();
        drainEdt();
    }

    @Test
    void remembersSelectedProviderWhenHiddenAndShown() throws Exception {
        window.show();
        drainEdt();
        window.getTabBar().setSelectedProvider("gemini");
        window.getContentPanels(); // forces creation before the selection callback is inspected
        java.lang.reflect.Method select = UsageWindow.class.getDeclaredMethod("selectProvider", String.class);
        select.setAccessible(true);
        select.invoke(window, "gemini");
        window.hide();
        drainEdt();
        window.show();
        drainEdt();

        assertThat(window.getTabBar().getSelectedProvider()).isEqualTo("gemini");
        assertThat(window.getContentPanels().get("gemini").isVisible()).isTrue();
        window.hide();
        drainEdt();
    }

    @Test
    void newWindowStartsAgainWithClaudeAndDoesNotCreateChart() throws Exception {
        window.show();
        drainEdt();
        window.hide();
        UsageWindow restarted = new UsageWindow(new UsageSnapshotPublisher(),
                new CountdownEvaluator(Clock.systemUTC()), Clock.systemUTC());
        restarted.show();
        drainEdt();

        assertThat(restarted.getTabBar().getSelectedProvider()).isEqualTo("claude");
        assertThat(restarted.getContentPanel().getComponents())
                .noneMatch(component -> component instanceof UsageChartPanel);
        restarted.hide();
        drainEdt();
    }

    @Test
    void publisherSnapshotUpdatesSelectedContentAndAllTabStates() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        Clock clock = Clock.systemUTC();
        UsageWindow receivingWindow = new UsageWindow(publisher, new CountdownEvaluator(clock), clock);
        receivingWindow.show();
        receivingWindow.onSnapshotChanged(new es.vargontoc.subscriptionviewer.usage.UsageSnapshot(Map.of()));
        drainEdt();

        assertThat(receivingWindow.getContentPanels().get("claude").getModel()).isNotNull();
        assertThat(receivingWindow.getTabBar().getComponentCount()).isGreaterThan(4);
        receivingWindow.hide();
        drainEdt();
    }

    private void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
