package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sshtools.client.shell.ExpectShell;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

class McpToolsetExpectShellTest {

    @TempDir
    Path tempDir;

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

    @Test
    void rejectsLegacyGlobalInitTimeoutMutation() throws Exception {
        Method method = McpToolset.class.getDeclaredMethod("expectShellSetShellInitTimeout", Map.class);
        method.setAccessible(true);

        McpSchema.CallToolResult result = (McpSchema.CallToolResult) method.invoke(null, Map.of("timeoutMs", 0));

        assertTrue(Boolean.TRUE.equals(result.isError()));
    }

    @Test
    void rejectsNonConsumingCommandExecution() throws Exception {
        HandleRegistry registry = new HandleRegistry();
        String handle = registry.registerExpectShell(testExpectShell());

        McpSchema.CallToolResult result = invokeExpectShellTool(
                "expectShellExecuteCommand",
                Map.of("expectShellHandle", handle, "cmd", "true", "consume", false),
                registry);

        assertTrue(Boolean.TRUE.equals(result.isError()));
    }

    @Test
    void registersSnakeCaseExpectToolNames() throws Exception {
        Set<String> expected = Set.of(
                "expect_open", "expect_close", "expect_get_character_encoding",
                "expect_set_character_encoding", "expect_set_shell_init_timeout", "expect_in_startup",
                "expect_su", "expect_sudo", "expect_is_closed", "expect_exit", "expect_get_newline",
                "expect_execute_with_output", "expect_execute_with_exit_code", "expect_execute",
                "expect_execute_command", "expect_get_num_commands_executed", "expect_get_os_type",
                "expect_get_os_description");

        try (McpRuntime runtime = new McpRuntime(
                new SshTeamService(SshTeamDeviceStore.forPath(tempDir)),
                new DumbToolGate(),
                McpRuntime.Mode.HTTP,
                "127.0.0.1",
                0,
                "/mcp",
                null)) {
            runtime.start();

            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                    .builder("http://127.0.0.1:" + runtime.httpPort())
                    .endpoint("/mcp")
                    .build();

            try (McpSyncClient client = McpClient.sync(transport).build()) {
                client.initialize();
                Set<String> toolNames = client.listTools().tools().stream()
                        .map(McpSchema.Tool::name)
                        .collect(Collectors.toSet());

                assertTrue(toolNames.containsAll(expected));
                assertTrue(toolNames.stream().noneMatch(name -> name.startsWith("expectShell")));
            }
        }
    }

    @Test
    void mapsSnakeCaseNamesToGatedTools() {
        assertEquals(StandardGatedTool.EXPECT_SHELL_OPEN,
                StandardGatedTool.fromToolName("expect_open").orElseThrow());
        assertTrue(StandardGatedTool.fromToolName("expectShellOpen").isEmpty());
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