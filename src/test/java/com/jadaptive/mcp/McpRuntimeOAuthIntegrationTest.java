package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpRuntimeOAuthIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Pattern CLIENT_ID = Pattern.compile("\"client_id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");
    private static final Pattern REGISTRATION_ENDPOINT = Pattern.compile("\"registration_endpoint\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    @TempDir
    Path tempDir;

    @Test
    void completesOAuthAuthorizationCodeFlowAndAuthenticatesMcp() throws Exception {
        try (McpRuntime runtime = oauthRuntime()) {
            runtime.start();
            String origin = "http://127.0.0.1:" + runtime.httpPort();

            HttpResponse<String> unauthorized = get(origin + "/mcp");
            assertEquals(401, unauthorized.statusCode());
            assertTrue(unauthorized.headers().firstValue("WWW-Authenticate").orElseThrow()
                    .contains("/.well-known/oauth-protected-resource/mcp"));

            HttpResponse<String> protectedMetadata = get(origin + "/.well-known/oauth-protected-resource/mcp");
            assertEquals(200, protectedMetadata.statusCode());
            assertTrue(protectedMetadata.body().contains("\"resource\":\"" + origin + "/mcp\""));

            HttpResponse<String> serverMetadata = get(origin + "/.well-known/oauth-authorization-server");
            assertEquals(200, serverMetadata.statusCode());
            assertEquals("*", serverMetadata.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertTrue(serverMetadata.body().contains("/oauth/register"));
            assertTrue(serverMetadata.body().contains("S256"));
            String registrationEndpoint = extract(REGISTRATION_ENDPOINT, serverMetadata.body());
            assertEquals(origin + "/oauth/register", registrationEndpoint);

            HttpResponse<String> metadataPreflight = preflight(origin + "/.well-known/oauth-authorization-server");
            assertEquals(204, metadataPreflight.statusCode());
            assertEquals("*", metadataPreflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertTrue(metadataPreflight.headers().firstValue("Access-Control-Allow-Headers").orElseThrow()
                    .contains("MCP-Protocol-Version"));

            HttpResponse<String> registerPreflight = preflight(registrationEndpoint);
            assertEquals(204, registerPreflight.statusCode());

            HttpResponse<String> mcpPreflight = preflight(origin + "/mcp");
            assertEquals(204, mcpPreflight.statusCode());

            String redirectUri = "http://127.0.0.1:49999/callback";
            HttpResponse<String> registration = postJson(registrationEndpoint, """
                    {"client_name":"OAuth test","redirect_uris":["%s"],"token_endpoint_auth_method":"none"}
                    """.formatted(redirectUri));
            assertEquals(201, registration.statusCode());
            String clientId = extract(CLIENT_ID, registration.body());

            String verifier = "a".repeat(64);
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            String authorizeUrl = origin + "/oauth/authorize?" + form(Map.of(
                    "client_id", clientId,
                    "redirect_uri", redirectUri,
                    "response_type", "code",
                    "state", "state-123",
                    "scope", "mcp",
                    "resource", origin + "/mcp",
                    "code_challenge", challenge,
                    "code_challenge_method", "S256"));
            HttpResponse<String> login = get(authorizeUrl);
            assertEquals(200, login.statusCode());
            assertTrue(login.body().contains("OAuth test"));
            String callback = extract(FORM_ACTION, login.body()).replace("&amp;", "&");

            HttpResponse<String> rejectedPassword = postForm(callback, form(Map.of(
                    "password", "wrong-token",
                    "decision", "allow")));
            assertEquals(200, rejectedPassword.statusCode());
            assertTrue(rejectedPassword.body().contains("password is incorrect"));

            HttpResponse<String> approval = postForm(callback, form(Map.of(
                    "password", "test-token",
                    "decision", "allow")));
            assertEquals(302, approval.statusCode());
            Map<String, String> authorizationResult = query(approval.headers().firstValue("Location").orElseThrow());
            assertEquals("state-123", authorizationResult.get("state"));
            assertNotNull(authorizationResult.get("code"));

            HttpResponse<String> token = postForm(origin + "/oauth/token", form(Map.of(
                    "grant_type", "authorization_code",
                    "client_id", clientId,
                    "redirect_uri", redirectUri,
                    "code", authorizationResult.get("code"),
                    "code_verifier", verifier)));
            assertEquals(200, token.statusCode());
            String accessToken = extract(ACCESS_TOKEN, token.body());

            HttpResponse<String> authenticated = sendMcp(origin, accessToken);
            assertNotEquals(401, authenticated.statusCode());

            HttpResponse<String> reusedCode = postForm(origin + "/oauth/token", form(Map.of(
                    "grant_type", "authorization_code",
                    "client_id", clientId,
                    "redirect_uri", redirectUri,
                    "code", authorizationResult.get("code"),
                    "code_verifier", verifier)));
            assertEquals(400, reusedCode.statusCode());
        }
    }

    @Test
    void supportsExplicitRejectionAndKeepsLegacyBearerToken() throws Exception {
        try (McpRuntime runtime = oauthRuntime()) {
            runtime.start();
            String origin = "http://127.0.0.1:" + runtime.httpPort();
            String redirectUri = "http://127.0.0.1:49999/callback";
            String clientId = extract(CLIENT_ID, postJson(origin + "/oauth/register", """
                    {"redirect_uris":["%s"]}
                    """.formatted(redirectUri)).body());
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest("verifier".getBytes(StandardCharsets.US_ASCII)));
            String authorizeUrl = origin + "/oauth/authorize?" + form(Map.of(
                    "client_id", clientId,
                    "redirect_uri", redirectUri,
                    "response_type", "code",
                    "state", "reject-state",
                    "code_challenge", challenge,
                    "code_challenge_method", "S256"));
            String callback = extract(FORM_ACTION, get(authorizeUrl).body());
            HttpResponse<String> rejection = postForm(callback, "decision=reject");
            assertEquals(302, rejection.statusCode());
            Map<String, String> result = query(rejection.headers().firstValue("Location").orElseThrow());
            assertEquals("access_denied", result.get("error"));
            assertEquals("reject-state", result.get("state"));
            assertFalse(result.containsKey("code"));

            assertNotEquals(401, sendMcp(origin, "test-token").statusCode());
        }
    }

    private McpRuntime oauthRuntime() {
        return new McpRuntime(
                new SshTeamService(SshTeamDeviceStore.forPath(tempDir)),
                new DumbToolGate(),
                McpRuntime.Mode.HTTP,
                "127.0.0.1",
                0,
                "/mcp",
                "test-token");
    }

    private static HttpResponse<String> get(String uri) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> preflight(String uri) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(uri))
                .header("Origin", "http://127.0.0.1:6274")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,mcp-protocol-version")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(String uri, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postForm(String uri, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendMcp(String origin, String token) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(origin + "/mcp"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        assertTrue(matcher.find(), () -> "No match for " + pattern + " in " + body);
        return matcher.group(1);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> query(String uri) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String raw = URI.create(uri).getRawQuery();
        if (raw == null) {
            return result;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }
}