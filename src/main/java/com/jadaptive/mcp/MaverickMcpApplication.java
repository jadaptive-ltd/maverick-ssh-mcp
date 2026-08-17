package com.jadaptive.mcp;

import java.util.concurrent.Callable;

import com.jadaptive.mcp.McpRuntime.Mode;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "maverick-mcp", mixinStandardHelpOptions = true, description = "Maverick Synergy MCP server")
public class MaverickMcpApplication implements Callable<Integer> {

    @Option(names = "--mode", defaultValue = "stdio", description = "Transport mode: ${COMPLETION-CANDIDATES}")
    private ModeOption mode;

    @Option(names = "--host", defaultValue = "0.0.0.0", description = "HTTP bind host (HTTP mode)")
    private String host;

    @Option(names = "--port", defaultValue = "7693", description = "HTTP bind port (HTTP mode)")
    private int port;

    @Option(names = "--endpoint", defaultValue = "/mcp", description = "MCP HTTP endpoint path")
    private String endpoint;

    @Option(names = "--destructive-policy", defaultValue = "prompt", description = "Destructive operation policy: prompt|allow")
    private String destructivePolicy;

    public static void main(String[] args) {
        int code = new CommandLine(new MaverickMcpApplication()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() throws Exception {
        DestructivePolicy policy = DestructivePolicy.fromCli(destructivePolicy);
        Mode runtimeMode = mode == ModeOption.http ? Mode.HTTP : Mode.STDIO;

        try (McpRuntime runtime = new McpRuntime(policy, runtimeMode, host, port, endpoint)) {
            runtime.start();
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
            runtime.blockUntilStopped();
            return 0;
        }
    }

    enum ModeOption {
        stdio,
        http
    }
}
