package es.vargontoc.subscriptionviewer.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeApiKeyTest {

    @Test
    void valueReturnsTheOriginalKey() {
        assertThat(new OpenCodeApiKey("SENTINEL-KEY-12345").value())
                .isEqualTo("SENTINEL-KEY-12345");
    }

    @Test
    void toStringDoesNotContainAnySentinelCharacter() {
        String sentinel = "SENTINEL-KEY-12345";

        String representation = new OpenCodeApiKey(sentinel).toString();

        assertThat(representation).doesNotContain("SENTINEL", "KEY", "12345");
        for (char character : sentinel.toCharArray()) {
            assertThat(representation).doesNotContain(String.valueOf(character));
        }
    }

    @Test
    void nullValueIsSupported() {
        OpenCodeApiKey key = new OpenCodeApiKey(null);

        assertThat(key.value()).isNull();
        assertThat(key.toString()).isEqualTo("***");
    }

    @Test
    void propertiesToStringDoesNotContainTheKey() {
        String sentinel = "SENTINEL-KEY-12345";
        AppProperties.OpenCodeProperties properties = new AppProperties.OpenCodeProperties();
        properties.setApiKey(sentinel);

        String representation = properties.toString();

        assertThat(representation).doesNotContain("SENTINEL", "KEY", "12345");
    }
}
