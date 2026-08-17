package com.jadaptive.mcp;

public enum DestructivePolicy {

    PROMPT,
    ALLOW;

    public static DestructivePolicy fromCli(String value) {
        if (value == null) {
            return PROMPT;
        }
        return "allow".equalsIgnoreCase(value) ? ALLOW : PROMPT;
    }

    public boolean requiresConfirmation() {
        return this == PROMPT;
    }
}
