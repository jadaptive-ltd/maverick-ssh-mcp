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
import com.sshtools.client.sftp.SftpClient;
import com.sshtools.client.sftp.SftpHandle;
import com.sshtools.common.forwarding.ForwardingHandle;

final class HandleRegistry implements Closeable {

    private final ConcurrentMap<String, SshClient> sshClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionChannelNG> shells = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SftpClient> sftpClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SftpHandle> sftpFiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ForwardingHandle> tunnels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Socket> sockets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServerSocket> socketListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> created = new ConcurrentHashMap<>();

    String registerSsh(SshClient client) {
        return put(sshClients, client, "ssh");
    }

    String registerShell(SessionChannelNG channel) {
        return put(shells, channel, "shell");
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

    Optional<SshClient> ssh(String id) {
        return Optional.ofNullable(sshClients.get(id));
    }

    Optional<SessionChannelNG> shell(String id) {
        return Optional.ofNullable(shells.get(id));
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
        return closeAndRemove(sshClients, id);
    }

    boolean closeShell(String id) {
        return closeAndRemove(shells, id);
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
        all.addAll(sshClients.keySet());
        all.forEach(this::closeSsh);
        all.clear();
        all.addAll(shells.keySet());
        all.forEach(this::closeShell);
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