#!/usr/bin/env bash
#
# Convenience script: builds the project with Maven and runs the resulting
# jar, forwarding all arguments straight through to the load tester.
#
# Usage:
#   ./run.sh --url http://localhost:8080/ -c 50 -d 30s
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f "target/mini-load-tester.jar" ] || [ "src" -nt "target/mini-load-tester.jar" ]; then
    echo "Building..." >&2
    mvn -q clean package
fi

exec java -jar target/mini-load-tester.jar "$@"
