# syntax=docker/dockerfile:1

FROM ghcr.io/graalvm/native-image-community:25 AS build
WORKDIR /workspace

# The GraalVM image does not include Maven by default.
RUN microdnf install -y maven && microdnf clean all

COPY . .
RUN mvn -Pnative-images -DskipTests package

FROM debian:bookworm-slim
ENV MCP_HOST=0.0.0.0
ENV MCP_PORT=7693
WORKDIR /app
COPY --from=build /workspace/target/maverick-ssh-mcp /app/maverick-ssh-mcp
EXPOSE 7693
ENTRYPOINT ["/bin/sh","-c","/app/maverick-ssh-mcp --mode http --host \"${MCP_HOST}\" --port \"${MCP_PORT}\" --endpoint /mcp"]
