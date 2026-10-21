package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.UsageProviderState;

import java.util.Objects;

/** Catálogo único de mensajes formales mostrados por la vista. */
public final class ProviderErrorTexts {
    public static final String CLAUDE = "claude";
    public static final String OPENCODE = "opencode";
    public static final String OPENAI = "openai";
    public static final String GEMINI = "gemini";

    public static final String FIRST_QUERY = "Obteniendo datos…";

    private static final String CLAUDE_ERROR =
            "No se han podido obtener los datos de uso de Claude. Compruebe que Claude Code está en uso "
                    + "y que el hook de la barra de estado está configurado, y vuelva a intentarlo más tarde.";
    private static final String OPENCODE_ERROR =
            "No se ha podido obtener el consumo de OpenCode. Por favor, compruebe que la herramienta opencode "
                    + "está instalada y vuelva a intentarlo más tarde.";
    private static final String OPENAI_SESSION_EXPIRED =
            "La sesión de OpenAI ha caducado o no es válida. Por favor, vuelva a iniciar sesión mediante el CLI "
                    + "de Codex u opencode para continuar consultando el consumo de su suscripción.";
    private static final String OPENAI_ERROR =
            "No se ha podido obtener el consumo de OpenAI. Por favor, compruebe su conexión y que ha iniciado sesión "
                    + "en el CLI de Codex u opencode, y vuelva a intentarlo más tarde.";
    private static final String GEMINI_ERROR =
            "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada "
                    + "y que ha iniciado sesión, y vuelva a intentarlo más tarde.";

    private ProviderErrorTexts() {
    }

    /** Devuelve el mensaje formal correspondiente a un estado de error publicado. */
    public static String errorText(String providerId, UsageProviderState.Estado estado) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(estado, "estado");

        return switch (providerId) {
            case CLAUDE -> errorFor(estado, CLAUDE_ERROR);
            case OPENCODE -> errorFor(estado, OPENCODE_ERROR);
            case GEMINI -> errorFor(estado, GEMINI_ERROR);
            case OPENAI -> estado == UsageProviderState.Estado.SESION_CADUCADA
                    ? OPENAI_SESSION_EXPIRED
                    : errorFor(estado, OPENAI_ERROR);
            default -> throw new IllegalArgumentException("Proveedor no reconocido: " + providerId);
        };
    }

    /**
     * La ausencia de estado publicado se representa con {@code null}; los estados
     * del enum solo existen después de completar una primera consulta.
     */
    public static boolean isFirstQuery(UsageProviderState.Estado estado) {
        return estado == null;
    }

    private static String errorFor(UsageProviderState.Estado estado, String text) {
        if (estado == UsageProviderState.Estado.CORRECTO) {
            throw new IllegalArgumentException("El estado correcto no tiene texto de error");
        }
        return text;
    }
}
