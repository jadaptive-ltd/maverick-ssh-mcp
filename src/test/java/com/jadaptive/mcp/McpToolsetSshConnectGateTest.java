package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

class McpToolsetSshConnectGateTest {

    @TempDir
    Path tempDir;

    @Test
    void connectionPolicyCanRequestSanitizedConfirmation() throws Exception {
        ToolGate gate = (tool, parameters) -> tool == StandardGatedTool.SSH_CONNECT
                ? Optional.of("Confirmation required by connection policy.")
                : Optional.empty();

        try (McpRuntime runtime = new McpRuntime(
                new SshTeamService(SshTeamDeviceStore.forPath(tempDir)),
                gate,
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
                McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("ssh_connect", Map.of(
                        "host", "server.example.test",
                        "username", "deploy",
                        "password", "super-secret",
                        "privateKeyPassphrase", "also-secret")));

                assertFalse(Boolean.TRUE.equals(result.isError()));
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
                @SuppressWarnings("unchecked")
                Map<String, Object> arguments = (Map<String, Object>) payload.get("arguments");
                assertFalse(arguments.containsKey("password"));
                assertFalse(arguments.containsKey("privateKeyPassphrase"));
                assertTrue(payload.get("next").toString().contains("confirm=true"));

                McpSchema.Tool sshConnect = client.listTools().tools().stream()
                        .filter(tool -> "ssh_connect".equals(tool.name()))
                        .findFirst()
                        .orElseThrow();
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) sshConnect.inputSchema().get("properties");
                assertTrue(properties.containsKey("confirm"));
            }
        }
    }
}
