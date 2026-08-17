package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sshteam.lib.DeviceCredentials;

class SshTeamDeviceStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void encryptsTokensAndDpopKeyAtRest() throws Exception {
        SshTeamDeviceStore store = SshTeamDeviceStore.forPath(tempDir);
        String server = "https://sshteam.example.test";

        store.initServer(server);
        store.saveDpopPrivateKey(server, "{\"kty\":\"EC\"}");
        store.saveCredentials(new DeviceCredentials(server, "access-token", "refresh-token", 2000000000L, "kid-1"));

        String serverKey = "sshteam.example.test";
        String dpopRaw = Files.readString(tempDir.resolve(serverKey).resolve("dpop-key.enc"));
        String credsRaw = Files.readString(tempDir.resolve(serverKey).resolve("config.json"));

        assertFalse(dpopRaw.contains("\"kty\""));
        assertTrue(credsRaw.contains("enc::"));
        assertFalse(credsRaw.contains("access-token"));
        assertFalse(credsRaw.contains("refresh-token"));

        assertEquals("{\"kty\":\"EC\"}", store.loadDpopPrivateKey(server));
        DeviceCredentials loaded = store.loadCredentials(server).orElseThrow();
        assertEquals("access-token", loaded.accessToken());
        assertEquals("refresh-token", loaded.refreshToken());
        assertEquals("kid-1", loaded.keyId());
    }
}
