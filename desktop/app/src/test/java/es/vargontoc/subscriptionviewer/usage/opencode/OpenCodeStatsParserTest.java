package es.vargontoc.subscriptionviewer.usage.opencode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeStatsParserTest {
    private final OpenCodeStatsParser parser = new OpenCodeStatsParser();

    @Test
    void extractsTargetAndIgnoresUnknownPropertiesAndOtherProviders() {
        String json = """
                {"data":{"models":[
                  {"model":{"id":"gpt-5","providerID":"opencode-go","variant":"high"},"steps":2,
                   "tokens":{"input":10,"output":20,"reasoning":3,"cache":{"read":4,"write":5}},"cost":1.25,"extra":true},
                  {"model":{"id":"other","providerID":"other-provider","variant":"x"},"steps":1,
                   "tokens":{"input":1,"output":1,"reasoning":0,"cache":{"read":0,"write":0}},"cost":99}
                ],"unknown":"ignored"}}
                """;

        OpenCodeStatsParser.ParseResult result = parser.parse(json);

        assertThat(result).extracting(OpenCodeStatsParser.ParseResult::valid).isEqualTo(true);
        assertThat(result.models()).singleElement().satisfies(model -> {
            assertThat(model.id()).isEqualTo("gpt-5");
            assertThat(model.providerID()).isEqualTo("opencode-go");
            assertThat(model.variant()).isEqualTo("high");
            assertThat(model.steps()).isEqualTo(2);
            assertThat(model.tokens()).isEqualTo(new OpenCodeTokens(10, 20, 3, 4, 5));
            assertThat(model.cost()).isEqualByComparingTo("1.25");
        });
    }

    @Test
    void rejectsInvalidJsonAndMissingModels() {
        assertThat(parser.parse("not json").valid()).isFalse();
        assertThat(parser.parse("{\"data\":{}}").valid()).isFalse();
        assertThat(parser.parse("{\"models\":[]}").valid()).isFalse();
    }

    @Test
    void acceptsAValidListWithoutTargetModels() {
        String json = "{\"data\":{\"models\":[{\"model\":{\"providerID\":\"other\"}}]}}";
        OpenCodeStatsParser.ParseResult result = parser.parse(json);
        assertThat(result.valid()).isTrue();
        assertThat(result.models()).isEmpty();
    }

    @Test
    void usesDefaultVariantWhenMissing() {
        String json = """
                {"data":{"models":[
                  {"model":{"id":"kimi","providerID":"opencode"},"steps":1,
                   "tokens":{"input":0,"output":0,"reasoning":0,"cache":{"read":0,"write":0}},"cost":0}
                ]}}
                """;

        OpenCodeStatsParser.ParseResult result = parser.parse(json);

        assertThat(result.valid()).isTrue();
        assertThat(result.models()).singleElement().satisfies(model -> {
            assertThat(model.providerID()).isEqualTo("opencode");
            assertThat(model.variant()).isEqualTo("default");
        });
    }
}
