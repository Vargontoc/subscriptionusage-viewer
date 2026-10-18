package es.vargontoc.subscriptionviewer.usage.opencode;

/** Contadores de tokens devueltos por session.stats. */
public record OpenCodeTokens(long input, long output, long reasoning,
                             long cacheRead, long cacheWrite) {
}
