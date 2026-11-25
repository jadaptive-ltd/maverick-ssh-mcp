package com.jadaptive.mcp.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** MCP-compatible OAuth 2.1 authorization server with persistent client registrations. */
public final class McpOAuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Set<String> SUPPORTED_GRANTS = Set.of("authorization_code");
    private static final Set<String> SUPPORTED_RESPONSES = Set.of("code");
    private static final long AUTHORIZATION_LIFETIME_SECONDS = 10 * 60;
    private static final long CODE_LIFETIME_SECONDS = 2 * 60;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final TypeRef<Map<String, Object>> MAP_TYPE = new TypeRef<>() { };

    private final String endpoint;
    private final String configuredIssuer;
    private final OAuthAuthorizationProvider provider;
    private final McpJsonMapper jsonMapper;
    private final OAuthClientStore clientStore;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, OAuthClient> clients = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationTransaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationCode> authorizationCodes = new ConcurrentHashMap<>();
    private final Set<String> accessTokens = ConcurrentHashMap.newKeySet();

    public McpOAuthServlet(
            String endpoint,
            String configuredIssuer,
            OAuthAuthorizationProvider provider,
            McpJsonMapper jsonMapper) {
        this(endpoint, configuredIssuer, provider, jsonMapper,
                Path.of(System.getProperty("user.home"), ".sshteam-mcp"));
    }

    public McpOAuthServlet(
            String endpoint,
            String configuredIssuer,
            OAuthAuthorizationProvider provider,
            McpJsonMapper jsonMapper,
            Path oauthStateDirectory) {
        this.endpoint = endpoint;
        this.configuredIssuer = normalizeIssuer(configuredIssuer);
        this.provider = Objects.requireNonNull(provider, "provider");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.clientStore = new OAuthClientStore(Objects.requireNonNull(oauthStateDirectory, "oauthStateDirectory"), jsonMapper);
        try {
            clients.putAll(clientStore.load());
        }
        catch (IOException ex) {
            throw new IllegalStateException("Unable to load persistent OAuth clients from " + oauthStateDirectory, ex);
        }
    }

    public boolean acceptsAccessToken(String token) {
        return token != null && accessTokens.contains(token);
    }

    public String protectedResourceMetadataUrl(HttpServletRequest request) {
        String resourcePath = endpoint.equals("/") ? "" : endpoint;
        return issuer(request) + "/.well-known/oauth-protected-resource" + resourcePath;
    }

    public String issuer(HttpServletRequest request) {
        return configuredIssuer == null ? requestOrigin(request) : configuredIssuer;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = requestPath(request);
        switch (path) {
            case "/.well-known/oauth-authorization-server",
                    "/.well-known/oauth-protected-resource" -> serveMetadata(request, response);
            case "/oauth/authorize" -> beginAuthorization(request, response);
            case "/oauth/provider/callback" -> continueAuthorization(request, response);
            default -> {
                if (path.startsWith("/.well-known/oauth-protected-resource")) {
                    serveMetadata(request, response);
                }
                else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = requestPath(request);
        switch (path) {
            case "/oauth/register" -> registerClient(request, response);
            case "/oauth/token" -> exchangeToken(request, response);
            case "/oauth/provider/callback" -> continueAuthorization(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        addCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.setHeader("Access-Control-Max-Age", "600");
    }

    private void serveMetadata(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String issuer = issuer(request);
        if (requestPath(request).startsWith("/.well-known/oauth-protected-resource")) {
            writeJson(response, HttpServletResponse.SC_OK, Map.of(
                    "resource", issuer + endpoint,
                    "authorization_servers", List.of(issuer),
                    "bearer_methods_supported", List.of("header"),
                    "scopes_supported", List.of("mcp")));
            return;
        }

        writeJson(response, HttpServletResponse.SC_OK, orderedMetadata(Map.ofEntries(
                Map.entry("issuer", issuer),
                Map.entry("authorization_endpoint", issuer + "/oauth/authorize"),
                Map.entry("token_endpoint", issuer + "/oauth/token"),
                Map.entry("registration_endpoint", issuer + "/oauth/register"),
                Map.entry("response_types_supported", List.copyOf(SUPPORTED_RESPONSES)),
                Map.entry("response_modes_supported", List.of("query")),
                Map.entry("grant_types_supported", List.copyOf(SUPPORTED_GRANTS)),
                Map.entry("token_endpoint_auth_methods_supported", List.of("none")),
                Map.entry("code_challenge_methods_supported", List.of("S256")),
                Map.entry("scopes_supported", List.of("mcp")),
                Map.entry("service_documentation", "https://modelcontextprotocol.io/specification/latest/basic/authorization"))));
    }

    private void registerClient(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> input = readJsonObject(request, response);
        if (input == null) {
            return;
        }

        List<URI> redirectUris;
        try {
            redirectUris = stringList(input.get("redirect_uris"), "redirect_uris").stream()
                    .map(URI::create)
                    .toList();
        }
        catch (IllegalArgumentException ex) {
            oauthJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_redirect_uri", ex.getMessage());
            return;
        }

        if (redirectUris.isEmpty() || redirectUris.stream().anyMatch(this::invalidRedirectUri)) {
            oauthJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_redirect_uri",
                    "redirect_uris must contain absolute URIs without fragments");
            return;
        }

        String clientId = randomToken(24);
        String clientName = optionalString(input.get("client_name"), "MCP client");
        OAuthClient client = new OAuthClient(clientId, clientName, Set.copyOf(redirectUris));
        clients.put(clientId, client);
        try {
            clientStore.save(clients.values());
        }
        catch (IOException ex) {
            clients.remove(clientId, client);
            oauthJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "Unable to persist OAuth client registration");
            return;
        }

        LinkedHashMap<String, Object> registration = new LinkedHashMap<>();
        registration.put("client_id", clientId);
        registration.put("client_name", clientName);
        registration.put("redirect_uris", redirectUris.stream().map(URI::toString).toList());
        registration.put("grant_types", List.copyOf(SUPPORTED_GRANTS));
        registration.put("response_types", List.copyOf(SUPPORTED_RESPONSES));
        registration.put("token_endpoint_auth_method", "none");
        registration.put("client_id_issued_at", Instant.now().getEpochSecond());
        writeJson(response, HttpServletResponse.SC_CREATED, registration);
    }

    private void beginAuthorization(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String clientId = request.getParameter("client_id");
        OAuthClient client = clientId == null ? null : clients.get(clientId);
        if (client == null) {
            oauthError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_request", "Unknown client_id");
            return;
        }

        URI redirectUri = parseUri(request.getParameter("redirect_uri"));
        if (redirectUri == null || !client.redirectUris().contains(redirectUri)) {
            oauthError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_request", "Unregistered redirect_uri");
            return;
        }
        if (!"code".equals(request.getParameter("response_type"))) {
            redirectError(response, redirectUri, request.getParameter("state"), "unsupported_response_type");
            return;
        }
        if (!"S256".equals(request.getParameter("code_challenge_method"))) {
            redirectError(response, redirectUri, request.getParameter("state"), "invalid_request");
            return;
        }
        String codeChallenge = request.getParameter("code_challenge");
        if (!isValidCodeChallenge(codeChallenge)) {
            redirectError(response, redirectUri, request.getParameter("state"), "invalid_request");
            return;
        }

        URI resource = parseUri(request.getParameter("resource"));
        URI expectedResource = URI.create(issuer(request) + endpoint);
        if (resource != null && !expectedResource.equals(resource)) {
            redirectError(response, redirectUri, request.getParameter("state"), "invalid_target");
            return;
        }

        Set<String> scopes = parseScopes(request.getParameter("scope"));
        OAuthAuthorizationRequest authorization = new OAuthAuthorizationRequest(
                client.clientId(), client.clientName(), redirectUri, scopes,
                request.getParameter("state"), resource == null ? expectedResource : resource);
        String transactionId = randomToken(24);
        AuthorizationTransaction transaction = new AuthorizationTransaction(
                transactionId, authorization, codeChallenge,
                Instant.now().plusSeconds(AUTHORIZATION_LIFETIME_SECONDS));
        transactions.put(transactionId, transaction);
        provider.beginAuthorization(authorization, new Flow(transaction, request, response), request, response);
    }

    private void continueAuthorization(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String transactionId = provider.transactionId(request);
        if (transactionId == null || transactionId.isBlank()) {
            oauthError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_request", "Missing authorization transaction");
            return;
        }
        AuthorizationTransaction transaction = transactions.get(transactionId);
        if (transaction == null || transaction.expiresAt().isBefore(Instant.now())) {
            transactions.remove(transactionId);
            oauthError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_request", "Authorization request expired");
            return;
        }
        provider.continueAuthorization(new Flow(transaction, request, response), request, response);
    }

    private void exchangeToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"authorization_code".equals(request.getParameter("grant_type"))) {
            oauthJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "unsupported_grant_type",
                    "Only authorization_code is supported");
            return;
        }

        String codeValue = request.getParameter("code");
        if (codeValue == null || codeValue.isBlank()) {
            oauthJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "Authorization code is required");
            return;
        }
        AuthorizationCode code = authorizationCodes.remove(codeValue);
        if (code == null || code.expiresAt().isBefore(Instant.now())) {
            oauthJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "Authorization code is invalid or expired");
            return;
        }
        if (!Objects.equals(code.clientId(), request.getParameter("client_id"))
                || !Objects.equals(code.redirectUri().toString(), request.getParameter("redirect_uri"))
                || !validVerifier(request.getParameter("code_verifier"), code.codeChallenge())) {
            oauthJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "Authorization code validation failed");
            return;
        }

        String accessToken = randomToken(32);
        accessTokens.add(accessToken);
        LinkedHashMap<String, Object> tokenResponse = new LinkedHashMap<>();
        tokenResponse.put("access_token", accessToken);
        tokenResponse.put("token_type", "Bearer");
        if (!code.scopes().isEmpty()) {
            tokenResponse.put("scope", String.join(" ", code.scopes()));
        }
        writeJson(response, HttpServletResponse.SC_OK, tokenResponse);
    }

    private Map<String, Object> readJsonObject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return jsonMapper.readValue(request.getInputStream().readAllBytes(), MAP_TYPE);
        }
        catch (IOException ex) {
            oauthJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_client_metadata", "Request body must be a JSON object");
            return null;
        }
    }

    private void complete(AuthorizationTransaction transaction, String subject, HttpServletResponse response) throws IOException {
        AuthorizationTransaction removed = transactions.remove(transaction.id());
        if (removed == null) {
            oauthError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_request", "Authorization request is no longer pending");
            return;
        }
        String codeValue = randomToken(32);
        OAuthAuthorizationRequest request = transaction.authorization();
        authorizationCodes.put(codeValue, new AuthorizationCode(
                codeValue, request.clientId(), request.redirectUri(), transaction.codeChallenge(),
                request.scopes(), subject, Instant.now().plusSeconds(CODE_LIFETIME_SECONDS)));
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("code", codeValue);
        if (request.state() != null) {
            parameters.put("state", request.state());
        }
        redirect(response, request.redirectUri(), parameters);
    }

    private void deny(AuthorizationTransaction transaction, String description, HttpServletResponse response) throws IOException {
        transactions.remove(transaction.id());
        OAuthAuthorizationRequest request = transaction.authorization();
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("error", "access_denied");
        if (description != null && !description.isBlank()) {
            parameters.put("error_description", description);
        }
        if (request.state() != null) {
            parameters.put("state", request.state());
        }
        redirect(response, request.redirectUri(), parameters);
    }

    private void writeJson(HttpServletResponse response, int status, Object value) throws IOException {
        addCorsHeaders(response);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(value));
    }

    private void oauthJsonError(HttpServletResponse response, int status, String error, String description) throws IOException {
        writeJson(response, status, Map.of("error", error, "error_description", description));
    }

    private static void oauthError(HttpServletResponse response, int status, String error, String description) throws IOException {
        addCorsHeaders(response);
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(error + ": " + description);
    }

    private static void redirectError(
            HttpServletResponse response,
            URI redirectUri,
            String state,
            String error) throws IOException {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("error", error);
        if (state != null) {
            parameters.put("state", state);
        }
        redirect(response, redirectUri, parameters);
    }

    private static void redirect(HttpServletResponse response, URI redirectUri, Map<String, String> parameters) throws IOException {
        addCorsHeaders(response);
        StringBuilder location = new StringBuilder(redirectUri.toString());
        String separator = redirectUri.getRawQuery() == null ? "?" : "&";
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            location.append(separator)
                    .append(urlEncode(entry.getKey()))
                    .append('=')
                    .append(urlEncode(entry.getValue()));
            separator = "&";
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location.toString());
        response.setHeader("Cache-Control", "no-store");
    }

    private static String requestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }

    private static String requestOrigin(HttpServletRequest request) {
        String forwardedProto = firstValue(request.getHeader("X-Forwarded-Proto"));
        String forwardedHost = firstValue(request.getHeader("X-Forwarded-Host"));
        if (forwardedProto != null && forwardedHost != null) {
            return forwardedProto + "://" + forwardedHost;
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = "http".equalsIgnoreCase(scheme) && port == 80
                || "https".equalsIgnoreCase(scheme) && port == 443;
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private static String firstValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.split(",", 2)[0].trim();
    }

    private static String optionalString(Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private static List<String> stringList(Object value, String field) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty array");
        }
        String[] strings = collection.stream().map(item -> {
            if (!(item instanceof String string) || string.isBlank()) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
            return string;
        }).toArray(String[]::new);
        return Arrays.asList(strings);
    }

    private boolean invalidRedirectUri(URI uri) {
        if (!uri.isAbsolute() || uri.getFragment() != null || uri.getRawUserInfo() != null) {
            return true;
        }
        String scheme = uri.getScheme();
        if ("javascript".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)
                || "file".equalsIgnoreCase(scheme) || "blob".equalsIgnoreCase(scheme)) {
            return true;
        }
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return uri.getHost() == null;
        }
        return uri.isOpaque();
    }

    private static URI parseUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Set<String> parseScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(value.trim().split("\\s+")));
    }

    private static boolean isValidCodeChallenge(String challenge) {
        return challenge != null && challenge.matches("[A-Za-z0-9_-]{43,128}");
    }

    private static boolean validVerifier(String verifier, String expectedChallenge) {
        if (verifier == null || verifier.isEmpty() || verifier.length() > 128 || !verifier.matches("[A-Za-z0-9._~-]+")) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return MessageDigest.isEqual(expectedChallenge.getBytes(StandardCharsets.US_ASCII), URL_ENCODER.encodeToString(digest).getBytes(StandardCharsets.US_ASCII));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return URL_ENCODER.encodeToString(value);
    }

    private static String normalizeIssuer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        if (!uri.isAbsolute() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("OAuth issuer must be an absolute URL without query or fragment");
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("OAuth issuer must be an absolute HTTP(S) URL with a host");
        }
        return normalized;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> orderedMetadata(Map<String, ?> values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.putAll(values);
        return result;
    }

    private static void addCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, MCP-Protocol-Version");
        response.setHeader("Access-Control-Expose-Headers", "WWW-Authenticate, Location");
    }

    private final class Flow implements OAuthAuthorizationFlow {

        private final AuthorizationTransaction transaction;
        private final HttpServletRequest request;
        private final HttpServletResponse response;

        private Flow(
                AuthorizationTransaction transaction,
                HttpServletRequest request,
                HttpServletResponse response) {
            this.transaction = transaction;
            this.request = request;
            this.response = response;
        }

        @Override
        public OAuthAuthorizationRequest authorizationRequest() {
            return transaction.authorization();
        }

        @Override
        public String transactionId() {
            return transaction.id();
        }

        @Override
        public URI callbackUri() {
            return URI.create(issuer(request) + "/oauth/provider/callback?transaction_id=" + urlEncode(transaction.id()));
        }

        @Override
        public void approve(String subject) throws IOException {
            complete(transaction, subject, response);
        }

        @Override
        public void deny(String description) throws IOException {
            McpOAuthServlet.this.deny(transaction, description, response);
        }
    }

    private record AuthorizationTransaction(
            String id,
            OAuthAuthorizationRequest authorization,
            String codeChallenge,
            Instant expiresAt) { }

    private record AuthorizationCode(
            String code,
            String clientId,
            URI redirectUri,
            String codeChallenge,
            Set<String> scopes,
            String subject,
            Instant expiresAt) { }
}