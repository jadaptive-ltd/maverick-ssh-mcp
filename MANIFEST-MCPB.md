# MCPB Build Notes

This document describes how `maverick-ssh-mcp` is packaged as an MCP Bundle (`.mcpb`) by CI.

## Where MCPB Is Built

The MCPB is assembled in `Jenkinsfile` in stage `MCPB`, which runs after all native builds complete and before `Docker Hub Image Publish`.

Upstream stages stash native executables:

- Linux amd64: `docker/native/maverick-ssh-mcp-linux-amd64`
- Linux arm64: `docker/native/maverick-ssh-mcp-linux-arm64`
- macOS amd64: `mcpb/server/maverick-ssh-mcp-macos-amd64`
- macOS arm64: `mcpb/server/maverick-ssh-mcp-macos-arm64`
- Windows amd64: `mcpb/server/maverick-ssh-mcp.exe`

The `MCPB` stage unstashes all of the above and builds the bundle.

## Generated Bundle Layout

CI assembles `target/mcpb-bundle` and then packs it with:

```bash
npx -y @anthropic-ai/mcpb pack target/mcpb-bundle target/maverick-ssh-mcp-${FULL_VERSION}.mcpb
```

Resulting logical bundle layout:

```text
maverick-ssh-mcp-<version>.mcpb
|- manifest.json
|- icon.png
`- server/
   |- maverick-ssh-mcp
   |- maverick-ssh-mcp-linux-amd64
   |- maverick-ssh-mcp-linux-arm64
   |- maverick-ssh-mcp-macos-amd64
   |- maverick-ssh-mcp-macos-arm64
   `- maverick-ssh-mcp.exe
```

## Generated `manifest.json`

The manifest is generated from `pom.xml` metadata using `readMavenPom` in Jenkins:

- `name`: `artifactId`
- `display_name`: `name`
- `description`: `description`
- `version`: CI full version (`getFullVersion()`)

Static fields currently used:

- `manifest_version`: `0.3`
- `author.name`: `Jadaptive`
- `icon`: `icon.png`
- `server.type`: `binary`
- `server.entry_point`: `server/maverick-ssh-mcp`
- `compatibility.platforms`: `linux`, `darwin`, `win32`

### Server Command Routing

The generated `mcp_config` is:

- default command: `${__dirname}/server/maverick-ssh-mcp`
- default args: `--mode stdio`
- platform override for Windows:
  - `win32.command = ${__dirname}/server/maverick-ssh-mcp.exe`

## Linux/macOS Disambiguation Strategy

MCPB `platform_overrides` can switch by OS, but not architecture. To support both amd64 and arm64 for Linux/macOS in one bundle, CI copies a committed launcher script:

- `server/maverick-ssh-mcp`

Source file in repository:

- `src/main/mcpb/maverick-ssh-mcp`

The launcher selects the correct binary using `uname -s` + `uname -m` and `exec`s it.

This is why both Linux and macOS binaries are included side by side in `server/`.

## Icon Generation

`icon.png` is committed at `src/main/icons/icon.png` and copied into the bundle as `target/mcpb-bundle/icon.png` during the `MCPB` stage.

## Validation and Inspection

CI validates and inspects the package with:

```bash
npx -y @anthropic-ai/mcpb validate target/mcpb-bundle/manifest.json
npx -y @anthropic-ai/mcpb info target/maverick-ssh-mcp-${FULL_VERSION}.mcpb
```

## Linux Native Image Note

Linux builds append the GraalVM argument below (in `pom.xml` profile `linux-packages`):

- `-H:+StaticExecutableWithDynamicLibC`

This is intentionally Linux-only.

## Maintainer Checklist

When changing MCPB packaging, verify:

1. Stash names in build stages still match unstash names in `MCPB` stage.
2. Binary filenames in `server/` still match launcher and manifest commands.
3. `manifest_version` remains compatible with MCPB CLI and target hosts.
4. `--mode stdio` is preserved for bundle execution unless intentionally changed.
5. Linux-only native-image flags stay scoped to Linux profiles.
