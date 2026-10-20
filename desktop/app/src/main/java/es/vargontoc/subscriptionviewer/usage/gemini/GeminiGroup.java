package es.vargontoc.subscriptionviewer.usage.gemini;

import java.util.List;

/** Grupo de modelos y sus ventanas de uso. */
public record GeminiGroup(String name, List<GeminiWindow> windows) {
    public GeminiGroup {
        windows = List.copyOf(windows == null ? List.of() : windows);
    }
}
