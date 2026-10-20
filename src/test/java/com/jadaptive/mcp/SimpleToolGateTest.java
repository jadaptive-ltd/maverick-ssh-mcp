package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SimpleToolGateTest {

    @Test
    void defaultsToPromptForDeleteOperations() throws Exception {
        SimpleToolGate gate = SimpleToolGate.fromCli(null);

        assertEquals("Confirmation required for destructive operation.",
                gate.verify(GatedTool.SFTP_RM, Map.of("path", "/tmp/test")).orElseThrow());
    }

    @Test
    void allowsDeleteWhenConfirmProvided() throws Exception {
        SimpleToolGate gate = SimpleToolGate.fromCli("prompt");

        assertTrue(gate.verify(GatedTool.SFTP_RM, Map.of("path", "/tmp/test", "confirm", true)).isEmpty());
    }

    @Test
    void allowsAllDeleteOperationsWhenConfigured() throws Exception {
        SimpleToolGate gate = SimpleToolGate.fromCli("allow");

        assertTrue(gate.verify(GatedTool.SFTP_RM, Map.of("path", "/tmp/test")).isEmpty());
    }

    @Test
    void doesNotGateNonDeleteOperations() throws Exception {
        SimpleToolGate gate = SimpleToolGate.fromCli("prompt");

        assertTrue(gate.verify(GatedTool.SSH_STATUS, Map.of("sshHandle", "ssh-1")).isEmpty());
    }
}
