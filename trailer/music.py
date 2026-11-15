"""Original score for the Giant Refresh trailer "Footsteps" (General MIDI, rendered with a soundfont).

All times are in seconds of the final cut; cue points come from cues.json so the music follows
the edit. Key: D (major for the village and the epilogue, minor for the Giant).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from midi import Song  # noqa: E402

NOTES = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def n(s):
    """'D4', 'F#3', 'Bb2' -> MIDI number."""
    p = NOTES[s[0]]
    i = 1
    while i < len(s) and s[i] in '#b':
        p += 1 if s[i] == '#' else -1
        i += 1
    return p + 12 * (int(s[i:]) + 1)


def ns(*names):
    return [n(x) for x in names]


# channels
STR, TREM, PIZZ, BASS, HORN, BRASS, FLUTE, CLAR, PIANO, DRUM, TIMP, CHOIR, HARP, TUBA, BSN, CEL = range(16)
DRUM = 9


def compose(c, out):
    s = Song()
    s.program(STR, 48, 100, 64, 70)
    s.program(TREM, 44, 92, 70, 70)
    s.program(PIZZ, 45, 96, 56, 60)
    s.program(BASS, 43, 105, 64, 50)
    s.program(HORN, 60, 100, 50, 70)
    s.program(BRASS, 61, 105, 72, 60)
    s.program(FLUTE, 73, 92, 80, 80)
    s.program(CLAR, 71, 90, 48, 70)
    s.program(PIANO, 0, 92, 64, 70)
    s.program(TIMP, 47, 120, 64, 60)
    s.program(CHOIR, 52, 92, 64, 90)
    s.program(HARP, 46, 88, 40, 80)
    s.program(TUBA, 58, 100, 64, 50)
    s.program(BSN, 70, 100, 60, 60)
    s.program(CEL, 8, 85, 88, 90)
    s.cc(DRUM, 0, 7, 118)
    s.cc(DRUM, 0, 91, 60)

    # ------------------------------------------------------------ A. dawn (D major, pastoral)
    t0, t_thump1, t_black = c['start'], c['thump1'], c['black1']
    prog = [ns('D3', 'F#3', 'A3', 'D4'), ns('A2', 'E3', 'A3', 'C#4'), ns('B2', 'F#3', 'B3', 'D4'), ns('G2', 'D3', 'G3', 'B3')]
    roots = ns('D2', 'A1', 'B1', 'G1')
    t = t0
    i = 0
    s.ramp(STR, t0, t0 + 3, 30, 95)
    while t < t_thump1 - 0.2:
        d = min(3.0, t_thump1 - t)
        s.chord(STR, t, d, prog[i % 4], 62)
        s.note(BASS, t, d, roots[i % 4] + 12, 55)
        for k, p in enumerate(prog[i % 4] + [prog[i % 4][1] + 12]):
            s.note(HARP, t + k * 0.25, 1.2, p + 12, 52)
        t += 3.0
        i += 1
    mel = [('A4', 1.0), ('F#4', .5), ('G4', .5), ('A4', 1.0), ('D5', 1.0), ('C#5', .75), ('B4', .25), ('A4', 1.0),
           ('F#4', 1.0), ('G4', .75), ('A4', .25), ('B4', 1.0), ('A4', 1.5)]
    t = t0 + 3.0
    for name, d in mel:
        if t + d > t_thump1 - 0.3:
            break
        s.note(FLUTE, t, d * 0.95, n(name), 70)
        t += d
    # the footfalls: music thins to a pedal, timpani answer each thump
    s.ramp(STR, t_thump1 - 0.1, t_thump1 + 0.8, 95, 35)
    s.note(BASS, t_thump1, t_black - t_thump1, n('D1'), 70)
    s.note(TREM, t_thump1 + 0.2, t_black - t_thump1 - 0.2, n('D3'), 40)
    s.ramp(TREM, t_thump1 + 0.2, t_black, 40, 100)
    for th in c['thumps']:
        s.note(TIMP, th, 1.6, n('D2'), 95)
        s.note(DRUM, th, 0.8, 35, 90)
    s.chord(BRASS, c['thumps'][-1], t_black - c['thumps'][-1], ns('D2', 'A2'), 70)
    s.ramp(BRASS, c['thumps'][-1], t_black, 40, 110)
    # the big boom that cuts to black
    s.chord(PIANO, t_black, 3.0, ns('D1', 'A1', 'D2', 'Eb2'), 120)
    s.note(TIMP, t_black, 2.5, n('D2'), 127)
    s.note(DRUM, t_black, 2.0, 35, 127)
    s.note(DRUM, t_black, 2.0, 57, 90)

    # ------------------------------------------------------------ B. the trail (D minor, mysterious)
    b0, b1 = c['trail'], c['silhouette']
    beat = 60 / 90
    harm = [ns('D2', 'D3', 'A3', 'F4'), ns('Bb1', 'Bb2', 'F3', 'D4'), ns('G1', 'G2', 'D3', 'Bb3'), ns('A1', 'A2', 'E3', 'C#4')]
    t = b0
    bar = 0
    s.ramp(STR, b0, b0 + 4, 30, 80)
    while t < c['tension'] - 0.1:
        d = min(4 * beat, c['tension'] - t)
        h = harm[bar % 4]
        s.chord(STR, t, d, h[1:], 50)
        s.note(BASS, t, d, h[0], 60)
        pat = [h[1], h[2], h[1] + 12, h[2]]
        for k in range(8):
            tt = t + k * beat / 2
            if tt < c['tension'] - 0.05:
                s.note(PIZZ, tt, beat / 2.5, pat[k % 4], 52 + (k % 2) * 8 + min(25, bar * 2))
        if t >= c['ruins']:
            s.note(TREM, t, d, h[3] + 12, 30)
        t += 4 * beat
        bar += 1
    cl = [('A4', 2), ('Bb4', 1), ('A4', 1), ('G4', 2), ('F4', 2), ('E4', 3), ('D4', 1), ('E4', 2), ('F4', 2), ('E4', 4)]
    t = c['follow']
    for name, d in cl:
        dd = d * beat
        if t + dd > b1:
            break
        s.note(CLAR, t, dd * 0.95, n(name), 62)
        t += dd
    # the Giant's theme, first time: distant horns
    theme = [('D4', 2), ('A3', 2), ('Bb3', 1), ('A3', 1), ('G3', 1), ('F3', 1), ('A3', 4)]
    t = b1 + 0.5
    s.ramp(HORN, b1, b1 + 4, 40, 100)
    for name, d in theme:
        dd = d * beat
        s.note(HORN, t, dd * 0.97, n(name), 70)
        s.note(HORN, t, dd * 0.97, n(name) - 12, 55)
        t += dd

    # ------------------------------------------------------------ C. the camp (tension)
    c0, c_spider, c_wake, c_roar = c['tension'], c['spider'], c['wake'], c['roar']
    beat = 0.6
    t = c0
    k = 0
    while t < c_wake - 0.05:
        h = harm[(k // 8) % 4]
        acc = (t - c0) / max(1.0, c_wake - c0)
        s.note(STR, t, beat / 2 * 0.9, h[1] + (12 if k % 4 == 2 else 0), 50 + acc * 45)
        if k % 4 == 0:
            s.note(BASS, t, beat * 2, h[0], 60 + acc * 40)
        if k % 4 in (0, 1) and t > c0 + 2:
            s.note(TIMP, t + (0.0 if k % 4 == 0 else -0.15), 0.4, n('D2'), 55 + acc * 50)
        t += beat / 2
        k += 1
    s.note(TREM, c0, c_spider - c0, n('D4'), 40)
    s.ramp(TREM, c0, c_spider, 30, 90)
    # spider drop: a sharp stab, then a rising, trembling cluster
    s.chord(STR, c_spider, 0.4, ns('C#4', 'D4', 'G#4', 'A4'), 120)
    s.chord(BRASS, c_spider, 0.4, ns('D3', 'Eb3'), 115)
    s.note(DRUM, c_spider, 1.0, 49, 90)
    t = c_spider + 0.5
    step = 0
    while t < c_wake:
        s.chord(TREM, t, 0.5, [n('D4') + step, n('A4') + step], 70 + step * 3)
        t += 0.5
        step += 1
    # eyes open: everything stops, only a low drone and a held breath
    s.note(BASS, c_wake, c_roar - c_wake, n('D1'), 60)
    s.note(CHOIR, c_wake + 0.5, c_roar - c_wake - 0.5, n('D3'), 40)
    s.ramp(CHOIR, c_wake + 0.5, c_roar, 20, 110)

    # ------------------------------------------------------------ D. the chase (D minor, 120 bpm)
    def action(a, b, intensity=1.0):
        beat = 0.5
        chords = [ns('D2', 'D3', 'F3', 'A3', 'D4'), ns('Bb1', 'Bb2', 'D3', 'F3', 'Bb3'),
                  ns('C2', 'C3', 'E3', 'G3', 'C4'), ns('A1', 'A2', 'C#3', 'E3', 'A3')]
        t = a
        bar = 0
        while t < b - 0.01:
            ch = chords[bar % 4]
            d = min(4 * beat, b - t)
            s.chord(STR, t, d, ch[1:], 85 * intensity)
            s.note(TUBA, t, d, ch[0], 90 * intensity)
            # string ostinato in sixteenths
            fig = [ch[2] + 12, ch[2] + 12, ch[3] + 12, ch[2] + 12, ch[4] + 12, ch[2] + 12, ch[3] + 12, ch[2] + 12]
            for k in range(16):
                tt = t + k * beat / 4
                if tt < b - 0.01:
                    s.note(TREM if k % 8 == 0 else PIZZ, tt, beat / 4 * 0.8, fig[k % 8], (70 + (15 if k % 4 == 0 else 0)) * intensity)
            # drums: big drum on 1, 2&, 3, 4& ; timpani on 1 and 3
            for off, v in ((0, 120), (1.5, 95), (2, 110), (3.5, 95)):
                tt = t + off * beat
                if tt < b:
                    s.note(DRUM, tt, 0.3, 35, v * intensity)
                    s.note(DRUM, tt, 0.3, 36, v * 0.8 * intensity)
            s.note(TIMP, t, 0.6, ch[0] + 12, 110 * intensity)
            s.note(TIMP, t + 2 * beat, 0.6, ch[0] + 12, 95 * intensity)
            if bar % 4 == 0:
                s.note(DRUM, t, 2.0, 49, 100 * intensity)
            t += 4 * beat
            bar += 1
        # the Giant's theme, full brass
        th = [('D4', 2), ('A3', 2), ('Bb3', 1), ('A3', 1), ('G3', 1), ('F3', 1), ('A3', 3), ('D3', 1),
              ('D4', 2), ('E4', 2), ('F4', 1), ('E4', 1), ('D4', 1), ('C#4', 1), ('D4', 4)]
        t = a + 4 * beat
        for name, d in th:
            dd = d * beat
            if t + dd > b:
                break
            s.note(BRASS, t, dd * 0.95, n(name), 105 * intensity)
            s.note(HORN, t, dd * 0.95, n(name) - 12, 95 * intensity)
            s.note(BRASS, t, dd * 0.95, n(name) - 12, 90 * intensity)
            t += dd

    def hit(t, big=1.0):
        s.chord(BRASS, t, 1.2, ns('D2', 'A2', 'D3', 'F3', 'A3'), 127 * big)
        s.note(TIMP, t, 1.5, n('D2'), 127)
        s.note(DRUM, t, 1.5, 35, 127)
        s.note(DRUM, t, 2.0, 49, 115 * big)
        s.note(DRUM, t, 2.0, 57, 100 * big)

    hit(c_roar)
    s.chord(CHOIR, c_roar, 3.0, ns('D3', 'A3', 'D4', 'F4'), 100)
    action(c_roar + 0.5, c['grow_peak'])
    for h in c['hits']:
        hit(h, 0.8)
    for d in c['doors']:
        s.chord(BRASS, d, 0.18, ns('D3', 'A3'), 115)
        s.note(DRUM, d, 0.3, 38, 110)
    # growing: a long rising line under the beat
    g0, gp = c['grow_start'], c['grow_peak']
    t = g0
    k = 0
    while t < gp:
        s.note(STR, t, 0.3, n('A3') + k, 70 + k * 3)
        s.note(CHOIR, t, 0.3, n('D4') + k, 50 + k * 3)
        t += 0.25
        k += 1
    # ...and silence. One comic bassoon.
    s.note(BSN, c['bwomp'], 0.45, n('D2'), 105)
    s.note(BSN, c['bwomp'] + 0.5, 0.9, n('C#2'), 100)
    # shrinking: a fast tumbling line down
    sh0 = c['shrink']
    for k in range(20):
        s.note(CEL, sh0 + k * 0.06, 0.12, n('D6') - k * 2, 85)
        s.note(PIZZ, sh0 + k * 0.06, 0.1, n('D5') - k * 2, 80)
    action(c['run2'], c['slowmo'])
    hit(c['stomp2'], 0.8)
    # slow motion fall: huge sustained chord, choir, timpani roll into the impact
    sm, fall, end = c['slowmo'], c['fall'], c['black2']
    s.chord(STR, sm, fall - sm, ns('D2', 'A2', 'D3', 'F3', 'A3', 'D4'), 95)
    s.chord(CHOIR, sm, fall - sm + 3.0, ns('D3', 'A3', 'D4', 'F4', 'A4'), 95)
    s.ramp(CHOIR, sm, fall, 70, 127)
    s.chord(HORN, sm + 0.5, fall - sm - 0.5, ns('F3', 'A3', 'D4'), 100)
    t = sm
    while t < fall:
        s.note(TIMP, t, 0.09, n('A1'), 60 + (t - sm) / (fall - sm) * 60)
        t += 0.09
    hit(fall, 1.0)
    s.chord(STR, fall, end - fall, ns('D2', 'D3', 'A3', 'D4'), 70)
    s.ramp(STR, fall + 0.5, end, 110, 20)
    s.note(BASS, fall, end - fall, n('D1'), 80)

    # ------------------------------------------------------------ G. epilogue (D major, warm)
    e0, e1 = c['epilogue'], c['eye']
    t = e0
    i = 0
    s.ramp(STR, e0, e0 + 2, 40, 80)
    while t < e1 - 0.1:
        d = min(2.5, e1 - t)
        s.chord(STR, t, d, prog[i % 4], 50)
        s.note(BASS, t, d, roots[i % 4] + 12, 45)
        t += 2.5
        i += 1
    t = e0 + 0.5
    for name, d in mel[:9]:
        dd = d * 0.85
        if t + dd > e1:
            break
        s.note(PIANO, t, dd, n(name), 70)
        s.note(PIANO, t, dd, n(name) - 12, 45)
        t += dd
    # the eye: tension returns
    s.note(BASS, e1, c['black3'] - e1, n('D1'), 70)
    s.note(TREM, e1 + 0.3, c['black3'] - e1 - 0.3, n('D4'), 40)
    s.note(TREM, e1 + 2.0, c['black3'] - e1 - 2.0, n('Eb4'), 40)
    s.ramp(TREM, e1, c['black3'], 30, 120)
    for th in c['thumps2']:
        s.note(TIMP, th, 1.0, n('D2'), 110)
        s.note(DRUM, th, 0.8, 35, 110)

    # ------------------------------------------------------------ I. title and end card
    ti, ec, fin = c['title'], c['endcard'], c['end']
    hit(ti, 1.0)
    s.chord(CHOIR, ti, 4.0, ns('D3', 'A3', 'D4', 'F4'), 110)
    s.chord(STR, ti, 4.5, ns('D2', 'A2', 'D3', 'F3', 'A3'), 100)
    s.ramp(STR, ti + 1, ti + 4.5, 120, 40)
    t = ti + 1.0
    for name, d in [('D4', 1.0), ('A3', 1.0), ('Bb3', .5), ('A3', .5), ('G3', .5), ('F3', .5)]:
        s.note(HORN, t, d * 0.95, n(name), 100)
        t += d
    t = ec
    i = 0
    s.ramp(STR, ec, ec + 1, 40, 70)
    while t < fin - 0.5:
        d = min(2.0, fin - t)
        s.chord(STR, t, d, prog[i % 4], 45)
        for k, p in enumerate(prog[i % 4]):
            s.note(HARP, t + k * 0.2, 1.0, p + 12, 45)
        t += 2.0
        i += 1
    t = ec + 0.6
    for name, d in mel[:6]:
        if t + d > fin:
            break
        s.note(FLUTE, t, d * 0.95, n(name), 60)
        t += d
    s.ramp(STR, fin - 2.0, fin, 70, 0)
    s.ramp(HARP, fin - 2.0, fin, 127, 0)
    s.ramp(FLUTE, fin - 2.0, fin, 127, 0)
    s.save(out)


if __name__ == '__main__':
    cues = json.load(open(sys.argv[1]))
    compose(cues, sys.argv[2])
    print('wrote', sys.argv[2])
