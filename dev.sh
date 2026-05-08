#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --frontend-only) FRONTEND_ONLY=true ;;
    esac
done

if $FRONTEND_ONLY; then
    cleanup() {
        echo ""
        echo "Stopping..."
        kill "$FRONTEND_PID" 2>/dev/null
        wait "$FRONTEND_PID" 2>/dev/null
    }
    trap cleanup INT TERM

    echo "Starting frontend (http://localhost:4200)..."
    cd "$ROOT/frontend" && npm start &
    FRONTEND_PID=$!

    echo "Frontend running — press Ctrl+C to stop"
    wait
    exit 0
fi

cleanup() {
    echo ""
    echo "Stopping..."
    kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null
    wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null
}
trap cleanup INT TERM

BACKEND_PORT=8080
EXISTING_PID=$(lsof -ti tcp:"$BACKEND_PORT" 2>/dev/null)
if [ -n "$EXISTING_PID" ]; then
    echo "Port $BACKEND_PORT in use (PID $EXISTING_PID) — killing..."
    kill "$EXISTING_PID" 2>/dev/null
    while lsof -ti tcp:"$BACKEND_PORT" > /dev/null 2>&1; do sleep 0.2; done
fi

echo "Starting backend (http://localhost:8080)..."
"$ROOT/gradlew" -p "$ROOT" --console plain bootRun --args='--spring.profiles.active=dev' &
BACKEND_PID=$!

echo "Waiting for backend to be ready..."
until curl -sf http://localhost:8080/api/admin/health > /dev/null 2>&1; do
    sleep 1
done
echo "Backend is up."

echo "Starting frontend (http://localhost:4200)..."
cd "$ROOT/frontend" && npm start &
FRONTEND_PID=$!

echo "TubeReturns running — press Ctrl+C to stop"
wait
