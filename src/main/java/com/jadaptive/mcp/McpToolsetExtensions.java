package com.jadaptive.mcp;

import io.modelcontextprotocol.server.McpServer.SyncSpecification;
 
public interface McpToolsetExtensions {

	void register(SyncSpecification<?> spec, HandleRegistry registry, ToolGate toolGate,
			SshTeamService sshTeamService);
}
