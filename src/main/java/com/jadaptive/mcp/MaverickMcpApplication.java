package com.jadaptive.mcp;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.jadaptive.mcp.McpRuntime.Mode;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import uk.co.bithatch.artifactversion.ArtifactVersion;

@Command(name = "maverick-mcp", mixinStandardHelpOptions = true, description = "Maverick Synergy MCP server", versionProvider = MaverickMcpApplication.Version.class)
public class MaverickMcpApplication implements Callable<Integer> {

    @Option(names = "--mode", defaultValue = "stdio", description = "Transport mode: ${COMPLETION-CANDIDATES}")
    private ModeOption mode;

    @Option(names = "--host", defaultValue = "0.0.0.0", description = "HTTP bind host (HTTP mode)")
    private String host;

    @Option(names = "--port", defaultValue = "7693", description = "HTTP bind port (HTTP mode)")
    private int port;

    @Option(names = "--endpoint", defaultValue = "/mcp", description = "MCP HTTP endpoint path")
    private String endpoint;

    @Option(names = "--oauth-issuer", description = "Public OAuth issuer URL (default: request origin; env: MCP_OAUTH_ISSUER)")
    private String oauthIssuer;

    @Option(names = "--oauth-state-dir", description = "OAuth client registration state directory (default: ~/.sshteam-mcp; env: MCP_OAUTH_STATE_DIR)")
    private Path oauthStateDirectory;

    @Option(names = "--destructive-policy", defaultValue = "prompt", description = "Delete-operation tool gate policy: prompt|allow")
    private String destructivePolicy;

    @Option(names = "--debug-requests", defaultValue = "false", description = "Log MCP tool requests to stderr")
    private boolean debugRequests;

    @Option(names = "--update-check", negatable = true,
            description = "Enable weekly version checks (default: true; env: MCP_UPDATE_CHECK)")
    private Boolean updateCheck;

    private final SshTeamService sshTeamService = new SshTeamService(SshTeamDeviceStore.defaultStore());

    public static void main(String[] args) {
        int code = new CommandLine(new MaverickMcpApplication()).execute(args);
        System.exit(code);
    } 
    
    public final static class Version implements IVersionProvider {
    	@Override
    	public String[] getVersion() throws Exception {
    		return new String[] {  ArtifactVersion.getVersion("com.jadaptive", "maverick-ssh-mcp") };
    	}	
    }

    @Override
    public Integer call() throws Exception {
        ToolGate toolGate = SimpleToolGate.fromCli(destructivePolicy);
        Mode runtimeMode = mode == ModeOption.http ? Mode.HTTP : Mode.STDIO;
        boolean requestDebugEnabled = debugRequests || isTrue(System.getenv("MCP_DEBUG_REQUESTS"));
        boolean updateChecksEnabled = updateCheck != null
                ? updateCheck
                : McpRuntime.updateChecksEnabled(System.getenv("MCP_UPDATE_CHECK"));

        try (McpRuntime runtime = new McpRuntime(
                sshTeamService,
                toolGate,
                runtimeMode,
                host,
                port,
                endpoint,
                System.getenv("MCP_TOKEN"),
                requestDebugEnabled,
                oauthIssuer == null ? System.getenv("MCP_OAUTH_ISSUER") : oauthIssuer,
                null,
                oauthStateDirectory,
                updateChecksEnabled)) {

            runtime.start();
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
            runtime.blockUntilStopped();
            return 0;
        }
    }

    private static boolean isTrue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    enum ModeOption {
        stdio,
        http
    }
}