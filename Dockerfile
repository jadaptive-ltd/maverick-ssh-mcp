# syntax=docker/dockerfile:1

FROM debian:bookworm-slim
ARG TARGETARCH

ENV MCP_HOST=0.0.0.0
ENV MCP_PORT=7693
WORKDIR /app
COPY --chmod=755 docker/native/maverick-ssh-mcp-linux-${TARGETARCH} /app/maverick-ssh-mcp
EXPOSE 7693
ENTRYPOINT ["/bin/sh", "-c", "/app/maverick-ssh-mcp --mode http --host \"${MCP_HOST}\" --port \"${MCP_PORT}\" --endpoint /mcp"]