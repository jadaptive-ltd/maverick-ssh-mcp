# Building Maverick SSH MCP

This page contains development-focused build and local run instructions.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (optional, for image testing)

## Build and Test

```bash
mvn -q test
mvn -q package
```

## Run for Development (No Native Build)

### Option 1: Run with Maven Exec

```bash
# STDIO mode
mvn -q exec:java -Dexec.args="--mode stdio"

# Streamable HTTP mode
mvn -q exec:java -Dexec.args="--mode http --host 0.0.0.0 --port 7693 --endpoint /mcp"

# HTTP mode with Bearer token authentication
MCP_TOKEN=my-secret-token mvn -q exec:java -Dexec.args="--mode http --host 0.0.0.0 --port 7693 --endpoint /mcp"
```

### Option 2: Launch with Classpath

```bash
mvn -q dependency:copy-dependencies -DoutputDirectory=target/dependency

java \
  -cp "target/classes:target/dependency/*" \
  com.jadaptive.mcp.MaverickMcpApplication \
  --mode stdio
```

### Option 3: Run the Packaged JAR

```bash
mvn -q package
java -jar target/maverick-ssh-mcp-0.0.1-SNAPSHOT.jar --mode stdio
```

## Native Image Build

```bash
mvn -Pnative-images -DskipTests package
```

## Docker Development

Build local image:

```bash
docker build -t maverick-ssh-mcp:local .
```

Run local image:

```bash
docker run --rm -p 7693:7693 maverick-ssh-mcp:local
```

Run local image with endpoint authentication:

```bash
docker run --rm -p 7693:7693 -e MCP_TOKEN=my-secret-token maverick-ssh-mcp:local
```

## Runtime Notes

- Endpoint authentication is enabled when `MCP_TOKEN` is set.
- Default streamable HTTP endpoint is `/mcp` on port `7693`.
- SSH Teams credentials are stored under `~/.sshteam-mcp`.
