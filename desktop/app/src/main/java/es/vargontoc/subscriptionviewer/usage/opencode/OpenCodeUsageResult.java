package es.vargontoc.subscriptionviewer.usage.opencode;

import java.time.Instant;
import java.util.List;

/** Resultado crudo y atómico de las tres ventanas de un ciclo. */
public record OpenCodeUsageResult(Instant capturedAt,
                                  List<OpenCodeModelStats> fiveHours,
                                  List<OpenCodeModelStats> sevenDays,
                                  List<OpenCodeModelStats> month) {
    public OpenCodeUsageResult {
        fiveHours = List.copyOf(fiveHours);
        sevenDays = List.copyOf(sevenDays);
        month = List.copyOf(month);
    }

    public List<OpenCodeModelStats> monthly() { return month; }
    public List<OpenCodeModelStats> fiveHour() { return fiveHours; }
    public List<OpenCodeModelStats> sevenDay() { return sevenDays; }
}
