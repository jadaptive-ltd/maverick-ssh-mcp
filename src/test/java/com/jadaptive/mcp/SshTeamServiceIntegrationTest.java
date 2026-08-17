package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class SshTeamServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void registerAndRevokeDeviceAgainstMockApi() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/device_authorization", ex -> json(ex, 200,
                "{\"device_code\":\"dev-1\",\"user_code\":\"ABCD\",\"verification_uri\":\"http://127.0.0.1/verify\",\"verification_uri_complete\":\"http://127.0.0.1/verify?user_code=ABCD\",\"expires_in\":120}"));
        server.createContext("/oauth2/token", ex -> json(ex, 200,
                "{\"access_token\":\"token-1\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600}"));
        server.createContext("/api/v1/devices/device-1", ex -> {
            if (!"DELETE".equals(ex.getRequestMethod())) {
                json(ex, 405, "{}");
                return;
            }
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        SshTeamDeviceStore store = SshTeamDeviceStore.forPath(tempDir);
        SshTeamService service = new SshTeamService(store);

        SshTeamService.RegistrationResult result = service.register(
                baseUrl,
                "sshteam-cli",
                "signing",
                "mcp-test",
                1,
                15,
                true,
                false,
                true);

        assertEquals("authorized", result.status);
        assertTrue(service.isRegistered(baseUrl));

        int revokeStatus = service.revokeDevice(baseUrl, false, "device-1");
        assertEquals(204, revokeStatus);
    }

    @Test
    void registerThenPollAuthorizesWithDeviceCode() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/device_authorization", ex -> json(ex, 200,
                "{\"device_code\":\"dev-2\",\"user_code\":\"WXYZ\",\"verification_uri\":\"http://127.0.0.1/verify\",\"verification_uri_complete\":\"http://127.0.0.1/verify?user_code=WXYZ\",\"expires_in\":120}"));
        server.createContext("/oauth2/token", ex -> json(ex, 200,
                "{\"access_token\":\"token-2\",\"refresh_token\":\"refresh-2\",\"expires_in\":3600}"));
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        SshTeamDeviceStore store = SshTeamDeviceStore.forPath(tempDir);
        SshTeamService service = new SshTeamService(store);

        SshTeamService.RegistrationResult first = service.register(
                baseUrl,
                "sshteam-cli",
                "signing",
                "mcp-test",
                1,
                15,
                false,
                false,
                true);

        assertEquals("authorization_pending", first.status);
        assertEquals("WXYZ", first.userCode);
        assertEquals("dev-2", first.deviceCode);

        SshTeamService.RegistrationResult second = service.pollRegistration(
                baseUrl,
                "sshteam-cli",
                first.deviceCode,
                first.verificationUri,
                first.userCode,
                1,
                15,
                false,
                true);

        assertEquals("authorized", second.status);
        assertTrue(service.isRegistered(baseUrl));
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
