package es.vargontoc.subscriptionviewer.usage.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class OpenAiCredentialsTest {

    @Test
    void exposesValuesOnlyThroughHeaderAccessWithinItsPackage() {
        OpenAiCredentials credentials = new OpenAiCredentials(
                "tokencentinelvalue", "accountsentinelvalue");

        assertThat(credentials.accessTokenForHeader()).isEqualTo("tokencentinelvalue");
        assertThat(credentials.accountIdForHeader()).isEqualTo("accountsentinelvalue");
    }

    @Test
    void textualRepresentationsDoNotRevealSecretOrAnyOfItsCharacters() {
        String token = "tokencentinelvalue";
        String account = "accountsentinelvalue";
        OpenAiCredentials credentials = new OpenAiCredentials(token, account);

        String dump = List.of(credentials).toString();

        assertThat(credentials.toString()).isEmpty();
        assertThat(dump).doesNotContain(token, account);
        for (char character : token.toCharArray()) {
            assertThat(dump).doesNotContain(String.valueOf(character));
        }
        for (char character : account.toCharArray()) {
            assertThat(dump).doesNotContain(String.valueOf(character));
        }
    }
}
