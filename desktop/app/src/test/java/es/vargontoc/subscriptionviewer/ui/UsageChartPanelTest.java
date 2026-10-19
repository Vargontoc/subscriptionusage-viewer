package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.UsageHistory;
import es.vargontoc.subscriptionviewer.usage.UsageHistoryPoint;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Pruebas unitarias de {@link UsageChartPanel}.
 *
 * <p>Se ejecutan solo cuando el entorno no es headless, ya que requieren
 * componentes Swing.</p>
 */
class UsageChartPanelTest {

    private static final Instant T0 = Instant.parse("2026-10-16T10:00:00Z");

    @BeforeEach
    void setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Entorno headless: se omiten tests de UI");
    }

    @Test
    void shouldProjectThreePointsInTemporalOrder() {
        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);

        List<UsageHistoryPoint> points = List.of(
                new UsageHistoryPoint(10, T0),
                new UsageHistoryPoint(40, T0.plusSeconds(300)),
                new UsageHistoryPoint(70, T0.plusSeconds(600))
        );
        chart.updateSeries(Map.of(new UsageHistory.Key("claude", UsageWindowType.FIVE_HOUR), points));

        List<UsageChartPanel.ScreenPoint> screen = chart.projectSeries("claude", UsageWindowType.FIVE_HOUR);
        assertThat(screen).hasSize(3);
        assertThat(screen.get(0).x()).isLessThan(screen.get(1).x());
        assertThat(screen.get(1).x()).isLessThan(screen.get(2).x());
    }

    @Test
    void shouldProjectThreeOpenAiPointsPerWindowInTemporalOrder() {
        UsageHistory history = new UsageHistory();
        for (int i = 0; i < 3; i++) {
            Instant writtenAt = T0.plusSeconds(i * 300L);
            history.add(new UsageReading("openai", writtenAt, List.of(
                    new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10 + i,
                            writtenAt.plusSeconds(1_000)),
                    new UsageWindowReading(UsageWindowType.SEVEN_DAY, 20 + i,
                            writtenAt.plusSeconds(2_000)))));
        }

        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);
        chart.updateSeries(history.getAllSeries());

        List<UsageChartPanel.ScreenPoint> fiveHour =
                chart.projectOpenAiSeries(UsageWindowType.FIVE_HOUR);
        List<UsageChartPanel.ScreenPoint> sevenDay =
                chart.projectOpenAiSeries(UsageWindowType.SEVEN_DAY);
        assertThat(fiveHour).hasSize(3);
        assertThat(sevenDay).hasSize(3);
        assertThat(fiveHour.get(0).x()).isLessThan(fiveHour.get(1).x());
        assertThat(fiveHour.get(1).x()).isLessThan(fiveHour.get(2).x());
        assertThat(sevenDay.get(0).x()).isLessThan(sevenDay.get(1).x());
        assertThat(sevenDay.get(1).x()).isLessThan(sevenDay.get(2).x());
    }

    @Test
    void shouldNotProjectDuplicateOpenAiValues() {
        UsageHistory history = new UsageHistory();
        history.add(new UsageReading("openai", T0, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 25, T0.plusSeconds(1_000)),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 40, T0.plusSeconds(2_000)))));
        history.add(new UsageReading("openai", T0.plusSeconds(300), List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 25, T0.plusSeconds(1_100)),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 40, T0.plusSeconds(2_100)))));

        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);
        chart.updateSeries(history.getAllSeries());

        assertThat(chart.projectOpenAiSeries(UsageWindowType.FIVE_HOUR)).hasSize(1);
        assertThat(chart.projectOpenAiSeries(UsageWindowType.SEVEN_DAY)).hasSize(1);
    }

    @Test
    void shouldHaveEmptyOpenAiSeriesWithoutReadings() {
        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);
        chart.updateSeries(Map.of());

        assertThat(chart.projectOpenAiSeries(UsageWindowType.FIVE_HOUR)).isEmpty();
        assertThat(chart.projectOpenAiSeries(UsageWindowType.SEVEN_DAY)).isEmpty();
    }

    @Test
    void shouldHandleEmptySeriesWithoutException() {
        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);

        chart.updateSeries(Map.of());

        List<UsageChartPanel.ScreenPoint> screen = chart.projectSeries("claude", UsageWindowType.FIVE_HOUR);
        assertThat(screen).isEmpty();
    }

    @Test
    void shouldShowOnePointPerChangedOpenCodeReadingInTimeOrder() {
        UsageHistory history = new UsageHistory();
        for (int i = 0; i < 3; i++) {
            Instant writtenAt = T0.plusSeconds(i * 300L);
            history.add(new UsageReading("opencode", writtenAt, List.of(
                    new UsageWindowReading(UsageWindowType.ROLLING, 10 + i * 10,
                            writtenAt.plusSeconds(1_000)),
                    new UsageWindowReading(UsageWindowType.WEEKLY, 20 + i * 10,
                            writtenAt.plusSeconds(2_000)))));
        }

        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);
        chart.updateSeries(history.getAllSeries());

        assertThat(chart.projectSeries("opencode", UsageWindowType.ROLLING)).hasSize(3);
        assertThat(chart.projectSeries("opencode", UsageWindowType.WEEKLY)).hasSize(3);
        assertThat(chart.projectSeries("opencode", UsageWindowType.ROLLING).get(0).x())
                .isLessThan(chart.projectSeries("opencode", UsageWindowType.ROLLING).get(1).x());
    }

    @Test
    void shouldNotShowDuplicateOpenCodeReading() {
        UsageHistory history = new UsageHistory();
        Instant reset = T0.plusSeconds(1_000);
        history.add(new UsageReading("opencode", T0,
                List.of(new UsageWindowReading(UsageWindowType.ROLLING, 25, reset))));
        history.add(new UsageReading("opencode", T0.plusSeconds(300),
                List.of(new UsageWindowReading(UsageWindowType.ROLLING, 25, reset))));

        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);
        chart.updateSeries(history.getAllSeries());

        assertThat(chart.projectSeries("opencode", UsageWindowType.ROLLING)).hasSize(1);
    }

    @Test
    void shouldProjectModelAndTotalSeriesIndependently() {
        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);
        List<UsageHistoryPoint> model = List.of(
                new UsageHistoryPoint(10, T0),
                new UsageHistoryPoint(20, T0.plusSeconds(300)),
                new UsageHistoryPoint(30, T0.plusSeconds(600)));
        List<UsageHistoryPoint> total = List.of(
                new UsageHistoryPoint(15, T0),
                new UsageHistoryPoint(25, T0.plusSeconds(300)),
                new UsageHistoryPoint(35, T0.plusSeconds(600)));

        chart.updateSeries(Map.of(
                new UsageHistory.Key("opencode", "model-a", UsageWindowType.MONTHLY), model,
                new UsageHistory.Key("opencode", UsageHistory.TOTAL_SERIES, UsageWindowType.MONTHLY), total));

        assertThat(chart.projectSeries("opencode", "model-a", UsageWindowType.MONTHLY)).hasSize(3);
        assertThat(chart.projectSeries("opencode", UsageHistory.TOTAL_SERIES, UsageWindowType.MONTHLY))
                .hasSize(3);
    }

    @Test
    void shouldPaintWithoutException() {
        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);

        List<UsageHistoryPoint> points = List.of(
                new UsageHistoryPoint(10, T0),
                new UsageHistoryPoint(40, T0.plusSeconds(300)),
                new UsageHistoryPoint(70, T0.plusSeconds(600))
        );
        chart.updateSeries(Map.of(new UsageHistory.Key("claude", UsageWindowType.FIVE_HOUR), points));

        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        chart.paintComponent(g2d);
        g2d.dispose();
    }

    @Test
    void shouldPaintEmptySeriesWithoutException() {
        UsageChartPanel chart = new UsageChartPanel();
        chart.setSize(400, 300);
        chart.updateSeries(Map.of());

        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        chart.paintComponent(g2d);
        g2d.dispose();
    }
}
