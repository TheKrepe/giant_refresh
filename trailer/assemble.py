"""Cuts the trailer: shots -> graded, letterboxed edit; title and end cards; sound mix; final mux.

    python3 assemble.py <trailer-out dir> <work dir> <out.mp4> [--preview]
"""
import glob
import json
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageFilter, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import edl  # noqa: E402
import pixfont as F  # noqa: E402

SRC, WORK, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
PREVIEW = '--preview' in sys.argv
W, H = (960, 540) if PREVIEW else (1920, 1080)
FPS = 24
SR = 48000
os.makedirs(WORK, exist_ok=True)
SHOTS = os.path.join(SRC, 'shots')
SFX = os.path.join(SRC, 'audio', 'sfx')
BAR = int(round(H * (1 - (16 / 9) / 2.39) / 2))  # 2.39:1 letterbox


def run(*a):
    subprocess.run(a, check=True)


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


# ===================================================================== cards
def text_layer(w, h, items):
    """items: (text, cx, y, scale, palette) in art pixels; returns RGBA numpy."""
    lay = np.zeros((h, w, 4), np.uint8)
    for s, cx, y, sc, pal in items:
        x = cx - F.width(s, sc) // 2
        F.stamp(lay, s, x, y, sc, pal[0], pal[1], pal[2], shadow=pal[3])
    return lay


GREEN = (hx('7fb85e'), hx('4e7b36'), hx('1b2a12'), hx('2b3a20'))
CYAN = (hx('2cd3cf'), hx('009e9e'), hx('0b2c2c'), hx('163838'))
WHITE = (hx('ffffff'), hx('dcdcdc'), hx('202020'), hx('101010'))
GOLD = (hx('fff489'), hx('e9b115'), hx('3a2a06'), hx('1c1404'))
GREY = (hx('bdbdbd'), hx('9a9a9a'), hx('202020'), hx('101010'))


def still(shot, at):
    p = os.path.join(SHOTS, shot + '.mp4')
    out = os.path.join(WORK, f'still_{shot}.png')
    if os.path.exists(p):
        run('ffmpeg', '-y', '-loglevel', 'error', '-ss', str(at), '-i', p, '-frames:v', '1', out)
        return Image.open(out).convert('RGB').resize((W, H))
    return Image.new('RGB', (W, H), (10, 10, 14))


