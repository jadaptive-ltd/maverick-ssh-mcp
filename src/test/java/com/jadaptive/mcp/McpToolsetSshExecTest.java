package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

class McpToolsetSshExecTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsSshExecToolToPermissionGate() {
        assertEquals(StandardGatedTool.SSH_EXEC, StandardGatedTool.fromToolName("ssh_exec").orElseThrow());
    }

    @Test
    void registersStatelessSshExecToolContract() throws Exception {
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
                McpSchema.Tool sshExec = client.listTools().tools().stream()
                        .filter(tool -> "ssh_exec".equals(tool.name()))
                        .findFirst()
                        .orElseThrow();

                assertTrue(sshExec.description().contains("stateless"));
                assertEquals(List.of("sshHandle", "command"), sshExec.inputSchema().get("required"));
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) sshExec.inputSchema().get("properties");
                assertEquals(4, properties.size());
                assertTrue(properties.containsKey("timeoutMs"));
                assertTrue(properties.containsKey("charset"));
            }
        }
    }

    @Test
    void passesSshExecThroughToolGate() throws Exception {
        ToolGate toolGate = (tool, parameters) -> tool == StandardGatedTool.SSH_EXEC
                ? java.util.Optional.of("SSH exec denied for test")
                : java.util.Optional.empty();

        try (McpRuntime runtime = new McpRuntime(
                new SshTeamService(SshTeamDeviceStore.forPath(tempDir)),
                toolGate,
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
                McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                        "ssh_exec",
                        Map.of("sshHandle", "ssh-missing", "command", "pwd")));

                assertTrue(Boolean.FALSE.equals(result.isError()));
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
                assertEquals(false, payload.get("approved"));
                assertEquals("ssh_exec", payload.get("tool"));
                assertEquals("SSH exec denied for test", payload.get("message"));
            }
        }
    }

    @Test
    void returnsErrorForUnknownSshHandle() throws Exception {
        McpSchema.CallToolResult result = invokeSshExec(
                Map.of(
                        "sshHandle", "ssh-missing",
                        "command", "pwd"),
                new HandleRegistry());

        assertTrue(Boolean.TRUE.equals(result.isError()));
    }

    @Test
    void rejectsNonPositiveTimeout() throws Exception {
        McpSchema.CallToolResult result = invokeSshExec(
                Map.of(
                        "sshHandle", "ssh-missing",
                        "command", "pwd",
                        "timeoutMs", 0),
                new HandleRegistry());

        assertTrue(Boolean.TRUE.equals(result.isError()));
    }

    private static McpSchema.CallToolResult invokeSshExec(Map<String, Object> args, HandleRegistry registry)
            throws Exception {
        Method method = McpToolset.class.getDeclaredMethod("sshExec", Map.class, HandleRegistry.class);
        method.setAccessible(true);
        return (McpSchema.CallToolResult) method.invoke(null, args, registry);
    }
}