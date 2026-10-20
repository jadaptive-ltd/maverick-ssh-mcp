package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sshtools.client.shell.ExpectShell;

import io.modelcontextprotocol.spec.McpSchema;

class McpToolsetExpectShellTest {

    @Test
    void returnsErrorForUnknownExpectShellHandle() throws Exception {
        McpSchema.CallToolResult result = invokeExpectShellTool(
                "expectShellGetOsType",
                Map.of("expectShellHandle", "expectshell-missing"),
                new HandleRegistry());

        assertTrue(Boolean.TRUE.equals(result.isError()));
    }

    @Test
    void reportsExpectShellOsTypeForRegisteredHandle() throws Exception {
        HandleRegistry registry = new HandleRegistry();
        try {
            ExpectShell expectShell = testExpectShell();
            String expectShellHandle = registry.registerExpectShell(expectShell);

            McpSchema.CallToolResult result = invokeExpectShellTool(
                    "expectShellGetOsType",
                    Map.of("expectShellHandle", expectShellHandle),
                    registry);

            assertFalse(Boolean.TRUE.equals(result.isError()));
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
            assertNotNull(payload);
            assertEquals(expectShellHandle, payload.get("expectShellHandle"));
            assertTrue(payload.get("osType") instanceof Number);
        }
        finally {
            registry.close();
        }
    }

    private static McpSchema.CallToolResult invokeExpectShellTool(String methodName, Map<String, Object> args, HandleRegistry registry)
            throws Exception {
        Method method = McpToolset.class.getDeclaredMethod(methodName, Map.class, HandleRegistry.class);
        method.setAccessible(true);
        return (McpSchema.CallToolResult) method.invoke(null, args, registry);
    }

    private static ExpectShell testExpectShell() throws Exception {
        return ExpectShell.ExpectShellBuilder.create()
                .withInput(new ByteArrayInputStream(new byte[0]))
                .withOutput(new ByteArrayOutputStream())
                .withoutDetectSettings()
                .build();
    }
}
