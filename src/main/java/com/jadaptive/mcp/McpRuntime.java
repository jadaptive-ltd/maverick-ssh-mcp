package com.jadaptive.mcp;

import java.io.IOException;
import java.time.Duration;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.ServiceLoader;

import com.jadaptive.mcp.oauth.FormOAuthAuthorizationProvider;
import com.jadaptive.mcp.oauth.McpOAuthServlet;
import com.jadaptive.mcp.oauth.OAuthAuthorizationProvider;

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
import uk.co.bithatch.artifactversion.ArtifactVersion;

public final class McpRuntime implements AutoCloseable {

    public enum Mode {
        STDIO,
        HTTP
    }

    private final HandleRegistry registry = new HandleRegistry();
    private final SshTeamService sshTeamService;
    private final ToolGate toolGate;
    private final Mode mode;
    private final String bindHost;
    private final int bindPort;
    private final String endpoint;
    private final String mcpToken;
    private final String oauthIssuer;
    private final OAuthAuthorizationProvider oauthProvider;
    private final Path oauthStateDirectory;
    private final boolean debugRequests;
    private final McpJsonMapper mcpJsonMapper;
    private final UpdateService updateService;
    private final boolean updateChecksEnabled;

    private McpSyncServer mcpServer;
    private Server httpServer;
    private McpOAuthServlet oauthServlet;

    McpRuntime(SshTeamService sshTeamService, ToolGate toolGate, Mode mode, String bindHost, int bindPort, String endpoint) {
        this(sshTeamService, toolGate, mode, bindHost, bindPort, endpoint, System.getenv("MCP_TOKEN"), false,
                System.getenv("MCP_OAUTH_ISSUER"), null);
    }

    public McpRuntime(SshTeamService sshTeamService, ToolGate toolGate, Mode mode, String bindHost, int bindPort, String endpoint,
            boolean debugRequests) {
        this(sshTeamService, toolGate, mode, bindHost, bindPort, endpoint, System.getenv("MCP_TOKEN"), debugRequests,
                System.getenv("MCP_OAUTH_ISSUER"), null);
    }

    McpRuntime(SshTeamService sshTeamService, ToolGate toolGate, Mode mode, String bindHost, int bindPort, String endpoint,
            String mcpToken) {
        this(sshTeamService, toolGate, mode, bindHost, bindPort, endpoint, mcpToken, false, null, null);
    }

    McpRuntime(SshTeamService sshTeamService, ToolGate toolGate, Mode mode, String bindHost, int bindPort, String endpoint,
            String mcpToken, boolean debugRequests) {
        this(sshTeamService, toolGate, mode, bindHost, bindPort, endpoint, mcpToken, debugRequests, null, null);
    }

    /**
     * Creates a runtime with a custom HTTP OAuth authorization flow. Passing a
     * provider enables OAuth even when no static MCP token is configured.
     */
    public McpRuntime(SshTeamService sshTeamService, ToolGate toolGate, Mode mode, String bindHost, int bindPort, String endpoint,
            String mcpToken, boolean debugRequests, String oauthIssuer, OAuthAuthorizationProvider oauthProvider) {
        this(sshTeamService, toolGate, mode, bindHost, bindPort, endpoint,
                mcpToken, debugRequests, oauthIssuer, oauthProvider, defaultOAuthStateDirectory());
    }

    public McpRuntime(SshTeamService sshTeamService, ToolGate toolGate, Mode mode, String bindHost, int bindPort, String endpoint,
            String mcpToken, boolean debugRequests, String oauthIssuer, OAuthAuthorizationProvider oauthProvider,
            Path oauthStateDirectory) {
        this(sshTeamService, toolGate, mode, bindHost, bindPort, endpoint, mcpToken, debugRequests,
                oauthIssuer, oauthProvider, oauthStateDirectory, updateChecksEnabled(System.getenv("MCP_UPDATE_CHECK")));
    }

