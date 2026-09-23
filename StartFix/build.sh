#!/usr/bin/env bash
# =============================================================================
#  Сборка ZI Office StartFix (Windows EXE) на Linux/Windows из исходников.
#
#  Требуется:
#    * zig (>= 0.14) с поддержкой `zig cc` и `zig rc`  — например: pip install ziglang
#    * python3 (для упаковки скриптов в EXE и сборки значка)
#    * Pillow — только если нужно перерисовать значок (res/app.ico уже готов)
#
#  Результат: dist/StartFix-x64.exe, dist/StartFix-cli-x64.exe,
#             dist/StartFix-x86.exe, dist/StartFix-cli-x86.exe
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
RES="$ROOT/res"
DIST="$ROOT/dist"
OBJ="$ROOT/.build"
PY="${PYTHON:-python3}"

# --- поиск компилятора -------------------------------------------------------
if [ -z "${ZIG:-}" ]; then
    if command -v zig >/dev/null 2>&1; then
        ZIG="$(command -v zig)"
    elif "$PY" -c "import ziglang" >/dev/null 2>&1; then
        ZIG="$("$PY" -c 'import ziglang,os;print(os.path.join(os.path.dirname(ziglang.__file__),"zig"))')"
    else
        echo "ОШИБКА: не найден zig. Установите: pip install ziglang  (или задайте ZIG=/путь/zig)" >&2
        exit 1
    fi
fi
echo "Компилятор: $ZIG ($("$ZIG" version))"

