package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

class McpSocketToolsHttpIntegrationTest {

    @Test
    void listenerAcceptsAndExchangesDataWithOutboundSocket() throws Exception {
        try (McpRuntime runtime = new McpRuntime(
                DestructivePolicy.PROMPT,
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

                Map<String, Object> listen = callTool(client, "socket_listen_open", Map.of(
                        "bindAddress", "127.0.0.1",
                        "bindPort", 0));
                String listenerHandle = stringField(listen, "socketListenerHandle");
                int listenerPort = numberField(listen, "bindPort");

                Map<String, Object> outbound = callTool(client, "socket_open", Map.of(
                        "host", "127.0.0.1",
                        "port", listenerPort));
                String outboundSocketHandle = stringField(outbound, "socketHandle");

                Map<String, Object> accepted = callTool(client, "socket_listen_accept", Map.of(
                        "socketListenerHandle", listenerHandle,
                        "timeoutMs", 3_000));
                assertTrue(Boolean.TRUE.equals(accepted.get("accepted")), "Listener should accept inbound connection.");
                String acceptedSocketHandle = stringField(accepted, "socketHandle");

                Map<String, Object> write = callTool(client, "socket_write", Map.of(
                        "socketHandle", outboundSocketHandle,
                        "data", "ping"));
                assertEquals(4, numberField(write, "bytesWritten"));

                Map<String, Object> read = callTool(client, "socket_read", Map.of(
                        "socketHandle", acceptedSocketHandle,
                        "waitMs", 3_000,
                        "maxBytes", 64));
                assertEquals(4, numberField(read, "bytesRead"));
                assertEquals("ping", stringField(read, "data"));

                Map<String, Object> closeAccepted = callTool(client, "socket_close", Map.of("socketHandle", acceptedSocketHandle));
                assertTrue(Boolean.TRUE.equals(closeAccepted.get("closed")));

                Map<String, Object> closeOutbound = callTool(client, "socket_close", Map.of("socketHandle", outboundSocketHandle));
                assertTrue(Boolean.TRUE.equals(closeOutbound.get("closed")));

                Map<String, Object> closeListener = callTool(client, "socket_listen_close", Map.of("socketListenerHandle", listenerHandle));
                assertTrue(Boolean.TRUE.equals(closeListener.get("closed")));
            }
        }
    }

    @Test
    void listenerAcceptTimesOutWhenNoInboundConnectionArrives() throws Exception {
        try (McpRuntime runtime = new McpRuntime(
                DestructivePolicy.PROMPT,
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

                Map<String, Object> listen = callTool(client, "socket_listen_open", Map.of(
                        "bindAddress", "127.0.0.1",
                        "bindPort", 0));
                String listenerHandle = stringField(listen, "socketListenerHandle");

                Map<String, Object> accepted = callTool(client, "socket_listen_accept", Map.of(
                        "socketListenerHandle", listenerHandle,
                        "timeoutMs", 100));
                assertFalse(Boolean.TRUE.equals(accepted.get("accepted")));
                assertEquals("Accept timed out.", stringField(accepted, "message"));

                Map<String, Object> closeListener = callTool(client, "socket_listen_close", Map.of("socketListenerHandle", listenerHandle));
                assertTrue(Boolean.TRUE.equals(closeListener.get("closed")));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> callTool(McpSyncClient client, String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(name, arguments));
        assertFalse(Boolean.TRUE.equals(result.isError()), () -> "Tool call failed: " + name + " => " + result.content());
        Object structuredContent = result.structuredContent();
        assertNotNull(structuredContent, "Expected structured content in tool result.");
        return (Map<String, Object>) structuredContent;
    }

    private static int numberField(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        assertTrue(value instanceof Number, () -> "Expected numeric field: " + field);
        return ((Number) value).intValue();
    }

    private static String stringField(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        assertNotNull(value, () -> "Missing field: " + field);
        return String.valueOf(value);
    }
}
