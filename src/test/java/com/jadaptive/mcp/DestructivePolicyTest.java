package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DestructivePolicyTest {

    @Test
    void defaultsToPrompt() {
        assertEquals(DestructivePolicy.PROMPT, DestructivePolicy.fromCli(null));
        assertEquals(DestructivePolicy.PROMPT, DestructivePolicy.fromCli("prompt"));
        assertTrue(DestructivePolicy.fromCli("prompt").requiresConfirmation());
    }

    @Test
    void parsesAllow() {
        assertEquals(DestructivePolicy.ALLOW, DestructivePolicy.fromCli("allow"));
    }
}
