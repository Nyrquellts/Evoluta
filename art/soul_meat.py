"""Paints the Soul Meat item textures (16x16) and writes their item models.

For each kind two layers:
  textures/item/soul_meat/<kind>.png       the meat, in the kind's own flesh (one shape per family: zombie flesh strip,
                                           skeleton rib chunk, creeper pod cluster, spider abdomen, cave spider egg sac,
                                           baby curled knot)
  textures/item/soul_meat/<kind>_soul.png  the soul and the element's streaks, in grey: SoulMeatClient tints this layer
                                           with the element's colour and makes it throb, so one pair of textures
                                           serves Ignited, Permafrost and Toxic
and one model per element and kind, models/item/<element>_<kind>_soul_meat.json.

Run from the project root: python art/soul_meat.py [preview_dir]
With a preview_dir it also writes soul_meat_preview.png: every item enlarged, tinted as the game tints it.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

SIZE = 16
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'evoluta')
ELEMENTS = {'ignited': (0xFF, 0x8A, 0x2A), 'permafrost': (0x8F, 0xEF, 0xFF), 'toxic': (0x9C, 0xFF, 0x45)}
LIGHT = (-0.5, -0.6, 0.9)

# ramps: outline, deep, dark, mid, light, highlight
RAMPS = {
    'zombie': ['24060E', '4E0F1C', '74192A', '9A2638', 'BC3A4A', 'D8606A'],
    'husk': ['2C1A0E', '4A2C18', '6A4024', '8C5A32', 'AE7646', 'CC9660'],
    'drowned': ['0C2224', '173C40', '22565A', '307478', '449496', '66B4B2'],
    'zombie_villager': ['1E2410', '33401A', '4A5C24', '62782E', '7E9A3C', 'A0BC56'],
    'baby_zombie': ['2A0714', '4C1024', '701A36', '962848', 'BA3C5E', 'DC6680'],
    'skeleton': ['3E3A32', '6E685C', '9A9484', 'C4BEAC', 'E6E0CE', 'FFFFF0'],
    'stray': ['2E3A44', '5A6E7E', '8AA2B4', 'B6CCDA', 'DCEAF2', 'F6FCFF'],
    'bogged': ['34382A', '5E6448', '8A9068', 'B2B88C', 'D2D6AE', 'ECEFD0'],
    'creeper': ['0C1E08', '1A3812', '28521A', '3C7424', '559A32', '7CC052'],
    'spider': ['1A0A08', '3A1612', '54201A', '6E2C22', '8C3C2E', 'AA5442'],
    'cave_spider': ['06181C', '0C2A30', '134048', '1C5860', '287478', '3A9496'],
}
# the sac a skeleton's ribs hold
SACS = {
    'skeleton': ['1A0A20', '2E1238', '461C52', '60286E', '7A3A8A', '9A58AA'],
    'stray': ['0E1E2E', '1A3048', '264662', '34607E', '4A7C9A', '6A9CB8'],
    'bogged': ['14200E', '22361A', '325028', '466C36', '5E8A48', '7AA864'],
}
# fat and sinew streaking the zombie family's meat
FAT = {
    'zombie': ('E9B3A8', 'B87C74'), 'husk': ('E8C898', 'B89464'), 'drowned': ('A8DCD6', '6EA8A2'),
    'zombie_villager': ('C8DC98', '98AC68'),
}
HOLLOW = (0x12, 0x04, 0x0A)
WEB = (0xDA, 0xD2, 0xC8)
FANG = [(0x8A, 0x82, 0x72), (0xEE, 0xE6, 0xD4)]
THREAD = (0xC8, 0xD8, 0xD8)
MOSS = (0x4E, 0x7A, 0x2A)
SPECK = (0xC6, 0xD6, 0xBE)


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def hash01(x, y, salt):
    h = (x * 374761393 + y * 668265263 + salt * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 0x10000


def shade(nx, ny, nz):
    lx, ly, lz = LIGHT
    length = math.sqrt(nx * nx + ny * ny + nz * nz) * math.sqrt(lx * lx + ly * ly + lz * lz)
    return max(0.0, (nx * lx + ny * ly + nz * lz) / length)


def step_for(light):
    return 1 if light < 0.25 else 2 if light < 0.45 else 3 if light < 0.66 else 4 if light < 0.86 else 5


class Canvas:
    """The meat layer and the grey soul layer of one kind, painted pixel by pixel."""

    def __init__(self, ramp):
        self.ramp = [rgb(c) for c in ramp]
        self.meat = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
        self.soul = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
        self.body = set()

    def blob(self, lobes, ramp=None, mottle=0):
        """Overlapping ellipses as one lit, rounded mass; {@code mottle} jitters the shade like a creeper's skin."""
        ramp = [rgb(c) for c in ramp] if ramp else self.ramp
        painted = set()
        for y in range(SIZE):
            for x in range(SIZE):
                best, lobe_at = 0.0, None
                for lobe in lobes:
                    cx, cy, rx, ry = lobe
                    q = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
                    if q < 1 and math.sqrt(1 - q) > best:
                        best, lobe_at = math.sqrt(1 - q), lobe
                if lobe_at:
                    cx, cy, rx, ry = lobe_at
                    step = step_for(shade((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry, best))
                    if mottle:
                        roll = hash01(x, y, mottle)
                        step = max(1, min(5, step + (-1 if roll < 0.3 else 1 if roll > 0.8 else 0)))
                    self.put(x, y, ramp[step])
                    painted.add((x, y))
        return painted

    def outline(self, color=None):
        color = color or self.ramp[0]
        edge = [(x, y) for y in range(SIZE) for x in range(SIZE) if (x, y) not in self.body
                and any((x + dx, y + dy) in self.body for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for x, y in edge:
            self.meat.putpixel((x, y), color + (255,))

    def put(self, x, y, color, solid=True):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.meat.putpixel((x, y), color + (255,))
            if solid:
                self.body.add((x, y))

    def erase(self, x, y):
        self.meat.putpixel((x, y), (0, 0, 0, 0))
        self.body.discard((x, y))

    def glow(self, x, y, level):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.soul.putpixel((x, y), (level, level, level, 255))

    def line(self, points, color, glow=None):
        """A one-pixel stroke through the points, optionally glowing."""
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            steps = max(abs(x1 - x0), abs(y1 - y0))
            for i in range(steps + 1):
                x = round(x0 + (x1 - x0) * i / max(1, steps))
                y = round(y0 + (y1 - y0) * i / max(1, steps))
                if (x, y) in self.body:
                    self.put(x, y, color)
                    if glow:
                        self.glow(x, y, glow)

    def wisp(self, cx, cy):
        """The soul: a glowing skull with dark eyes and a curling tail, in a dark hollow of the meat."""
        for dx in range(-2, 3):
            for dy in range(-3, 3):
                if abs(dx) + abs(dy) <= 3 and (cx + dx, cy + dy) in self.body:
                    self.put(cx + dx, cy + dy, HOLLOW)
        for x, y, level in ((cx, cy - 2, 235),
                            (cx - 1, cy - 1, 225), (cx, cy - 1, 255), (cx + 1, cy - 1, 225),
                            (cx - 1, cy, 55), (cx, cy, 255), (cx + 1, cy, 55),
                            (cx - 1, cy + 1, 235), (cx, cy + 1, 255), (cx + 1, cy + 1, 235),
                            (cx + 1, cy + 2, 200), (cx + 1, cy + 3, 150), (cx, cy + 4, 105)):
            self.put(x, y, HOLLOW)
            self.glow(x, y, level)


def flesh_strip(kind):
    """Zombie family: a torn strip of raw flesh, streaked with fat, strands hanging off it."""
    c = Canvas(RAMPS[kind])
    c.blob([(4.6, 7.8, 4.0, 3.1), (8.6, 7.2, 3.6, 3.6), (12.1, 8.1, 3.1, 2.8)])
    ramp = c.ramp
    fat, fat_shadow = rgb(FAT[kind][0]), rgb(FAT[kind][1])
    # torn edges: bites out of the rim, frayed tufts, strands hanging below
    for x, y in ((2, 5), (14, 6), (5, 11), (10, 11)):
        c.erase(x, y)
    for x, y in ((6, 3), (10, 3), (11, 3)):
        c.put(x, y, ramp[3])
    for x, top, length in ((3, 11, 2), (7, 11, 3), (12, 11, 2)):
        for i in range(length):
            c.put(x, top + i, ramp[2] if i < length - 1 else ramp[1])
    # fat and sinew run along the strip
    c.line([(1, 8), (3, 6), (5, 5)], fat)
    c.line([(2, 9), (4, 10)], fat_shadow)
    c.line([(11, 5), (13, 6), (14, 8)], fat)
    c.line([(12, 10), (13, 9)], fat_shadow)
    c.outline()
    c.wisp(8, 7)
    # the element's streaks run through the torn edges
    for x, y, level in ((2, 7, 150), (4, 9, 170), (13, 7, 160), (12, 9, 140), (7, 12, 120)):
        c.glow(x, y, level)
    if kind == 'drowned':
        for x, y in ((3, 7), (13, 9)):
            c.put(x, y, rgb('8E2140'))
    return c


def rib_chunk(kind):
    """Skeleton family: two ribs cradling a dark sac with the soul inside."""
    c = Canvas(RAMPS[kind])
    c.blob([(8.0, 8.0, 3.4, 5.0)], ramp=SACS[kind])
    bone = c.ramp
    for base, outward in ((3, -1), (11, 1)):
        # two pixels wide, lit on the left, bowing outward in the middle
        for y in range(2, 14):
            x = base + (outward if 5 <= y <= 10 else 0)
            c.put(x, y, bone[4])
            c.put(x + 1, y, bone[3])
        # knobbed ends, three pixels across
        for y in (1, 14):
            first = base - 1 if outward < 0 else base
            c.put(first, y, bone[4])
            c.put(first + 1, y, bone[3])
            c.put(first + 2, y, bone[2])
    c.outline()
    c.wisp(8, 7)
    for x, y, level in ((6, 11, 150), (9, 11, 150), (7, 12, 120), (6, 4, 110), (9, 4, 110)):
        c.glow(x, y, level)
    if kind == 'bogged':
        for x, y in ((3, 4), (4, 9), (12, 6), (11, 12), (2, 12)):
            c.put(x, y, MOSS)
    return c


def pod_cluster(kind):
    """Creeper: four swollen, mottled pods cracked open round the soul, on stubby roots."""
    c = Canvas(RAMPS[kind])
    c.blob([(4.9, 5.1, 3.2, 3.1), (11.0, 4.9, 3.1, 3.2), (4.8, 10.7, 3.0, 3.1), (11.2, 10.8, 3.1, 3.0)], mottle=7)
    ramp = c.ramp
    # grey-white specks, as on a creeper's hide
    for x, y in ((3, 4), (12, 3), (4, 11), (12, 12), (6, 6), (10, 9)):
        if (x, y) in c.body:
            c.put(x, y, SPECK)
    # cracks where the pods split, glowing with the element
    for x, y in ((8, 2), (8, 3), (7, 13), (8, 12), (1, 8), (2, 8), (14, 8), (13, 8)):
        c.put(x, y, ramp[1])
    c.outline()
    for x, y in ((5, 14), (4, 15), (11, 14), (12, 15)):
        c.put(x, y, ramp[0])
    c.wisp(8, 7)
    for x, y, level in ((8, 2, 150), (8, 12, 170), (7, 13, 120), (2, 8, 160), (13, 8, 160), (3, 5, 90), (12, 10, 90)):
        c.glow(x, y, level)
    return c


def abdomen(kind):
    """Spider: a bloated abdomen laced with web, a dark underside and two fangs."""
    c = Canvas(RAMPS[kind])
    c.blob([(8.5, 11.0, 5.6, 2.6)], ramp=['1E1C22', '1E1C22', '242229', '2E2B33', '3A3640', '46424C'])
    c.blob([(8.3, 7.2, 6.2, 4.6)])
    c.line([(3, 5), (5, 7), (8, 8), (10, 8)], WEB, glow=70)
    c.line([(9, 3), (11, 4), (13, 7), (14, 9)], WEB, glow=70)
    c.line([(4, 3), (6, 3)], WEB)
    c.outline()
    for x in (5, 11):
        c.put(x, 13, FANG[1])
        c.put(x, 14, FANG[0])
    c.wisp(9, 6)
    for x, y, level in ((5, 7, 140), (8, 8, 160), (11, 4, 130), (13, 7, 150)):
        c.glow(x, y, level)
    return c


def egg_sac(kind):
    """Cave spider: a clutch of eggs, each with a glowing spot, trailing a thread of silk."""
    c = Canvas(RAMPS[kind])
    c.blob([(4.8, 9.2, 2.7, 2.7), (8.5, 7.0, 2.9, 2.9), (12.0, 9.1, 2.6, 2.6), (6.1, 12.6, 2.3, 2.2), (10.6, 12.4, 2.4, 2.3)])
    c.outline()
    for x, y in ((11, 4), (12, 3), (13, 2), (14, 1), (15, 0)):
        c.put(x, y, THREAD, solid=False)
    c.wisp(8, 7)
    for x, y, level in ((4, 9, 230), (12, 9, 220), (6, 12, 200), (10, 12, 210), (12, 3, 110), (14, 1, 90)):
        c.glow(x, y, level)
    return c


def curled_knot(kind):
    """Baby zombie: a small tube of flesh coiled tight round the soul, lit on its upper-left side."""
    c = Canvas(RAMPS[kind])
    ramp = c.ramp
    turns, inner, outer = 1.3, 2.2, 6.6
    samples = [(8.0 + math.cos(t) * r, 8.0 + math.sin(t) * r, t) for t, r in
               ((i / 400 * turns * 2 * math.pi, outer - (outer - inner) * i / 400) for i in range(401))]
    for y in range(SIZE):
        for x in range(SIZE):
            px, py = x + 0.5, y + 0.5
            best = min(samples, key=lambda s: (s[0] - px) ** 2 + (s[1] - py) ** 2)
            distance = math.hypot(best[0] - px, best[1] - py)
            if distance < 1.2:
                # the tube's roundness: its side facing the light is lit
                nx, ny = (px - best[0]) / 1.2, (py - best[1]) / 1.2
                c.put(x, y, ramp[step_for(shade(nx, ny, math.sqrt(max(0.0, 1 - nx * nx - ny * ny))))])
    c.outline()
    c.wisp(8, 8)
    for x, y, level in ((3, 6, 130), (5, 12, 150), (12, 11, 140), (12, 4, 120)):
        if (x, y) in c.body:
            c.glow(x, y, level)
    return c


PAINTERS = {
    'zombie': flesh_strip, 'husk': flesh_strip, 'drowned': flesh_strip, 'zombie_villager': flesh_strip,
    'baby_zombie': curled_knot, 'skeleton': rib_chunk, 'stray': rib_chunk, 'bogged': rib_chunk,
    'creeper': pod_cluster, 'spider': abdomen, 'cave_spider': egg_sac,
}
# SoulMeatClient's tint at rest and at the peak of an Apex beat
REST, APEX_FLARE = 0.8, 0.45


def tinted(canvas, color, beat):
    """The item as the game draws it: the soul layer tinted by the element, pushed toward white on a beat."""
    frame = canvas.meat.copy()
    scale = REST + (1 - REST) * beat
    tint = [min(255, round(channel * scale + (255 - channel * scale) * APEX_FLARE * beat)) for channel in color]
    for y in range(SIZE):
        for x in range(SIZE):
            level, _, _, alpha = canvas.soul.getpixel((x, y))
            if alpha:
                frame.putpixel((x, y), tuple(level * t // 255 for t in tint) + (255,))
    return frame


def preview(canvases, directory):
    scale, pad = 6, 10
    columns = [(e, c, beat) for e, c in ELEMENTS.items() for beat in (0, 1)]
    sheet = Image.new('RGBA', (140 + len(columns) * (SIZE * scale + pad), 24 + len(canvases) * (SIZE * scale + pad)), (27, 27, 31, 255))
    draw = ImageDraw.Draw(sheet)
    for column, (element, _, beat) in enumerate(columns):
        draw.text((140 + column * (SIZE * scale + pad), 6), f'{element} {"pulse" if beat else "rest"}', fill=(200, 200, 210, 255))
    for row, (kind, canvas) in enumerate(canvases.items()):
        y = 24 + row * (SIZE * scale + pad)
        draw.text((8, y + SIZE * scale // 2 - 6), kind, fill=(200, 200, 210, 255))
        for column, (_, color, beat) in enumerate(columns):
            frame = tinted(canvas, color, beat).resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            sheet.alpha_composite(frame, (140 + column * (SIZE * scale + pad), y))
    sheet.save(os.path.join(directory, 'soul_meat_preview.png'))


def main():
    textures = os.path.join(ASSETS, 'textures', 'item', 'soul_meat')
    models = os.path.join(ASSETS, 'models', 'item')
    os.makedirs(textures, exist_ok=True)
    canvases = {}
    for kind, painter in PAINTERS.items():
        canvas = painter(kind)
        canvas.meat.save(os.path.join(textures, f'{kind}.png'))
        canvas.soul.save(os.path.join(textures, f'{kind}_soul.png'))
        canvases[kind] = canvas
        for element in ELEMENTS:
            model = {'parent': 'minecraft:item/generated', 'textures': {
                'layer0': f'evoluta:item/soul_meat/{kind}', 'layer1': f'evoluta:item/soul_meat/{kind}_soul'}}
            with open(os.path.join(models, f'{element}_{kind}_soul_meat.json'), 'w', encoding='utf-8', newline='\n') as f:
                json.dump(model, f, indent=2)
                f.write('\n')
    if len(sys.argv) > 1:
        preview(canvases, sys.argv[1])
    print('painted', len(canvases), 'kinds')


if __name__ == '__main__':
    main()
