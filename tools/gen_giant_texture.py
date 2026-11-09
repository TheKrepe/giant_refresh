#!/usr/bin/env python3
"""
Paints the Giant's 256x256 entity texture in the vanilla zombie palette.

Every colour comes from the vanilla zombie skin (skin greens, shirt cyans, trouser purples,
boot greys, eye black) plus the brown of the vanilla harness for the rope belt and bone tones
for the teeth. Shading is done the vanilla way: flat tones broken up by small clusters of the
neighbouring tones, darker seams/folds drawn as 1px lines, no gradients, alpha only 0/255.

Usage: python3 tools/gen_giant_texture.py <out.png>
"""
import math
import sys

from PIL import Image

# ----------------------------------------------------------------------------- palettes (vanilla)
def hx(s):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


SKIN = [hx(c) for c in ('799c65', '71955b', '679056', '5a7b48', '4e7b36', '497135', '3e692d', '3b622f', '385226')]
SKIN_DEEP = hx('2b3f1f')
CYAN = [hx(c) for c in ('00cccc', '00afaf', '00a8a8', '009e9e', '009999', '007f7f', '006868', '005b5b')]
PURPLE = [hx(c) for c in ('5649cc', '463aa5', '3a3189', '302872', '26215b')]
GREY = [hx(c) for c in ('8f8f8f', '7a7777', '6b6b6b', '616161', '4c4c4c', '3f3f3f', '282828')]
MOSS = [hx(c) for c in ('698756', '5a7b48', '4e7b36', '3e692d', '365a25')]
BROWN = [hx(c) for c in ('d18a59', 'b17141', '925e38', '7d4e29', '704424', '5e371a', '4e2c13')]
BONE = [hx(c) for c in ('fcfbed', 'e8e5d2', 'cbc6a5', '7b7e6b')]
EYE = hx('1a1a1a')
CLEAR = (0, 0, 0, 0)

W = H = 256


