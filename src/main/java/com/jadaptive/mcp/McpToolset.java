package com.jadaptive.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sshtools.client.KeyboardInteractiveAuthenticator;
import com.sshtools.client.PasswordAuthenticator;
import com.sshtools.client.PasswordOverKeyboardInteractiveCallback;
import com.sshtools.client.PrivateKeyFileAuthenticator;
import com.sshtools.client.SessionChannelNG;
import com.sshtools.client.SshClient;
import com.sshtools.client.SshClient.SshClientBuilder;
import com.sshtools.client.scp.ScpClient;
import com.sshtools.client.sftp.SftpChannel;
import com.sshtools.client.sftp.SftpClient;
import com.sshtools.client.sftp.SftpFile;
import com.sshtools.client.sftp.SftpHandle;
import com.sshtools.client.tasks.ShellTask;
import com.sshtools.common.forwarding.ForwardingHandle;
import com.sshtools.common.forwarding.ForwardingPolicy.ForwardingPolicyBuilder;
import com.sshtools.common.forwarding.ForwardingRequest.ForwardingType;
import com.sshtools.common.forwarding.ForwardingRequest.Protocol;
import com.sshtools.common.forwarding.ForwardingRequest.ForwardingRequestBuilder;
import com.sshtools.common.permissions.UnauthorizedException;
import com.sshtools.common.sftp.PosixPermissions;
import com.sshtools.common.sftp.PosixPermissions.PosixPermissionsBuilder;
import com.sshtools.common.sftp.SftpFileAttributes;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.RequestFuture;
import com.sshtools.common.ssh.SshException;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema;

final class McpToolset {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private McpToolset() {
    }

