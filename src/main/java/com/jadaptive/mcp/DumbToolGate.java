package com.jadaptive.mcp;

import java.util.Map;
import java.util.Optional;

final class DumbToolGate implements ToolGate {

    @Override
    public Optional<String> verify(GatedTool tool, Map<String, Object> parameters) {
        return Optional.empty();
    }
}
