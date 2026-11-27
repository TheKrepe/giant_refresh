"""Decode each export, check stereo and 9:16, then optionally pack the files."""
from argparse import ArgumentParser
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json
import re
import subprocess
from zipfile import ZipFile, ZIP_STORED


def check(file):
    raw = subprocess.check_output(["ffprobe", "-v", "error", "-show_streams", "-of", "json", str(file)])
    video, audio = json.loads(raw)["streams"]
    assert tuple(video[k] for k in ("codec_name", "width", "height", "r_frame_rate", "pix_fmt")) == (
        "h264", 1080, 1920, "30/1", "yuv420p"
    ), str(file)
    assert tuple(audio[k] for k in ("codec_name", "channels", "sample_rate")) == ("aac", 2, "48000")
    duration = float(video["duration"])
    assert abs(duration - float(audio["duration"])) < .1, file
    logs = subprocess.run(["ffmpeg", "-nostdin", "-xerror", "-hide_banner", "-threads", "2", "-i", str(file),
                           "-af", "ebur128=peak=true", "-f", "null", "-"], capture_output=True, text=True,
                          check=True).stderr
    lufs = float(re.findall(r"I:\s+([-\d.]+) LUFS", logs)[-1])
    peak = float(re.findall(r"Peak:\s+([-\d.]+) dBFS", logs)[-1])
    assert peak < -.1 and lufs > -50, (file, lufs, peak)
    print(f"{file.name}: {duration:.2f} s, {lufs:.1f} LUFS, {peak:.1f} dBTP", flush=True)
    return file.stem[:-3], file.stem[-2:], int(video["nb_frames"])


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("out", type=Path)
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--bilingual", action="store_true")
    args = parser.parse_args()
    files = sorted(args.out.glob("*.mp4"))
    if not files:
        parser.error("No MP4 found")
    pairs = defaultdict(dict)
    with ThreadPoolExecutor(max_workers=3) as executor:
        for name, language, frames in executor.map(check, files):
            pairs[name][language] = frames
    if args.bilingual:
        assert all(set(langs) == {"RU","EN"} and len(set(langs.values())) == 1 for langs in pairs.values())
    if args.bundle:
        with ZipFile(args.bundle, "w", compression=ZIP_STORED) as zip_file:
            for file in files:
                zip_file.write(file, file.name)
        with ZipFile(args.bundle) as zip_file:
            assert zip_file.testzip() is None and len(zip_file.namelist()) == len(files)

