"""Edit decision list of the trailer and the music cue points derived from it."""
import json
import os

# (shot or 'black'/'title'/'endcard', in, out) in film seconds of that shot
EDL = [
    ('black', 0, 1.2),
    ('s01_dawn', 0, 7.0),
    ('s02_farmer', 0, 4.5),
    ('s03_thump', 0, 4.0),
    ('s04_lookup', 0, 3.5),
    ('black', 0, 1.4),
    ('s05_footprint', 0, 6.5),
    ('s06_follow', 0, 5.5),
    ('s07_ruins', 0, 7.0),
    ('s08_silhouette', 0, 7.0),
    ('s09_sneak', 0, 6.0),
    ('s10_sleeper', 0, 7.0),
    ('s11_spider', 0, 3.5),
    ('s12_wake', 0, 6.5),
    ('s13_stomp', 0, 4.0),
    ('s14_arrows', 0, 3.5),
    ('s15_grab', 0, 3.4),
    ('s16_golem', 0, 4.0),
    ('s17_hide', 0, 4.0),
    ('s18_growth', 0, 7.0),
    ('s19_shrink', 0, 6.5),
    ('s20_fall', 0, 7.27),
    ('black', 0, 1.4),
    ('s21_trophy', 0, 6.5),
    ('s22_eye', 0, 6.6),
    ('title', 0, 5.0),
    ('endcard', 0, 8.0),
]

# events inside shots (film seconds from the shot's start)
EVENTS = {
    's03_thump': {'thump': [1.1, 2.6]},
    's04_lookup': {'thump': [0.7, 3.2]},
    's11_spider': {'spider': [0.7]},
    's12_wake': {'wake': [0.8], 'roar': [3.6]},
    's13_stomp': {'hit': [1.5]},
    's16_golem': {'hit': [0.85]},
    's17_hide': {'door': [2.6, 2.85, 3.1, 3.35]},
    's18_growth': {'grow': [2.0], 'peak': [4.5], 'bwomp': [5.7]},
    's19_shrink': {'shrink': [1.9], 'run': [3.6], 'stomp': [5.0]},
    's20_fall': {'fall': [(0.5 + 1.3) / 0.55]},
    's22_eye': {'thump': [1.0, 2.2]},
}


def timeline():
    t = 0.0
    out = []
    for name, a, b in EDL:
        out.append((name, a, b, t))
        t += b - a
    return out, t


def cues():
    tl, total = timeline()
    start = {}
    for name, a, b, t in tl:
        start.setdefault(name, t)
    ev = lambda s, k, i=0: start[s] + EVENTS[s][k][i]
    blacks = [t for name, a, b, t in tl if name == 'black']
    c = {
        'start': start['s01_dawn'],
        'thumps': [ev('s03_thump', 'thump', 0), ev('s03_thump', 'thump', 1), ev('s04_lookup', 'thump', 0), ev('s04_lookup', 'thump', 1)],
        'black1': blacks[1],
        'trail': start['s05_footprint'],
        'follow': start['s06_follow'],
        'ruins': start['s07_ruins'],
        'silhouette': start['s08_silhouette'],
        'tension': start['s09_sneak'],
        'spider': ev('s11_spider', 'spider'),
        'wake': ev('s12_wake', 'wake'),
        'roar': ev('s12_wake', 'roar'),
        'hits': [ev('s13_stomp', 'hit'), ev('s16_golem', 'hit')],
        'doors': [start['s17_hide'] + x for x in EVENTS['s17_hide']['door']],
        'grow_start': ev('s18_growth', 'grow'),
        'grow_peak': ev('s18_growth', 'peak'),
        'bwomp': ev('s18_growth', 'bwomp'),
        'shrink': ev('s19_shrink', 'shrink'),
        'run2': ev('s19_shrink', 'run'),
        'stomp2': ev('s19_shrink', 'stomp'),
        'slowmo': start['s20_fall'],
        'fall': ev('s20_fall', 'fall'),
        'black2': blacks[2],
        'epilogue': start['s21_trophy'],
        'eye': start['s22_eye'],
        'thumps2': [ev('s22_eye', 'thump', 0), ev('s22_eye', 'thump', 1)],
        'black3': start['title'],
        'title': start['title'],
        'endcard': start['endcard'],
        'end': total,
    }
    c['thump1'] = c['thumps'][0]
    return c


if __name__ == '__main__':
    c = cues()
    json.dump(c, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cues.json'), 'w'), indent=1)
    print(json.dumps(c))
