package com.jadaptive.mcp;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import io.modelcontextprotocol.spec.McpSchema;

public interface ToolGate {

    Optional<String> verify(GatedTool tool, Map<String, Object> parameters) throws ToolPermissionDenied;

    /**
     * Called after a tool that passed {@link #verify(GatedTool, Map)} has been executed, whether or not
     * it succeeded. Implementations must not throw and must not alter the tool result.
     *
     * @param result the tool result, or {@code null} if the executor failed unexpectedly
     */
    default void completed(GatedTool tool, Map<String, Object> parameters, McpSchema.CallToolResult result,
            Instant startedAt, Instant completedAt) {
    }
}
