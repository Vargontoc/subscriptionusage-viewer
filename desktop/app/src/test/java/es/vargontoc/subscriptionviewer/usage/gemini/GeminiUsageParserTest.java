package es.vargontoc.subscriptionviewer.usage.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

class GeminiUsageParserTest {

    private final GeminiUsageParser parser = new GeminiUsageParser();

    @Test
    void aceptaSobreValidoYExtraeGroups() {
        GeminiUsageParser.ParseResult result = parser.parse("""
                {"status":"SUCCESS","command":{"data":{"groups":[{"name":"Gemini Models","buckets":[{"window":"weekly","remaining_fraction":0.9818568229675293,"reset_time":"2026-10-26T00:00:00Z"}]}]}}}
                """);

        assertTrue(result.isValid());
        assertNotNull(result.groups());
        assertEquals("Gemini Models", result.groups().get(0).name());
        assertEquals(1, result.groups().get(0).windows().size());
        assertEquals(UsageWindowType.WEEKLY, result.groups().get(0).windows().get(0).type());
        assertEquals(1.8, result.groups().get(0).windows().get(0).percentage());
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), result.groups().get(0).windows().get(0).resetsAt());
    }

    @Test
    void ignoraPropiedadesDesconocidas() {
        GeminiUsageParser.ParseResult result = parser.parse("""
                {"status":"SUCCESS","unknown":{"secret":"ignored"},"command":{"data":{"groups":[]}},"other":true}
                """);

        assertFalse(result.isValid());
    }

    @Test
    void textoNoJsonEsInvalido() {
        GeminiUsageParser.ParseResult result = parser.parse("esto no es json");

        assertFalse(result.isValid());
        assertEquals("no-es-json", result.invalidReason());
    }

    @Test
    void statusDistintoEsInvalido() {
        GeminiUsageParser.ParseResult result = parser.parse(
                "{\"status\":\"ERROR\",\"command\":{\"data\":{\"groups\":[]}}}");

        assertFalse(result.isValid());
        assertEquals("status-no-es-success", result.invalidReason());
    }

    @Test
    void gruposAusentesSonInvalidos() {
        GeminiUsageParser.ParseResult result = parser.parse("{\"status\":\"SUCCESS\",\"command\":{\"data\":{}}}");

        assertFalse(result.isValid());
        assertEquals("grupos-ausentes", result.invalidReason());
    }

    @Test
    void responseSeIgnoraCuandoGroupsEstaPresente() {
        GeminiUsageParser.ParseResult result = parser.parse("""
                {"status":"SUCCESS","command":{"data":{"groups":[{"name":"Gemini Models"}]}},"response":"texto tabulado ignorado"}
                """);

        assertFalse(result.isValid());
    }

    @Test
    void extraeCincoVentanasYAcotaPorcentajes() {
        GeminiUsageParser.ParseResult result = parser.parse("""
                {"status":"SUCCESS","command":{"data":{"groups":[
                  {"name":"Gemini Models","buckets":[
                    {"window":"weekly","remaining_fraction":0.9818568229675293},
                    {"window":"5h","remaining_fraction":1},
                    {"window":"other","remaining_fraction":0.5}]},
                  {"name":"Claude and GPT models","buckets":[
                    {"window":"weekly","remaining_fraction":0},
                    {"window":"5h","remaining_fraction":0.5}]},
                  {"name":"Gemini 4 Argon","buckets":[
                    {"window":"weekly","remaining_fraction":-1}]}
                ]}}}
                """);

        assertTrue(result.isValid());
        assertEquals(3, result.groups().size());
        assertEquals(5, result.groups().stream().mapToInt(group -> group.windows().size()).sum());
        assertEquals(0.0, result.groups().get(0).windows().get(1).percentage());
        assertEquals(100.0, result.groups().get(1).windows().get(0).percentage());
        assertEquals(50.0, result.groups().get(1).windows().get(1).percentage());
        assertEquals(100.0, result.groups().get(2).windows().get(0).percentage());
    }

    @Test
    void omiteCubosInvalidosYDejaReinicioNulo() {
        GeminiUsageParser.ParseResult result = parser.parse("""
                {"status":"SUCCESS","command":{"data":{"groups":[
                  {"name":"Nuevo grupo","buckets":[
                    {"window":"weekly","remaining_fraction":"bad"},
                    {"window":"5h","remaining_fraction":0.5,"reset_time":"no-es-iso"},
                    {"window":"weekly","remaining_fraction":0.5}
                  ]},
                  {"name":"Vacío","buckets":[]},
                  {"name":"Ausente"}
                ]}}}
                """);

        assertTrue(result.isValid());
        assertEquals(1, result.groups().size());
        assertEquals(2, result.groups().get(0).windows().size());
        assertEquals(null, result.groups().get(0).windows().get(0).resetsAt());
    }

    @Test
    void sinCubosValidosEsConsultaFallida() {
        GeminiUsageParser.ParseResult result = parser.parse("""
                {"status":"SUCCESS","command":{"data":{"groups":[
                  {"name":"Grupo","buckets":[{"window":"weekly"}]}
                ]}}}
                """);

        assertFalse(result.isValid());
        assertEquals("sin-grupos-validos", result.invalidReason());
    }

    @Test
    void motivoNuncaContieneLaSalidaInvalida() {
        String sentinel = "SENTINEL-DATO-SENSIBLE-123";
        GeminiUsageParser.ParseResult result = parser.parse("{\"status\":\"ERROR\",\"secret\":\"" + sentinel + "\"}");

        assertFalse(result.isValid());
        assertFalse(result.invalidReason().contains(sentinel));
    }
}
