package es.vargontoc.subscriptionviewer.usage;

/**
 * Listener de cambios en el snapshot publicado de uso.
 *
 * <p>La implementación recibe las notificaciones en el hilo de interfaz (EDT),
 * nunca desde el poller.</p>
 */
@FunctionalInterface
public interface UsageSnapshotListener {

    /**
     * Se invoca cada vez que cambia el snapshot publicado.
     *
     * @param snapshot snapshot inmutable con el estado actual por proveedor
     */
    void onSnapshotChanged(UsageSnapshot snapshot);
}
