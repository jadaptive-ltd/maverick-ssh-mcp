package com.jadaptive.mcp;

import java.nio.file.Path;

/**
 * Lightweight local DeviceStore for MCP runtime usage.
 *
 * Tokens are persisted under ~/.sshteam-mcp to support reuse across sessions.
 */
public final class SshTeamDeviceStore extends AbstractDeviceStore {

    private static final String HSM_KEYSTORE_FILE = "sshteam-mcp-hsm.p12";

    private SshTeamDeviceStore(Path baseDir) {
    	super(baseDir, HSM_KEYSTORE_FILE);
    }

    public static SshTeamDeviceStore defaultStore() {
        return new SshTeamDeviceStore(Path.of(System.getProperty("user.home"), ".sshteam-mcp"));
    }

    static SshTeamDeviceStore forPath(Path baseDir) {
        return new SshTeamDeviceStore(baseDir);
    }
}
