package com.jadaptive.mcp;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.security.Provider;
import java.security.Security;

import com.fasterxml.jackson.databind.JsonNode;
import com.sshteam.lib.DeviceCredentials;
import com.sshteam.lib.DpopSigner;
import com.sshteam.lib.ServerUrlNormalizer;
import com.sshteam.lib.SshteamHttpClient;

final class SshTeamService {

    private final SshTeamDeviceStore store;

    SshTeamService(SshTeamDeviceStore store) {
        this.store = store;
        ensureBouncyCastleProvider();
    }

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider("BC") != null) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            Provider provider = (Provider) clazz.getDeclaredConstructor().newInstance();
            Security.addProvider(provider);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to load Bouncy Castle provider", e);
        }
    }

    RegistrationResult register(String serverUrl, String clientId, String scope, String deviceName,
            int pollIntervalSeconds, int maxWaitSeconds, boolean waitForAuthorization,
            boolean ignoreSslTrust, boolean setDefault) throws Exception {
        String normalizedServerUrl = ServerUrlNormalizer.normalize(serverUrl);
        store.initServer(normalizedServerUrl);

        SshteamHttpClient client = new SshteamHttpClient(normalizedServerUrl, ignoreSslTrust);
        
        /* TODO hrm... looks importantion ...  why is this here with unused variable? */
        DpopSigner signer = DpopSigner.generate();

        JsonNode authResponse = client.deviceAuthorize(clientId, scope, deviceName);
        String deviceCode = text(authResponse, "device_code");
        String userCode = text(authResponse, "user_code");
//        String verificationUri = text(authResponse, "verification_uri");
        String verificationUriComplete = authResponse.path("verification_uri_complete").asText();
        long expiresIn = authResponse.path("expires_in").asLong(600L);

        if (!waitForAuthorization) {
            return RegistrationResult.pending(
                    normalizedServerUrl,
                    verificationUriComplete,
                    userCode,
                    deviceCode,
                    "Open verificationUri, complete approval, then call sshteam_register_poll with deviceCode.");
        }

        return pollRegistration(normalizedServerUrl, clientId, deviceCode, verificationUriComplete, userCode,
                pollIntervalSeconds, maxWaitSeconds, ignoreSslTrust, setDefault);
    }

    RegistrationResult pollRegistration(String serverUrl, String clientId, String deviceCode,
            String verificationUri, String userCode,
            int pollIntervalSeconds, int maxWaitSeconds,
            boolean ignoreSslTrust, boolean setDefault) throws Exception {
        String normalizedServerUrl = ServerUrlNormalizer.normalize(serverUrl);
        store.initServer(normalizedServerUrl);

        SshteamHttpClient client = new SshteamHttpClient(normalizedServerUrl, ignoreSslTrust);
        DpopSigner signer = DpopSigner.generate();

        long waitBudgetMs = Math.max(5, maxWaitSeconds) * 1000L;
        long deadline = System.currentTimeMillis() + waitBudgetMs;

        JsonNode tokenResponse = null;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(1, pollIntervalSeconds) * 1000L);
            String dpopProof = signer.createProof("POST", client.tokenEndpointUrl());
            JsonNode pollResult = client.pollToken(clientId, deviceCode, dpopProof);
            if (pollResult.has("access_token")) {
                tokenResponse = pollResult;
                break;
            }

            String error = pollResult.path("error").asText();
            if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                continue;
            }
            if ("access_denied".equals(error)) {
                return RegistrationResult.denied(normalizedServerUrl, verificationUri, userCode, deviceCode,
                        "Authorization was denied by the user.");
            }
            if ("expired_token".equals(error)) {
                return RegistrationResult.expired(normalizedServerUrl, verificationUri, userCode, deviceCode,
                        "Authorization code expired. Re-run sshteam_register.");
            }
            return RegistrationResult.error(normalizedServerUrl, verificationUri, userCode, deviceCode,
                    "Unexpected registration error: " + error);
        }

        if (tokenResponse == null) {
            return RegistrationResult.pending(normalizedServerUrl, verificationUri, userCode, deviceCode,
                    "Authorization still pending. Complete approval and call sshteam_register_poll again.");
        }

        long now = Instant.now().getEpochSecond();
        long expiresInSecs = tokenResponse.path("expires_in").asLong(86400L);
        String accessToken = text(tokenResponse, "access_token");
        String refreshToken = tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").asText() : null;

        store.saveDpopPrivateKey(normalizedServerUrl, signer.toPrivateKeyJwkJson());
        store.saveCredentials(new DeviceCredentials(
                normalizedServerUrl,
                accessToken,
                refreshToken,
                now + expiresInSecs,
                signer.getKeyId()));

        boolean defaultUpdated = false;
        if (setDefault || store.getDefaultServer().isEmpty()) {
            store.setDefaultServer(normalizedServerUrl);
            defaultUpdated = true;
        }

        return RegistrationResult.authorized(normalizedServerUrl, verificationUri, userCode, deviceCode,
                now + expiresInSecs, signer.getKeyId(), defaultUpdated);
    }

    List<Map<String, Object>> listRegistrations(String preferredServerUrl) throws Exception {
        Optional<String> defaultServer = store.getDefaultServer();
        List<String> servers;
        if (preferredServerUrl != null && !preferredServerUrl.isBlank()) {
            servers = List.of(resolveServer(preferredServerUrl));
        }
        else {
            servers = store.listServers();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String server : servers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("server", server);
            row.put("default", server.equals(defaultServer.orElse(null)));

            Optional<DeviceCredentials> creds = store.loadCredentials(server);
            if (creds.isEmpty()) {
                row.put("registered", false);
            }
            else {
                DeviceCredentials credentials = creds.get();
                row.put("registered", true);
                row.put("keyId", credentials.keyId());
                row.put("accessTokenStatus", tokenStatus(credentials));
                row.put("accessTokenExpiresAt", credentials.accessTokenExpiresAt());
            }
            rows.add(row);
        }
        return rows;
    }

    int revokeDevice(String preferredServerUrl, boolean ignoreSslTrust, String deviceId) throws Exception {
        String resolvedServer = resolveServer(preferredServerUrl);
        DpopSigner signer = store.loadDpopSigner(resolvedServer);
        SshteamHttpClient client = new SshteamHttpClient(resolvedServer, ignoreSslTrust);
        String accessToken = store.getCurrentAccessToken(resolvedServer, signer, ignoreSslTrust);
        String dpopProof = signer.createProof("DELETE", client.revokeDeviceUrl(deviceId), accessToken);
        return client.revokeDevice(deviceId, accessToken, dpopProof);
    }

    String requestCertificate(String preferredServerUrl, boolean ignoreSslTrust, String principal,
            String serverFingerprint, String publicKey, String certificateType, String timezone) throws Exception {
        String resolvedServer = resolveServer(preferredServerUrl);
        DpopSigner signer = store.loadDpopSigner(resolvedServer);
        SshteamHttpClient client = new SshteamHttpClient(resolvedServer, ignoreSslTrust);
        String accessToken = store.getCurrentAccessToken(resolvedServer, signer, ignoreSslTrust);

        String requestTimezone = (timezone == null || timezone.isBlank())
                ? ZoneId.systemDefault().getId()
                : timezone;

        String dpopProof = signer.createProof("POST", client.signEndpointUrl(), accessToken);
        JsonNode response = client.sign(
                accessToken,
                dpopProof,
                publicKey,
                principal,
                serverFingerprint,
                requestTimezone,
                certificateType == null || certificateType.isBlank() ? "ED25519" : certificateType);

        String certificate = response.path("certificate").asText().trim();
        if (certificate.isEmpty()) {
            String error = response.path("error").asText();
            throw new IllegalStateException(error);
        }
        return certificate;
    }

    boolean isRegistered(String preferredServerUrl) {
        try {
            String resolvedServer = resolveServer(preferredServerUrl);
            return store.loadCredentials(resolvedServer).isPresent();
        }
        catch (Exception e) {
            return false;
        }
    }

    String resolveServer(String preferredServerUrl) throws Exception {
        return store.resolveServer(preferredServerUrl);
    }

    private static String text(JsonNode node, String key) {
        String value = node.path(key).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("Missing expected field: " + key);
        }
        return value;
    }

    private static String tokenStatus(DeviceCredentials credentials) {
        if (credentials.accessToken() == null || credentials.accessToken().isBlank()) {
            return "missing";
        }
        long now = Instant.now().getEpochSecond();
        long remaining = credentials.accessTokenExpiresAt() - now;
        if (remaining <= 0) {
            return credentials.refreshToken() == null || credentials.refreshToken().isBlank()
                    ? "expired"
                    : "expired-refresh-available";
        }
        return "valid";
    }

    static final class RegistrationResult {
        final String status;
        final String server;
        final String verificationUri;
        final String userCode;
        final String deviceCode;
        final String message;
        final Long accessTokenExpiresAt;
        final String keyId;
        final Boolean defaultServerUpdated;

        private RegistrationResult(String status, String server, String verificationUri,
                String userCode, String deviceCode, String message,
                Long accessTokenExpiresAt, String keyId, Boolean defaultServerUpdated) {
            this.status = status;
            this.server = server;
            this.verificationUri = verificationUri;
            this.userCode = userCode;
            this.deviceCode = deviceCode;
            this.message = message;
            this.accessTokenExpiresAt = accessTokenExpiresAt;
            this.keyId = keyId;
            this.defaultServerUpdated = defaultServerUpdated;
        }

        static RegistrationResult authorized(String server, String verificationUri,
                String userCode, String deviceCode, long accessTokenExpiresAt,
                String keyId, boolean defaultServerUpdated) {
            return new RegistrationResult("authorized", server, verificationUri, userCode, deviceCode,
                    "Device authorized and credentials saved.", accessTokenExpiresAt, keyId, defaultServerUpdated);
        }

        static RegistrationResult pending(String server, String verificationUri,
                String userCode, String deviceCode, String message) {
            return new RegistrationResult("authorization_pending", server, verificationUri,
                    userCode, deviceCode, message, null, null, null);
        }

        static RegistrationResult denied(String server, String verificationUri,
                String userCode, String deviceCode, String message) {
            return new RegistrationResult("access_denied", server, verificationUri,
                    userCode, deviceCode, message, null, null, null);
        }

        static RegistrationResult expired(String server, String verificationUri,
                String userCode, String deviceCode, String message) {
            return new RegistrationResult("expired_token", server, verificationUri,
                    userCode, deviceCode, message, null, null, null);
        }

        static RegistrationResult error(String server, String verificationUri,
                String userCode, String deviceCode, String message) {
            return new RegistrationResult("error", server, verificationUri,
                    userCode, deviceCode, message, null, null, null);
        }
    }
}
