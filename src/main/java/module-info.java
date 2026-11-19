import com.jadaptive.mcp.McpToolsetExtensions;
import com.jadaptive.mcp.SshTeamServiceExtension;

module com.jadaptive.mcp {
	requires transitive com.sshtools.synergy.common;
	requires com.sshtools.common.logger;
	requires com.sshtools.common.util;
	requires tools.jackson.databind;
	requires info.picocli;
	requires com.sshtools.synergy.client;
	requires com.jadaptive.hsm;
	requires java.net.http;
	requires jdk.httpserver;
	requires jetty.servlet.api;
	requires org.eclipse.jetty.server;
	requires transitive com.sshteam.lib;
	requires org.eclipse.jetty.servlet;
	requires io.modelcontextprotocol.sdk.mcp.core;
	exports com.jadaptive.mcp;
	
	uses io.modelcontextprotocol.json.McpJsonMapperSupplier;
	uses McpToolsetExtensions;
	uses SshTeamServiceExtension;
}
