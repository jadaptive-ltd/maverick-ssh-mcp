#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${IMAGE_NAME:-maverick-mcp:test}"
CONTAINER_NAME="${CONTAINER_NAME:-maverick-mcp-smoke}"
HOST_PORT="${HOST_PORT:-7693}"
CONTAINER_PORT="${CONTAINER_PORT:-7693}"
BUILD_IMAGE="${BUILD_IMAGE:-1}"

cleanup() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ "${BUILD_IMAGE}" == "1" ]]; then
  echo "[smoke] building image ${IMAGE_NAME}"
  docker build -t "${IMAGE_NAME}" "${ROOT_DIR}" >/dev/null
fi

echo "[smoke] starting container ${CONTAINER_NAME}"
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
docker run -d \
  --name "${CONTAINER_NAME}" \
  -e MCP_HOST=0.0.0.0 \
  -e MCP_PORT="${CONTAINER_PORT}" \
  -p "${HOST_PORT}:${CONTAINER_PORT}" \
  "${IMAGE_NAME}" >/dev/null

for _ in {1..30}; do
  if curl -sS "http://127.0.0.1:${HOST_PORT}/" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

status_root=$(curl -sS -o /tmp/mcp-smoke-root-body.txt -w "%{http_code}" "http://127.0.0.1:${HOST_PORT}/")
status_mcp_get=$(curl -sS -o /tmp/mcp-smoke-mcp-get-body.txt -w "%{http_code}" "http://127.0.0.1:${HOST_PORT}/mcp")
status_mcp_post_bad=$(curl -sS -o /tmp/mcp-smoke-mcp-post-bad-body.txt -w "%{http_code}" -X POST "http://127.0.0.1:${HOST_PORT}/mcp" -H "content-type: application/json" -d '{}')
status_mcp_post_rpc=$(curl -sS -o /tmp/mcp-smoke-mcp-post-rpc-body.txt -w "%{http_code}" -X POST "http://127.0.0.1:${HOST_PORT}/mcp" -H "content-type: application/json" -H "accept: application/json, text/event-stream" -H "mcp-session-id: smoke-session" -d '{"jsonrpc":"2.0","id":1,"method":"bogus","params":{}}')

if [[ "${status_root}" != "404" ]]; then
  echo "[smoke] expected GET / -> 404, got ${status_root}"
  exit 1
fi

if [[ "${status_mcp_get}" != "400" ]]; then
  echo "[smoke] expected GET /mcp -> 400, got ${status_mcp_get}"
  cat /tmp/mcp-smoke-mcp-get-body.txt
  exit 1
fi

if [[ "${status_mcp_post_bad}" != "400" ]]; then
  echo "[smoke] expected POST /mcp {} -> 400, got ${status_mcp_post_bad}"
  cat /tmp/mcp-smoke-mcp-post-bad-body.txt
  exit 1
fi

if [[ "${status_mcp_post_rpc}" != "200" && "${status_mcp_post_rpc}" != "400" && "${status_mcp_post_rpc}" != "404" ]]; then
  echo "[smoke] expected POST /mcp JSON-RPC -> 200/400/404, got ${status_mcp_post_rpc}"
  cat /tmp/mcp-smoke-mcp-post-rpc-body.txt
  exit 1
fi

if ! grep -q '"jsonRpcError"' /tmp/mcp-smoke-mcp-post-rpc-body.txt; then
  echo "[smoke] expected JSON-RPC error envelope in response body"
  cat /tmp/mcp-smoke-mcp-post-rpc-body.txt
  exit 1
fi

echo "[smoke] endpoint checks passed"