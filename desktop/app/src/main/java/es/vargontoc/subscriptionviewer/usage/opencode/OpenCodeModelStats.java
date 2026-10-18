package es.vargontoc.subscriptionviewer.usage.opencode;

import java.math.BigDecimal;

/** Estadísticas de una variante de modelo de OpenCode Go. */
public record OpenCodeModelStats(String id, String providerId, String variant,
                                 int steps, OpenCodeTokens tokens, BigDecimal cost) {
    /** Alias que conserva la grafía del campo del JSON. */
    public String providerID() {
        return providerId;
    }
}
