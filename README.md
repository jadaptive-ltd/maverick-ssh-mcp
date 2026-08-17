# Maverick MCP

Maverick Synergy based MCP server with handle-oriented SSH tooling.

## Implemented focus (Option B)

This build prioritizes **connection + shell workflows** first:

- Open, inspect and close SSH connections
- Open shell/exec channels, write commands, read stdout/stderr, close channels
- Open/close SFTP clients
- Open/close TCP or Unix-socket tunnels
- Run SCP upload/download operations
- Expose runtime state as MCP resources

## Destructive policy

Use `--destructive-policy prompt|allow`.

- `prompt` (default): destructive actions should require explicit `confirm=true`
- `allow`: destructive actions can proceed without confirmation checks

The current build exposes a guard tool `sftp_remove_guard` that enforces this policy before integrating concrete delete operations.

## MCP resources

- `mcp://maverick/handles` - current handle registry snapshot
- `mcp://maverick/policy` - active destructive policy

## Fast development run (no native build)

Run the MCP server directly with Maven or plain Java without building a native image first. Choose either approach:

### Option 1: `mvn exec:java` (recommended for dev)

Maven resolves dependencies and starts the process in one step:

```bash
cd /home/SOUTHPARK/tanktarta/Workspaces/Maverick-Synergy-Develop-Os/maverick-mcp
# STDIO mode (e.g. for editor/integration testing)
mvn -q exec:java -Dexec.args="--mode stdio"

# Streamable HTTP mode (default port 7693)
mvn -q exec:java -Dexec.args="--mode http --host 0.0.0.0 --port 7693 --endpoint /mcp"
```

Arguments are passed through to `com.jadaptive.mcp.MaverickMcpApplication`.

### Option 2: Resolve deps + launch directly

Download dependencies and run with the system classpath plugin:

```bash
cd /home/SOUTHPARK/tanktarta/Workspaces/Maverick-Synergy-Develop-Os/maverick-mcp

# Step 1: Copy dependencies to target/dependency/
mvn -q dependency:copy-dependencies -DoutputDirectory=target/dependency

# Step 2: Launch with classpath
java \
  -cp "target/classes:target/dependency/*" \
  com.jadaptive.mcp.MaverickMcpApplication \
  --mode stdio
```

### Option 3: Run from packaged JAR (requires `package`)

Build the application JAR (not native) and run it:

```bash
mvn -q package
java -jar target/maverick-mcp-0.0.1-SNAPSHOT.jar --mode stdio
```


## Build

```bash
cd /home/SOUTHPARK/tanktarta/Workspaces/Maverick-Synergy-Develop-Os/maverick-mcp
mvn -q test
mvn -q package
```

## Native image profile

```bash
cd /home/SOUTHPARK/tanktarta/Workspaces/Maverick-Synergy-Develop-Os/maverick-mcp
mvn -Pnative-images -DskipTests package
```

## Docker

The `Dockerfile` runs the native executable in streamable HTTP mode.

Environment variables:

- `MCP_HOST` (default `0.0.0.0`)
- `MCP_PORT` (default `7693`)

### Local Build

```
docker build -t maverick-mcp:local .
```

### Local Run

```bash
docker run --rm -p 7693:7693 maverick-mcp:local
```

## Endpoint smoke test (native Docker)

Run the smoke script to build the image, boot the container, and verify HTTP endpoint behavior (`/` and `/mcp`).

```bash
./scripts/smoke-http.sh
```

Useful overrides:

```bash
BUILD_IMAGE=0 IMAGE_NAME=maverick-mcp:test HOST_PORT=7694 ./scripts/smoke-http.sh
```

## SSH Team integration

- Register this runtime as an SSH Team device with MCP tool `sshteam_register`.
- Credentials are stored locally under `~/.sshteam-mcp`.
- Access/refresh tokens and DPoP private key are encrypted at rest.
- After registration, `ssh_connect` automatically attempts SSH Team certificate auth first.
- If certificate auth does not succeed, normal auth methods continue (private key/password/keyboard-interactive).

`sshteam_register` key args:

- `serverUrl` (required)
- `clientId` (default `sshteam-cli`)
- `scope` (default `signing`)
- `pollIntervalSeconds` (default `5`)
- `maxWaitSeconds` (default `600`)
- `waitForAuthorization` (default `false`; when `false`, returns URL/code immediately)
- `ignoreSslTrust` (default `false`)
- `setDefault` (default `true`)

Typical interactive flow:

1. Call `sshteam_register` with `waitForAuthorization=false` to receive `verificationUri` and `userCode`.
2. Complete approval in the browser.
3. Call `sshteam_register_poll` with `serverUrl` and `deviceCode` to wait for token issuance and save credentials.

`ssh_connect` SSH Team args:

- `sshTeamEnabled` (default `true`)
- `sshTeamServer` (optional; uses default registered server when omitted)
- `sshTeamIgnoreSslTrust` (default `false`)
- `sshTeamCertificateType` (`ED25519` or `RSA`, default `ED25519`)
- `sshTeamTimezone` (optional IANA timezone)

Additional SSH Team tools:

- `sshteam_status` - list local registrations and token state.
- `sshteam_revoke_device` - revoke a remote device by id.
- `sshteam_register_poll` - poll OAuth device flow with an existing `deviceCode`.

## SSH Team smoke test

```bash
./scripts/smoke-sshteam.sh
```