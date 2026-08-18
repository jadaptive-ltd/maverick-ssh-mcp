package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

class McpRuntimeHttpAuthIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Test
    void rejectsRequestWithoutBearerTokenWhenConfigured() throws Exception {
        try (McpRuntime runtime = new McpRuntime(
                DestructivePolicy.PROMPT,
                McpRuntime.Mode.HTTP,
                "127.0.0.1",
                0,
                "/mcp",
                "test-token")) {
            runtime.start();

            HttpResponse<String> response = send(false, runtime.httpPort(), null);

            assertEquals(401, response.statusCode());
        }
    }

    @Test
    void acceptsRequestWithMatchingBearerTokenWhenConfigured() throws Exception {
        try (McpRuntime runtime = new McpRuntime(
                DestructivePolicy.PROMPT,
                McpRuntime.Mode.HTTP,
                "127.0.0.1",
                0,
                "/mcp",
                "test-token")) {
            runtime.start();

            HttpResponse<String> response = send(true, runtime.httpPort(), "test-token");

            // Any non-401 means the auth filter let the request reach the MCP servlet.
            assertNotEquals(401, response.statusCode());
        }
    }

    private static HttpResponse<String> send(boolean post, int port, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = post
                ? builder.POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .header("content-type", "application/json")
                        .build()
                : builder.GET().build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
