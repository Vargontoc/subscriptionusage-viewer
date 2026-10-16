package es.vargontoc.subscriptionviewer.usage.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de paquete para el nodo {@code rate_limits} del fichero de estado de Claude.
 */
class ClaudeRateLimitsDto {

    @JsonProperty("five_hour")
    ClaudeWindowDto fiveHour;

    @JsonProperty("seven_day")
    ClaudeWindowDto sevenDay;
}
