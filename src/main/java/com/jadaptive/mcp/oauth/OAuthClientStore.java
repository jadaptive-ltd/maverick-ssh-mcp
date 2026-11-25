package com.jadaptive.mcp.oauth;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

/** Persists dynamically registered OAuth clients independently of short-lived grants. */
final class OAuthClientStore {

    private static final String FILE_NAME = "oauth-clients.json";
    private static final TypeRef<List<Map<String, Object>>> CLIENT_LIST_TYPE = new TypeRef<>() { };

    private final Path stateFile;
    private final McpJsonMapper jsonMapper;

    OAuthClientStore(Path stateDirectory, McpJsonMapper jsonMapper) {
        this.stateFile = stateDirectory.resolve(FILE_NAME);
        this.jsonMapper = jsonMapper;
    }

    synchronized Map<String, OAuthClient> load() throws IOException {
        if (!Files.exists(stateFile)) {
            return Map.of();
        }

        List<Map<String, Object>> stored = jsonMapper.readValue(Files.readAllBytes(stateFile), CLIENT_LIST_TYPE);
        if (stored == null) {
            throw new IOException("OAuth client store must contain a JSON array");
        }

        Map<String, OAuthClient> clients = new LinkedHashMap<>();
        for (Map<String, Object> entry : stored) {
            OAuthClient client = decode(entry);
            clients.put(client.clientId(), client);
        }
        return clients;
    }

    synchronized void save(Collection<OAuthClient> clients) throws IOException {
        Files.createDirectories(stateFile.getParent());
        Path temporary = Files.createTempFile(stateFile.getParent(), FILE_NAME, ".tmp");
        try {
            List<Map<String, Object>> stored = clients.stream().map(OAuthClientStore::encode).toList();
            Files.write(temporary, jsonMapper.writeValueAsBytes(stored));
            restrictPermissions(temporary);
            try {
                Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(stateFile);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static OAuthClient decode(Map<String, Object> stored) throws IOException {
        try {
            String clientId = requiredString(stored.get("client_id"), "client_id");
            String clientName = requiredString(stored.get("client_name"), "client_name");
            Object rawRedirectUris = stored.get("redirect_uris");
            if (!(rawRedirectUris instanceof Collection<?> values) || values.isEmpty()) {
                throw new IOException("Stored OAuth client redirect_uris must be a non-empty array");
            }
            Set<URI> redirectUris = values.stream()
                    .map(value -> {
                        if (!(value instanceof String uri) || uri.isBlank()) {
                            throw new IllegalArgumentException("Stored OAuth client redirect_uri must be a string");
                        }
                        return URI.create(uri);
                    })
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new OAuthClient(clientId, clientName, redirectUris);
        }
        catch (IllegalArgumentException ex) {
            throw new IOException("Invalid OAuth client in persistent store", ex);
        }
    }

    private static Map<String, Object> encode(OAuthClient client) {
        LinkedHashMap<String, Object> stored = new LinkedHashMap<>();
        stored.put("client_id", client.clientId());
        stored.put("client_name", client.clientName());
        stored.put("redirect_uris", client.redirectUris().stream().map(URI::toString).toList());
        return stored;
    }

    private static String requiredString(Object value, String field) throws IOException {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IOException("Stored OAuth client field " + field + " must be a string");
        }
        return string;
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        catch (UnsupportedOperationException | IOException ignored) {
        }
    }
}

record OAuthClient(String clientId, String clientName, Set<URI> redirectUris) { }
