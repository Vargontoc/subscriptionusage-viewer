package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;

/** Tarjeta cuyo contenido procede del estado publicado de un proveedor. */
public final class UsageProviderCardPanel extends UsageCardPanel {

    public UsageProviderCardPanel(String providerId, CountdownEvaluator evaluator) {
        super(providerId, evaluator);
    }
}
