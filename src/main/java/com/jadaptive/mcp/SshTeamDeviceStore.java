package com.jadaptive.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jadaptive.hsm.encrypt.HsmEncryptionProvider;
import com.jadaptive.hsm.encrypt.SoftwareEncryptionProvider;
import com.sshteam.lib.DeviceCredentials;
import com.sshteam.lib.DeviceStore;

/**
 * Lightweight local DeviceStore for MCP runtime usage.
 *
 * Tokens are persisted under ~/.sshteam-mcp to support reuse across sessions.
 */
final class SshTeamDeviceStore implements DeviceStore {

    private static final String DEFAULTS_FILE = "defaults.json";
    private static final String CONFIG_FILE = "config.json";
    private static final String DPOP_KEY_FILE = "dpop-key.enc";
    private static final String HSM_KEYSTORE_FILE = "sshteam-mcp-hsm.p12";
    private static final String HSM_ALIAS = "sshteam-mcp";
    private static final String HSM_PASSWORD = "changeit-changeit";
    private static final String ENC_PREFIX = "enc::";

    private final Path baseDir;
    private final ObjectMapper mapper;

    private SshTeamDeviceStore(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    static SshTeamDeviceStore defaultStore() {
        return new SshTeamDeviceStore(Path.of(System.getProperty("user.home"), ".sshteam-mcp"));
    }

    static SshTeamDeviceStore forPath(Path baseDir) {
        return new SshTeamDeviceStore(baseDir);
    }

    @Override
    public void initServer(String serverUrl) throws IOException {
        ensureBaseDirExists();
        Path dir = serverDir(serverUrl);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    @Override
    public void saveCredentials(DeviceCredentials credentials) throws Exception {
        StoredCredentials stored = new StoredCredentials(
                credentials.serverUrl(),
                encryptToken(credentials.serverUrl(), credentials.accessToken()),
                encryptToken(credentials.serverUrl(), credentials.refreshToken()),
                credentials.accessTokenExpiresAt(),
                credentials.keyId());
        Path file = serverDir(credentials.serverUrl()).resolve(CONFIG_FILE);
        mapper.writeValue(file.toFile(), stored);
        restrictPermissions(file);
    }

    @Override
    public Optional<DeviceCredentials> loadCredentials(String serverUrl) throws Exception {
        Path file = serverDir(serverUrl).resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        StoredCredentials stored = mapper.readValue(file.toFile(), StoredCredentials.class);
        return Optional.of(new DeviceCredentials(
                stored.serverUrl(),
                decryptToken(serverUrl, stored.accessToken()),
                decryptToken(serverUrl, stored.refreshToken()),
                stored.accessTokenExpiresAt(),
                stored.keyId()));
    }

    @Override
    public void saveDpopPrivateKey(String serverUrl, String privateKeyJwk) throws Exception {
        Path file = serverDir(serverUrl).resolve(DPOP_KEY_FILE);
        Files.writeString(file, buildHsm(serverUrl).encrypt(privateKeyJwk));
        restrictPermissions(file);
    }

    @Override
    public String loadDpopPrivateKey(String serverUrl) throws Exception {
        Path file = serverDir(serverUrl).resolve(DPOP_KEY_FILE);
        if (!Files.exists(file)) {
            throw new IllegalStateException(
                    "DPoP key not found for " + serverUrl + " - run sshteam_register first");
        }
        return buildHsm(serverUrl).decrypt(Files.readString(file));
    }

    @Override
    public void setDefaultServer(String serverUrl) throws IOException {
        ensureBaseDirExists();
        Path file = baseDir.resolve(DEFAULTS_FILE);
        mapper.writeValue(file.toFile(), new DefaultsRecord(serverUrl));
        restrictPermissions(file);
    }

    @Override
    public Optional<String> getDefaultServer() throws IOException {
        Path file = baseDir.resolve(DEFAULTS_FILE);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        DefaultsRecord rec = mapper.readValue(file.toFile(), DefaultsRecord.class);
        return Optional.ofNullable(rec.defaultServer()).filter(s -> !s.isBlank());
    }

    @Override
    public List<String> listServers() throws IOException {
        if (!Files.exists(baseDir)) {
            return List.of();
        }
        List<String> servers = new ArrayList<>();
        try (var stream = Files.list(baseDir)) {
            for (Path entry : stream.toList()) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                Path config = entry.resolve(CONFIG_FILE);
                if (!Files.exists(config)) {
                    continue;
                }
                try {
                    StoredCredentials stored = mapper.readValue(config.toFile(), StoredCredentials.class);
                    if (stored.serverUrl() != null && !stored.serverUrl().isBlank()) {
                        servers.add(stored.serverUrl());
                    }
                }
                catch (IOException ignored) {
                }
            }
        }
        return List.copyOf(servers);
    }

    private Path serverDir(String serverUrl) {
        return baseDir.resolve(toServerKey(serverUrl));
    }

    private static String toServerKey(String serverUrl) {
        String s = serverUrl;
        if (s.startsWith("https://")) {
            s = s.substring(8);
        }
        else if (s.startsWith("http://")) {
            s = s.substring(7);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.replace(':', '_').replace('/', '_');
    }

    private void ensureBaseDirExists() throws IOException {
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }
    }

    private HsmEncryptionProvider buildHsm(String serverUrl) throws Exception {
        Path serverDirectory = serverDir(serverUrl);
        HsmEncryptionProvider provider = SoftwareEncryptionProvider.builder()
                .setEnabled(true)
                .setKeystoreDir(serverDirectory.toString())
                .setKeystoreSubdir("")
                .setKeystoreFilename(HSM_KEYSTORE_FILE)
                .setKeystoreAlias(HSM_ALIAS)
                .setKeystorePassword(HSM_PASSWORD)
                .build();
        provider.init();
        return provider;
    }

    private String encryptToken(String serverUrl, String token) throws Exception {
        if (token == null || token.isBlank()) {
            return token;
        }
        return ENC_PREFIX + buildHsm(serverUrl).encrypt(token);
    }

    private String decryptToken(String serverUrl, String token) throws Exception {
        if (token == null || token.isBlank()) {
            return token;
        }
        if (!token.startsWith(ENC_PREFIX)) {
            return token;
        }
        return buildHsm(serverUrl).decrypt(token.substring(ENC_PREFIX.length()));
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class StoredCredentials {
        @JsonProperty
        String serverUrl;
        @JsonProperty
        String accessToken;
        @JsonProperty
        String refreshToken;
        @JsonProperty
        long accessTokenExpiresAt;
        @JsonProperty
        String keyId;

        StoredCredentials() {
        }

        StoredCredentials(String serverUrl, String accessToken, String refreshToken, long accessTokenExpiresAt,
                String keyId) {
            this.serverUrl = serverUrl;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.accessTokenExpiresAt = accessTokenExpiresAt;
            this.keyId = keyId;
        }

        String serverUrl() {
            return serverUrl;
        }

        String accessToken() {
            return accessToken;
        }

        String refreshToken() {
            return refreshToken;
        }

        long accessTokenExpiresAt() {
            return accessTokenExpiresAt;
        }

        String keyId() {
            return keyId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DefaultsRecord {
        @JsonProperty
        String defaultServer;

        DefaultsRecord() {
        }

        DefaultsRecord(String defaultServer) {
            this.defaultServer = defaultServer;
        }

        String defaultServer() {
            return defaultServer;
        }
    }
}
