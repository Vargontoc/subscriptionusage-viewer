package es.vargontoc.subscriptionviewer.usage.openai;

import es.vargontoc.subscriptionviewer.usage.UsageReading;

/** Resultado del parseo de la respuesta de cuota de OpenAI. */
public record OpenAiUsageResult(UsageReading reading, String planType) {
    public boolean isPlus() {
        return "plus".equalsIgnoreCase(planType);
    }

    public boolean isPro() {
        return "pro".equalsIgnoreCase(planType);
    }
}
