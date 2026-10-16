#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SCRIPT_DIR="$(pwd)"
source "$SCRIPT_DIR/load-env.sh"
npm ci
npm run build
exec npx tsx server.ts
