package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McpToolsetAuthFallbackTest {

    @Test
    void detectsAuthenticationFailureInCauseChain() {
        Throwable nested = new IllegalStateException("Permission denied for public key");
        Throwable top = new RuntimeException("Connect failed", nested);

        assertTrue(McpToolset.isAuthenticationFailure(top));
    }

    @Test
    void ignoresNonAuthenticationFailures() {
        Throwable nested = new IllegalStateException("Connection reset by peer");
        Throwable top = new RuntimeException("Transport error", nested);

        assertFalse(McpToolset.isAuthenticationFailure(top));
    }

    @Test
    void retriesOnlyWhenFallbackAndSshTeamAttemptedAndAuthFailure() {
        Throwable authFailure = new RuntimeException("Authentication failed");

        assertTrue(McpToolset.shouldRetryWithoutSshTeam(true, true, authFailure));
        assertFalse(McpToolset.shouldRetryWithoutSshTeam(false, true, authFailure));
        assertFalse(McpToolset.shouldRetryWithoutSshTeam(true, false, authFailure));
        assertFalse(McpToolset.shouldRetryWithoutSshTeam(true, true, new RuntimeException("Socket closed")));
    }
}
