"""Paints the Blight Locator: a soul-bound compass (iron and copper rim, dark dial, bone needle, a soul gem), in the 32
needle frames the locator's "angle" predicate chooses between, and writes their models.

Frame i points the needle i * 11.25 degrees clockwise from straight up, as vanilla's recovery compass frames do, so
the model's existing angle overrides keep their meaning; only their models change.

The soul gem is its own layer (layer1, painted in grey) set into the rim, out of the needle's reach: the client tints
it in the element colour of the champion the needle points at and flashes it with each click.

Run from the project root: python art/blight_locator.py [preview_dir]
"""
import json
import math
import os
import sys

from PIL import Image

SIZE = 16
FRAMES = 32
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'evoluta')

CENTER = 7.5
OUTLINE = (0x1C, 0x16, 0x16)
COPPER = [(0x6E, 0x36, 0x1E), (0xA8, 0x58, 0x32), (0xD0, 0x7A, 0x48), (0xEE, 0xA2, 0x6E)]
VERDIGRIS = (0x46, 0xA8, 0x90)
IRON = (0xC6, 0xC8, 0xD0)
DIAL = [(0x0A, 0x10, 0x14), (0x10, 0x1C, 0x22), (0x16, 0x26, 0x2E)]
BONE = (0xEC, 0xE4, 0xD2)
BONE_SHADE = (0xB8, 0xAE, 0x9A)
IRON_TAIL = (0x3E, 0x40, 0x4A)
TIP = (0xF4, 0xDE, 0xFF)
TIP_GLOW = (0xB8, 0x6C, 0xFF)
SOCKET = (0x3A, 0x0A, 0x18)
# the gem's pixels and grey levels: 2x2 in the rim below the dial, where the needle (4.5 pixels long) never reaches
GEM = {(7, 13): 0xFF, (8, 13): 0xD8, (7, 14): 0xB4, (8, 14): 0x84}
# the colours the client tints the gem with, for the preview: no signal, then Ignited, Permafrost and Toxic champions
TINTS = [(0xC0, 0x7C, 0xFF), (0xFF, 0x8A, 0x2A), (0x8F, 0xEF, 0xFF), (0x9C, 0xFF, 0x45)]


def body():
    """Everything but the needle: rim, dial, ticks and the soul gem's socket at the bottom of the rim."""
    image = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - CENTER - 0.5, y + 0.5 - CENTER - 0.5
            r = math.hypot(dx, dy)
            if r > 7.4:
                continue
            if r > 6.6:
                color = OUTLINE
            elif r > 5.0:
                # copper rim lit from the top left, verdigris creeping in at the bottom
                light = -(dx + dy) / max(r, 0.01)
                color = COPPER[3] if light > 0.75 else COPPER[2] if light > 0.1 else COPPER[1] if light > -0.6 else COPPER[0]
                if dy > 3.5 and (x * 7 + y * 3) % 5 == 0:
                    color = VERDIGRIS
            elif r > 4.3:
                color = OUTLINE
            else:
                color = DIAL[2] if dx + dy < -3 else DIAL[1] if dx + dy < 1 else DIAL[0]
            image.putpixel((x, y), color + (255,))
    # iron studs at north, east and west, bone ticks just inside
    for x, y in ((8, 1), (14, 8), (1, 8)):
        image.putpixel((x, y), IRON + (255,))
    for x, y in ((8, 4), (12, 8), (4, 8)):
        image.putpixel((x, y), BONE_SHADE + (255,))
    # the gem's socket at south, under the gem (layer1) and either side of it
    for x, y in list(GEM) + [(6, 13), (9, 13), (6, 14), (9, 14)]:
        image.putpixel((x, y), SOCKET + (255,))
    return image


def gem():
    """The soul gem alone, in grey (the tint gives the colour), lit from the top left like the rim."""
    image = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    for (x, y), level in GEM.items():
        image.putpixel((x, y), (level, level, level, 255))
    return image


def frame(index):
    image = body()
    angle = math.radians(index * 360.0 / FRAMES)
    ux, uy = math.sin(angle), -math.cos(angle)
    cx, cy = CENTER + 0.5, CENTER + 0.5
    # the tail, then the bone needle from the pivot to its soul-lit tip
    for step in range(1, 3):
        t = step * 0.55
        x, y = int(math.floor(cx - ux * t)), int(math.floor(cy - uy * t))
        image.putpixel((x, y), IRON_TAIL + (255,))
    points = []
    for step in range(0, 11):
        t = step * 0.45
        x, y = int(math.floor(cx + ux * t)), int(math.floor(cy + uy * t))
        if (x, y) not in points:
            points.append((x, y))
    for i, (x, y) in enumerate(points):
        assert (x, y) not in GEM, f'frame {index}: the needle reaches the gem'
        color = TIP if i == len(points) - 1 else TIP_GLOW if i == len(points) - 2 else BONE if i % 2 == 0 else BONE_SHADE
        image.putpixel((x, y), color + (255,))
    # the copper pivot
    image.putpixel((int(cx), int(cy)), COPPER[3] + (255,))
    return image


def layers(index):
    return {'layer0': f'evoluta:item/blight_locator/{index:02d}', 'layer1': 'evoluta:item/blight_locator/gem'}


def tinted(image, rgb):
    out = image.copy()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = out.getpixel((x, y))
            if a:
                out.putpixel((x, y), (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a))
    return out


def main():
    textures = os.path.join(ASSETS, 'textures', 'item', 'blight_locator')
    models = os.path.join(ASSETS, 'models', 'item')
    os.makedirs(textures, exist_ok=True)
    frames = [frame(i) for i in range(FRAMES)]
    gem().save(os.path.join(textures, 'gem.png'))
    for i, image in enumerate(frames):
        image.save(os.path.join(textures, f'{i:02d}.png'))
        with open(os.path.join(models, f'blight_locator_{i:02d}.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': layers(i)}, f, indent=2)
            f.write('\n')
    # keep the angle thresholds of the locator's model; point each at our frame of the same number
    path = os.path.join(models, 'blight_locator.json')
    with open(path, encoding='utf-8') as f:
        model = json.load(f)
    model['textures'] = layers(16)
    for override in model['overrides']:
        name = override['model']
        number = 16 if name == 'evoluta:item/blight_locator' else int(name.rsplit('_', 1)[1])
        override['model'] = f'evoluta:item/blight_locator_{number:02d}'
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, indent=2)
        f.write('\n')
    if len(sys.argv) > 1:
        # one row per gem tint, as the client shows it
        sheet = Image.new('RGBA', (8 * 16 * 6 + 90, 4 * 16 * 6 + 50), (0x8B, 0x8B, 0x8B, 255))
        for i, image in enumerate(frames):
            shown = image.copy()
            shown.alpha_composite(tinted(gem(), TINTS[i // 8]))
            sheet.alpha_composite(shown.resize((96, 96), Image.NEAREST), (10 + (i % 8) * 106, 10 + (i // 8) * 106))
        sheet.save(os.path.join(sys.argv[1], 'blight_locator_preview.png'))
    print('painted', FRAMES, 'frames and the gem')


if __name__ == '__main__':
    main()
