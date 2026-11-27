package com.jadaptive.mcp;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import io.modelcontextprotocol.spec.McpSchema;

public interface ToolGate {

    Optional<String> verify(GatedTool tool, Map<String, Object> parameters) throws ToolPermissionDenied;

    /**
     * Verifies a tool call with the trusted execution context MCP resolved from its handle registry.
     * The default delegates to {@link #verify(GatedTool, Map)}.
     *
     * @param context the resolved context, or empty when the tool is not bound to a known session
     */
    default Optional<String> verify(GatedTool tool, Map<String, Object> parameters,
            Optional<ExecutionContext> context) throws ToolPermissionDenied {
        return verify(tool, parameters);
    }

    /**
     * Whether MCP must enforce the persistent-shell command boundary (no raw interactive shell input,
     * single-line persistent-shell commands, no control characters or completion-marker literals).
     * Gates that authorise individual commands, such as Command Intelligence, must return {@code true}.
     */
    default boolean enforcesCommandBoundary() {
        return false;
    }

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