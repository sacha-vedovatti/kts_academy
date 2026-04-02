#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_LIBS="$ROOT_DIR/build/libs"
CONFIG_DIR="$ROOT_DIR/config"

usage() {
  cat <<'EOF'
Usage:
  ./deploy.sh <DEST>
  ./deploy.sh --dest <DEST> [--jar <JAR_PATH>]

What it does:
  - Copies the most recent mod .jar from build/libs/ into DEST/
  - Copies the local config/ folder into DEST/config/

Examples:
  ./deploy.sh /opt/minecraft/mods
  ./deploy.sh --dest /opt/minecraft/server --jar build/libs/pokedollars-1.0.0.jar

Notes:
  - This script does NOT build the jar. Run ./gradlew build first.
  - DEST can be a server root or a mods folder; the jar is copied directly into DEST.
EOF
}

dest=""
jar_override=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --dest)
      [[ $# -ge 2 ]] || { echo "Missing value for --dest" >&2; exit 2; }
      dest="$2"
      shift 2
      ;;
    --jar)
      [[ $# -ge 2 ]] || { echo "Missing value for --jar" >&2; exit 2; }
      jar_override="$2"
      shift 2
      ;;
    *)
      if [[ -z "$dest" ]]; then
        dest="$1"
        shift
      else
        echo "Unexpected argument: $1" >&2
        usage >&2
        exit 2
      fi
      ;;
  esac
done

if [[ -z "$dest" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -d "$BUILD_LIBS" ]]; then
  echo "Missing directory: $BUILD_LIBS" >&2
  echo "Did you run: ./gradlew build ?" >&2
  exit 1
fi

if [[ -n "$jar_override" ]]; then
  jar_path="$jar_override"
else
  # Pick most recent jar, excluding common non-runtime jars.
  jar_path="$(ls -t "$BUILD_LIBS"/*.jar 2>/dev/null | grep -vE '(-sources|-javadoc)\.jar$' | head -n 1 || true)"
fi

if [[ -z "${jar_path:-}" || ! -f "$jar_path" ]]; then
  echo "No jar found to deploy." >&2
  echo "Looked in: $BUILD_LIBS" >&2
  exit 1
fi

# Ensure destination folders exist.
mkdir -p "$dest/mods"

# Copy jar to destination.
cp -a "$jar_path" "$dest/mods/"

# Copy config directory into DEST/config/
if [[ -d "$CONFIG_DIR" ]]; then
  mkdir -p "$dest/config"
  mkdir -p "$dest/config/ktsacademy"
  cp -a "$CONFIG_DIR/." "$dest/config/ktsacademy"
else
  echo "Warning: config directory not found at $CONFIG_DIR; skipping config copy." >&2
fi

echo "Deployed mod: $(basename "$jar_path")"
echo "  -> $dest/mods/"
if [[ -d "$CONFIG_DIR" ]]; then
  echo "Deployed config: $CONFIG_DIR"
  echo "  -> $dest/config/"
fi
