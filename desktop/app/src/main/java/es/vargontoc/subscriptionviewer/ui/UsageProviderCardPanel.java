package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;

/** Tarjeta cuyo contenido procede del estado publicado de un proveedor. */
public final class UsageProviderCardPanel extends UsageCardPanel {

    /** Texto de usuario definido para una sesión de OpenAI no válida. */
    public static final String OPENAI_EXPIRED_SESSION_MESSAGE =
            "La sesión de OpenAI ha caducado o no es válida. Por favor, vuelva a iniciar sesión mediante el CLI de Codex u opencode para continuar consultando el consumo de su suscripción.";

    private final String providerId;

    public UsageProviderCardPanel(String providerId, CountdownEvaluator evaluator) {
        super(providerId, evaluator);
        this.providerId = providerId;
    }

    /**
     * Ajusta el texto de sesión caducada de OpenAI al mensaje formal de la
     * especificación. El estado no contiene credenciales ni respuestas HTTP.
     */
    @Override
    public void update(es.vargontoc.subscriptionviewer.usage.UsageProviderState state) {
        super.update(state);
        if (state.estado() == es.vargontoc.subscriptionviewer.usage.UsageProviderState.Estado.SESION_CADUCADA) {
            if (state.reading() == null && "openai".equalsIgnoreCase(providerId)) {
                java.util.Arrays.stream(getComponents())
                        .filter(component -> component instanceof javax.swing.JLabel)
                        .map(component -> (javax.swing.JLabel) component)
                        .findFirst()
                        .ifPresent(label -> label.setText("OpenAI"));
            }
            replaceExpiredSessionMessage();
        }
    }

    private void replaceExpiredSessionMessage() {
        java.util.Arrays.stream(getComponents())
                .filter(component -> component instanceof javax.swing.JLabel)
                .map(component -> (javax.swing.JLabel) component)
                .filter(label -> label.getText().startsWith("Sesion caducada"))
                .findFirst()
                .ifPresent(label -> label.setText(OPENAI_EXPIRED_SESSION_MESSAGE));
    }
}