    public McpRuntime(SshTeamService sshTeamService, ToolGate toolGate, Mode mode, String bindHost, int bindPort, String endpoint,
            String mcpToken, boolean debugRequests, String oauthIssuer, OAuthAuthorizationProvider oauthProvider,
            Path oauthStateDirectory, boolean updateChecksEnabled) {
        this.sshTeamService = sshTeamService;
        this.toolGate = toolGate == null ? new DumbToolGate() : toolGate;
        this.mode = mode;
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.endpoint = endpoint;
        this.mcpToken = emptyToNull(mcpToken);
        this.oauthIssuer = oauthIssuer;
        this.oauthProvider = oauthProvider != null
                 ? oauthProvider
                 : this.mcpToken == null ? null : new FormOAuthAuthorizationProvider(this.mcpToken);
        this.oauthStateDirectory = oauthStateDirectory == null ? defaultOAuthStateDirectory() : oauthStateDirectory;
        this.debugRequests = debugRequests;
        this.mcpJsonMapper = createJsonMapper();
        this.updateService = new UpdateService(currentVersion(), this.oauthStateDirectory);
        this.updateChecksEnabled = updateChecksEnabled;
    }

    public void start() throws Exception {
        updateService.start(updateChecksEnabled);
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
        if (oauthProvider != null) {
            oauthServlet = new McpOAuthServlet(normalizedEndpoint(), oauthIssuer, oauthProvider,
                    mcpJsonMapper, oauthStateDirectory);
            ServletHolder oauthHolder = new ServletHolder(oauthServlet);
            context.addServlet(oauthHolder, "/oauth/*");
            context.addServlet(oauthHolder, "/.well-known/*");
        }
        if (mcpToken != null || oauthServlet != null) {
            context.addFilter(
                    new FilterHolder(new BearerTokenFilter(mcpToken, oauthServlet)),
                    normalizedEndpoint() + "/*",
                    EnumSet.of(DispatcherType.REQUEST));
        }
        context.addServlet(new ServletHolder(transport), normalizedEndpoint() + "/*");
        httpServer.setHandler(context);
        httpServer.start();
    }

    public void blockUntilStopped() throws Exception {
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

        updateService.close();

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

        if (oauthProvider != null) {
            try {
                oauthProvider.close();
            }
            catch (Exception ignored) {
            }
        }
    }

    private McpSyncServer buildServer(McpServer.SyncSpecification<?> specification) {
        McpToolset.register(specification, registry, toolGate, sshTeamService, updateService,
                debugRequests, mode == Mode.STDIO);
        return specification
                .serverInfo("maverick-mcp", updateService.currentVersion())
                .instructions("Maverick Synergy MCP server. Use handle-based tools for SSH, stateless ssh_exec commands, interactive shells, local sockets, SFTP, tunnels, and SCP workflows.")
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

    private static String currentVersion() {
        return ArtifactVersion.getVersion("com.jadaptive", "maverick-ssh-mcp");
    }

    private static Path defaultOAuthStateDirectory() {
        String configured = System.getenv("MCP_OAUTH_STATE_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        return Path.of(System.getProperty("user.home"), ".sshteam-mcp");
    }

    static boolean updateChecksEnabled(String configured) {
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return switch (configured.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "0", "false", "no", "off", "disabled" -> false;
            default -> true;
        };
    }

    private static final class BearerTokenFilter implements Filter {

        private final String expectedToken;
        private final McpOAuthServlet oauthServlet;

        private BearerTokenFilter(String token, McpOAuthServlet oauthServlet) {
            this.expectedToken = token;
            this.oauthServlet = oauthServlet;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (!(request instanceof HttpServletRequest httpRequest)
                    || !(response instanceof HttpServletResponse httpResponse)) {
                chain.doFilter(request, response);
                return;
            }

            if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
                httpResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
                httpResponse.setHeader("Access-Control-Allow-Headers",
                        "Authorization, Content-Type, MCP-Protocol-Version, MCP-Session-Id, Last-Event-ID");
                httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            String authorization = httpRequest.getHeader("Authorization");
            String bearerToken = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? authorization.substring(7)
                    : null;
            boolean staticTokenAccepted = expectedToken != null && expectedToken.equals(bearerToken);
            boolean oauthTokenAccepted = oauthServlet != null && oauthServlet.acceptsAccessToken(bearerToken);
            if (!staticTokenAccepted && !oauthTokenAccepted) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                String challenge = "Bearer";
                if (oauthServlet != null) {
                    challenge += " resource_metadata=\"" + oauthServlet.protectedResourceMetadataUrl(httpRequest) + "\"";
                }
                httpResponse.setHeader("WWW-Authenticate", challenge);
                httpResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpResponse.setHeader("Access-Control-Expose-Headers", "WWW-Authenticate, MCP-Session-Id");
                httpResponse.setContentType("text/plain;charset=UTF-8");
                httpResponse.getWriter().write("Unauthorized");
                return;
            }

            chain.doFilter(request, response);
        }
    }
}