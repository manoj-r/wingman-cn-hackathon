#!/bin/sh
set -eu

cd /app/web
case "${DATABASE_URL:-}" in
  postgresql://*) DATABASE_URL="jdbc:${DATABASE_URL}"; export DATABASE_URL ;;
  postgres://*) DATABASE_URL="jdbc:postgresql://${DATABASE_URL#postgres://}"; export DATABASE_URL ;;
esac

java -jar /app/backend/app.jar &
backend_pid=$!

node node_modules/next/dist/bin/next start --hostname 0.0.0.0 --port 8080 &
frontend_pid=$!

stop_children() {
  kill -TERM "$frontend_pid" "$backend_pid" 2>/dev/null || true
  wait "$frontend_pid" 2>/dev/null || true
  wait "$backend_pid" 2>/dev/null || true
}

trap stop_children INT TERM EXIT
wait "$frontend_pid"
