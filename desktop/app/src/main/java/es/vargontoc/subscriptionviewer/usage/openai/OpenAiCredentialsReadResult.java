package es.vargontoc.subscriptionviewer.usage.openai;

import java.util.Optional;

/** Resultado de una lectura de credenciales de Codex, sin conservar el JSON. */
public final class OpenAiCredentialsReadResult {
    private final boolean filePresent;
    private final OpenAiCredentials credentials;

    OpenAiCredentialsReadResult(boolean filePresent, OpenAiCredentials credentials) {
        this.filePresent = filePresent;
        this.credentials = credentials;
    }

    public boolean filePresent() {
        return filePresent;
    }

    public boolean isFilePresent() {
        return filePresent;
    }

    public boolean available() {
        return credentials != null;
    }

    public boolean isAvailable() {
        return available();
    }

    public Optional<OpenAiCredentials> credentials() {
        return Optional.ofNullable(credentials);
    }

    public Optional<OpenAiCredentials> getCredentials() {
        return credentials();
    }
}
