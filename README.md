# Maverick SSH MCP

Maverick SSH MCP is a handle-oriented MCP server for SSH automation.

## Features

- SSH connection lifecycle tools (open, inspect, close).
- Shell and exec tools for command execution and output streaming.
- SFTP tools for directory and file operations.
- SCP upload/download tools.
- Local and remote tunnel tools (TCP and Unix sockets).
- SSH Teams integration for OAuth device registration and certificate-based authentication.
- MCP resources for handle registry and destructive-operation policy.

## Run with Docker

```bash
docker run --rm -p 7693:7693 -e MCP_TOKEN=replace-with-token jadaptive/maverick-ssh-mcp:latest
```

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
  - [amd64 executable](https://athene.jadaptive.com/r/files/jadaptive/maverick-ssh-mcp/current/WINDOWS/amd64/maverick-ssh-mcp.exe)

For development and local build instructions, see `BUILDING.md`.

