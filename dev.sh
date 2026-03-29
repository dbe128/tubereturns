#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

cleanup() {
    echo ""
    echo "Stopping..."
    kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null
    wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null
}
trap cleanup INT TERM

echo "Starting backend (http://localhost:8080, debug port 5005)..."
"$ROOT/gradlew" -p "$ROOT" --console plain bootRun --args='--spring.profiles.active=dev' &
BACKEND_PID=$!

echo "Starting frontend (http://localhost:4200)..."
cd "$ROOT/frontend" && npm start &
FRONTEND_PID=$!

echo "TubeReturns running — press Ctrl+C to stop"
wait
