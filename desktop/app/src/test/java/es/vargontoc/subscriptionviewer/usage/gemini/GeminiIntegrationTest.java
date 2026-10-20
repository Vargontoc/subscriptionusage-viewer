package es.vargontoc.subscriptionviewer.usage.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;

/** Verifica el cableado Spring de los componentes de Gemini. */
@SpringBootTest(classes = ApplicationContextConfig.class)
class GeminiIntegrationTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void losComponentesDeGeminiSonBeansYEstanInyectadosEnLaFuente() {
        GeminiCliRunner runner = context.getBean(GeminiCliRunner.class);
        GeminiUsageParser parser = context.getBean(GeminiUsageParser.class);
        GeminiUsageSource source = context.getBean(GeminiUsageSource.class);

        assertThat(runner).isSameAs(context.getBean("geminiCliRunner"));
        assertThat(parser).isSameAs(context.getBean("geminiUsageParser"));
        assertThat(source).isSameAs(context.getBean("geminiUsageSource"));
        assertThat(source.getProviderId()).isEqualTo("gemini");
        assertThat(source.getPollInterval(null)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void laConfiguracionDeGeminiEstaPresenteYEsValida() {
        assertThat(environment.getProperty("subscription-viewer.gemini.usage-poll-interval"))
                .isEqualTo("60s");
        assertThat(context.getBean(GeminiUsageSource.class).getPollInterval(null))
                .isGreaterThanOrEqualTo(Duration.ofSeconds(30));
    }
}
