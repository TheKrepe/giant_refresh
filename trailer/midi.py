"""Tiny MIDI writer: tempo fixed at 60 bpm, so times are given directly in seconds."""
import struct

PPQ = 960  # ticks per beat = per second


def vlq(n):
    out = [n & 0x7F]
    n >>= 7
    while n:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    return bytes(reversed(out))


class Song:
    def __init__(self):
        self.ev = []  # (tick, order, bytes)
        self.programs = {}

    def program(self, ch, prog, vol=100, pan=64, reverb=60):
        self.programs[ch] = prog
        self.ev.append((0, 0, bytes([0xC0 | ch, prog])))
        self.ev.append((0, 0, bytes([0xB0 | ch, 7, vol])))
        self.ev.append((0, 0, bytes([0xB0 | ch, 10, pan])))
        self.ev.append((0, 0, bytes([0xB0 | ch, 91, reverb])))
        self.ev.append((0, 0, bytes([0xB0 | ch, 11, 127])))

    def note(self, ch, t, dur, pitch, vel=80):
        if dur <= 0 or not (0 <= pitch <= 127):
            return
        a = int(round(t * PPQ))
        b = int(round((t + dur) * PPQ))
        vel = max(1, min(127, int(vel)))
        self.ev.append((a, 2, bytes([0x90 | ch, pitch, vel])))
        self.ev.append((max(a + 1, b), 1, bytes([0x80 | ch, pitch, 0])))

    def chord(self, ch, t, dur, pitches, vel=80):
        for p in pitches:
            self.note(ch, t, dur, p, vel)

    def cc(self, ch, t, num, val):
        self.ev.append((int(round(t * PPQ)), 0, bytes([0xB0 | ch, num, max(0, min(127, int(val)))])))

    def ramp(self, ch, t0, t1, v0, v1, num=11, steps=None):
        steps = steps or max(2, int((t1 - t0) * 20))
        for i in range(steps + 1):
            u = i / steps
            self.cc(ch, t0 + (t1 - t0) * u, num, v0 + (v1 - v0) * u)

    def bend(self, ch, t, value):  # -8192..8191
        v = value + 8192
        self.ev.append((int(round(t * PPQ)), 0, bytes([0xE0 | ch, v & 0x7F, (v >> 7) & 0x7F])))

    def save(self, path):
        evs = sorted(self.ev, key=lambda e: (e[0], e[1]))
        data = bytearray()
        data += b'\x00\xFF\x51\x03' + (1000000).to_bytes(3, 'big')  # 60 bpm
        last = 0
        for tick, _, b in evs:
            data += vlq(tick - last) + b
            last = tick
        data += b'\x00\xFF\x2F\x00'
        with open(path, 'wb') as f:
            f.write(b'MThd' + struct.pack('>IHHH', 6, 0, 1, PPQ))
            f.write(b'MTrk' + struct.pack('>I', len(data)) + bytes(data))
