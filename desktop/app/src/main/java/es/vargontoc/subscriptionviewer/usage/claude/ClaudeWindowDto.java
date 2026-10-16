package es.vargontoc.subscriptionviewer.usage.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de paquete para una ventana de límite del fichero de estado de Claude.
 */
class ClaudeWindowDto {

    @JsonProperty("used_percentage")
    Number usedPercentage;

    @JsonProperty("resets_at")
    Long resetsAt;
}
