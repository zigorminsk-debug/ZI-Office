#!/usr/bin/env bash
# Модульные тесты «чистой» логики утилиты на Linux с эмуляцией Win32.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.build/test_rt"

mkdir -p "$ROOT/.build" /tmp/zi-tests-home /tmp/zi-tests-tmp
gcc -g -O1 -Wall -Wextra -Wno-unused-parameter \
    -I"$ROOT/test/shim" \
    "$ROOT/test/test_rt.c" "$ROOT/test/shim/win_shim.c" \
    -o "$OUT"

"$OUT"

# --- тесты ядра утилиты (repair.c собирается с -Dstatic= для доступа внутрь) ---
OUT2="$ROOT/.build/test_app"
gcc -g -O1 -Wall -Wextra -Wno-unused-parameter \
    -I"$ROOT/test/shim" -I"$ROOT/src" \
    "$ROOT/test/test_app.c" "$ROOT/src/repair.c" "$ROOT/src/rt.c" "$ROOT/test/shim/win_shim.c" \
    -DZI_TESTING \
    -o "$OUT2"
"$OUT2"
