"""Writes the models and block states of the blocks elemental mutants leave behind (mutant fire, soul fire, frost
spikes, blight). Every one is kitbashed from vanilla: the fires use vanilla's own fire models, the spikes packed and
blue ice, the blight vanilla's sculk tinted in code. No texture is made or copied.

Run from the project root: python art/hazard_blocks.py
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'evoluta')
# a list of plain numbers (from, to, uv, origin), which json.dumps would spread one number per line
NUMBER_LIST = re.compile(r'\[\s+([-0-9.,\s]+?)\s+\]')


def write(path, data):
    path = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = NUMBER_LIST.sub(lambda match: '[' + ' '.join(match.group(1).split()) + ']', json.dumps(data, indent=2))
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text + '\n')


def fire_state(prefix):
    """Vanilla soul fire's block state (fire on the floor, its four sides turned), on the given fire models."""
    parts = [{'apply': [{'model': f'minecraft:block/{prefix}_floor0'}, {'model': f'minecraft:block/{prefix}_floor1'}]}]
    sides = [f'{prefix}_side0', f'{prefix}_side1', f'{prefix}_side_alt0', f'{prefix}_side_alt1']
    for y in (0, 90, 180, 270):
        apply = []
        for side in sides:
            model = {'model': f'minecraft:block/{side}'}
            if y:
                model['y'] = y
            apply.append(model)
        parts.append({'apply': apply})
    return {'multipart': parts}


def box(x0, y0, z0, x1, y1, z1, texture, rotation=None, tint=False):
    """One element with all six faces, UVs taken from its size so the texture keeps its scale."""
    faces = {}
    for face, (u0, v0, u1, v1) in {
        'north': (16 - x1, 16 - y1, 16 - x0, 16 - y0), 'south': (x0, 16 - y1, x1, 16 - y0),
        'west': (z0, 16 - y1, z1, 16 - y0), 'east': (16 - z1, 16 - y1, 16 - z0, 16 - y0),
        'up': (x0, z0, x1, z1), 'down': (x0, 16 - z1, x1, 16 - z0),
    }.items():
        faces[face] = {'uv': [round(u0, 2), round(v0, 2), round(u1, 2), round(v1, 2)], 'texture': texture}
        if tint:
            faces[face]['tintindex'] = 0
    element = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': faces}
    if rotation:
        element['rotation'] = rotation
    return element


def spike(cx, cz, height, width, axis, angle):
    """A spike leaning by angle: a packed-ice shaft narrowing in three steps to a blue-ice point."""
    rotation = {'origin': [cx, 0, cz], 'axis': axis, 'angle': angle}
    steps = [(width, 0.0, height * 0.45, '#ice'), (width * 0.66, height * 0.45, height * 0.8, '#ice'),
             (width * 0.33, height * 0.8, height, '#tip')]
    return [box(cx - w / 2, y0, cz - w / 2, cx + w / 2, y1, cz + w / 2, texture, rotation) for w, y0, y1, texture in steps]


def frost_spikes():
    elements = []
    elements += spike(7.0, 7.5, 15.0, 4.0, 'z', 22.5)
    elements += spike(10.5, 9.0, 11.0, 3.4, 'x', -22.5)
    elements += spike(5.0, 11.0, 8.0, 3.0, 'x', 22.5)
    elements += spike(10.0, 4.5, 9.0, 3.0, 'z', -22.5)
    elements += spike(4.0, 4.0, 6.0, 2.4, 'z', 22.5)
    return {
        'ambientocclusion': False,
        'textures': {'particle': 'minecraft:block/packed_ice', 'ice': 'minecraft:block/packed_ice', 'tip': 'minecraft:block/blue_ice'},
        'elements': elements,
    }


def blight():
    """A 2-pixel crust with blisters, all tinted (tintindex 0) by the client's Toxic green."""
    elements = [box(0, 0, 0, 16, 2, 16, '#goo', tint=True)]
    for x0, z0, size, height in ((2, 3, 3, 1.5), (9, 2, 4, 2.5), (11, 10, 3, 1.5), (4, 10, 4, 2.0), (7, 7, 2, 1.0)):
        elements.append(box(x0, 2, z0, x0 + size, 2 + height, z0 + size, '#goo', tint=True))
    return {'textures': {'particle': 'minecraft:block/sculk', 'goo': 'minecraft:block/sculk'}, 'elements': elements}


def tiered(model):
    """The same model at every tier, turned four ways at random so neighbours differ."""
    turns = [{'model': model}] + [{'model': model, 'y': y} for y in (90, 180, 270)]
    return {'variants': {f'tier={tier}': turns for tier in (1, 2, 3)}}


def main():
    write('blockstates/mutant_fire.json', fire_state('fire'))
    write('blockstates/soul_fire.json', fire_state('soul_fire'))
    write('models/block/frost_spikes.json', frost_spikes())
    write('blockstates/frost_spikes.json', tiered('evoluta:block/frost_spikes'))
    write('models/block/blight.json', blight())
    write('blockstates/blight.json', tiered('evoluta:block/blight'))
    print('wrote hazard block models and states')


if __name__ == '__main__':
    main()
