package es.vargontoc.subscriptionviewer.usage.gemini;

import java.time.Instant;

import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

/** Ventana de uso extraída de un cubo de Gemini. */
public record GeminiWindow(UsageWindowType type, double percentage, Instant resetsAt) {
}
