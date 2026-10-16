package es.vargontoc.subscriptionviewer.usage.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de paquete para el fichero de estado de Claude.
 */
class ClaudeStateFileDto {

    @JsonProperty("written_at")
    Object writtenAt;

    @JsonProperty("five_hour")
    ClaudeWindowDto fiveHour;

    @JsonProperty("seven_day")
    ClaudeWindowDto sevenDay;

    @JsonProperty("rate_limits")
    ClaudeRateLimitsDto rateLimits;

    boolean hasRateLimits() {
        return rateLimits != null;
    }
}
