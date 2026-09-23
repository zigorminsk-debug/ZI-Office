#!/usr/bin/env bash
# Модульные тесты «чистой» логики утилиты на Linux с эмуляцией Win32.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.build/test_rt"

mkdir -p "$ROOT/.build"
gcc -g -O1 -Wall -Wextra -Wno-unused-parameter \
    -I"$ROOT/test/shim" \
    "$ROOT/test/test_rt.c" "$ROOT/test/shim/win_shim.c" \
    -o "$OUT"

"$OUT"
