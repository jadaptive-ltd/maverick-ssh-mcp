package com.jadaptive.mcp;

import java.util.Map;
import java.util.Optional;

public interface ToolGate {

    Optional<String> verify(GatedTool tool, Map<String, Object> parameters) throws ToolPermissionDenied;
}
