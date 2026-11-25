# Maverick SSH MCP

Maverick SSH MCP is a handle-oriented MCP server for SSH automation.

## Features

- SSH connection lifecycle tools (open, inspect, close).
- Stateless one-command execution through SSH exec requests.
- Interactive shell tools for stateful command execution and output streaming.
- ExpectShell tools for command-oriented shell automation with expect-style flows.
- SFTP tools for directory and file operations.
- SCP upload/download tools.
- Local and remote tunnel tools (TCP and Unix sockets).
- SSH Teams integration for OAuth device registration and certificate-based authentication.
- MCP resources for handle registry and destructive-operation policy.

## Run with Docker

```bash
docker run --rm -p 7693:7693 \
  -e MCP_TOKEN=replace-with-token \
  -v maverick-sshteam-data:/root/.sshteam-mcp \
  jadaptive/maverick-ssh-mcp:latest
```

The named volume keeps SSH Team registrations and credentials across container recreation. Omit it if you do not use SSH Team authentication.

## Request Debug Logging

- Enable with CLI flag: `--debug-requests`
- Or enable with environment variable: `MCP_DEBUG_REQUESTS=true`
- Logs are written to stderr only, so STDIO MCP payloads on stdout remain unchanged.

### Compose Fragment

```yaml
services:
  maverick-ssh-mcp:
    image: jadaptive/maverick-ssh-mcp:latest
    ports:
      - "7693:7693"
    environment:
      MCP_HOST: 0.0.0.0
      MCP_PORT: 7693
      MCP_TOKEN: ${MCP_TOKEN}
    volumes:
      - maverick-sshteam-data:/root/.sshteam-mcp

volumes:
  maverick-sshteam-data:
```

## Download Locations

All artifacts are available through Jadaptive Athene, our universal repository:

- [Athene repository home](https://athene.jadaptive.com)
- Repository setup docs:
  - [Debian/Ubuntu APT repository setup](https://athene.jadaptive.com/r/debian/jadaptive/)
  - [Red Hat RPM repository setup](https://athene.jadaptive.com/r/rpm/jadaptive/)

Distribution channels:

- Docker:
  - [Docker Hub: `jadaptive/maverick-ssh-mcp`](https://hub.docker.com/r/jadaptive/maverick-ssh-mcp)
- Linux Debian/Ubuntu:
  - Install package `maverick-ssh-mcp`
  - [APT repository setup instructions](https://athene.jadaptive.com/r/debian/jadaptive/)
- Linux Red Hat/Rocky/Alma/Fedora:
  - Install package `maverick-ssh-mcp`
  - [RPM repository setup instructions](https://athene.jadaptive.com/r/rpm/jadaptive/)
- Linux generic binaries:
  - [amd64 executable](https://athene.jadaptive.com/r/files/jadaptive/maverick-ssh-mcp/current/LINUX/amd64/maverick-ssh-mcp)
  - [arm64 executable](https://athene.jadaptive.com/r/files/jadaptive/maverick-ssh-mcp/0.0.1.26/LINUX/arm64/maverick-ssh-mcp)
- macOS packages (.pkg):
  - [Apple Silicon (aarch64) package](https://athene.jadaptive.com/r/files/jadaptive/maverick-ssh-mcp/current/MACOS/aarch64/maverick-ssh-mcp.pkg)
  - [Intel (amd64) package](https://athene.jadaptive.com/r/files/jadaptive/maverick-ssh-mcp/current/MACOS/amd64/maverick-ssh-mcp.pkg)
- Windows executable (.exe):
  - [Windows setup installer (NSIS)](https://athene.jadaptive.com/r/files/jadaptive/maverick-ssh-mcp-installer/current/WINDOWS/amd64/maverick-ssh-mcp-setup.exe)
  - [amd64 executable](https://athene.jadaptive.com/r/files/jadaptive/maverick-ssh-mcp/current/WINDOWS/amd64/maverick-ssh-mcp.exe)

For development and local build instructions, see `BUILDING.md`.

## HTTP Authentication and OAuth

When `MCP_TOKEN` is configured in HTTP mode, the server exposes MCP OAuth authorization-server discovery, dynamic client registration, authorization-code exchange with PKCE, and bearer-token validation. MCP clients can connect to `http://host:7693/mcp` without separately configuring an OAuth endpoint.

The built-in authorization flow opens a browser form. Enter the same `MCP_TOKEN` value as the password, then select **Allow** or **Reject**. Dynamic client registrations are persisted in `~/.sshteam-mcp/oauth-clients.json`, so MCP clients such as Inspector can reconnect after a server restart without re-registering. Set `--oauth-state-dir` or `MCP_OAUTH_STATE_DIR` to choose another state directory; containers should mount that directory as a volume. Authorization transactions, authorization codes, and issued bearer access tokens remain in memory and are intentionally invalidated on restart.

Direct clients that already send `Authorization: Bearer <MCP_TOKEN>` continue to work. The static token is therefore both the legacy bearer credential and the password for the built-in OAuth form.

```bash
MCP_TOKEN=replace-with-token \
  java -jar target/maverick-ssh-mcp-0.9.0-SNAPSHOT.jar \
  --mode http --host 0.0.0.0 --port 7693
```

For a reverse proxy or published URL, set the externally visible origin with `--oauth-issuer` or `MCP_OAUTH_ISSUER`. It must be the origin clients use to reach the server, for example `https://mcp.example.com`.

Custom authentication flows can implement `com.jadaptive.mcp.oauth.OAuthAuthorizationProvider` and pass it to the public `McpRuntime` constructor. The provider owns the interactive UI or external identity-provider redirects; the runtime continues to own OAuth registration, PKCE, authorization codes and access tokens.