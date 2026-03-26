#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

ensure_jar
cd "$ROOT_DIR"
exec java -jar "$JAR_PATH" mcp-server "$@"
