package com.jadaptive.mcp;

import java.util.List;

/**
 * Trusted execution context for a gated tool call, resolved by MCP from its own handle registry
 * rather than from caller-supplied arguments.
 *
 * @param sessionId       most specific session handle (expect shell, shell, or SSH handle)
 * @param sshHandle       root SSH connection handle
 * @param targetHost      connected host
 * @param targetPort      connected port
 * @param identity        SSH login user
 * @param operatingSystem detected OS description, or {@code null} when unknown
 * @param privilegeChain  privilege-changing commands (su/sudo) recorded for this session, oldest first
 * @param persistentShell whether commands run in a persistent (stateful) shell
 */
public record ExecutionContext(
        String sessionId,
        String sshHandle,
        String targetHost,
        int targetPort,
        String identity,
        String operatingSystem,
        List<String> privilegeChain,
        boolean persistentShell) {

    public ExecutionContext {
        privilegeChain = privilegeChain == null ? List.of() : List.copyOf(privilegeChain);
    }
}
