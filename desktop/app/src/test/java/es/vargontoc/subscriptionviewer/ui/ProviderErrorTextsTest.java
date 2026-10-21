package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderErrorTextsTest {
    private static final String CLAUDE_ERROR =
            "No se han podido obtener los datos de uso de Claude. Compruebe que Claude Code está en uso y que el hook de la barra de estado está configurado, y vuelva a intentarlo más tarde.";
    private static final String OPENCODE_ERROR =
            "No se ha podido obtener el consumo de OpenCode. Por favor, compruebe que la herramienta opencode está instalada y vuelva a intentarlo más tarde.";
    private static final String OPENAI_SESSION_EXPIRED =
            "La sesión de OpenAI ha caducado o no es válida. Por favor, vuelva a iniciar sesión mediante el CLI de Codex u opencode para continuar consultando el consumo de su suscripción.";
    private static final String OPENAI_ERROR =
            "No se ha podido obtener el consumo de OpenAI. Por favor, compruebe su conexión y que ha iniciado sesión en el CLI de Codex u opencode, y vuelva a intentarlo más tarde.";
    private static final String GEMINI_ERROR =
            "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde.";

    @Test
    void returnsExactTextForEveryProviderAndErrorState() {
        var errorStates = new UsageProviderState.Estado[]{
                UsageProviderState.Estado.SIN_DATOS,
                UsageProviderState.Estado.CONSULTA_FALLIDA,
                UsageProviderState.Estado.DESACTUALIZADO
        };

        for (var state : errorStates) {
            assertThat(ProviderErrorTexts.errorText(ProviderErrorTexts.CLAUDE, state)).isEqualTo(CLAUDE_ERROR);
            assertThat(ProviderErrorTexts.errorText(ProviderErrorTexts.OPENCODE, state)).isEqualTo(OPENCODE_ERROR);
            assertThat(ProviderErrorTexts.errorText(ProviderErrorTexts.OPENAI, state)).isEqualTo(OPENAI_ERROR);
            assertThat(ProviderErrorTexts.errorText(ProviderErrorTexts.GEMINI, state)).isEqualTo(GEMINI_ERROR);
        }
    }

    @Test
    void distinguishesExpiredOpenAiSession() {
        assertThat(ProviderErrorTexts.errorText(
                ProviderErrorTexts.OPENAI, UsageProviderState.Estado.SESION_CADUCADA))
                .isEqualTo(OPENAI_SESSION_EXPIRED)
                .isNotEqualTo(OPENAI_ERROR);
    }

    @Test
    void firstQueryTextIsExactAndContainsNoErrorTerms() {
        assertThat(ProviderErrorTexts.FIRST_QUERY).isEqualTo("Obteniendo datos…");
        assertThat(ProviderErrorTexts.FIRST_QUERY).doesNotContain("error", "fallo", "aviso");
        assertThat(ProviderErrorTexts.isFirstQuery(null)).isTrue();
        assertThat(ProviderErrorTexts.isFirstQuery(UsageProviderState.Estado.SIN_DATOS)).isFalse();
    }
}
