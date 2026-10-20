package com.jadaptive.mcp;

import java.util.Map;
import java.util.Optional;

final class SimpleToolGate implements ToolGate {

    enum DeletePolicy {
        PROMPT,
        ALLOW
    }

    private final DeletePolicy deletePolicy;

    private SimpleToolGate(DeletePolicy deletePolicy) {
        this.deletePolicy = deletePolicy;
    }

    static SimpleToolGate fromCli(String value) {
        if (value == null) {
            return new SimpleToolGate(DeletePolicy.PROMPT);
        }
        return "allow".equalsIgnoreCase(value) ? new SimpleToolGate(DeletePolicy.ALLOW) : new SimpleToolGate(DeletePolicy.PROMPT);
    }

    @Override
    public Optional<String> verify(GatedTool tool, Map<String, Object> parameters) {
        if (!tool.isDeleteOperation()) {
            return Optional.empty();
        }

        if (deletePolicy == DeletePolicy.ALLOW) {
            return Optional.empty();
        }

        if (Boolean.TRUE.equals(parameters.get("confirm"))) {
            return Optional.empty();
        }

        return Optional.of("Confirmation required for destructive operation.");
    }
}