# --- каталоги заголовков Windows из поставки zig -----------------------------
resolve_lib_dir() {
    local cand rel
    cand="$(dirname "$(readlink -f "$ZIG")")/lib"
    if [ -f "$cand/libc/include/any-windows-any/windows.h" ] || \
       [ -f "$cand/libc/mingw/include/windows.h" ] || \
       [ -f "$cand/include/windows.h" ]; then
        echo "$cand"; return
    fi
    rel="$(cd "$ROOT" && "$ZIG" env 2>/dev/null | sed -n 's/.*lib_dir *= *"\([^"]*\)".*/\1/p' | head -1)"
    case "$rel" in
        /*) echo "$rel" ;;
        "") echo "$cand" ;;
        *)  echo "$ROOT/$rel" ;;
    esac
}

ZIG_LIB_DIR="$(resolve_lib_dir)"
WIN_HEADERS=""
for cand in "$ZIG_LIB_DIR/libc/include/any-windows-any" "$ZIG_LIB_DIR/libc/mingw/include" "$ZIG_LIB_DIR/include"; do
    if [ -f "$cand/windows.h" ]; then WIN_HEADERS="$cand"; break; fi
done
if [ -z "$WIN_HEADERS" ]; then
    echo "ОШИБКА: не найдены заголовочные файлы Windows в поставке zig ($ZIG_LIB_DIR)" >&2
    exit 1
fi
echo "Заголовки Windows: $WIN_HEADERS"

mkdir -p "$DIST" "$OBJ"

# --- 1. Значок ---------------------------------------------------------------
if [ ! -f "$RES/app.ico" ]; then
    echo "--- генерация значка ---"
    "$PY" "$ROOT/tools/make_icon.py"
fi

# --- 2. Встроенные скрипты ---------------------------------------------------
echo "--- упаковка PowerShell-скриптов ---"
"$PY" "$ROOT/tools/embed_scripts.py"

# --- 3. Ресурсы --------------------------------------------------------------
echo "--- компиляция ресурсов ---"
for arch in x86_64 x86; do
    ( cd "$RES" && "$ZIG" rc /fo "$OBJ/app-$arch.res" app.rc )
done

# --- 3.5 Проверка синтаксиса PowerShell (если доступен node + tree-sitter) ---
if command -v node >/dev/null 2>&1 && \
   node --input-type=module -e "import('tree-sitter-powershell')" >/dev/null 2>&1; then
    echo "--- проверка синтаксиса PowerShell ---"
    node "$ROOT/tools/check_ps_syntax.mjs" "$ROOT"/scripts/*.ps1
else
    echo "--- проверка синтаксиса PowerShell пропущена (нужны node и пакеты tree-sitter*) ---"
fi

# --- 4. Сборка ---------------------------------------------------------------
INCLUDE_MINGW="$("$ZIG" env 2>/dev/null | head -1 >/dev/null; echo)"
CFLAGS_COMMON=(
    -O2 -ffreestanding -fno-stack-protector -fno-sanitize=undefined
    -ffunction-sections -fdata-sections
    -Wall -Wextra -Wno-unused-parameter -Wno-unused-variable -Wno-unused-function
    -DUNICODE -D_UNICODE -I"$SRC" -I"$WIN_HEADERS"
)
LIBS=( -lkernel32 -luser32 -lgdi32 -ladvapi32 -lshell32 -lole32 -lcomctl32 )
SOURCES_COMMON=( "$SRC/rt.c" "$SRC/ui.c" "$SRC/cli.c" "$SRC/repair.c" "$SRC/entry.c" )

build() {
    local target="$1" res="$2" subsystem="$3" outname="$4"
    local defines=()
    local sources=( "${SOURCES_COMMON[@]}" )
    if [ "$subsystem" = "console" ]; then
        defines+=( -DZI_CONSOLE -DZI_UI_NO_GUI )
    else
        sources+=( "$SRC/gui.c" )
    fi
    echo "--- сборка $outname ($target, $subsystem) ---"
    "$ZIG" cc -target "$target" "${defines[@]}" \
        "${CFLAGS_COMMON[@]}" -g0 -nostdlib -rtlib=compiler-rt \
        -Wl,--subsystem,"$subsystem" -Wl,--entry=zi_entry -Wl,--gc-sections \
        "${sources[@]}" "$res" "${LIBS[@]}" \
        -o "$DIST/$outname"
    ls -l "$DIST/$outname"
}

# GUI-сборки содержат оба режима (окно и /cli); консольные — только консольный
build x86_64-windows-gnu "$OBJ/app-x86_64.res" windows "StartFix-x64.exe"
build x86_64-windows-gnu "$OBJ/app-x86_64.res" console "StartFix-cli-x64.exe"
build x86-windows-gnu    "$OBJ/app-x86.res"    windows "StartFix-x86.exe"
build x86-windows-gnu    "$OBJ/app-x86.res"    console "StartFix-cli-x86.exe"

# --- 4.5 Служебные файлы отладки и контрольные суммы ---
rm -f "$DIST"/*.pdb
( cd "$DIST" && sha256sum StartFix-x64.exe StartFix-cli-x64.exe StartFix-x86.exe StartFix-cli-x86.exe > SHA256SUMS.txt )
echo "--- контрольные суммы ---"
cat "$DIST/SHA256SUMS.txt"

# --- 5. Архив для распространения -------------------------------------------
echo "--- упаковка архива ---"
"$PY" - "$DIST" "$ROOT" <<'PYEOF'
import os, sys, zipfile
dist, root = sys.argv[1], sys.argv[2]
zip_path = os.path.join(dist, "ZI-Office-StartFix-1.0.0.zip")
names = ["StartFix-x64.exe", "StartFix-cli-x64.exe", "StartFix-x86.exe", "StartFix-cli-x86.exe"]
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    for n in names:
        p = os.path.join(dist, n)
        if os.path.exists(p):
            z.write(p, arcname=os.path.join("StartFix", n))
    readme = os.path.join(root, "README.md")
    if os.path.exists(readme):
        z.write(readme, arcname="StartFix/ПРОЧТИ-МЕНЯ.md")
    sums = os.path.join(dist, "SHA256SUMS.txt")
    if os.path.exists(sums):
        z.write(sums, arcname="StartFix/SHA256SUMS.txt")
print("создано:", zip_path)
PYEOF

echo ""
echo "Готово. Файлы в каталоге: $DIST"
