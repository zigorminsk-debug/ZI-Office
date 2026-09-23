#!/usr/bin/env python3
"""Генерация res/app.ico для утилиты ZI Office StartFix.

Требуется Pillow (pip install pillow). Иконка уже собрана в res/app.ico,
скрипт нужен только для её перерисовки.
"""
import os
import sys

try:
    from PIL import Image, ImageDraw
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False


def make_basic_ico(path: str, size: int = 32) -> None:
    """Запасной вариант без Pillow: синий квадрат с белым «окном»."""
    import struct
    px = bytearray()
    cell = size // 2 - size // 8
    for y in range(size):
        for x in range(size):
            inside = (size // 8 <= x < size // 8 + cell or size // 2 + size // 16 <= x < size // 2 + size // 16 + cell) and \
                     (size // 8 <= y < size // 8 + cell or size // 2 + size // 16 <= y < size // 2 + size // 16 + cell)
            if inside:
                px += bytes((0xFF, 0xFF, 0xFF, 0xFF))       # белый (BGRA)
            else:
                px += bytes((0xD1, 0x2F, 0x0B, 0xFF))       # синий (BGRA)
    dib = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 32, 0, len(px), 0, 0, 0, 0)
    hdr = struct.pack("<IIHH", 0, 1, 1, 0)
    entry = struct.pack("<BBBBHHII", size, size, 0, 0, 1, 32, len(dib) + len(px), 22)
    with open(path, "wb") as fh:
        fh.write(hdr + entry + dib + bytes(px))

SIZE = 512
BLUE_TOP = (32, 106, 235)
BLUE_BOTTOM = (9, 54, 145)
GREEN = (26, 160, 84)
WHITE = (255, 255, 255)


def rounded_gradient(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        grad.putpixel((0, y), (
            int(BLUE_TOP[0] + (BLUE_BOTTOM[0] - BLUE_TOP[0]) * t),
            int(BLUE_TOP[1] + (BLUE_BOTTOM[1] - BLUE_TOP[1]) * t),
            int(BLUE_TOP[2] + (BLUE_BOTTOM[2] - BLUE_TOP[2]) * t),
            255,
        ))
    grad = grad.resize((size, size))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
    img.paste(grad, (0, 0), mask)
    return img


def draw_window_logo(draw: "ImageDraw.ImageDraw", x: int, y: int, w: int, gap: int) -> None:
    """Логотип «окно» из четырёх прямоугольников (символ меню «Пуск»)."""
    cell = (w - gap) // 2
    for i in range(2):
        for j in range(2):
            cx = x + j * (cell + gap)
            cy = y + i * (cell + gap)
            draw.rounded_rectangle([cx, cy, cx + cell, cy + cell], radius=max(2, cell // 8), fill=WHITE)


def build() -> Image.Image:
    img = rounded_gradient(SIZE)
    draw = ImageDraw.Draw(img)
    # логотип
    logo_w = int(SIZE * 0.46)
    draw_window_logo(draw, int(SIZE * 0.24), int(SIZE * 0.26), logo_w, int(SIZE * 0.035))
    # зелёный круг-галочка (починка)
    r = int(SIZE * 0.22)
    cx, cy = int(SIZE * 0.70), int(SIZE * 0.70)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GREEN, outline=WHITE, width=int(SIZE * 0.022))
    lw = max(3, int(SIZE * 0.055))
    draw.line([
        (cx - int(r * 0.5), cy + int(r * 0.05)),
        (cx - int(r * 0.12), cy + int(r * 0.42)),
        (cx + int(r * 0.52), cy - int(r * 0.38)),
    ], fill=WHITE, width=lw, joint="curve")
    return img


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    res_dir = os.path.join(root, "res")
    os.makedirs(res_dir, exist_ok=True)
    ico_path = os.path.join(res_dir, "app.ico")
    if not HAVE_PIL:
        make_basic_ico(ico_path)
        print("создано (упрощённый значок, без Pillow):", ico_path)
        return 0
    img = build()
    img.save(ico_path, format="ICO",
             sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    png_path = os.path.join(res_dir, "app.png")
    img.resize((256, 256), Image.LANCZOS).save(png_path, format="PNG")
    print("создано:", ico_path)
    print("создано:", png_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