# ----------------------------------------------------------------------------- noise
def h01(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return (n & 0xFFFF) / 65536.0


def vnoise(x, y, scale, seed):
    """Blocky value noise: cells of `scale` px with bilinear blend, used for clustered tones."""
    fx, fy = x / scale, y / scale
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    a = h01(x0, y0, seed)
    b = h01(x0 + 1, y0, seed)
    c = h01(x0, y0 + 1, seed)
    d = h01(x0 + 1, y0 + 1, seed)
    top = a + (b - a) * tx
    bot = c + (d - c) * tx
    return top + (bot - top) * ty


def tone(ramp, x, y, seed, base, spread=1.6, bias=0.0):
    """Pick a ramp tone: clustered noise around index `base`."""
    n = 0.78 * vnoise(x, y, 3.0, seed) + 0.22 * h01(x, y, seed + 7)
    idx = base + (n - 0.5) * 2.0 * spread + bias
    return ramp[max(0, min(len(ramp) - 1, int(round(idx))))]


# ----------------------------------------------------------------------------- canvas helpers
img = Image.new('RGBA', (W, H), CLEAR)
P = img.load()


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        P[x, y] = c


class Box:
    """UV net of a cube at texOffs (u, v) with size (w, h, d)."""

    def __init__(self, u, v, w, h, d):
        self.u, self.v, self.w, self.h, self.d = u, v, w, h, d

    @property
    def strip_w(self):
        return 2 * (self.w + self.d)

    def strip(self, sx, sy):
        """Side strip (right | front | left | back) -> texture coords."""
        return self.u + sx, self.v + self.d + sy

    def top(self, x, y):
        return self.u + self.d + x, self.v + y

    def bottom(self, x, y):
        return self.u + self.d + self.w + x, self.v + y

    def face_of(self, sx):
        """0 = right side (-x), 1 = front, 2 = left side (+x), 3 = back; plus local x."""
        d, w = self.d, self.w
        if sx < d:
            return 0, sx
        if sx < d + w:
            return 1, sx - d
        if sx < 2 * d + w:
            return 2, sx - d - w
        return 3, sx - 2 * d - w


def paint_strip(box, fn):
    for sy in range(box.h):
        for sx in range(box.strip_w):
            c = fn(sx, sy)
            if c is not None:
                put(*box.strip(sx, sy), c)


def paint_top(box, fn):
    for y in range(box.d):
        for x in range(box.w):
            c = fn(x, y)
            if c is not None:
                put(*box.top(x, y), c)


def paint_bottom(box, fn):
    for y in range(box.d):
        for x in range(box.w):
            c = fn(x, y)
            if c is not None:
                put(*box.bottom(x, y), c)


def ragged(sx, seed, base, amp):
    """A torn edge: base row + 0..amp, changing in 1-3 px steps."""
    n = vnoise(sx, 0, 2.0, seed)
    return base + int(round(n * amp))


def moss_here(x, y, seed, density):
    return vnoise(x, y, 2.5, seed) * 0.88 + h01(x, y, seed + 3) * 0.12 > 1.0 - density * 0.85


# ----------------------------------------------------------------------------- head
HEAD = Box(0, 0, 24, 24, 24)


def skin(x, y, seed, base=4.0, spread=1.7, bias=0.0):
    return tone(SKIN, x, y, seed, base, spread, bias)


def paint_head():
    def side(sx, sy):
        face, lx = HEAD.face_of(sx)
        bias = 0.9 if face in (0, 2, 3) else 0.0
        c = skin(sx, sy, 11, 4.2, 1.6, bias)
        if sy >= 21:  # jaw / neck shadow
            c = skin(sx, sy, 12, 6.0, 1.0)
        if sy < 6 and moss_here(sx, sy, 13, 0.30):
            c = tone(MOSS, sx, sy, 14, 2.0, 1.2)
        return c

    paint_strip(HEAD, side)

    # back of the head: an old stitched scar
    for sy in range(4, 19):
        sx = 72 + 9 + (sy // 6)
        put(*HEAD.strip(sx, sy), SKIN_DEEP)
        if sy % 3 == 0:
            put(*HEAD.strip(sx - 1, sy), BONE[2])
            put(*HEAD.strip(sx + 1, sy), BONE[2])
    # ears: a darker fold on each side
    for side_x in (0, 48):
        for sy in range(11, 18):
            put(*HEAD.strip(side_x + 11, sy), SKIN[7])
            put(*HEAD.strip(side_x + 12, sy), SKIN[6])
        for sy in (11, 17):
            put(*HEAD.strip(side_x + 13, sy), SKIN[7])

    paint_top(HEAD, lambda x, y: tone(MOSS, x, y, 15, 2.2, 1.3) if moss_here(x, y, 16, 0.45) else skin(x, y, 17, 5.0, 1.5))
    paint_bottom(HEAD, lambda x, y: skin(x, y, 18, 6.6, 1.0))
    paint_face()


def paint_face():
    """24x24 face. Eyes sit exactly under the lenses of the lowered harness goggles (rows 12-16)."""
    def fp(x, y, c):
        put(*HEAD.strip(24 + x, y), c)

    for y in range(24):
        for x in range(24):
            center = 3 <= x <= 20 and 4 <= y <= 20
            fp(x, y, skin(x + 24, y, 21, 3.4 if center else 4.4, 1.5))
    # forehead moss
    for y in range(0, 6):
        for x in range(24):
            if moss_here(x, y, 22, 0.25):
                fp(x, y, tone(MOSS, x, y, 23, 2.0, 1.0))
    # heavy brow
    for x in range(2, 22):
        fp(x, 10, SKIN[7])
        fp(x, 11, SKIN[6] if x in (11, 12) else SKIN[8])
    for x in (2, 21):
        fp(x, 11, SKIN[6])
    # eye sockets + eyes (cols 4-8 and 15-19, rows 13-14)
    for ex in (4, 15):
        for y in range(12, 17):
            for x in range(ex - 1, ex + 6):
                fp(x, y, SKIN[7] if y in (12, 16) or x in (ex - 1, ex + 5) else SKIN[6])
        for y in (13, 14):
            for x in range(ex, ex + 5):
                fp(x, y, EYE)
        fp(ex + 1, 13, hx('3a3a3a'))  # a dull glint
        for x in range(ex, ex + 5):
            fp(x, 15, SKIN[8])
    # nose
    for y in range(13, 18):
        fp(11, y, SKIN[2])
        fp(12, y, SKIN[3])
    for y in range(15, 19):
        fp(10, y, SKIN[6])
        fp(13, y, SKIN[7])
    fp(10, 18, SKIN_DEEP)
    fp(13, 18, SKIN_DEEP)
    fp(11, 18, SKIN[5])
    fp(12, 18, SKIN[5])
    # cheekbones
    for x, y in ((4, 17), (5, 17), (6, 17), (5, 18), (17, 17), (18, 17), (19, 17), (18, 18)):
        fp(x, y, SKIN[2])
    # mouth: a crooked slit with two teeth and a gap (the Giant Tooth had to come from somewhere)
    for x in range(6, 18):
        fp(x, 20, SKIN_DEEP)
    for x in range(7, 17):
        fp(x, 21, SKIN[8])
    fp(5, 19, SKIN[8])
    fp(18, 19, SKIN[8])
    fp(8, 20, BONE[1])
    fp(8, 21, BONE[2])
    fp(10, 20, BONE[2])
    fp(14, 20, BONE[1])
    fp(15, 20, BONE[2])
    fp(15, 21, BONE[2])
    # cheek scar with stitches
    for i, (x, y) in enumerate(((20, 14), (20, 15), (19, 16), (19, 17), (18, 18))):
        fp(x, y, SKIN_DEEP)
        if i % 2 == 0:
            fp(x - 1, y, BONE[2])
            fp(x + 1, y, BONE[2])
    # chin shadow
    for x in range(4, 20):
        fp(x, 23, SKIN[8])


def paint_eyelids():
    for u, v in ((192, 48), (192, 56)):
        for face in (0, 6):
            for y in range(4):
                for x in range(6):
                    if y < 3:
                        c = SKIN[5] if y == 0 else SKIN[4]
                    else:
                        c = SKIN_DEEP
                    put(u + face + x, v + y, c)


# ----------------------------------------------------------------------------- body
BODY = Box(96, 0, 24, 36, 12)
SHIRT = Box(168, 0, 24, 36, 12)
BELT_Y = 30


def body_hem(sx):
    return ragged(sx, 31, 26, 2)


def rope(sx, sy):
    k = (sx + sy) % 4
    return BROWN[2] if k == 0 else BROWN[3] if k in (1, 3) else BROWN[5]


def paint_body():
    def fn(sx, sy):
        face, lx = BODY.face_of(sx)
        # trousers
        if sy >= BELT_Y + 2:
            c = tone(PURPLE, sx, sy, 41, 1.6, 1.0)
            if moss_here(sx, sy, 42, 0.18):
                c = tone(MOSS, sx, sy, 43, 2.5, 1.0)
            return c
        if sy >= BELT_Y:
            return rope(sx, sy)
        hem = body_hem(sx)
        if sy > hem:
            return skin(sx, sy, 44, 5.0, 1.2)  # belly showing under the torn hem
        # shirt
        c = tone(CYAN, sx, sy, 45, 2.4, 1.5)
        if sy >= hem - 1:
            c = CYAN[6]
        if face == 1:
            # V-neck
            dist = abs(lx - 11.5)
            if sy < 6 and dist < 6 - sy:
                return skin(sx, sy, 46, 4.5, 1.0)
            if sy < 7 and dist < 7 - sy:
                return CYAN[6]
            # folds from the armpits towards the belly
            for start in (1, 22):
                fx = start + (sy - 6) // 2 * (1 if start == 1 else -1)
                if 6 <= sy <= 20 and lx == fx:
                    return CYAN[5]
            if lx == 12 and 8 <= sy <= 24 and sy % 5 != 0:
                return CYAN[4]
        if face == 3 and lx in (6, 17) and 4 <= sy <= 22:
            return CYAN[5]
        if moss_here(sx, sy, 47, 0.16 + 0.22 * (sy / 30.0)):
            c = tone(MOSS, sx, sy, 48, 2.0, 1.4)
        return c

    paint_strip(BODY, fn)
    # rips in the shirt with skin showing through (irregular, torn outwards)
    rips = {
        (16, 15): [".##.", "####", ".###", "..#."],
        (31, 20): [".#", "##", "##", "#."],
        (58, 9): ["..##.", "#####", ".##.."],
    }
    for (ox, oy), shape in rips.items():
        cells = {(ox + x, oy + y) for y, row in enumerate(shape) for x, ch in enumerate(row) if ch == '#'}
        for (x, y) in cells:
            put(*BODY.strip(x, y), skin(x, y, 49, 4.8, 1.0))
        for (x, y) in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in cells:
                    put(*BODY.strip(*q), CYAN[6] if dy < 0 or dx < 0 else CYAN[4])
    # rope knot on the front left
    for (x, y) in ((28, 30), (29, 30), (28, 31), (29, 31), (28, 32), (29, 33), (27, 33), (30, 34)):
        put(*BODY.strip(x, y), BROWN[1] if (x + y) % 2 else BROWN[3])
    paint_top(BODY, lambda x, y: skin(x, y, 50, 5.0, 1.0) if 6 <= x <= 17 and 3 <= y <= 8 else tone(CYAN, x, y, 51, 2.5, 1.3))
    paint_bottom(BODY, lambda x, y: tone(PURPLE, x, y, 52, 2.2, 0.8))

    # overlay: tattered shirt flaps hanging over the belt
    def flap(sx, sy):
        if sy < 22:
            return None
        end = body_hem(sx) + 1 + int(vnoise(sx, 5, 2.5, 53) * 5.0)
        if sy > end:
            return None
        if h01(sx // 4, 0, 54) < 0.18 and sy > body_hem(sx) - 1:  # a few gaps between tatters
            return None
        c = tone(CYAN, sx, sy, 55, 3.6, 1.3)
        if sy == end:
            c = CYAN[7]
        if moss_here(sx, sy, 56, 0.3):
            c = tone(MOSS, sx, sy, 57, 2.5, 1.0)
        return c

    paint_strip(SHIRT, flap)


# ----------------------------------------------------------------------------- limbs
def limb_box(u, v):
    return Box(u, v, 12, 18, 12)


def paint_upper_arm(box, seed, outer):
    def fn(sx, sy):
        face, lx = box.face_of(sx)
        end = ragged(sx, seed, 6, 3)
        if sy <= end:
            c = tone(CYAN, sx, sy, seed + 1, 2.2, 1.4)
            if sy == end:
                c = CYAN[6]
            if moss_here(sx, sy, seed + 2, 0.25):
                c = tone(MOSS, sx, sy, seed + 3, 2.0, 1.2)
            return c
        c = skin(sx, sy, seed + 4, 3.0, 1.7)
        if face == outer and lx in (5, 6) and 11 <= sy <= 15:  # an old scrape
            c = SKIN[6] if lx == 5 else SKIN[7]
        return c

    paint_strip(box, fn)
    paint_top(box, lambda x, y: tone(CYAN, x, y, seed + 5, 2.3, 1.3))
    paint_bottom(box, lambda x, y: skin(x, y, seed + 6, 5.5, 1.0))


def paint_sleeve(box, seed):
    def fn(sx, sy):
        if sy < 1:
            return None
        end = ragged(sx, seed, 7, 3) + int(vnoise(sx, 3, 2.5, seed + 9) * 3)
        if sy > end:
            return None
        c = tone(CYAN, sx, sy, seed + 10, 3.0, 1.4)
        if sy == end or sy == 1:
            c = CYAN[6]
        if moss_here(sx, sy, seed + 11, 0.3):
            c = tone(MOSS, sx, sy, seed + 12, 2.0, 1.0)
        return c

    paint_strip(box, fn)


def paint_forearm(box, seed, inner):
    def fn(sx, sy):
        face, lx = box.face_of(sx)
        c = skin(sx, sy, seed, 3.2, 1.7)
        if face == inner and lx in (4, 5) and 2 <= sy <= 10 and (sy + lx) % 3 != 0:  # veins
            c = SKIN[6]
        if sy == 12 and lx % 3 == 1:  # knuckles
            c = SKIN[6]
        if sy >= 13:
            if face in (1, 3) and lx % 3 == 2 and lx != 11:  # gaps between fingers
                c = SKIN[7]
            if sy == 17:
                c = SKIN[1] if face == 1 and lx % 3 in (0, 1) else SKIN[6]  # nails / fingertips
        if moss_here(sx, sy, seed + 1, 0.14):
            c = tone(MOSS, sx, sy, seed + 2, 1.5, 1.0)
        return c

    paint_strip(box, fn)
    paint_top(box, lambda x, y: skin(x, y, seed + 3, 5.5, 1.0))

    def palm(x, y):
        if x % 3 == 2 and y < 4:
            return SKIN[7]
        return skin(x, y, seed + 4, 2.6, 1.2)

    paint_bottom(box, palm)


def paint_thigh(box, seed, patch):
    def fn(sx, sy):
        face, lx = box.face_of(sx)
        c = tone(PURPLE, sx, sy, seed, 1.5, 1.0)
        if face == 3 and 2 <= sy <= 7 and lx in (2, 9):  # back pocket seams
            c = PURPLE[3]
        if face == 3 and sy == 2 and 2 <= lx <= 9:
            c = PURPLE[3]
        if moss_here(sx, sy, seed + 1, 0.06 + 0.2 * sy / 18.0):
            c = tone(MOSS, sx, sy, seed + 2, 2.4, 1.1)
        if patch and face == 1 and 10 <= sy <= 16 and 2 <= lx <= 9:
            # a brown knee patch with light stitches
            edge = sy in (10, 16) or lx in (2, 9)
            if edge and (sx + sy) % 2 == 0:
                c = BONE[2]
            else:
                c = tone(BROWN, sx, sy, seed + 3, 3.5, 0.9)
        return c

    paint_strip(box, fn)
    paint_top(box, lambda x, y: tone(PURPLE, x, y, seed + 4, 1.6, 0.9))
    paint_bottom(box, lambda x, y: tone(PURPLE, x, y, seed + 5, 3.0, 0.7))


def shin_hem(sx, seed):
    return ragged(sx, seed, 4, 3)


def paint_shin(box, seed):
    def fn(sx, sy):
        face, lx = box.face_of(sx)
        if sy >= 13:  # rag wrappings around the feet
            k = (sx + sy * 2) % 6
            c = tone(GREY, sx, sy, seed, 2.6, 1.0)
            if k == 0:
                c = GREY[5]
            if sy == 17:
                c = GREY[5] if face != 1 else GREY[4]
            if sy == 13 and h01(sx, sy, seed + 1) < 0.5:
                c = GREY[1]
            return c
        if sy <= shin_hem(sx, seed):
            c = tone(PURPLE, sx, sy, seed + 2, 1.7, 1.0)
            if sy == shin_hem(sx, seed):
                c = PURPLE[3]
            if moss_here(sx, sy, seed + 3, 0.35):
                c = tone(MOSS, sx, sy, seed + 4, 2.4, 1.0)
            return c
        c = skin(sx, sy, seed + 5, 3.8, 1.7)
        if moss_here(sx, sy, seed + 6, 0.32):
            c = tone(MOSS, sx, sy, seed + 7, 2.5, 1.2)
        return c

    paint_strip(box, fn)
    paint_top(box, lambda x, y: tone(PURPLE, x, y, seed + 8, 3.0, 0.7))
    paint_bottom(box, lambda x, y: tone(GREY, x, y, seed + 9, 4.6, 0.9))


def paint_hem(box, seed, shin_seed):
    def fn(sx, sy):
        end = shin_hem(sx, shin_seed) + 1 + int(vnoise(sx, 7, 2.5, seed) * 4.0)
        if sy > end:
            return None
        if h01(sx // 4, 1, seed + 1) < 0.15 and sy > shin_hem(sx, shin_seed):
            return None
        c = tone(PURPLE, sx, sy, seed + 2, 2.2, 1.0)
        if sy == end:
            c = PURPLE[4]
        if moss_here(sx, sy, seed + 3, 0.3):
            c = tone(MOSS, sx, sy, seed + 4, 2.5, 1.0)
        return c

    paint_strip(box, fn)


def main(out):
    paint_head()
    paint_eyelids()
    paint_body()
    # arms: right arm's outer side is face 0, left arm's outer side is face 2
    paint_upper_arm(limb_box(0, 48), 100, outer=0)
    paint_upper_arm(limb_box(48, 48), 120, outer=2)
    paint_forearm(limb_box(96, 48), 140, inner=2)
    paint_forearm(limb_box(144, 48), 160, inner=0)
    paint_sleeve(limb_box(0, 108), 180)
    paint_sleeve(limb_box(48, 108), 200)
    paint_thigh(limb_box(0, 78), 220, patch=True)
    paint_thigh(limb_box(48, 78), 240, patch=False)
    paint_shin(limb_box(96, 78), 260)
    paint_shin(limb_box(144, 78), 280)
    paint_hem(limb_box(96, 108), 300, 260)
    paint_hem(limb_box(144, 108), 320, 280)
    img.save(out)


if __name__ == '__main__':
    main(sys.argv[1])
