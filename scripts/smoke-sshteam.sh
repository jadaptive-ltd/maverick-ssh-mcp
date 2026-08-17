#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"
echo "[smoke-sshteam] running sshteam integration smoke tests"
mvn -q -Dtest=SshTeamDeviceStoreTest,SshTeamServiceIntegrationTest test

echo "[smoke-sshteam] checks passed"
