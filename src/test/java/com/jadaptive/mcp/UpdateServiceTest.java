package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

class UpdateServiceTest {

    @TempDir
    Path dataDirectory;

    @Test
    void fetchesVersionWithRequiredHeadersAndCachesIt() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/current", exchange -> {
            requests.incrementAndGet();
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = """
                    {"version":"0.9.0-85","tag":"current"}
                    """.strip().getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/current");
        try (UpdateService service = new UpdateService(endpoint, "0.9.0-84", dataDirectory,
                Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC), HttpClient.newHttpClient(),
                new Random(1))) {
            service.start();
            await(() -> "0.9.0-85".equals(service.availableVersion()));

            assertEquals(1, requests.get());
            assertEquals("application/json", accept.get());
            assertEquals("application/json", contentType.get());
            assertEquals("Maverick SSH MCP 0.9.0-84 (" + System.getProperty("os.name") + "; "
                    + System.getProperty("os.arch") + "; " + System.getProperty("os.version") + ")",
                    userAgent.get());

            Path cache = dataDirectory.resolve("maverick-ssh-mcp-available-version.json");
            assertTrue(Files.isRegularFile(cache));
            assertTrue(Files.readString(cache).contains("0.9.0-85"));
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void reloadsCachedVersionWithoutTreatingItAsStale() throws Exception {
        Path cache = dataDirectory.resolve("maverick-ssh-mcp-available-version.json");
        Files.writeString(cache, "{\"version\":\"0.9.0-85\"}");

        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/current", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/current");
        try (UpdateService service = new UpdateService(endpoint, "0.9.0-84", dataDirectory,
                Clock.systemUTC(), HttpClient.newHttpClient(), new Random(1))) {
            service.start();
            await(() -> "0.9.0-85".equals(service.availableVersion()));
            Thread.sleep(200);

            assertEquals("0.9.0-85", service.availableVersion());
            assertEquals(0, requests.get());
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void disabledUpdateChecksLoadCacheWithoutNetworkRequest() throws Exception {
        Path cache = dataDirectory.resolve("maverick-ssh-mcp-available-version.json");
        Files.writeString(cache, "{\"version\":\"0.9.0-85\"}");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/current", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/current");
        try (UpdateService service = new UpdateService(endpoint, "0.9.0-84", dataDirectory,
                Clock.systemUTC(), HttpClient.newHttpClient(), new Random(1))) {
            service.start(false);
            Thread.sleep(200);

            assertEquals("0.9.0-85", service.availableVersion());
            assertEquals(0, requests.get());
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void parsesUpdateCheckEnvironmentValues() {
        assertTrue(McpRuntime.updateChecksEnabled(null));
        assertTrue(McpRuntime.updateChecksEnabled("true"));
        assertFalse(McpRuntime.updateChecksEnabled("false"));
        assertFalse(McpRuntime.updateChecksEnabled("0"));
        assertFalse(McpRuntime.updateChecksEnabled("off"));
    }

    @Test
    void runtimeRegistersGetVersionTool() throws Exception {
        Files.writeString(dataDirectory.resolve("maverick-ssh-mcp-available-version.json"),
                "{\"version\":\"0.9.0-85\"}");
        try (McpRuntime runtime = new McpRuntime(
                new SshTeamService(SshTeamDeviceStore.forPath(dataDirectory)),
                new DumbToolGate(),
                McpRuntime.Mode.HTTP,
                "127.0.0.1",
                0,
                "/mcp",
                null,
                false,
                null,
                null,
                dataDirectory)) {
            runtime.start();
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                    .builder("http://127.0.0.1:" + runtime.httpPort())
                    .endpoint("/mcp")
                    .build();

            try (McpSyncClient client = McpClient.sync(transport).build()) {
                client.initialize();
                assertTrue(client.listTools().tools().stream().anyMatch(tool -> "get_version".equals(tool.name())));

                McpSchema.CallToolResult result = client.callTool(
                        new McpSchema.CallToolRequest("get_version", java.util.Map.of()));
                assertFalse(Boolean.TRUE.equals(result.isError()));
                var payload = (java.util.Map<?, ?>) result.structuredContent();
                assertTrue(payload.containsKey("currentVersion"));
                assertTrue(payload.containsKey("availableVersion"));
            }
        }
    }

    @Test
    void getVersionReturnsCurrentAndCachedAvailableVersions() {
        try (UpdateService service = new UpdateService("0.9.0-84", dataDirectory)) {
            McpSchema.CallToolResult beforeRefresh = McpToolset.getVersion(service);
            assertFalse(Boolean.TRUE.equals(beforeRefresh.isError()));
            assertEquals("0.9.0-84", ((java.util.Map<?, ?>) beforeRefresh.structuredContent())
                    .get("currentVersion"));
            assertNull(((java.util.Map<?, ?>) beforeRefresh.structuredContent()).get("availableVersion"));
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for update check");
    }
}