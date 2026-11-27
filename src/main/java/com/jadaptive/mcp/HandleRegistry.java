package com.jadaptive.mcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.sshtools.client.SessionChannelNG;
import com.sshtools.client.SshClient;
import com.sshtools.client.scp.ScpClient;
import com.sshtools.client.shell.ExpectShell;
import com.sshtools.client.sftp.SftpClient;
import com.sshtools.client.sftp.SftpHandle;
import com.sshtools.common.forwarding.ForwardingHandle;

public final class HandleRegistry implements Closeable {

    private final ConcurrentMap<String, SshClient> sshClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionChannelNG> shells = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ExpectShell> expectShells = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SftpClient> sftpClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SftpHandle> sftpFiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ForwardingHandle> tunnels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Socket> sockets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServerSocket> socketListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> created = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> shellSshOwners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> expectShellOwners = new ConcurrentHashMap<>();
    /** Shell handles started as interactive shells (no command); unmediated input to these is unsafe. */
    private final java.util.Set<String> interactiveShells = ConcurrentHashMap.newKeySet();
    /** Child expect shell handle to its parent expect shell handle (su/sudo). */
    private final ConcurrentMap<String, String> expectShellParents = new ConcurrentHashMap<>();
    /** Privilege-changing command that created a child expect shell. */
    private final ConcurrentMap<String, String> expectShellPrivilegeHops = new ConcurrentHashMap<>();

    String registerSsh(SshClient client) {
        return put(sshClients, client, "ssh");
    }

    String registerShell(SessionChannelNG channel) {
        return registerShell(channel, null);
    }

    String registerShell(SessionChannelNG channel, String sshHandle) {
        return registerShell(channel, sshHandle, false);
    }

    String registerShell(SessionChannelNG channel, String sshHandle, boolean interactive) {
        String shellHandle = put(shells, channel, "shell");
        if (sshHandle != null) {
            shellSshOwners.put(shellHandle, sshHandle);
        }
        if (interactive) {
            interactiveShells.add(shellHandle);
        }
        return shellHandle;
    }

    String registerExpectShell(ExpectShell shell) {
        return registerExpectShell(shell, null);
    }

    String registerExpectShell(ExpectShell shell, String shellHandle) {
        String expectShellHandle = put(expectShells, shell, "expectshell");
        if (shellHandle != null) {
            expectShellOwners.put(expectShellHandle, shellHandle);
        }
        return expectShellHandle;
    }

    String registerChildExpectShell(ExpectShell shell, String parentExpectShellHandle) {
        return registerChildExpectShell(shell, parentExpectShellHandle, null);
    }

    String registerChildExpectShell(ExpectShell shell, String parentExpectShellHandle, String privilegeCommand) {
        String child = registerExpectShell(shell, expectShellOwners.get(parentExpectShellHandle));
        expectShellParents.put(child, parentExpectShellHandle);
        if (privilegeCommand != null) {
            expectShellPrivilegeHops.put(child, privilegeCommand);
        }
        return child;
    }

    String registerSftp(SftpClient client) {
        return put(sftpClients, client, "sftp");
    }

    String registerSftpFile(SftpHandle handle) {
        return put(sftpFiles, handle, "sftpfile");
    }

    String registerTunnel(ForwardingHandle tunnel) {
        return put(tunnels, tunnel, "tunnel");
    }

    String registerSocket(Socket socket) {
        return put(sockets, socket, "socket");
    }

    String registerSocketListener(ServerSocket socket) {
        return put(socketListeners, socket, "socketlistener");
    }

    /** Whether the shell handle was opened as an interactive shell (no command). */
    boolean isInteractiveShell(String shellHandle) {
        return shellHandle != null && interactiveShells.contains(shellHandle);
    }

