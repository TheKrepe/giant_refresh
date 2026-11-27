"""Three short chip scores with the existing scene-timed effects."""
import wave

import numpy as np

import subprocess as sp
from functools import lru_cache

RATE = 48000
# Notes are offsets from middle C. "-" leaves a breath before the next phrase.
TRACKS = {
    'snow': (110, (0, 4, 5, 7), (
        19, None, 16, 24, None, 16, 14, None,
        16, 19, 23, None, 19, 16, 14, None,
        12, 17, 21, None, 24, None, 21, 17,
        14, None, 19, 23, 21, None, 14, None,
    )),
    'tag': (136, (0, -2, 5, 7), (
        12, 16, 21, None, 19, 16, 14, None,
        10, None, 14, 17, 19, None, 12, 10,
        12, 17, 22, 19, None, 17, 12, None,
        14, 19, 23, None, 17, 14, 11, None,
    )),
    'trial': (122, (-3, -5, 0, -2), (
        9, None, 12, 15, None, 12, 9, None,
        7, 10, 14, None, 16, None, 10, None,
        12, 15, 19, 22, 19, 15, None, 12,
        10, 14, 17, 19, None, 17, 14, None,
    )),
}

def oscillator(pitch, length, voice):
    t = np.arange(round(length * RATE)) / RATE
    frequency = 440 * 2 ** ((pitch - 69) / 12)
    phase = 2 * np.pi * frequency * t
    envelope = np.minimum(t / .012, 1) * np.minimum((length - t) / .042, 1)
    if voice == 'triangle':
        signal = np.arcsin(np.sin(phase)) * (2 / np.pi)
    else:
        # Band-limit the pulse so high notes stay soft.
        width = .25 if voice == 'lead' else .125
        signal = sum(np.sin(phase * i - np.pi * i * width)
                     * (2 * np.sin(np.pi * i * width) / (np.pi * i))
                     for i in range(1, 11) if i * frequency < 11000)
    return envelope * signal

def write(profile, shots, cues, shared_assets, output, special=None):
    cuts = np.cumsum([0] + [round(shot["duration"] * 30) / 30 for shot in shots])
    key = profile
    story_end = cuts[-1]
    length = story_end + 2.6
    music = np.zeros((round(length * RATE), 2))
    effects = np.zeros_like(music)
    rng = np.random.default_rng(76)

    def add(buffer, at, signal, gain, pan=0):
        first = round(at * RATE)
        count = min(len(signal), len(buffer) - first)
        if count > 0:
            buffer[first:first + count] += signal[:count, None] * gain * np.sqrt([1 - pan, 1 + pan])

    def note(at, pitch, length, voice, gain, pan=0):
        add(music, at, oscillator(pitch, length, voice), gain, pan)

    @lru_cache(maxsize=64)
    def sample(name):
        source = shared_assets / "audio" / ({"step":"wood1", "icechime":"pling"}.get(name, name) + ".ogg")
        if source.is_file():
            command = ["ffmpeg", "-v", "error", "-i", str(source), "-ac", "1",
                       "-ar", str(RATE), "-f", "f32le", "-"]
            return np.frombuffer(sp.check_output(command), np.float32)
        # Original fallback effect when a project has no Minecraft sound cache.
        count = RATE // 6
        time = np.arange(count) / RATE
        seed = sum((i + 1) * ord(c) for i, c in enumerate(name))
        noise = np.random.default_rng(seed).uniform(-1, 1, count)
        pitch = {"pop": 164, "pling": 740, "icechime": 950, "wood_click": 320}.get(name)
        if pitch:
            return (.33 * np.sin(2 * np.pi * pitch * time) + .11 * noise) * np.exp(-time * 36)
        noise = np.convolve(noise, np.ones(19) / 19, "same")
        return .9 * noise * (np.sin(np.pi * time / time[-1]) if name == "swish" else np.exp(-time * 26))

    def fx(at, name, gain, pitch, pan=0):
        raw = sample(name)
        positions = np.arange(0, len(raw), 2 ** (pitch / 12))
        add(effects, at, np.interp(positions, np.arange(len(raw)), raw), gain, pan)

    bpm, bass, melody = TRACKS[key]
    eighth = 30 / bpm
    for step in range(int((story_end - .1) / eighth)):
        at = step * eighth
        bar, count = divmod(step, 8)
        root = bass[bar % len(bass)]
        pitch = melody[step % len(melody)]
        if pitch is not None:
            note(at, pitch + 60, eighth * .83, 'lead', .115, -.18)
        if count % 2 == 0:
            note(at, 41 + root + (7 if count == 4 else 0), eighth * 1.52, 'triangle', .12, .07)
        if count in (2, 5, 7):
            note(at + .016, 60 + root + (7 if count == 5 else 12), eighth * .53, 'echo', .045, .33)
            time = np.arange(RATE // 14) / RATE
            add(music, at, rng.uniform(-1, 1, len(time)) * np.exp(-time * 65), .037, .13)
    # Resolve the melody on the end card using the same chip instruments.
    for i, pitch in enumerate({'snow':(64,67,72,76),'tag':(67,72,76,81),'trial':(63,67,70,75)}[key]):
        note(story_end + i * .13, pitch, .29 + .18 * i, 'lead', .12, -.13)
        note(story_end + i * .13 + .054, pitch + 12, .17, 'echo', .042, .34)
    note(story_end, 43, .66, 'triangle', .105)

    for i, (at, name, gain, pitch) in enumerate(cues):
        fx(at, name, gain * .68, pitch, (-.21, .14, .02)[i % 3])
    # Sync the most distinctive effects to the actual visual beats.
    if special == 'tag' and len(shots) >= 3:
        fx(cuts[2] + 3.24, 'wood_click', .55, 8)
        fx(cuts[2] + 3.31, 'pop', .25, -3)
    if special == 'trial' and len(shots) >= 4:
        for start, stop in ((.07, 2.53), (cuts[2], cuts[3]), (cuts[3] + 2.66, story_end)):
            time = np.arange(round((stop - start) * RATE)) / RATE
            noise = np.convolve(rng.uniform(-1, 1, len(time)), np.ones(29) / 29, 'same')
            envelope = np.minimum(time / .18, 1) * np.minimum((stop - start - time) / .29, 1)
            add(effects, start, noise * envelope * (1 + .55 * np.sin(32 * time)), .24, -.26)
    if special == 'snow' and len(shots) >= 3:
        fx(cuts[2] + .12, 'pling', .32, 10)

    # Soft gain dips make room for impacts without cutting the tune.
    time = np.arange(len(music)) / RATE
    duck = np.ones(len(music))
    for at, _, gain, _ in cues:
        if gain >= .5:
            duck -= .16 * np.exp(-((time - at) / .14) ** 2)
    mix = music * np.maximum(duck[:, None], .50) + effects
    mix *= min(1.7, .90 / np.max(np.abs(mix)))
    tail = round(RATE * .52)
    mix[-tail:] *= np.linspace(1, 0, tail)[:, None]
    with wave.open(str(output), 'wb') as wave_file:
        wave_file.setparams((2, 2, RATE, 0, 'NONE', 'not compressed'))
        wave_file.writeframes((mix * 32767).astype('<i2').tobytes())


