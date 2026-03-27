#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$ROOT_DIR/build/libs/mcp-trial-kotlin-all.jar"

resolve_java_bin() {
  if command -v java >/dev/null 2>&1; then
    if java -version >/dev/null 2>&1; then
      command -v java
      return
    fi
  fi

  local candidate
  for candidate in \
    "/opt/homebrew/opt/openjdk@21/bin/java" \
    "/opt/homebrew/opt/openjdk/bin/java" \
    "/usr/local/opt/openjdk@21/bin/java" \
    "/usr/local/opt/openjdk/bin/java"; do
    if [[ -x "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done

  echo "Errore: Java non trovato. Installa un JDK 21+ o configura PATH/JAVA_HOME." >&2
  exit 1
}

JAVA_BIN="$(resolve_java_bin)"

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
