package com.caio.escapekind.exception;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(UUID sessionId) {
        super("Sessão não encontrada: " + sessionId);
    }
}