#!/usr/bin/env python3
"""Giant skin: the vanilla zombie texture at twice the pixel density (128x128 on the 64x64
zombie/player layout, separate left limbs), restyled after the concept art:

* finer grain made only from the vanilla colours of each face (no new gradients),
* short ragged sleeves, rot spots on the shirt,
* trousers rotting off below the knee into mossy green shins, grey worn soles,
* flat eyelid patches for the sleeping pose.

python3 tools/gen_giant_texture.py <vanilla zombie.png> <out giant.png>
"""
import random
import sys

from PIL import Image

S = 2  # texels per vanilla pixel


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


SKIN = [hx(c) for c in ('799c65', '71955b', '679056', '5a7b48', '4e7b36', '497135', '3e692d', '3b622f')]
SKIN_DARK = [hx(c) for c in ('3b622f', '385226', '2b3f1f')]
MOSS = [hx(c) for c in ('4e7b36', '497135', '3e692d', '5a7b48', '385226')]
ROT = [hx(c) for c in ('4e5b2a', '5c5a2b', '45512a', '3e692d')]
SOLE = [hx(c) for c in ('6b6b6b', '616161', '4c4c4c', '3f3f3f')]


def faces(u, v, w, h, d):
    """Vanilla box UV net, in vanilla pixels: name -> (x0, y0, x1, y1)."""
    return {
        'top': (u + d, v, u + d + w, v + d),
        'bottom': (u + d + w, v, u + d + 2 * w, v + d),
        'right': (u, v + d, u + d, v + d + h),
        'front': (u + d, v + d, u + d + w, v + d + h),
        'left': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


PARTS = {
    'head': faces(0, 0, 8, 8, 8),
    'body': faces(16, 16, 8, 12, 4),
    'rarm': faces(40, 16, 4, 12, 4),
    'rleg': faces(0, 16, 4, 12, 4),
    'larm': faces(32, 48, 4, 12, 4),
    'lleg': faces(16, 48, 4, 12, 4),
}
SIDES = ('right', 'front', 'left', 'back')


def main():
    if '--force' not in sys.argv:
        sys.exit('The mod now ships hand-drawn textures by Star_1mp; this generator would overwrite them. '
                 'Pass --force to run it anyway.')
    sys.argv = [a for a in sys.argv if a != '--force']
    src, out = sys.argv[1], sys.argv[2]
    rnd = random.Random(26_3)
    van = Image.open(src).convert('RGBA')
    # left limbs: copy of the right ones (vanilla mirrors them), varied below
    van.paste(van.crop((40, 16, 56, 32)), (32, 48))
    van.paste(van.crop((0, 16, 16, 32)), (16, 48))
    W = 64 * S
    img = van.resize((W, W), Image.NEAREST)
    px = img.load()

    # face id of every texel, so that the grain never bleeds over a face edge
    region = {}
    for pname, fs in PARTS.items():
        for fname, (x0, y0, x1, y1) in fs.items():
            for y in range(y0 * S, y1 * S):
                for x in range(x0 * S, x1 * S):
                    region[(x, y)] = (pname, fname)

    def grain(prob=0.32):
        snapshot = img.copy().load()
        for (x, y), reg in region.items():
            if snapshot[x, y][3] == 0 or rnd.random() > prob:
                continue
            if reg == ('head', 'front') and 11 * S <= y < 13 * S + 1:
                continue  # keep the eyes crisp
            dx, dy = rnd.choice(((S, 0), (-S, 0), (0, S), (0, -S), (1, 0), (0, 1), (-1, 0), (0, -1)))
            q = (x + dx, y + dy)
            if region.get(q) == reg and snapshot[q][3] and not (reg == ('head', 'front') and 11 * S <= q[1] < 13 * S):
                px[x, y] = snapshot[q]

    def side_columns(fs):
        """Columns around a limb (right, front, left, back faces) as (x, face) in texels."""
        cols = []
        for f in SIDES:
            x0, _, x1, _ = fs[f]
            cols += [(x, f) for x in range(x0 * S, x1 * S)]
        return cols

    # ---------------------------------------------------------------- legs
    vanilla_skin_rows = [[px[x, y] for x in range(40 * S, 56 * S)] for y in range(24 * S, 32 * S)]

    def skin_at(i, y):
        row = vanilla_skin_rows[y % len(vanilla_skin_rows)]
        return row[(i * 3 + y * 5) % len(row)] if rnd.random() < 0.25 else row[i % len(row)]

    for leg in ('rleg', 'lleg'):
        fs = PARTS[leg]
        top = fs['front'][1] * S
        height = 12 * S
        edge_base = 13 + (1 if leg == 'lleg' else 0)
        level = edge_base
        for i, (x, f) in enumerate(side_columns(fs)):
            # ragged hem: a random walk around ~55% of the leg
            level = max(edge_base - 3, min(edge_base + 3, level + rnd.choice((-1, 0, 0, 1))))
            for y in range(height):
                yy = top + y
                if y >= height - S:  # worn grey sole
                    px[x, yy] = rnd.choice(SOLE[1:] if y == height - 1 else SOLE[:3])
                elif y >= level + 2:
                    px[x, yy] = skin_at(i, y)
                    if rnd.random() < 0.12:
                        px[x, yy] = rnd.choice(MOSS)
                elif y >= level:
                    px[x, yy] = rnd.choice(MOSS)
                elif y >= level - 4 and rnd.random() < 0.45 - (level - y) * 0.09:
                    px[x, yy] = rnd.choice(MOSS)  # moss creeping up into the cloth
        # soles
        x0, y0, x1, y1 = fs['bottom']
        for y in range(y0 * S, y1 * S):
            for x in range(x0 * S, x1 * S):
                px[x, y] = rnd.choice(SOLE)
        # a few moss and dirt spots on the trousers
        for _ in range(5):
            f = rnd.choice(SIDES)
            x0, y0, x1, y1 = fs[f]
            cx = rnd.randrange(x0 * S, x1 * S - 1)
            cy = rnd.randrange(y0 * S + 1, y0 * S + 10)
            for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1), (rnd.choice((-1, 2)), rnd.randrange(2))):
                if region.get((cx + dx, cy + dy)) == (leg, f) and rnd.random() < 0.85:
                    px[cx + dx, cy + dy] = rnd.choice(MOSS)

    # ---------------------------------------------------------------- arms: short ragged sleeves
    for arm in ('rarm', 'larm'):
        fs = PARTS[arm]
        top = fs['front'][1] * S
        sleeve_rows = [[px[x, top + y] for x in range(fs['right'][0] * S, fs['back'][2] * S)] for y in range(8)]
        skin_rows = [[px[x, top + y] for x in range(fs['right'][0] * S, fs['back'][2] * S)] for y in range(8, 24)]
        level = 5
        for i, (x, f) in enumerate(side_columns(fs)):
            level = max(3, min(7, level + rnd.choice((-1, 0, 0, 1))))
            for y in range(24):
                if y < level:
                    c = sleeve_rows[min(y, 7)][i]
                else:
                    # stretch the vanilla skin down to fill the old sleeve rows
                    src_y = int((y - level) * 15 / max(1, 23 - level))
                    c = skin_rows[min(15, src_y)][i]
                    if y == level and rnd.random() < 0.5:
                        c = rnd.choice(SKIN_DARK)  # shadow under the cuff
                px[x, top + y] = c
        # hands (bottom face) a touch darker
        x0, y0, x1, y1 = fs['bottom']
        for y in range(y0 * S, y1 * S):
            for x in range(x0 * S, x1 * S):
                px[x, y] = rnd.choice(SKIN[3:])

    # ---------------------------------------------------------------- shirt: rot spots and stains
    fs = PARTS['body']
    for f, n in (('front', 7), ('back', 5), ('right', 2), ('left', 2)):
        x0, y0, x1, y1 = fs[f]
        for _ in range(n):
            cx = rnd.randrange(x0 * S, x1 * S - 1)
            cy = rnd.randrange(y0 * S + 2, y1 * S - 4)
            shape = [(0, 0), (1, 0), (0, 1)] + rnd.sample([(1, 1), (-1, 0), (0, -1), (2, 1), (1, 2)], 2)
            for dx, dy in shape:
                if region.get((cx + dx, cy + dy)) == ('body', f):
                    px[cx + dx, cy + dy] = rnd.choice(ROT)
    # a little moss on the shoulders (top face)
    x0, y0, x1, y1 = fs['top']
    for y in range(y0 * S, y1 * S):
        for x in range(x0 * S, x1 * S):
            if rnd.random() < 0.08:
                px[x, y] = rnd.choice(MOSS)

    grain()

    # ---------------------------------------------------------------- eyelids (2x1 vanilla px each)
    for ey in (16, 18):
        for y in range(ey * S, ey * S + S):
            for x in range(56 * S, 58 * S):
                px[x, y] = hx('3e692d') if y == ey * S + S - 1 else hx('4e7b36')

    img.save(out)
    print('wrote', out, img.size)


if __name__ == '__main__':
    main()
