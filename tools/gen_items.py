#!/usr/bin/env python3
"""
Item / block / effect textures for Giant Refresh, made from vanilla textures the vanilla way.

Usage: python3 tools/gen_items.py <vanilla assets/minecraft/textures dir> <mod textures dir>
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from mctex import bevel, recolor_lum  # noqa: E402


def hx(s):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


# ----------------------------------------------------------------------------- Giant Tooth
def giant_tooth(out):
    """A big molar in the vanilla bone palette, bevel-shaded like vanilla icons."""
    shape = [
        "................",
        "................",
        "...##..##..##...",
        "..############..",
        "..############..",
        "..############..",
        "..############..",
        "...##########...",
        "...##########...",
        "....########....",
        "....###..###....",
        "....###..###....",
        "....##....##....",
        ".....#....#.....",
        "................",
        "................",
    ]
    body = {(x, y) for y, row in enumerate(shape) for x, ch in enumerate(row) if ch == '#'}
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    # bone: highlight, light, base, shade, dark ; outline TL lighter, BR darker (as item/bone)
    pal = [hx('fffffd'), hx('fcfbed'), hx('e8e5d2'), hx('cbc6a5'), hx('a5a189')]
    bevel(px, body, pal, outline=(hx('a5a189'), hx('7b7e6b')))
    # grooves between the cusps
    for x, y in ((5, 3), (6, 3), (9, 3), (10, 3), (6, 4), (9, 4)):
        px[x, y] = hx('cbc6a5')
    # a glossy streak on the crown
    for x, y in ((3, 4), (3, 5), (4, 4)):
        px[x, y] = hx('fffffd')
    # old, stained roots
    for x, y in ((4, 11), (5, 12), (10, 11), (10, 12), (5, 13), (10, 13), (11, 12)):
        px[x, y] = hx('a5a189') if (x + y) % 2 else hx('8f8a6e')
    im.save(out)
    return im


# ----------------------------------------------------------------------------- spawn egg
def giant_spawn_egg(vanilla, out):
    """Vanilla zombie spawn egg with the harness hat (brown cap + goggle strap) on top."""
    src = Image.open(os.path.join(vanilla, 'item/zombie_spawn_egg.png')).convert('RGBA')
    im = src.copy()
    px = im.load()
    brown = [hx('b17141'), hx('925e38'), hx('7d4e29'), hx('5e371a'), hx('4e2c13'), hx('301b0d')]
    for y in range(0, 6):
        for x in range(16):
            r, g, b, a = px[x, y]
            if not a:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            if lum < 50:
                px[x, y] = brown[5] if lum < 40 else brown[4]
            else:
                idx = 0 if lum > 120 else 1 if lum > 100 else 2 if lum > 80 else 3
                px[x, y] = brown[idx]
    # goggles strap with two lenses
    strap_y = 6
    for x in range(16):
        if px[x, strap_y][3]:
            px[x, strap_y] = brown[4]
    for x in (5, 6, 9, 10):
        px[x, strap_y] = hx('a6d8e0') if x in (5, 9) else hx('6fa9b5')
    for x in (4, 7, 8, 11):
        if px[x, strap_y][3]:
            px[x, strap_y] = brown[5]
    im.save(out)
    return im


# ----------------------------------------------------------------------------- Growth effect icon
def growth_icon(vanilla, out):
    """Vanilla jump_boost arrows recoloured to the Giant's mossy green, with a wider base arrow."""
    im = recolor_lum(os.path.join(vanilla, 'mob_effect/jump_boost.png'), 95, sat_mul=1.0, dark_hue=-6, lum_mul=0.95)
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a and r > 200 and g > 200 and b > 200:
                px[x, y] = hx('eef8dc') if r > 240 else hx('d3ebb4')
    # widen the lower arrow by one pixel each side: "it keeps getting bigger"
    edge = px[4, 12]
    if edge[3]:
        px[2, 13] = px[3, 13]
        px[15, 13] = px[14, 13]
    im.save(out)
    return im


# ----------------------------------------------------------------------------- Giant Head block
def head_block(skin_path, outdir):
    """Block faces straight from the Giant skin's head (16 texels per face on the 128x128 skin)."""
    skin = Image.open(skin_path).convert('RGBA')
    faces = {'right': (0, 16), 'front': (16, 16), 'left': (32, 16), 'back': (48, 16), 'top': (16, 0), 'bottom': (32, 0)}
    for name, (u, v) in faces.items():
        skin.crop((u, v, u + 16, v + 16)).save(os.path.join(outdir, f'giant_head_{name}.png'))
    icon = skin.crop((16, 16, 32, 32)).resize((128, 128), Image.NEAREST)
    canvas = Image.new('RGBA', (128, 128))
    canvas.alpha_composite(icon)
    return canvas


def main():
    vanilla, tex = sys.argv[1], sys.argv[2]
    giant_tooth(os.path.join(tex, 'item/giant_tooth.png'))
    giant_spawn_egg(vanilla, os.path.join(tex, 'item/giant_spawn_egg.png'))
    growth_icon(vanilla, os.path.join(tex, 'mob_effect/growth.png'))
    icon = head_block(os.path.join(tex, 'entity/giant/giant.png'), os.path.join(tex, 'block'))
    icon.save(os.path.join(tex, '..', 'icon.png'))


if __name__ == '__main__':
    main()
