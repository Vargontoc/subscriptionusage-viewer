package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.CountdownResult;
import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshot;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

/**
 * Modelo inmutable que representa lo que debe mostrar una pestaña de proveedor.
 *
 * <p>La construcción no realiza E/S. La evaluación de las ventanas se deja al
     * {@link CountdownEvaluator}.</p>
 */
public record ProviderViewModel(
         String providerId,
         PresentationState state,
         Instant updatedAt,
         List<TableViewModel> tables,
         String presentationErrorText) {

    public ProviderViewModel(String providerId, PresentationState state,
                             Instant updatedAt, List<TableViewModel> tables) {
        this(providerId, state, updatedAt, tables, defaultErrorText(providerId, state));
    }

    public ProviderViewModel {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(state, "state");
        tables = List.copyOf(tables == null ? List.of() : tables);
    }

    private static String defaultErrorText(String providerId, PresentationState state) {
        if (state == PresentationState.FETCHING) {
            return ProviderErrorTexts.FIRST_QUERY;
        }
        if (state == PresentationState.ERROR) {
            return ProviderErrorTexts.errorText(providerId, UsageProviderState.Estado.SIN_DATOS);
        }
        return null;
    }

    /** Estado de presentación de una pestaña. */
    public enum PresentationState {
        FETCHING,
        ERROR,
        DATA
    }

    /** Texto formal asociado al estado gris, o el aviso de primera consulta. */
    public String errorText() {
        return presentationErrorText;
    }

    /** Modelo de una tabla de la pestaña. */
    public record TableViewModel(String title, List<String> headers, List<RowViewModel> rows) {
        /** Constructor compatible con consumidores que aún no especifican cabeceras. */
        public TableViewModel(String title, List<RowViewModel> rows) {
            this(title, List.of(title, "Reinicia en"), rows);
        }

        public TableViewModel {
            headers = List.copyOf(headers == null ? List.of() : headers);
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    /** Modelo de una fila de tabla. */
    public record RowViewModel(String label, CellViewModel percentageCell,
                               CellViewModel timeCell, List<CellViewModel> additionalCells) {
        public RowViewModel(String label, CellViewModel percentageCell, CellViewModel timeCell) {
            this(label, percentageCell, timeCell, List.of());
        }

        public RowViewModel {
            Objects.requireNonNull(percentageCell, "percentageCell");
            additionalCells = List.copyOf(additionalCells == null ? List.of() : additionalCells);
            additionalCells.forEach(Objects::requireNonNull);
        }

        /** Celdas de porcentaje en el orden de las cabeceras de uso. */
        public List<CellViewModel> percentageCells() {
            List<CellViewModel> cells = new ArrayList<>();
            cells.add(percentageCell);
            cells.addAll(additionalCells);
            return List.copyOf(cells);
        }
    }

    /** Modelo de una celda de porcentaje, texto o ausencia de datos. */
    public record CellViewModel(CellType type, Double percentage, String text) {
        public CellViewModel {
            Objects.requireNonNull(type, "type");
            if (type == CellType.PERCENTAGE && percentage == null) {
                throw new IllegalArgumentException("Una celda de porcentaje requiere un valor");
            }
            if (type == CellType.PERCENTAGE && text != null) {
                throw new IllegalArgumentException("Una celda de porcentaje no tiene texto");
            }
            if (type != CellType.PERCENTAGE && percentage != null) {
                throw new IllegalArgumentException("Una celda de texto no tiene porcentaje");
            }
        }

        public enum CellType {
            PERCENTAGE,
            TEXT,
            NA
        }
    }

    /**
     * Construye el modelo correspondiente al proveedor indicado.
     *
     * <p>La ausencia de estado publicado significa primera consulta. Un estado
     * distinto de correcto nunca expone la lectura válida que pueda conservar
     * internamente el publicador.</p>
     */
    public static ProviderViewModel from(String providerId, UsageSnapshot snapshot,
                                         Clock clock, CountdownEvaluator evaluator) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(evaluator, "evaluator");

        UsageProviderState providerState = snapshot.providerStateOf(providerId).orElse(null);
        if (providerState == null) {
            return new ProviderViewModel(providerId, PresentationState.FETCHING, null, List.of());
        }
        if (providerState.estado() != UsageProviderState.Estado.CORRECTO) {
            // La selección del texto permanece centralizada en ProviderErrorTexts;
            // el contenedor lo resolverá al pintar el estado de error.
            ProviderErrorTexts.errorText(providerId, providerState.estado());
            return new ProviderViewModel(providerId, PresentationState.ERROR, null, List.of(),
                    ProviderErrorTexts.errorText(providerId, providerState.estado()));
        }

        if ((ProviderErrorTexts.CLAUDE.equalsIgnoreCase(providerId)
                || ProviderErrorTexts.OPENAI.equalsIgnoreCase(providerId))) {
            UsageReading reading = providerState.reading();
            List<EvaluatedWindow> validWindows = reading.windows().stream()
                    .map(window -> new EvaluatedWindow(window,
                            evaluator.evaluate(providerId, window.resetsAt())))
                    .filter(window -> window.result().valid())
                    .toList();
            if (validWindows.isEmpty()) {
                return new ProviderViewModel(providerId, PresentationState.ERROR, null, List.of(),
                        ProviderErrorTexts.errorText(providerId, UsageProviderState.Estado.SIN_DATOS));
            }
            return new ProviderViewModel(providerId, PresentationState.DATA,
                    providerState.lastSuccessfulQuery(), buildWindowTables(validWindows));
        }

        if (ProviderErrorTexts.GEMINI.equalsIgnoreCase(providerId)) {
            return new ProviderViewModel(providerId, PresentationState.DATA,
                    providerState.lastSuccessfulQuery(), buildGeminiTables(providerState.reading(), evaluator));
        }

        if (ProviderErrorTexts.OPENCODE.equalsIgnoreCase(providerId)) {
            return new ProviderViewModel(providerId, PresentationState.DATA,
                    providerState.lastSuccessfulQuery(), buildOpenCodeTables(providerState.reading()));
        }

        return new ProviderViewModel(providerId, PresentationState.DATA,
                providerState.lastSuccessfulQuery(), List.of());
    }

    private static List<TableViewModel> buildWindowTables(List<EvaluatedWindow> windows) {
        return windows.stream()
                .filter(window -> window.window().type() == UsageWindowType.FIVE_HOUR
                        || window.window().type() == UsageWindowType.SEVEN_DAY
                        || window.window().type() == UsageWindowType.WEEKLY)
                .map(window -> {
                    String title = window.window().type() == UsageWindowType.FIVE_HOUR
                            ? "Uso 5h" : "Uso semanal";
                    String remaining = window.result().formattedRemainingTime();
            return new TableViewModel(title, List.of(title, "Reinicia en"), List.of(new RowViewModel("",
                            new CellViewModel(CellViewModel.CellType.PERCENTAGE,
                                    window.window().usedPercentage(), null),
                            new CellViewModel(CellViewModel.CellType.TEXT, null, remaining))));
                })
                .toList();
    }

    private static List<TableViewModel> buildGeminiTables(UsageReading reading,
                                                           CountdownEvaluator evaluator) {
        List<UsageReading> groups = reading.seriesReadings().stream()
                .filter(group -> !group.windows().isEmpty())
                .sorted((left, right) -> Integer.compare(geminiGroupOrder(left.seriesId()),
                        geminiGroupOrder(right.seriesId())))
                .toList();

        List<RowViewModel> modelRows = groups.stream()
                .map(group -> new RowViewModel(group.seriesId(),
                        textCell(group.seriesId()), textCell(group.seriesId())))
                .toList();
        List<RowViewModel> fiveHourRows = groups.stream()
                .map(group -> geminiWindowRow(group, UsageWindowType.FIVE_HOUR, evaluator))
                .toList();
        List<RowViewModel> weeklyRows = groups.stream()
                .map(group -> geminiWindowRow(group, UsageWindowType.SEVEN_DAY, evaluator))
                .toList();

        return List.of(
                new TableViewModel("Modelo", List.of("Modelo"), modelRows),
                new TableViewModel("Uso 5h", List.of("Uso 5h", "Reinicia en"), fiveHourRows),
                new TableViewModel("Uso semanal", List.of("Uso semanal", "Reinicia en"), weeklyRows));
    }

    private static List<TableViewModel> buildOpenCodeTables(UsageReading reading) {
        List<UsageReading> series = reading.seriesReadings().stream()
                .filter(Objects::nonNull)
                .toList();

        List<UsageReading> models = series.stream()
                .filter(item -> item.seriesId() != null && !"Total".equalsIgnoreCase(item.seriesId()))
                .toList();
        UsageReading total = series.stream()
                .filter(item -> item.seriesId() == null || "Total".equalsIgnoreCase(item.seriesId()))
                .findFirst()
                .orElse(new UsageReading(reading.providerId(), "Total", reading.writtenAt(), reading.windows()));

        List<UsageReading> ordered = new ArrayList<>(models);
        ordered.add(total);

        List<RowViewModel> modelRows = ordered.stream()
                .map(item -> new RowViewModel(item.seriesId() == null ? "Total" : item.seriesId(),
                        percentageCell(windowPercentage(item, UsageWindowType.FIVE_HOUR)), null))
                .toList();
        List<RowViewModel> usageRows = ordered.stream()
                .map(item -> new RowViewModel(null,
                        percentageCell(windowPercentage(item, UsageWindowType.FIVE_HOUR)), null,
                        List.of(percentageCell(windowPercentage(item, UsageWindowType.WEEKLY)),
                                percentageCell(windowPercentage(item, UsageWindowType.MONTHLY)))))
                .toList();

        return List.of(
                new TableViewModel("Modelo", List.of("Modelo"), modelRows),
                new TableViewModel(null, List.of("Uso 5h", "Uso semanal", "Uso mensual"), usageRows));
    }

    private static double windowPercentage(UsageReading reading, UsageWindowType type) {
        return reading.windows().stream()
                .filter(window -> window.type() == type
                        || (type == UsageWindowType.WEEKLY && window.type() == UsageWindowType.SEVEN_DAY))
                .mapToDouble(UsageWindowReading::usedPercentage)
                .findFirst()
                .orElse(0.0);
    }

    private static CellViewModel percentageCell(double percentage) {
        return new CellViewModel(CellViewModel.CellType.PERCENTAGE, percentage, null);
    }

    private static RowViewModel geminiWindowRow(UsageReading group, UsageWindowType type,
                                                 CountdownEvaluator evaluator) {
        UsageWindowReading window = group.windows().stream()
                .filter(candidate -> candidate.type() == type
                        || (type == UsageWindowType.SEVEN_DAY
                        && candidate.type() == UsageWindowType.WEEKLY))
                .findFirst().orElse(null);
        if (window == null) {
            return new RowViewModel("", naCell(), naCell());
        }
        CountdownResult countdown = evaluator.evaluate("gemini", window.resetsAt());
        return new RowViewModel("",
                new CellViewModel(CellViewModel.CellType.PERCENTAGE,
                        window.usedPercentage(), null),
                new CellViewModel(CellViewModel.CellType.TEXT, null,
                        countdown.formattedRemainingTime()));
    }

    private static int geminiGroupOrder(String name) {
        return switch (name) {
            case "Gemini Models" -> 0;
            case "Claude and GPT models" -> 1;
            case "Gemini 4 Argon" -> 2;
            default -> 3;
        };
    }

    private static CellViewModel naCell() {
        return new CellViewModel(CellViewModel.CellType.NA, null, "N/A");
    }

    private static CellViewModel textCell(String text) {
        return new CellViewModel(CellViewModel.CellType.TEXT, null, text);
    }

    private record EvaluatedWindow(UsageWindowReading window, CountdownResult result) {
    }

    /** Alias semántico para consumidores que prefieran una factoría explícita. */
    public static ProviderViewModel build(String providerId, UsageSnapshot snapshot,
                                          Clock clock, CountdownEvaluator evaluator) {
        return from(providerId, snapshot, clock, evaluator);
    }

    /** Variante con el snapshot como primer argumento. */
    public static ProviderViewModel from(UsageSnapshot snapshot, String providerId,
                                         Clock clock, CountdownEvaluator evaluator) {
        return from(providerId, snapshot, clock, evaluator);
    }
}
