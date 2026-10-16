#!/usr/bin/env bash
# Run the local app and connect it to ChatGPT through OpenAI Secure MCP Tunnel.
# The tunnel provides HTTPS transport without exposing a public listener.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_PID=""
cleanup() {
  [[ -z "$APP_PID" ]] || kill "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

cd "$SCRIPT_DIR"
source "$SCRIPT_DIR/load-env.sh"

for executable in npm curl tunnel-client; do
  if ! command -v "$executable" >/dev/null 2>&1; then
    echo "Required command not found: $executable" >&2
    exit 1
  fi
done

node_ok() {
  local major minor
  major="$(node -p 'Number(process.versions.node.split(".")[0])')"
  minor="$(node -p 'Number(process.versions.node.split(".")[1])')"
  (( major > 22 || (major == 22 && minor >= 12) ))
}
if ! command -v node >/dev/null 2>&1 || ! node_ok; then
  for candidate in /opt/homebrew/opt/node/bin/node /opt/homebrew/opt/node@23/bin/node /opt/homebrew/opt/node@22/bin/node; do
    if [[ -x "$candidate" ]]; then
      export PATH="$(dirname "$candidate"):$PATH"
      if node_ok; then break; fi
    fi
  done
fi
if ! command -v node >/dev/null 2>&1 || ! node_ok; then
  echo "Node.js 22.12+ is required (found $(node --version 2>/dev/null || echo unavailable))." >&2
  exit 1
fi
if [[ -z "${CONTROL_PLANE_TUNNEL_ID:-}" ]]; then
  echo "Set CONTROL_PLANE_TUNNEL_ID to the provisioned OpenAI Secure MCP Tunnel ID." >&2
  exit 1
fi
if [[ -z "${CONTROL_PLANE_API_KEY:-}" ]]; then
  echo "Set CONTROL_PLANE_API_KEY in the process environment to a runtime key with Tunnels Read and Use. Do not use an admin key." >&2
  exit 1
fi

if [[ -z "${ORACLE_MCP_URL:-}" ]]; then
  echo "Set ORACLE_MCP_URL in chatgpt/.env to the running Oracle MCP Toolkit endpoint." >&2
  exit 1
fi

npm ci
npm run build
PORT="${PORT:-3001}" MCP_BIND_HOST=127.0.0.1 npx tsx server.ts &
APP_PID=$!

for attempt in {1..60}; do
  if curl --silent --fail "http://127.0.0.1:${PORT}/health" >/dev/null; then
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "MCP app server exited before becoming healthy." >&2
    exit 1
  fi
  sleep 1
done
if ! curl --silent --fail "http://127.0.0.1:${PORT}/health" >/dev/null; then
  echo "MCP app server did not become healthy on port ${PORT}." >&2
  exit 1
fi

export MCP_SERVER_URL="http://127.0.0.1:${PORT}/mcp"
echo "Local MCP app is healthy at ${MCP_SERVER_URL}. Starting its private OpenAI tunnel."
echo "Keep this process running. In ChatGPT, add a custom MCP server and choose Connection: Tunnel."
tunnel-client run --health.listen-addr "${TUNNEL_HEALTH_LISTEN_ADDR:-127.0.0.1:8766}" --log.level=info --log.format=struct-text
