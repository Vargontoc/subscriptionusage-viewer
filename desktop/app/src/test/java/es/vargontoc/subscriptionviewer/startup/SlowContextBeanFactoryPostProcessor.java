package es.vargontoc.subscriptionviewer.startup;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Post-procesador de prueba que retrasa la inicialización del contexto.
 *
 * <p>Se activa únicamente con el perfil {@code slow-startup} y duerme el hilo de
 * arranque lo suficiente como para superar el límite de 30 segundos definido en
 * el NFR de tiempo de inicio. Se utiliza en el test de integración de caso
 * límite para comprobar que el mecanismo de medición detecta un arranque
 * excesivamente lento.</p>
 */
@Component
@Profile("slow-startup")
public class SlowContextBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private static final long DELAY_MILLIS = 35_000L;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            Thread.sleep(DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
