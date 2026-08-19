package com.jadaptive.mcp;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.ServiceLoader;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;
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
    private final String mcpToken;
    private final McpJsonMapper mcpJsonMapper;

    private McpSyncServer mcpServer;
    private Server httpServer;

    McpRuntime(DestructivePolicy destructivePolicy, Mode mode, String bindHost, int bindPort, String endpoint) {
        this(destructivePolicy, mode, bindHost, bindPort, endpoint, System.getenv("MCP_TOKEN"));
    }

    McpRuntime(DestructivePolicy destructivePolicy, Mode mode, String bindHost, int bindPort, String endpoint,
            String mcpToken) {
        this.destructivePolicy = destructivePolicy;
        this.mode = mode;
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.endpoint = endpoint;
        this.mcpToken = emptyToNull(mcpToken);
        this.mcpJsonMapper = createJsonMapper();
    }

    void start() throws Exception {
        if (mode == Mode.STDIO) {
            StdioServerTransportProvider transport = new StdioServerTransportProvider(mcpJsonMapper);
            mcpServer = buildServer(McpServer.sync(transport));
            return;
        }

        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mcpJsonMapper)
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
        if (mcpToken != null) {
            context.addFilter(
                    new FilterHolder(new BearerTokenFilter(mcpToken)),
                    normalizedEndpoint() + "/*",
                    EnumSet.of(DispatcherType.REQUEST));
        }
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

    int httpPort() {
        if (httpServer == null || httpServer.getConnectors().length == 0) {
            return -1;
        }
        return ((ServerConnector) httpServer.getConnectors()[0]).getLocalPort();
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
                .instructions("Maverick Synergy MCP server. Use handle-based tools for SSH, local sockets, shell, SFTP, tunnels, and SCP workflows.")
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

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static McpJsonMapper createJsonMapper() {
        return ServiceLoader.load(McpJsonMapperSupplier.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No McpJsonMapperSupplier found on classpath."))
                .get();
    }

    private static final class BearerTokenFilter implements Filter {

        private final String expectedAuthorization;

        private BearerTokenFilter(String token) {
            this.expectedAuthorization = "Bearer " + token;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (!(request instanceof HttpServletRequest httpRequest)
                    || !(response instanceof HttpServletResponse httpResponse)) {
                chain.doFilter(request, response);
                return;
            }

            String authorization = httpRequest.getHeader("Authorization");
            if (!expectedAuthorization.equals(authorization)) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setHeader("WWW-Authenticate", "Bearer");
                httpResponse.setContentType("text/plain;charset=UTF-8");
                httpResponse.getWriter().write("Unauthorized");
                return;
            }

            chain.doFilter(request, response);
        }
    }
}
