#!/usr/bin/env python3
"""Упаковка PowerShell-скриптов внутрь исполняемого файла.

Читает каталог scripts/*.ps1 и создаёт src/embedded_scripts.h с массивами
байтов UTF-8. Утилита записывает эти скрипты во временный рабочий каталог
перед запуском, поэтому пользователю нужен только один EXE-файл.
"""
import os
import sys
import datetime


def c_identifier(index: int) -> str:
    return f"zi_f{index}"


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    scripts_dir = os.path.join(root, "scripts")
    out_path = os.path.join(root, "src", "embedded_scripts.h")

    files = sorted(f for f in os.listdir(scripts_dir) if f.lower().endswith(".ps1"))
    if not files:
        print("Нет скриптов в", scripts_dir, file=sys.stderr)
        return 1

    parts = [
        "/* ---------------------------------------------------------------------",
        " *  АВТОМАТИЧЕСКИ СГЕНЕРИРОВАННЫЙ ФАЙЛ — не редактировать вручную.",
        " *  Создан: tools/embed_scripts.py",
        f" *  Дата: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        " * -------------------------------------------------------------------*/",
        "#ifndef ZI_EMBEDDED_SCRIPTS_H",
        "#define ZI_EMBEDDED_SCRIPTS_H",
        "",
    ]

    entries = []
    for i, name in enumerate(files):
        with open(os.path.join(scripts_dir, name), "rb") as fh:
            data = fh.read()
        if data.startswith(b"\xef\xbb\xbf"):      # BOM добавляет сама утилита
            data = data[3:]
        ident = c_identifier(i)
        parts.append(f"/* {name} — {len(data)} байт */")
        parts.append(f"static const unsigned char {ident}[] = {{")
        line = "   "
        for j, b in enumerate(data):
            line += f" {b},"
            if (j + 1) % 24 == 0:
                parts.append(line)
                line = "   "
        if line.strip():
            parts.append(line)
        parts.append("};")
        parts.append("")
        entries.append((name, ident, len(data)))

    parts.append("typedef struct {")
    parts.append("    const wchar_t* name;")
    parts.append("    const unsigned char* data;")
    parts.append("    unsigned int size;")
    parts.append("} zi_embed_file;")
    parts.append("")
    parts.append("static const zi_embed_file zi_embed_files[] = {")
    for name, ident, size in entries:
        parts.append(f'    {{ L"{name}", {ident}, {size} }},')
    parts.append("};")
    parts.append("")
    parts.append(f"#define ZI_EMBED_COUNT {len(entries)}")
    parts.append("")
    parts.append("#endif /* ZI_EMBEDDED_SCRIPTS_H */")
    parts.append("")

    with open(out_path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(parts))

    total = sum(s for _, _, s in entries)
    print(f"embedded_scripts.h: {len(entries)} файл(ов), {total} байт -> {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
