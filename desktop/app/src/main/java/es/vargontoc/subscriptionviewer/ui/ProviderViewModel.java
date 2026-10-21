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

/**
 * Modelo inmutable que representa lo que debe mostrar una pestaña de proveedor.
 *
 * <p>La construcción no realiza E/S. La evaluación de las ventanas se deja al
 * {@link CountdownEvaluator}; las tablas se completan en las tareas posteriores
 * de esta feature.</p>
 */
public record ProviderViewModel(
        String providerId,
        PresentationState state,
        Instant updatedAt,
        List<TableViewModel> tables) {

    public ProviderViewModel {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(state, "state");
        tables = List.copyOf(tables == null ? List.of() : tables);
    }

    /** Estado de presentación de una pestaña. */
    public enum PresentationState {
        FETCHING,
        ERROR,
        DATA
    }

    /** Texto formal asociado al estado gris, o el aviso de primera consulta. */
    public String errorText() {
        if (state == PresentationState.FETCHING) {
            return ProviderErrorTexts.FIRST_QUERY;
        }
        if (state == PresentationState.ERROR) {
            return ProviderErrorTexts.errorText(providerId,
                    UsageProviderState.Estado.SIN_DATOS);
        }
        return null;
    }

    /** Modelo de una tabla de la pestaña. */
    public record TableViewModel(String title, List<RowViewModel> rows) {
        public TableViewModel {
            Objects.requireNonNull(title, "title");
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    /** Modelo de una fila de tabla. */
    public record RowViewModel(String label, CellViewModel percentageCell,
                               CellViewModel timeCell) {
        public RowViewModel {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(percentageCell, "percentageCell");
            Objects.requireNonNull(timeCell, "timeCell");
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
            return new ProviderViewModel(providerId, PresentationState.ERROR, null, List.of());
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
                return new ProviderViewModel(providerId, PresentationState.ERROR, null, List.of());
            }
            return new ProviderViewModel(providerId, PresentationState.DATA,
                    providerState.lastSuccessfulQuery(), buildWindowTables(validWindows));
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
                    return new TableViewModel(title, List.of(new RowViewModel("",
                            new CellViewModel(CellViewModel.CellType.PERCENTAGE,
                                    window.window().usedPercentage(), null),
                            new CellViewModel(CellViewModel.CellType.TEXT, null, remaining))));
                })
                .toList();
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
