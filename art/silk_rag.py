"""Paints the Diamond Silk Rag's item texture (16x16): a folded silver cloth stitched with diamond thread.

Run from the project root: python art/silk_rag.py [preview_dir]
"""
import math
import os
import sys

from PIL import Image

SIZE = 16
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'evoluta', 'textures', 'item', 'diamond_silk_rag.png')

OUTLINE = (0x2E, 0x36, 0x44)
# shadow, dark, mid, light, highlight
SILK = [(0x7A, 0x88, 0x9E), (0x9C, 0xAA, 0xBE), (0xC0, 0xCC, 0xDA), (0xDE, 0xE8, 0xF2), (0xFA, 0xFC, 0xFF)]
THREAD = (0x3E, 0xD8, 0xD0)
THREAD_DARK = (0x1F, 0x9E, 0x9A)
SPARKLE = (0xFF, 0xFF, 0xFF)


def inside(x, y):
    """A cloth square turned on its corner, its bottom corner hanging lower where it folds over."""
    cx, cy = 7.6, 7.4
    dx, dy = x + 0.5 - cx, y + 0.5 - cy
    u, v = (dx + dy) / math.sqrt(2), (dy - dx) / math.sqrt(2)
    return abs(u) + 0.25 * abs(v) < 6.2 and abs(v) < 5.2


def paint():
    rag = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    cloth = {(x, y) for y in range(SIZE) for x in range(SIZE) if inside(x, y)}
    for x, y in cloth:
        # a fold runs corner to corner: light on the near side, shadowed past it, with ripples
        fold = (x - y) + 0.6 * math.sin((x + y) * 0.9)
        if fold < -3:
            step = 3
        elif fold < 0:
            step = 2
        elif fold < 1:
            step = 0
        elif fold < 4:
            step = 1
        else:
            step = 2
        if (x + y) % 5 == 0 and fold < 0:
            step = min(4, step + 1)
        rag.putpixel((x, y), SILK[step] + (255,))
    for y in range(SIZE):
        for x in range(SIZE):
            if (x, y) not in cloth and any((x + dx, y + dy) in cloth for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                rag.putpixel((x, y), OUTLINE + (255,))
    # diamond thread stitched along the fold, every other pixel
    for i in range(-6, 7):
        x, y = 8 + i, 7 + i
        if (x, y) in cloth and i % 2 == 0:
            rag.putpixel((x, y), (THREAD if i < 0 else THREAD_DARK) + (255,))
    # and a diamond caught in the weave, glinting
    for x, y, color in ((4, 6, THREAD), (5, 5, THREAD), (5, 6, SPARKLE), (5, 7, THREAD_DARK), (6, 6, THREAD_DARK)):
        if (x, y) in cloth:
            rag.putpixel((x, y), color + (255,))
    return rag


if __name__ == '__main__':
    image = paint()
    image.save(OUT)
    if len(sys.argv) > 1:
        preview = Image.new('RGBA', (SIZE * 16 + 32, SIZE * 16 + 32), (0x8B, 0x8B, 0x8B, 255))
        preview.alpha_composite(image.resize((SIZE * 16, SIZE * 16), Image.NEAREST), (16, 16))
        preview.save(os.path.join(sys.argv[1], 'silk_rag_preview.png'))
    print('painted', os.path.normpath(OUT))