    /**
     * Resolves the trusted execution context for tool arguments from this registry. Only handles
     * known to the registry contribute; caller-supplied host or identity values are never used.
     */
    Optional<ExecutionContext> executionContext(Map<String, Object> args) {
        String expectShellHandle = handleArg(args, "expectShellHandle");
        String shellHandle = handleArg(args, "shellHandle");
        String sshHandle = handleArg(args, "sshHandle");

        String sessionId;
        boolean persistent;
        String operatingSystem = null;
        List<String> privilegeChain = new ArrayList<>();
        if (expectShellHandle != null && expectShells.containsKey(expectShellHandle)) {
            sessionId = expectShellHandle;
            persistent = true;
            ExpectShell shell = expectShells.get(expectShellHandle);
            try {
                operatingSystem = shell.getOsDescription();
            }
            catch (RuntimeException ignored) {
                // OS detection is optional context.
            }
            for (String current = expectShellHandle; current != null; current = expectShellParents.get(current)) {
                String hop = expectShellPrivilegeHops.get(current);
                if (hop != null) {
                    privilegeChain.add(0, hop);
                }
            }
            shellHandle = expectShellOwners.get(expectShellHandle);
            sshHandle = shellHandle == null ? null : shellSshOwners.get(shellHandle);
        }
        else if (shellHandle != null && shells.containsKey(shellHandle)) {
            sessionId = shellHandle;
            persistent = interactiveShells.contains(shellHandle);
            sshHandle = shellSshOwners.get(shellHandle);
        }
        else if (sshHandle != null && sshClients.containsKey(sshHandle)) {
            sessionId = sshHandle;
            persistent = false;
        }
        else {
            return Optional.empty();
        }

        SshClient client = sshHandle == null ? null : sshClients.get(sshHandle);
        String host = client == null ? null : client.getHost();
        int port = client == null ? 0 : client.getPort();
        String identity = null;
        if (client != null) {
            try {
                identity = client.getContext().getUsername();
            }
            catch (RuntimeException ignored) {
                // Identity is optional context.
            }
        }
        return Optional.of(new ExecutionContext(sessionId, sshHandle, host, port, identity, operatingSystem,
                privilegeChain, persistent));
    }

    private static String handleArg(Map<String, Object> args, String name) {
        return args.get(name) instanceof String value && !value.isBlank() ? value : null;
    }

    Optional<SshClient> ssh(String id) {
        return Optional.ofNullable(sshClients.get(id));
    }

    Optional<SessionChannelNG> shell(String id) {
        SessionChannelNG shell = shells.get(id);
        if (shell != null && shell.isClosed()) {
            removeShell(id);
            return Optional.empty();
        }
        return Optional.ofNullable(shell);
    }

    Optional<ExpectShell> expectShell(String id) {
        ExpectShell shell = expectShells.get(id);
        if (shell != null && shell.isClosed()) {
            removeExpectShell(id);
            return Optional.empty();
        }
        return Optional.ofNullable(shell);
    }

    Optional<SftpClient> sftp(String id) {
        return Optional.ofNullable(sftpClients.get(id));
    }

    Optional<SftpHandle> sftpFile(String id) {
        return Optional.ofNullable(sftpFiles.get(id));
    }

    Optional<ForwardingHandle> tunnel(String id) {
        return Optional.ofNullable(tunnels.get(id));
    }

    Optional<Socket> socket(String id) {
        return Optional.ofNullable(sockets.get(id));
    }

    Optional<ServerSocket> socketListener(String id) {
        return Optional.ofNullable(socketListeners.get(id));
    }

    boolean closeSsh(String id) {
        closeOwned(shellSshOwners, id, this::closeShell);
        return closeAndRemove(sshClients, id);
    }

    boolean closeShell(String id) {
        closeOwned(expectShellOwners, id, this::closeExpectShellOnly);
        shellSshOwners.remove(id);
        interactiveShells.remove(id);
        return closeAndRemove(shells, id);
    }

    boolean closeExpectShell(String id) {
        String shellOwner = expectShellOwners.remove(id);
        boolean closed = closeExpectShellOnly(id);
        if (shellOwner != null) {
            closeShell(shellOwner);
        }
        return closed;
    }

    boolean closeSftp(String id) {
        return closeAndRemove(sftpClients, id);
    }

