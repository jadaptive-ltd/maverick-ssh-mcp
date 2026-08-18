#!/bin/sh
# post-install hook for maverick-ssh-mcp

set -e

command -v maverick-ssh-mcp >/dev/null 2>&1 || true
