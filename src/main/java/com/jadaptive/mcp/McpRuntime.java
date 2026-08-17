package com.jadaptive.mcp;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

final class McpRuntime implements AutoCloseable {

    enum Mode {
        STDIO,
        HTTP
    }

    private final HandleRegistry registry = new HandleRegistry();
    private final SshTeamService sshTeamService = new SshTeamService(SshTeamDeviceStore.defaultStore());
    private final DestructivePolicy destructivePolicy;
    private final Mode mode;
    private final String bindHost;
    private final int bindPort;
    private final String endpoint;

    private McpSyncServer mcpServer;
    private Server httpServer;

    McpRuntime(DestructivePolicy destructivePolicy, Mode mode, String bindHost, int bindPort, String endpoint) {
        this.destructivePolicy = destructivePolicy;
        this.mode = mode;
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.endpoint = endpoint;
    }

    void start() throws Exception {
        if (mode == Mode.STDIO) {
            StdioServerTransportProvider transport = new StdioServerTransportProvider();
            mcpServer = buildServer(McpServer.sync(transport));
            return;
        }

        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .objectMapper(new ObjectMapper())
                .mcpEndpoint(normalizedEndpoint())
                .keepAliveInterval(Duration.ofSeconds(15))
                .build();

        mcpServer = buildServer(McpServer.sync(transport));
        httpServer = new Server();
        ServerConnector connector = new ServerConnector(httpServer);
        connector.setHost(bindHost);
        connector.setPort(bindPort);
        httpServer.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transport), normalizedEndpoint() + "/*");
        httpServer.setHandler(context);
        httpServer.start();
    }

    void blockUntilStopped() throws Exception {
        if (mode == Mode.HTTP && httpServer != null) {
            httpServer.join();
            return;
        }
        Thread.currentThread().join();
    }

    @Override
    public void close() {
        try {
            registry.close();
        }
        catch (Exception ignored) {
        }

        if (mcpServer != null) {
            try {
                mcpServer.closeGracefully();
            }
            catch (Exception ignored) {
            }
        }

        if (httpServer != null) {
            try {
                httpServer.stop();
            }
            catch (Exception ignored) {
            }
        }
    }

    private McpSyncServer buildServer(McpServer.SyncSpecification<?> specification) {
        McpToolset.register(specification, registry, destructivePolicy, sshTeamService);
        return specification
                .serverInfo("maverick-mcp", "0.0.1-SNAPSHOT")
                .instructions("Maverick Synergy MCP server. Use handle-based tools for SSH, shell, SFTP, tunnels, and SCP workflows.")
                .requestTimeout(Duration.ofSeconds(60))
                .immediateExecution(true)
                .build();
    }

    private String normalizedEndpoint() {
        if (endpoint == null || endpoint.isBlank()) {
            return "/mcp";
        }
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }
}