    static void register(McpServer.SyncSpecification<?> spec, HandleRegistry registry, DestructivePolicy destructivePolicy,
            SshTeamService sshTeamService) {
        spec.tool(tool("sshteam_register", "Register this MCP runtime as an SSH Team device using OAuth device flow.",
                """
                {"type":"object","required":["serverUrl"],"properties":{"serverUrl":{"type":"string"},"clientId":{"type":"string","default":"sshteam-cli"},"scope":{"type":"string","default":"signing"},"deviceName":{"type":"string"},"pollIntervalSeconds":{"type":"integer","default":5},"maxWaitSeconds":{"type":"integer","default":600},"waitForAuthorization":{"type":"boolean","default":false},"ignoreSslTrust":{"type":"boolean","default":false},"setDefault":{"type":"boolean","default":true}}}
                """),
                (exchange, args) -> sshteamRegister(args, sshTeamService));

        spec.tool(tool("sshteam_status", "List local SSH Team registrations and token state.",
                """
                {"type":"object","properties":{"serverUrl":{"type":"string"}}}
                """),
                (exchange, args) -> sshteamStatus(args, sshTeamService));

        spec.tool(tool("sshteam_register_poll", "Poll SSH Team OAuth device flow using a previously returned device code.",
                """
                {"type":"object","required":["serverUrl","deviceCode"],"properties":{"serverUrl":{"type":"string"},"deviceCode":{"type":"string"},"clientId":{"type":"string","default":"sshteam-cli"},"verificationUri":{"type":"string"},"userCode":{"type":"string"},"pollIntervalSeconds":{"type":"integer","default":5},"maxWaitSeconds":{"type":"integer","default":600},"ignoreSslTrust":{"type":"boolean","default":false},"setDefault":{"type":"boolean","default":true}}}
                """),
                (exchange, args) -> sshteamRegisterPoll(args, sshTeamService));

        spec.tool(tool("sshteam_revoke_device", "Revoke an SSH Team device by id.",
                """
                {"type":"object","required":["deviceId"],"properties":{"serverUrl":{"type":"string"},"deviceId":{"type":"string"},"ignoreSslTrust":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> sshteamRevokeDevice(args, sshTeamService));

        spec.tool(tool("ssh_connect", "Open an SSH connection and return a handle.",
                """
                {"type":"object","required":["host","username"],"properties":{"host":{"type":"string"},"port":{"type":"integer","default":22},"username":{"type":"string"},"password":{"type":"string"},"privateKeyPath":{"type":"string"},"privateKeyPassphrase":{"type":"string"},"keyboardInteractivePassword":{"type":"boolean","default":false},"connectTimeoutMs":{"type":"integer","default":30000},"sshTeamEnabled":{"type":"boolean","default":true},"sshTeamServer":{"type":"string"},"sshTeamIgnoreSslTrust":{"type":"boolean","default":false},"sshTeamCertificateType":{"type":"string","default":"ED25519"},"sshTeamTimezone":{"type":"string"}}}
                """),
                (exchange, args) -> sshConnect(args, registry, sshTeamService));

        spec.tool(tool("ssh_status", "Get status for one SSH handle.",
                "{" +
                        "\"type\":\"object\",\"required\":[\"sshHandle\"],\"properties\":{\"sshHandle\":{\"type\":\"string\"}}" +
                        "}"),
                (exchange, args) -> sshStatus(args, registry));

        spec.tool(tool("ssh_close", "Close an SSH handle.",
                "{" +
                        "\"type\":\"object\",\"required\":[\"sshHandle\"],\"properties\":{\"sshHandle\":{\"type\":\"string\"}}" +
                        "}"),
                (exchange, args) -> closeResult("ssh", registry.closeSsh(stringArg(args, "sshHandle", true))));

        spec.tool(tool("shell_open", "Open a shell or exec command on an SSH connection.",
                """
                {"type":"object","required":["sshHandle"],"properties":{"sshHandle":{"type":"string"},"pty":{"type":"boolean","default":true},"term":{"type":"string","default":"xterm"},"cols":{"type":"integer","default":120},"rows":{"type":"integer","default":40},"command":{"type":"string"},"timeoutMs":{"type":"integer","default":30000}}}
                """),
                (exchange, args) -> shellOpen(args, registry));

        spec.tool(tool("shell_write", "Write data to an open shell handle.",
                """
                {"type":"object","required":["shellHandle","data"],"properties":{"shellHandle":{"type":"string"},"data":{"type":"string"},"appendNewline":{"type":"boolean","default":true}}}
                """),
                (exchange, args) -> shellWrite(args, registry));

        spec.tool(tool("shell_read", "Read available stdout/stderr bytes from a shell handle.",
                """
                {"type":"object","required":["shellHandle"],"properties":{"shellHandle":{"type":"string"},"maxBytes":{"type":"integer","default":8192},"base64":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> shellRead(args, registry));

        spec.tool(tool("shell_close", "Close a shell handle.",
                "{" +
                        "\"type\":\"object\",\"required\":[\"shellHandle\"],\"properties\":{\"shellHandle\":{\"type\":\"string\"}}" +
                        "}"),
                (exchange, args) -> closeResult("shell", registry.closeShell(stringArg(args, "shellHandle", true))));

        spec.tool(tool("sftp_open", "Open an SFTP client from an SSH handle.",
                """
                {"type":"object","required":["sshHandle"],"properties":{"sshHandle":{"type":"string"},"remotePath":{"type":"string","default":"/"}}}
                """),
                (exchange, args) -> sftpOpen(args, registry));

        spec.tool(tool("sftp_close", "Close an SFTP handle.",
                "{" +
                        "\"type\":\"object\",\"required\":[\"sftpHandle\"],\"properties\":{\"sftpHandle\":{\"type\":\"string\"}}" +
                        "}"),
                (exchange, args) -> closeResult("sftp", registry.closeSftp(stringArg(args, "sftpHandle", true))));

        spec.tool(tool("sftp_pwd", "Get the current remote working directory of an SFTP client.",
                """
                {"type":"object","required":["sftpHandle"],"properties":{"sftpHandle":{"type":"string"}}}
                """),
                (exchange, args) -> sftpPwd(args, registry));

        spec.tool(tool("sftp_cd", "Change the current remote working directory of an SFTP client.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"}}}
                """),
                (exchange, args) -> sftpCd(args, registry));

        spec.tool(tool("sftp_ls", "List files in a remote directory.",
                """
                {"type":"object","required":["sftpHandle"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"filter":{"type":"string"},"regexFilter":{"type":"boolean","default":false},"maximumFiles":{"type":"integer","default":0}}}
                """),
                (exchange, args) -> sftpLs(args, registry));

        spec.tool(tool("sftp_stat", "Get attributes of a remote file or directory.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"followLink":{"type":"boolean","default":true}}}
                """),
                (exchange, args) -> sftpStat(args, registry));

        spec.tool(tool("sftp_mkdir", "Create a remote directory.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"parents":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> sftpMkdir(args, registry));

        spec.tool(tool("sftp_rmdir", "Remove a remote directory.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"}}}
                """),
                (exchange, args) -> sftpRmdir(args, registry));

        spec.tool(tool("sftp_rm", "Remove a remote file or directory tree.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"recursive":{"type":"boolean","default":false},"force":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> sftpRm(args, registry));

        spec.tool(tool("sftp_rename", "Rename a remote file or directory.",
                """
                {"type":"object","required":["sftpHandle","oldPath","newPath"],"properties":{"sftpHandle":{"type":"string"},"oldPath":{"type":"string"},"newPath":{"type":"string"},"posix":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> sftpRename(args, registry));

        spec.tool(tool("sftp_symlink", "Create a symbolic link on the remote server.",
                """
                {"type":"object","required":["sftpHandle","target","linkPath"],"properties":{"sftpHandle":{"type":"string"},"target":{"type":"string"},"linkPath":{"type":"string"}}}
                """),
                (exchange, args) -> sftpSymlink(args, registry));

        spec.tool(tool("sftp_chmod", "Change permissions of a remote file or directory.",
                """
                {"type":"object","required":["sftpHandle","path","permissions"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"permissions":{"type":"string"}}}
                """),
                (exchange, args) -> sftpChmod(args, registry));

        spec.tool(tool("sftp_chown", "Change owner/group of a remote file or directory.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"owner":{"type":"string"},"group":{"type":"string"}}}
                """),
                (exchange, args) -> sftpChown(args, registry));

        spec.tool(tool("sftp_file_open", "Open a remote file for random access and return an SFTP file handle.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"read":{"type":"boolean","default":true},"write":{"type":"boolean","default":false},"create":{"type":"boolean","default":false},"truncate":{"type":"boolean","default":false},"append":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> sftpFileOpen(args, registry));

        spec.tool(tool("sftp_file_read", "Read bytes from an open SFTP file handle at a given offset.",
                """
                {"type":"object","required":["sftpFileHandle","offset","length"],"properties":{"sftpFileHandle":{"type":"string"},"offset":{"type":"integer"},"length":{"type":"integer"},"base64":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> sftpFileRead(args, registry));

        spec.tool(tool("sftp_file_write", "Write bytes to an open SFTP file handle at a given offset.",
                """
                {"type":"object","required":["sftpFileHandle","offset","data"],"properties":{"sftpFileHandle":{"type":"string"},"offset":{"type":"integer"},"data":{"type":"string"},"base64":{"type":"boolean","default":true}}}
                """),
                (exchange, args) -> sftpFileWrite(args, registry));

        spec.tool(tool("sftp_file_close", "Close an SFTP file handle.",
                """
                {"type":"object","required":["sftpFileHandle"],"properties":{"sftpFileHandle":{"type":"string"}}}
                """),
                (exchange, args) -> closeResult("sftpfile", registry.closeSftpFile(stringArg(args, "sftpFileHandle", true))));

        spec.tool(tool("tunnel_open_local", "Open a local tunnel and return a handle.",
                """
                {"type":"object","required":["sshHandle","bindAddress","bindPort","destinationAddress","destinationPort"],"properties":{"sshHandle":{"type":"string"},"protocol":{"type":"string","enum":["tcp","unix"],"default":"tcp"},"bindAddress":{"type":"string"},"bindPort":{"type":"integer"},"destinationAddress":{"type":"string"},"destinationPort":{"type":"integer"},"bindPath":{"type":"string"},"destinationPath":{"type":"string"}}}
                """),
                (exchange, args) -> tunnelOpenLocal(args, registry));

        spec.tool(tool("tunnel_open_remote", "Open a remote tunnel and return a handle.",
                """
                {"type":"object","required":["sshHandle","bindAddress","bindPort","destinationAddress","destinationPort"],"properties":{"sshHandle":{"type":"string"},"protocol":{"type":"string","enum":["tcp","unix"],"default":"tcp"},"bindAddress":{"type":"string"},"bindPort":{"type":"integer"},"destinationAddress":{"type":"string"},"destinationPort":{"type":"integer"},"bindPath":{"type":"string"},"destinationPath":{"type":"string"}}}
                """),
                (exchange, args) -> tunnelOpenRemote(args, registry));

        spec.tool(tool("tunnel_close", "Close a tunnel handle.",
                "{" +
                        "\"type\":\"object\",\"required\":[\"tunnelHandle\"],\"properties\":{\"tunnelHandle\":{\"type\":\"string\"}}" +
                        "}"),
                (exchange, args) -> closeResult("tunnel", registry.closeTunnel(stringArg(args, "tunnelHandle", true))));

        spec.tool(tool("scp_copy_to", "Upload files to a remote host using legacy SCP via an SSH handle.",
                """
                {"type":"object","required":["sshHandle","localPath","remotePath"],"properties":{"sshHandle":{"type":"string"},"localPath":{"type":"string"},"remotePath":{"type":"string"},"recursive":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> scpCopyTo(args, registry));

        spec.tool(tool("scp_copy_from", "Download files from a remote host using legacy SCP via an SSH handle.",
                """
                {"type":"object","required":["sshHandle","remotePath","localPath"],"properties":{"sshHandle":{"type":"string"},"remotePath":{"type":"string"},"localPath":{"type":"string"},"recursive":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> scpCopyFrom(args, registry));

        spec.tool(tool("sftp_remove_guard", "Policy-gated helper for destructive file operations.",
                """
                {"type":"object","required":["sftpHandle","path"],"properties":{"sftpHandle":{"type":"string"},"path":{"type":"string"},"recursive":{"type":"boolean","default":false},"confirm":{"type":"boolean","default":false}}}
                """),
                (exchange, args) -> destructiveGuard(args, destructivePolicy));

        spec.resources(
                new SyncResourceSpecification(
                        McpSchema.Resource.builder()
                                .uri("mcp://maverick/handles")
                                .name("Maverick Handle Registry")
                                .description("Current SSH/SFTP/shell/tunnel handles and status")
                                .mimeType("application/json")
                                .build(),
                        (exchange, request) -> new McpSchema.ReadResourceResult(
                                List.of(new McpSchema.TextResourceContents(
                                        request.uri(),
                                        "application/json",
                                        asJson(registry.snapshot()))))),
                new SyncResourceSpecification(
                        McpSchema.Resource.builder()
                                .uri("mcp://maverick/policy")
                                .name("Destructive Policy")
                                .description("Current destructive-operation policy")
                                .mimeType("application/json")
                                .build(),
                        (exchange, request) -> {
                            Map<String, Object> policyState = Map.of(
                                    "policy", destructivePolicy.name().toLowerCase(),
                                    "requiresConfirmation", destructivePolicy.requiresConfirmation());
                            return new McpSchema.ReadResourceResult(
                                    List.of(new McpSchema.TextResourceContents(
                                            request.uri(),
                                            "application/json",
                                            asJson(policyState))));
                        }));
    }

    private static McpSchema.CallToolResult sshConnect(Map<String, Object> args, HandleRegistry registry,
            SshTeamService sshTeamService) {
        try {
            String host = stringArg(args, "host", true);
            int port = intArg(args, "port", 22);
            String username = stringArg(args, "username", true);
            String password = stringArg(args, "password", false);
            String privateKeyPath = stringArg(args, "privateKeyPath", false);
            String privateKeyPassphrase = stringArg(args, "privateKeyPassphrase", false);
            boolean keyboardInteractivePassword = boolArg(args, "keyboardInteractivePassword", false);
            int connectTimeoutMs = intArg(args, "connectTimeoutMs", 30000);
            boolean sshTeamEnabled = boolArg(args, "sshTeamEnabled", true);
            String sshTeamServer = stringArg(args, "sshTeamServer", false);
            boolean sshTeamIgnoreSslTrust = boolArg(args, "sshTeamIgnoreSslTrust", false);
            String sshTeamCertificateType = stringArg(args, "sshTeamCertificateType", false);
            String sshTeamTimezone = stringArg(args, "sshTeamTimezone", false);

            SshClientBuilder builder = SshClientBuilder.create()
                    .withTarget(host, port)
                    .withUsername(username)
                    .withConnectTimeout(Duration.ofMillis(connectTimeoutMs));
            
            
            builder.withPolicies(ForwardingPolicyBuilder.create().allowAll().build());

            boolean sshTeamCertAttempted = false;
            if (sshTeamEnabled && sshTeamService.isRegistered(sshTeamServer)) {
                builder.addAuthenticators(new SshTeamCertificateAuthenticator(
                        sshTeamService,
                        sshTeamServer,
                        sshTeamIgnoreSslTrust,
                        sshTeamCertificateType,
                        sshTeamTimezone));
                sshTeamCertAttempted = true;
            }

            if (password != null) {
                builder.withPassword(password);
            }
            if (privateKeyPath != null) {
                if (privateKeyPassphrase != null) {
                    builder.withPrivateKeyFile(Path.of(privateKeyPath), keyInfo -> privateKeyPassphrase);
                }
                else {
                    builder.withPrivateKeyFile(Path.of(privateKeyPath));
                }
            }
            if (keyboardInteractivePassword && password != null) {
                PasswordAuthenticator auth = PasswordAuthenticator.forPassword(password);
                builder.addAuthenticators(new KeyboardInteractiveAuthenticator(new PasswordOverKeyboardInteractiveCallback(auth)));
            }

            SshClient client = builder.build();
            String handle = registry.registerSsh(client);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sshHandle", handle);
            payload.put("host", client.getHost());
            payload.put("port", client.getPort());
            payload.put("authenticated", client.isAuthenticated());
            payload.put("remotePublicKeys", client.getRemotePublicKeys());
            payload.put("sshTeamAttempted", sshTeamEnabled);
            payload.put("sshTeamCertAttempted", sshTeamCertAttempted);
            return ok(payload);
        }
        catch (Exception e) {
            return error("ssh_connect failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sshteamRegister(Map<String, Object> args, SshTeamService sshTeamService) {
        try {
            String serverUrl = stringArg(args, "serverUrl", true);
            String clientId = defaultString(stringArg(args, "clientId", false), "sshteam-cli");
            String scope = defaultString(stringArg(args, "scope", false), "signing");
            String deviceName = stringArg(args, "deviceName", false);
            int pollIntervalSeconds = intArg(args, "pollIntervalSeconds", 5);
            int maxWaitSeconds = intArg(args, "maxWaitSeconds", 600);
            boolean waitForAuthorization = boolArg(args, "waitForAuthorization", false);
            boolean ignoreSslTrust = boolArg(args, "ignoreSslTrust", false);
            boolean setDefault = boolArg(args, "setDefault", true);

            SshTeamService.RegistrationResult result = sshTeamService.register(
                    serverUrl,
                    clientId,
                    scope,
                    deviceName,
                    pollIntervalSeconds,
                    maxWaitSeconds,
                    waitForAuthorization,
                    ignoreSslTrust,
                    setDefault);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", result.status);
            payload.put("server", result.server);
            payload.put("verificationUri", result.verificationUri);
            payload.put("userCode", result.userCode);
            payload.put("deviceCode", result.deviceCode);
            payload.put("message", result.message);
            if (result.accessTokenExpiresAt != null) {
                payload.put("accessTokenExpiresAt", result.accessTokenExpiresAt);
            }
            if (result.keyId != null) {
                payload.put("keyId", result.keyId);
            }
            if (result.defaultServerUpdated != null) {
                payload.put("defaultServerUpdated", result.defaultServerUpdated);
            }
            return ok(payload);
        }
        catch (Exception e) {
            return error("sshteam_register failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sshteamStatus(Map<String, Object> args, SshTeamService sshTeamService) {
        try {
            String serverUrl = stringArg(args, "serverUrl", false);
            List<Map<String, Object>> registrations = sshTeamService.listRegistrations(serverUrl);
            return ok(Map.of("registrations", registrations));
        }
        catch (Exception e) {
            return error("sshteam_status failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sshteamRegisterPoll(Map<String, Object> args, SshTeamService sshTeamService) {
        try {
            String serverUrl = stringArg(args, "serverUrl", true);
            String deviceCode = stringArg(args, "deviceCode", true);
            String clientId = defaultString(stringArg(args, "clientId", false), "sshteam-cli");
            String verificationUri = stringArg(args, "verificationUri", false);
            String userCode = stringArg(args, "userCode", false);
            int pollIntervalSeconds = intArg(args, "pollIntervalSeconds", 5);
            int maxWaitSeconds = intArg(args, "maxWaitSeconds", 600);
            boolean ignoreSslTrust = boolArg(args, "ignoreSslTrust", false);
            boolean setDefault = boolArg(args, "setDefault", true);

            SshTeamService.RegistrationResult result = sshTeamService.pollRegistration(
                    serverUrl,
                    clientId,
                    deviceCode,
                    verificationUri,
                    userCode,
                    pollIntervalSeconds,
                    maxWaitSeconds,
                    ignoreSslTrust,
                    setDefault);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", result.status);
            payload.put("server", result.server);
            payload.put("verificationUri", result.verificationUri);
            payload.put("userCode", result.userCode);
            payload.put("deviceCode", result.deviceCode);
            payload.put("message", result.message);
            if (result.accessTokenExpiresAt != null) {
                payload.put("accessTokenExpiresAt", result.accessTokenExpiresAt);
            }
            if (result.keyId != null) {
                payload.put("keyId", result.keyId);
            }
            if (result.defaultServerUpdated != null) {
                payload.put("defaultServerUpdated", result.defaultServerUpdated);
            }
            return ok(payload);
        }
        catch (Exception e) {
            return error("sshteam_register_poll failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sshteamRevokeDevice(Map<String, Object> args, SshTeamService sshTeamService) {
        try {
            String serverUrl = stringArg(args, "serverUrl", false);
            String deviceId = stringArg(args, "deviceId", true);
            boolean ignoreSslTrust = boolArg(args, "ignoreSslTrust", false);
            int status = sshTeamService.revokeDevice(serverUrl, ignoreSslTrust, deviceId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deviceId", deviceId);
            payload.put("httpStatus", status);
            payload.put("revoked", status == 204);
            if (status == 404) {
                payload.put("message", "Device not found.");
            }
            else if (status != 204) {
                payload.put("message", "Unexpected revoke status from server.");
            }
            return ok(payload);
        }
        catch (Exception e) {
            return error("sshteam_revoke_device failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sshStatus(Map<String, Object> args, HandleRegistry registry) {
        String sshHandle = stringArg(args, "sshHandle", true);
        Optional<SshClient> ssh = registry.ssh(sshHandle);
        if (ssh.isEmpty()) {
            return error("Unknown sshHandle: " + sshHandle);
        }
        SshClient client = ssh.get();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sshHandle", sshHandle);
        payload.put("connected", client.isConnected());
        payload.put("authenticated", client.isAuthenticated());
        payload.put("host", client.getHost());
        payload.put("port", client.getPort());
        payload.put("remoteIdentification", client.getRemoteIdentification());
        return ok(payload);
    }

    private static McpSchema.CallToolResult shellOpen(Map<String, Object> args, HandleRegistry registry) {
        String sshHandle = stringArg(args, "sshHandle", true);
        Optional<SshClient> ssh = registry.ssh(sshHandle);
        if (ssh.isEmpty()) {
            return error("Unknown sshHandle: " + sshHandle);
        }

        int timeout = intArg(args, "timeoutMs", 30000);
        boolean pty = boolArg(args, "pty", true);
        String term = defaultString(stringArg(args, "term", false), "xterm");
        int cols = intArg(args, "cols", 120);
        int rows = intArg(args, "rows", 40);
        String command = stringArg(args, "command", false);
        
        try {
            
            SessionChannelNG channel = ssh.get().openSessionChannel(timeout, false);
            if (pty) {
                RequestFuture ptyFuture = channel.allocatePseudoTerminal(term, cols, rows).waitFor(timeout);
                if (!ptyFuture.isDoneAndSuccess()) {
                    channel.close();
                    return error("PTY request failed.");
                }
            }

            RequestFuture startFuture = (command == null || command.isBlank())
                    ? channel.startShell().waitFor(timeout)
                    : channel.executeCommand(command).waitFor(timeout);

            if (!startFuture.isDoneAndSuccess()) {
                channel.close();
                return error("Shell/exec start request failed.");
            }

            String shellHandle = registry.registerShell(channel);
            Map<String, Object> payload = Map.of(
                    "shellHandle", shellHandle,
                    "sshHandle", sshHandle,
                    "mode", (command == null || command.isBlank()) ? "shell" : "exec");
            return ok(payload);
        }
        catch (Exception e) {
            return error("shell_open failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult shellWrite(Map<String, Object> args, HandleRegistry registry) {
        String shellHandle = stringArg(args, "shellHandle", true);
        String data = stringArg(args, "data", true);
        boolean appendNewline = boolArg(args, "appendNewline", true);

        Optional<SessionChannelNG> shell = registry.shell(shellHandle);
        if (shell.isEmpty()) {
            return error("Unknown shellHandle: " + shellHandle);
        }

        try {
            byte[] bytes = appendNewline ? (data + "\n").getBytes(StandardCharsets.UTF_8) : data.getBytes(StandardCharsets.UTF_8);
            shell.get().getOutputStream().write(bytes);
            shell.get().getOutputStream().flush();
            return ok(Map.of("shellHandle", shellHandle, "bytesWritten", bytes.length));
        }
        catch (IOException e) {
            return error("shell_write failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult shellRead(Map<String, Object> args, HandleRegistry registry) {
        String shellHandle = stringArg(args, "shellHandle", true);
        int maxBytes = intArg(args, "maxBytes", 8192);
        boolean base64 = boolArg(args, "base64", false);

        Optional<SessionChannelNG> shell = registry.shell(shellHandle);
        if (shell.isEmpty()) {
            return error("Unknown shellHandle: " + shellHandle);
        }

        try {
            byte[] stdout = readAvailable(shell.get().getInputStream(), maxBytes);
            byte[] stderr = readAvailable(shell.get().getStderrStream(), maxBytes);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("shellHandle", shellHandle);
            payload.put("stdoutBytes", stdout.length);
            payload.put("stderrBytes", stderr.length);

            if (base64) {
                payload.put("stdout", Base64.getEncoder().encodeToString(stdout));
                payload.put("stderr", Base64.getEncoder().encodeToString(stderr));
                payload.put("encoding", "base64");
            }
            else {
                payload.put("stdout", new String(stdout, StandardCharsets.UTF_8));
                payload.put("stderr", new String(stderr, StandardCharsets.UTF_8));
                payload.put("encoding", "utf-8");
            }

            return ok(payload);
        }
        catch (IOException e) {
            return error("shell_read failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpOpen(Map<String, Object> args, HandleRegistry registry) {
        String sshHandle = stringArg(args, "sshHandle", true);
        Optional<SshClient> ssh = registry.ssh(sshHandle);
        if (ssh.isEmpty()) {
            return error("Unknown sshHandle: " + sshHandle);
        }

        String remotePath = defaultString(stringArg(args, "remotePath", false), "/");
        try {
            SftpClient sftp = SftpClient.SftpClientBuilder.create()
                    .withClient(ssh.get())
                    .withRemotePath(remotePath)
                    .build();
            String handle = registry.registerSftp(sftp);
            return ok(Map.of("sftpHandle", handle, "sshHandle", sshHandle, "remotePath", remotePath));
        }
        catch (Exception e) {
            return error("sftp_open failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpPwd(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            return ok(Map.of("sftpHandle", sftpHandle, "pwd", sftp.get().pwd()));
        }
        catch (Exception e) {
            return error("sftp_pwd failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpCd(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);
        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            sftp.get().cd(path);
            return ok(Map.of("sftpHandle", sftpHandle, "pwd", sftp.get().pwd()));
        }
        catch (Exception e) {
            return error("sftp_cd failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpLs(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", false);
        String filter = stringArg(args, "filter", false);
        boolean regexFilter = boolArg(args, "regexFilter", false);
        int maximumFiles = intArg(args, "maximumFiles", 0);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            SftpFile[] files;
            if (filter == null || filter.isBlank()) {
                files = path == null ? sftp.get().ls() : sftp.get().ls(path);
            }
            else {
                files = sftp.get().ls(path == null ? "." : path, filter, regexFilter, maximumFiles);
            }

            List<Map<String, Object>> entries = new ArrayList<>();
            if (files != null) {
                for (SftpFile file : files) {
                    entries.add(sftpFileToMap(file));
                }
            }
            return ok(Map.of("sftpHandle", sftpHandle, "path", path == null ? "." : path, "entries", entries));
        }
        catch (Exception e) {
            return error("sftp_ls failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpStat(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);
        boolean followLink = boolArg(args, "followLink", true);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            SftpFileAttributes attrs = followLink ? sftp.get().stat(path) : sftp.get().statLink(path);
            return ok(Map.of("sftpHandle", sftpHandle, "path", path, "attributes", sftpAttributesToMap(attrs)));
        }
        catch (Exception e) {
            return error("sftp_stat failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpMkdir(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);
        boolean parents = boolArg(args, "parents", false);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            if (parents) {
                sftp.get().mkdirs(path);
            }
            else {
                sftp.get().mkdir(path);
            }
            return ok(Map.of("sftpHandle", sftpHandle, "path", path, "parents", parents));
        }
        catch (Exception e) {
            return error("sftp_mkdir failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpRmdir(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            sftp.get().rmdir(path);
            return ok(Map.of("sftpHandle", sftpHandle, "path", path));
        }
        catch (Exception e) {
            return error("sftp_rmdir failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpRm(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);
        boolean recursive = boolArg(args, "recursive", false);
        boolean force = boolArg(args, "force", false);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            sftp.get().rm(path, force, recursive);
            return ok(Map.of("sftpHandle", sftpHandle, "path", path, "recursive", recursive, "force", force));
        }
        catch (Exception e) {
            return error("sftp_rm failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpRename(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String oldPath = stringArg(args, "oldPath", true);
        String newPath = stringArg(args, "newPath", true);
        boolean posix = boolArg(args, "posix", false);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            sftp.get().rename(oldPath, newPath, posix);
            return ok(Map.of("sftpHandle", sftpHandle, "oldPath", oldPath, "newPath", newPath));
        }
        catch (Exception e) {
            return error("sftp_rename failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpSymlink(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String target = stringArg(args, "target", true);
        String linkPath = stringArg(args, "linkPath", true);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            sftp.get().symlink(target, linkPath);
            return ok(Map.of("sftpHandle", sftpHandle, "target", target, "linkPath", linkPath));
        }
        catch (Exception e) {
            return error("sftp_symlink failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpChmod(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);
        String permissions = stringArg(args, "permissions", true);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            PosixPermissions perms = PosixPermissionsBuilder.create().fromFileModeString(permissions).build();
            sftp.get().chmod(perms, path);
            return ok(Map.of("sftpHandle", sftpHandle, "path", path, "permissions", permissions));
        }
        catch (Exception e) {
            return error("sftp_chmod failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpChown(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);
        String owner = stringArg(args, "owner", false);
        String group = stringArg(args, "group", false);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            if (owner != null && group != null) {
                sftp.get().chown(owner, group, path);
            }
            else if (owner != null) {
                sftp.get().chown(owner, path);
            }
            else {
                return error("sftp_chown requires owner or both owner and group");
            }
            return ok(Map.of("sftpHandle", sftpHandle, "path", path, "owner", owner, "group", group));
        }
        catch (Exception e) {
            return error("sftp_chown failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpFileOpen(Map<String, Object> args, HandleRegistry registry) {
        String sftpHandle = stringArg(args, "sftpHandle", true);
        String path = stringArg(args, "path", true);
        boolean read = boolArg(args, "read", true);
        boolean write = boolArg(args, "write", false);
        boolean create = boolArg(args, "create", false);
        boolean truncate = boolArg(args, "truncate", false);
        boolean append = boolArg(args, "append", false);

        Optional<SftpClient> sftp = registry.sftp(sftpHandle);
        if (sftp.isEmpty()) {
            return error("Unknown sftpHandle: " + sftpHandle);
        }
        try {
            int flags = 0;
            if (read) flags |= SftpChannel.OPEN_READ;
            if (write) flags |= SftpChannel.OPEN_WRITE;
            if (create) flags |= SftpChannel.OPEN_CREATE;
            if (truncate) flags |= SftpChannel.OPEN_TRUNCATE;
            if (append) flags |= SftpChannel.OPEN_APPEND;
            if (flags == 0) {
                return error("sftp_file_open requires at least one of read/write/create/truncate/append");
            }

            SftpHandle fileHandle = sftp.get().openFile(path, flags);
            String fileHandleId = registry.registerSftpFile(fileHandle);
            return ok(Map.of(
                    "sftpFileHandle", fileHandleId,
                    "sftpHandle", sftpHandle,
                    "path", path,
                    "flags", flags));
        }
        catch (Exception e) {
            return error("sftp_file_open failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpFileRead(Map<String, Object> args, HandleRegistry registry) {
        String fileHandleId = stringArg(args, "sftpFileHandle", true);
        long offset = longArg(args, "offset", 0);
        int length = intArg(args, "length", 8192);
        boolean base64 = boolArg(args, "base64", false);

        Optional<SftpHandle> handle = registry.sftpFile(fileHandleId);
        if (handle.isEmpty()) {
            return error("Unknown sftpFileHandle: " + fileHandleId);
        }
        try {
            byte[] buffer = new byte[length];
            int read = handle.get().read(offset, buffer, 0, length);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sftpFileHandle", fileHandleId);
            payload.put("offset", offset);
            payload.put("bytesRead", read);

            if (read > 0) {
                byte[] data = read == length ? buffer : java.util.Arrays.copyOf(buffer, read);
                if (base64) {
                    payload.put("data", Base64.getEncoder().encodeToString(data));
                    payload.put("encoding", "base64");
                }
                else {
                    payload.put("data", new String(data, StandardCharsets.UTF_8));
                    payload.put("encoding", "utf-8");
                }
            }
            else {
                payload.put("data", "");
                payload.put("encoding", "utf-8");
            }
            return ok(payload);
        }
        catch (Exception e) {
            return error("sftp_file_read failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult sftpFileWrite(Map<String, Object> args, HandleRegistry registry) {
        String fileHandleId = stringArg(args, "sftpFileHandle", true);
        long offset = longArg(args, "offset", 0);
        String data = stringArg(args, "data", true);
        boolean base64 = boolArg(args, "base64", true);

        Optional<SftpHandle> handle = registry.sftpFile(fileHandleId);
        if (handle.isEmpty()) {
            return error("Unknown sftpFileHandle: " + fileHandleId);
        }
        try {
            byte[] bytes = base64 ? Base64.getDecoder().decode(data) : data.getBytes(StandardCharsets.UTF_8);
            handle.get().write(offset, bytes, 0, bytes.length);
            return ok(Map.of(
                    "sftpFileHandle", fileHandleId,
                    "offset", offset,
                    "bytesWritten", bytes.length));
        }
        catch (Exception e) {
            return error("sftp_file_write failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult tunnelOpenLocal(Map<String, Object> args, HandleRegistry registry) {
        String sshHandle = stringArg(args, "sshHandle", true);
        Optional<SshClient> ssh = registry.ssh(sshHandle);
        if (ssh.isEmpty()) {
            return error("Unknown sshHandle: " + sshHandle);
        }

        String protocol = defaultString(stringArg(args, "protocol", false), "tcp");
        
        System.out.println("tunnelOpenLocal called with protocol: " + protocol);
        args.forEach((k, v) -> System.out.println("Arg: " + k + " = " + v));

        try {
            ForwardingRequestBuilder builder = ForwardingRequestBuilder.create();

            if ("unix".equalsIgnoreCase(protocol)) {
                builder.withProtocol(Protocol.DOMAIN_SOCKETS)
                        .withBindPath(stringArg(args, "bindPath", true))
                        .withDestinationPath(stringArg(args, "destinationPath", true));
            }
            else {
                builder.withProtocol(Protocol.TCP)
                        .withBind(defaultString(stringArg(args, "bindAddress", false), "::"), intArg(args, "bindPort", 0))
                        .withDestination(stringArg(args, "destinationAddress", true), intArg(args, "destinationPort", 0));
            }

            ForwardingHandle tunnel = ssh.get().bindLocal(builder.build());
            String tunnelHandle = registry.registerTunnel(tunnel);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tunnelHandle", tunnelHandle);
            payload.put("type", "local");
            payload.put("boundPort", tunnel.boundPort().orElse(null));
            payload.put("boundPath", tunnel.boundPath().orElse(null));
            return ok(payload);
        }
        catch (UnauthorizedException | SshException e) {
        	e.printStackTrace();
            return error("tunnel_open_local failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult tunnelOpenRemote(Map<String, Object> args, HandleRegistry registry) {
        String sshHandle = stringArg(args, "sshHandle", true);
        Optional<SshClient> ssh = registry.ssh(sshHandle);
        if (ssh.isEmpty()) {
            return error("Unknown sshHandle: " + sshHandle);
        }

        String protocol = defaultString(stringArg(args, "protocol", false), "tcp");

        try {
            ForwardingRequestBuilder builder = ForwardingRequestBuilder.create();

            if ("unix".equalsIgnoreCase(protocol)) {
                builder.withProtocol(Protocol.DOMAIN_SOCKETS)
                        .withBindPath(stringArg(args, "bindPath", true))
                        .withDestinationPath(stringArg(args, "destinationPath", true));
            }
            else {
                builder.withProtocol(Protocol.TCP)
                        .withBind(defaultString(stringArg(args, "bindAddress", false), "127.0.0.1"), intArg(args, "bindPort", 0))
                        .withDestination(stringArg(args, "destinationAddress", true), intArg(args, "destinationPort", 0));
            }

            ForwardingHandle tunnel = ssh.get().bindRemote(builder.build());
            String tunnelHandle = registry.registerTunnel(tunnel);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tunnelHandle", tunnelHandle);
            payload.put("type", "remote");
            payload.put("boundPort", tunnel.boundPort().orElse(null));
            payload.put("boundPath", tunnel.boundPath().orElse(null));
            return ok(payload);
        }
        catch (UnauthorizedException | SshException e) {
        	e.printStackTrace();
            return error("tunnel_open_remote failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult scpCopyTo(Map<String, Object> args, HandleRegistry registry) {
        String sshHandle = stringArg(args, "sshHandle", true);
        Optional<SshClient> ssh = registry.ssh(sshHandle);
        if (ssh.isEmpty()) {
            return error("Unknown sshHandle: " + sshHandle);
        }

        String localPath = stringArg(args, "localPath", true);
        String remotePath = stringArg(args, "remotePath", true);
        boolean recursive = boolArg(args, "recursive", false);

        try {
            ScpClient scp = new ScpClient(ssh.get());
            scp.put(localPath, remotePath, recursive);

            return ok(Map.of("direction", "upload", "localPath", localPath, "remotePath", remotePath));
        }
        catch (Exception e) {
            return error("scp_copy_to failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult scpCopyFrom(Map<String, Object> args, HandleRegistry registry) {
        String sshHandle = stringArg(args, "sshHandle", true);
        Optional<SshClient> ssh = registry.ssh(sshHandle);
        if (ssh.isEmpty()) {
            return error("Unknown sshHandle: " + sshHandle);
        }

        String remotePath = stringArg(args, "remotePath", true);
        String localPath = stringArg(args, "localPath", true);
        boolean recursive = boolArg(args, "recursive", false);

        try {
            ScpClient scp = new ScpClient(ssh.get());
            scp.get(localPath, remotePath, recursive);

            return ok(Map.of("direction", "download", "localPath", localPath, "remotePath", remotePath));
        }
        catch (Exception e) {
            return error("scp_copy_from failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult destructiveGuard(Map<String, Object> args, DestructivePolicy destructivePolicy) {
        String path = stringArg(args, "path", true);
        boolean recursive = boolArg(args, "recursive", false);
        boolean confirm = boolArg(args, "confirm", false);

        if (destructivePolicy.requiresConfirmation() && !confirm) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("approved", false);
            payload.put("policy", "prompt");
            payload.put("message", "Confirmation required for destructive operation.");
            payload.put("path", path);
            payload.put("recursive", recursive);
            payload.put("next", "Call again with confirm=true to continue.");
            return ok(payload);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", true);
        payload.put("policy", destructivePolicy.name().toLowerCase());
        payload.put("path", path);
        payload.put("recursive", recursive);
        return ok(payload);
    }

    private static McpSchema.Tool tool(String name, String description, String schemaJson) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(jsonSchema(schemaJson))
                .build();
    }

    private static McpSchema.JsonSchema jsonSchema(String schemaJson) {
        try {
            Map<String, Object> root = MAPPER.readValue(schemaJson, MAP_TYPE);
            return new McpSchema.JsonSchema(
                    asString(root.get("type")),
                    asMap(root.get("properties")),
                    asStringList(root.get("required")),
                    asBoolean(root.get("additionalProperties")),
                    asMap(root.get("$defs")),
                    asMap(root.get("definitions")));
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid tool schema JSON.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Schema field must be an object.");
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        throw new IllegalArgumentException("Schema field must be an array.");
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static McpSchema.CallToolResult closeResult(String type, boolean closed) {
        if (!closed) {
            return error(type + " handle not found or close failed");
        }
        return ok(Map.of("closed", true, "type", type));
    }

    private static McpSchema.CallToolResult ok(Map<String, Object> payload) {
        return McpSchema.CallToolResult.builder()
                .structuredContent(payload)
                .addTextContent(asJson(payload))
                .isError(false)
                .build();
    }

    private static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .isError(true)
                .addTextContent(message)
                .build();
    }

    private static String asJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static byte[] readAvailable(InputStream input, int maxBytes) throws IOException {
        int available = input.available();
        if (available <= 0) {
            return new byte[0];
        }
        int size = Math.min(Math.max(1, maxBytes), available);
        
        // WORKS
        byte[] bytes = new byte[size];
        int read = input.read(bytes);
        
        if(read != available) {
            System.err.println("Warning: readAvailable read " + read + " bytes, but available was " + available);
        }
        
        return read == -1 ? new byte[0] : bytes;
        
        // HANGS
//        byte[] bytes = input.readNBytes(size);
//        return bytes == null ? new byte[0] : bytes;
    }

    private static Map<String, Object> sftpFileToMap(SftpFile file) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("filename", file.getFilename());
        map.put("absolutePath", file.getAbsolutePath());
        map.put("longName", file.getLongname());
        map.put("attributes", sftpAttributesToMap(file.attributes()));
        return map;
    }

    private static Map<String, Object> sftpAttributesToMap(SftpFileAttributes attrs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", attrs.type());
        map.put("directory", attrs.isDirectory());
        map.put("file", attrs.isFile());
        map.put("link", attrs.isLink());
        if (attrs.hasSize()) {
            map.put("size", attrs.size().longValue());
        }
        if (attrs.hasPermissions()) {
            map.put("permissions", attrs.toPermissionsString());
        }
        if (attrs.hasUsername()) {
            map.put("username", attrs.username());
        }
        if (attrs.hasGroup()) {
            map.put("group", attrs.group());
        }
        if (attrs.hasUid()) {
            map.put("uid", attrs.uid());
        }
        if (attrs.hasGid()) {
            map.put("gid", attrs.gid());
        }
        if (attrs.hasLastModifiedTime()) {
            map.put("lastModifiedTime", attrs.lastModifiedTime().toMillis());
        }
        if (attrs.hasLastAccessTime()) {
            map.put("lastAccessTime", attrs.lastAccessTime().toMillis());
        }
        return map;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String stringArg(Map<String, Object> args, String key, boolean required) {
        Object value = args.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required argument: " + key);
            }
            return null;
        }
        String str = String.valueOf(value);
        if (required && str.isBlank()) {
            throw new IllegalArgumentException("Blank argument: " + key);
        }
        return str;
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String s = String.valueOf(value);
        if (s.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(s);
    }

    private static long longArg(Map<String, Object> args, String key, long fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String s = String.valueOf(value);
        if (s.isBlank()) {
            return fallback;
        }
        return Long.parseLong(s);
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
