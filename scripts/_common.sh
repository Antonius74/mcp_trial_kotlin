#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$ROOT_DIR/build/libs/mcp-trial-kotlin-all.jar"

ensure_jar() {
  if [[ -f "$JAR_PATH" ]]; then
    return
  fi

  if [[ -x "$ROOT_DIR/gradlew" ]]; then
    "$ROOT_DIR/gradlew" -q fatJar >/dev/null
    return
  fi

  if command -v gradle >/dev/null 2>&1; then
    (
      cd "$ROOT_DIR"
      gradle -q fatJar >/dev/null
    )
    return
  fi

  echo "Errore: non trovo Gradle. Installa gradle o aggiungi gradle wrapper (./gradlew)." >&2
  exit 1
}