    boolean closeSftpFile(String id) {
        return closeAndRemove(sftpFiles, id);
    }

    boolean closeTunnel(String id) {
        return closeAndRemove(tunnels, id);
    }

    boolean closeSocket(String id) {
        return closeAndRemove(sockets, id);
    }

    boolean closeSocketListener(String id) {
        return closeAndRemove(socketListeners, id);
    }

    Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ssh", summarizeSsh());
        map.put("shell", summarize(shells));
        map.put("expectshell", summarize(expectShells));
        map.put("sftp", summarize(sftpClients));
        map.put("sftpfile", summarize(sftpFiles));
        map.put("tunnel", summarize(tunnels));
        map.put("socket", summarize(sockets));
        map.put("socketlistener", summarize(socketListeners));
        return map;
    }

    @Override
    public void close() {
        List<String> all = new ArrayList<>();
        all.addAll(expectShells.keySet());
        all.forEach(this::closeExpectShellOnly);
        all.clear();
        all.addAll(shells.keySet());
        all.forEach(this::closeShell);
        all.clear();
        all.addAll(sshClients.keySet());
        all.forEach(this::closeSsh);
        all.clear();
        all.addAll(sftpClients.keySet());
        all.forEach(this::closeSftp);
        all.clear();
        all.addAll(sftpFiles.keySet());
        all.forEach(this::closeSftpFile);
        all.clear();
        all.addAll(tunnels.keySet());
        all.forEach(this::closeTunnel);
        all.clear();
        all.addAll(sockets.keySet());
        all.forEach(this::closeSocket);
        all.clear();
        all.addAll(socketListeners.keySet());
        all.forEach(this::closeSocketListener);
    }

    private List<Map<String, Object>> summarizeSsh() {
        List<Map<String, Object>> list = new ArrayList<>();
        sshClients.forEach((id, client) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("connected", client.isConnected());
            row.put("authenticated", client.isAuthenticated());
            row.put("host", client.getHost());
            row.put("port", client.getPort());
            row.put("createdAt", created.get(id));
            list.add(row);
        });
        return list;
    }

    private List<Map<String, Object>> summarize(ConcurrentMap<String, ?> mapRef) {
        List<Map<String, Object>> list = new ArrayList<>();
        mapRef.forEach((id, ignored) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("createdAt", created.get(id));
            list.add(row);
        });
        return list;
    }

    private <T> String put(ConcurrentMap<String, T> map, T value, String prefix) {
        String id = prefix + "-" + UUID.randomUUID();
        map.put(id, value);
        created.put(id, Instant.now());
        return id;
    }

    private boolean closeExpectShellOnly(String id) {
        expectShellOwners.remove(id);
        expectShellParents.remove(id);
        expectShellPrivilegeHops.remove(id);
        return closeAndRemove(expectShells, id);
    }

    private void removeShell(String id) {
        shells.remove(id);
        created.remove(id);
        shellSshOwners.remove(id);
        interactiveShells.remove(id);
    }

    private void removeExpectShell(String id) {
        expectShells.remove(id);
        created.remove(id);
        expectShellOwners.remove(id);
        expectShellParents.remove(id);
        expectShellPrivilegeHops.remove(id);
    }

    private void closeOwned(Map<String, String> owners, String owner, java.util.function.Consumer<String> close) {
        List<String> owned = owners.entrySet().stream()
                .filter(entry -> owner.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        owned.forEach(close);
    }

    private <T> boolean closeAndRemove(ConcurrentMap<String, T> map, String id) {
        T value = map.remove(id);
        created.remove(id);
        if (value == null) {
            return false;
        }
        try {
            if (value instanceof ForwardingHandle handle) {
                handle.close();
            }
            else if (value instanceof ScpClient scp) {
                scp.exit();
            }
            else if (value instanceof ExpectShell expectShell) {
                expectShell.close();
            }
            else if (value instanceof Closeable closable) {
                closable.close();
            }
            return true;
        }
        catch (IOException e) {
            return false;
        }
        catch (Exception e) {
            return false;
        }
    }
}