def card_frames(kind, dur, folder):
    os.makedirs(folder, exist_ok=True)
    px = 4 if W >= 1920 else 2
    aw, ah = W // px, H // px
    n = int(round(dur * FPS))
    if kind == 'title':
        bg = ImageEnhance.Brightness(still('s12_wake', 4.6).filter(ImageFilter.GaussianBlur(W / 160))).enhance(0.28)
        logo = text_layer(aw, ah, [('GIANT', aw // 2, ah // 2 - 34, 5, GREEN), ('REFRESH', aw // 2, ah // 2 + 8, 4, CYAN)])
        logo_im = Image.fromarray(logo, 'RGBA').resize((W, H), Image.NEAREST)
        for i in range(n):
            t = i / FPS
            im = bg.copy()
            k = max(0.0, 1 - t / 0.35)
            scale = 1.0 + 0.12 * k * k
            shake = 6 * k * math.sin(t * 90) * (W / 1920)
            lw, lh = int(W * scale), int(H * scale)
            L = logo_im.resize((lw, lh), Image.NEAREST)
            im = im.convert('RGBA')
            im.alpha_composite(L, (int((W - lw) / 2 + shake), int((H - lh) / 2)))
            if t < 0.12:
                im = Image.blend(im, Image.new('RGBA', (W, H), (255, 255, 255, 255)), 0.85 * (1 - t / 0.12))
            fade = min(1.0, (dur - t) / 0.6)
            im = Image.blend(Image.new('RGBA', (W, H), (0, 0, 0, 255)), im, max(0, fade))
            im.convert('RGB').save(os.path.join(folder, f'{i:05d}.png'))
    else:
        bg = ImageEnhance.Brightness(still('s01_dawn', 6.0).filter(ImageFilter.GaussianBlur(W / 120))).enhance(0.32)
        icon = Image.open(os.path.join(HERE, 'star_1mp_icon.png')).convert('RGBA')
        isz = int(H * 0.30)
        icon = icon.resize((isz, isz), Image.NEAREST)
        cx = aw // 2
        lay = text_layer(aw, ah, [
            ('GIANT', cx + 34, 30, 3, GREEN), ('REFRESH', cx + 34, 56, 2, CYAN),
            ('AVAILABLE NOW ON', cx + 34, 84, 1, WHITE),
            ('MODRINTH', cx + 34, 98, 2, GOLD),
            ('CURSEFORGE', cx + 34, 118, 2, GOLD),
            ('FABRIC & NEOFORGE', cx + 34, 142, 1, GREY),
            ('MINECRAFT 1.21.6 – 26.3', cx + 34, 154, 1, GREY),
            ('BY STAR_1MP', int((W / 2 - W * 0.36 + isz / 2) / px), int((H * 0.30 + isz) / px) + 6, 1, WHITE),
        ])
        L = Image.fromarray(lay, 'RGBA').resize((W, H), Image.NEAREST)
        for i in range(n):
            t = i / FPS
            im = bg.copy().convert('RGBA')
            im.alpha_composite(L)
            ix = int(W / 2 - W * 0.36)
            iy = int(H * 0.30)
            im.alpha_composite(icon, (ix, iy))
            a = min(1.0, t / 0.6, (dur - t) / 1.2)
            im = Image.blend(Image.new('RGBA', (W, H), (0, 0, 0, 255)), im, max(0.0, a))
            im.convert('RGB').save(os.path.join(folder, f'{i:05d}.png'))
    seg = os.path.join(WORK, f'seg_{kind}.mp4')
    run('ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS), '-i', os.path.join(folder, '%05d.png'),
        '-c:v', 'libx264', '-crf', '16', '-pix_fmt', 'yuv420p', seg)
    return seg


# ===================================================================== picture
GRADE = ("eq=contrast=1.07:saturation=1.10:gamma=0.97,"
         "colorbalance=rs=0.02:gs=0.0:bs=-0.02:rh=0.03:bh=-0.03,"
         "vignette=angle=PI/5")


def segment(i, name, a, b):
    out = os.path.join(WORK, f'seg_{i:02d}.mp4')
    dur = b - a
    if name == 'black':
        run('ffmpeg', '-y', '-loglevel', 'error', '-f', 'lavfi', '-i', f'color=c=black:s={W}x{H}:r={FPS}', '-t', f'{dur:.3f}',
            '-c:v', 'libx264', '-crf', '16', '-pix_fmt', 'yuv420p', out)
        return out
    if name in ('title', 'endcard'):
        return card_frames(name, dur, os.path.join(WORK, 'card_' + name))
    src = os.path.join(SHOTS, name + '.mp4')
    if not os.path.exists(src):
        txt = f"drawtext=text='{name}':fontcolor=white:fontsize={H // 18}:x=(w-text_w)/2:y=(h-text_h)/2"
        run('ffmpeg', '-y', '-loglevel', 'error', '-f', 'lavfi', '-i', f'color=c=0x202020:s={W}x{H}:r={FPS}', '-t', f'{dur:.3f}',
            '-vf', txt, '-c:v', 'libx264', '-crf', '16', '-pix_fmt', 'yuv420p', out)
        return out
    vf = f"scale={W}:{H}:flags=lanczos,{GRADE},drawbox=x=0:y=0:w=iw:h={BAR}:color=black:t=fill,drawbox=x=0:y=ih-{BAR}:w=iw:h={BAR}:color=black:t=fill"
    run('ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{a:.3f}', '-i', src, '-t', f'{dur:.3f}', '-vf', vf, '-r', str(FPS),
        '-c:v', 'libx264', '-crf', '16', '-pix_fmt', 'yuv420p', out)
    return out


def picture():
    segs = [segment(i, n, a, b) for i, (n, a, b) in enumerate(edl.EDL)]
    lst = os.path.join(WORK, 'concat.txt')
    with open(lst, 'w') as f:
        for s in segs:
            f.write(f"file '{s}'\n")
    out = os.path.join(WORK, 'picture.mp4')
    run('ffmpeg', '-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', lst, '-c', 'copy', out)
    return out


# ===================================================================== sound
_cache = {}


def load(name):
    """name: 'mob/zombie/say1' -> float32 mono at SR."""
    if name in _cache:
        return _cache[name]
    p = os.path.join(SFX, name.replace('/', '__') + '.ogg')
    if not os.path.exists(p):
        _cache[name] = None
        return None
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', p, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                         capture_output=True, check=True).stdout
    a = np.frombuffer(raw, np.float32).copy()
    _cache[name] = a
    return a


def place(mix, t, clip, gain, pitch=1.0, pan=0.0):
    if clip is None or gain <= 0.0005:
        return
    if abs(pitch - 1.0) > 1e-3:
        idx = np.arange(0, len(clip) - 1, pitch)
        clip = np.interp(idx, np.arange(len(clip)), clip).astype(np.float32)
    i0 = int(t * SR)
    if i0 >= mix.shape[0] or i0 + len(clip) <= 0:
        return
    if i0 < 0:
        clip = clip[-i0:]
        i0 = 0
    n = min(len(clip), mix.shape[0] - i0)
    l = math.cos((pan + 1) * math.pi / 4)
    r = math.sin((pan + 1) * math.pi / 4)
    mix[i0:i0 + n, 0] += clip[:n] * gain * l * 1.4142
    mix[i0:i0 + n, 1] += clip[:n] * gain * r * 1.4142


SKIP_SRC = {'music', 'record', 'ambient', 'weather'}
SKIP = ('trial_spawner', 'cave', 'portal')


def foley(mix, tl):
    """Every sound the game played during each shot, attenuated from the camera's point of view."""
    for name, a, b, start in tl:
        log = os.path.join(SHOTS, name + '.sounds.jsonl')
        if not os.path.exists(log):
            continue
        scale = 0.55 if name == 's20_fall' else 1.0
        for line in open(log):
            try:
                d = json.loads(line)
            except ValueError:
                continue
            if d['src'] in SKIP_SRC or any(s in d['file'] for s in SKIP):
                continue
            ft = d['t'] / scale
            if ft < a or ft >= b:
                continue
            vol = d['vol']
            if d['rel']:
                g = min(1.0, vol)
            else:
                dist = math.dist((d['x'], d['y'], d['z']), (d['cx'], d['cy'], d['cz']))
                maxd = d['dist'] * max(1.0, vol)
                g = min(1.0, vol) * max(0.0, 1.0 - dist / maxd) if d['att'] == 'LINEAR' else min(1.0, vol)
            path = d['file'].split(':', 1)[1]
            place(mix, start + ft - a, load(path), g * 0.55, d['pitch'] * scale)


def designed(mix, c):
    """Sound design on top of the game's own sounds."""
    boom = [load('random/explode%d' % i) for i in (1, 2, 3, 4)]
    step = load('mob/irongolem/walk1')
    for k, t in enumerate(c['thumps'] + c['thumps2']):
        place(mix, t, boom[k % 4], 0.45, 0.42)
        place(mix, t, step, 0.9, 0.35)
    place(mix, c['black1'], boom[0], 0.9, 0.35)
    place(mix, c['black1'], boom[2], 0.6, 0.5)
    place(mix, c['roar'], load('mob/ravager/roar1'), 0.5, 0.55)
    for h in c['hits']:
        place(mix, h, boom[1], 0.55, 0.5)
    place(mix, c['fall'], boom[3], 1.0, 0.4)
    place(mix, c['fall'], boom[0], 0.7, 0.3)
    place(mix, c['title'], boom[2], 0.9, 0.38)
    place(mix, c['title'], load('mob/ravager/roar1'), 0.45, 0.5)
    wind = load('item/elytra/elytra_loop')
    if wind is not None:
        t = c['trail']
        while t < c['tension'] + 6:
            place(mix, t, wind, 0.05, 0.6)
            t += len(wind) / SR / 0.6 - 0.2


def sound(total):
    n = int((total + 1) * SR)
    sfx = np.zeros((n, 2), np.float32)
    tl, _ = edl.timeline()
    c = edl.cues()
    foley(sfx, tl)
    designed(sfx, c)
    music = np.zeros((n, 2), np.float32)
    mp = os.path.join(SRC, 'audio', 'music.flac')
    if os.path.exists(mp):
        raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', mp, '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-'],
                             capture_output=True, check=True).stdout
        m = np.frombuffer(raw, np.float32).reshape(-1, 2)
        k = min(n, len(m))
        music[:k] = m[:k]
    # gentle side-chain: music dips under loud effects
    env = np.abs(sfx).max(axis=1)
    win = int(0.08 * SR)
    env = np.convolve(env, np.ones(win) / win, mode='same')
    duck = 1.0 - np.clip(env * 1.6, 0, 0.45)
    mix = music * (0.8 * duck)[:, None] + sfx
    # normalise on the loud body, not the single highest peak; tanh rounds the few hits above it
    peak = float(np.percentile(np.abs(mix), 99.97)) or 1.0
    mix = np.tanh(mix / peak * 1.25) / math.tanh(1.25) * 0.89
    fade = int(1.5 * SR)
    mix[-fade:] *= np.linspace(1, 0, fade)[:, None]
    out = os.path.join(WORK, 'mix.wav')
    import wave
    with wave.open(out, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(mix, -1, 1) * 32767).astype('<i2').tobytes())
    return out


if __name__ == '__main__':
    pic = picture()
    _, total = edl.timeline()
    wav = sound(total)
    run('ffmpeg', '-y', '-loglevel', 'error', '-i', pic, '-i', wav, '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-crf',
        '18' if not PREVIEW else '22', '-preset', 'slow', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '256k', '-shortest',
        '-movflags', '+faststart', OUT)
    print('wrote', OUT)
