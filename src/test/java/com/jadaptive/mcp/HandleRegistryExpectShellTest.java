package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sshtools.client.shell.ExpectShell;

class HandleRegistryExpectShellTest {

    @Test
    void closesRegisteredExpectShellAndExposesSnapshot() throws Exception {
        HandleRegistry registry = new HandleRegistry();
        ExpectShell expectShell = testExpectShell();
        String expectShellHandle = registry.registerExpectShell(expectShell);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expectShells = (List<Map<String, Object>>) registry.snapshot().get("expectshell");
        assertEquals(1, expectShells.size());

        assertTrue(registry.closeExpectShell(expectShellHandle));
        assertFalse(registry.closeExpectShell(expectShellHandle));
    }

    @Test
    void closesExpectShellOnRegistryShutdown() throws Exception {
        HandleRegistry registry = new HandleRegistry();
        ExpectShell expectShell = testExpectShell();
        registry.registerExpectShell(expectShell);

        registry.close();

        assertTrue(expectShell.isClosed());
    }

    private static ExpectShell testExpectShell() throws Exception {
        return ExpectShell.ExpectShellBuilder.create()
                .withInput(new ByteArrayInputStream(new byte[0]))
                .withOutput(new ByteArrayOutputStream())
                .withoutDetectSettings()
                .build();
    }
}
