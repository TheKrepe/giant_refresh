"""Finish one authentic game-client take, preserving the same edit for both languages."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys

shot, output = sys.argv[1:]
out = Path(output)
assets = Path(__file__).resolve().parent / "assets"
number = int(shot.removeprefix("short"))
duration = (18, 23, 21, 25, 22)[number - 1]
slug = ("The_Last_Block", "On_Tiptoe", "Room_for_One_More", "Standard_Door", "Who_Trampled_the_Crops")[number - 1]
source = out / (shot + ".mp4")
bubbles = {
    1: [(10, 3.75, 5.59, .49, .31)],
    2: [(20, 10.76, 12.46, .73, .315), (21, 12.58, 13.58, .22, .30)],
    3: [(30, 4.05, 6.41, .48, .319)],
    4: [],
    5: [(50, 3.31, 5.64, .77, .315)],
}
def probe(path):
    raw = subprocess.check_output(["ffprobe", "-v", "error", "-show_streams", "-of", "json", str(path)])
    return json.loads(raw)["streams"]
stream = probe(source)[0]
assert (int(stream["width"]), int(stream["height"])) == (1080,1920), stream
rows = [s.split() for s in (out / (shot + ".frames.txt")).read_text().splitlines()]
assert len(rows) >= duration*24
print("Captured", len(set(row[1] for row in rows)), "distinct game frames /", len(rows), flush=True)
for lang in ["RU", "EN"]:
    args = ["ffmpeg","-nostdin","-hide_banner","-loglevel","error","-y","-filter_complex_threads","2",
            "-i", str(source), "-i", str(assets/f"audio{number}.m4a"), "-i", str(assets/f"end_{lang}.png")]
    filters = []
    tag = "0:v"
    for j, (index, start, end, cx, cy) in enumerate(bubbles[number]):
        image = assets / f"bub_{lang}_{index}.png"
        width, = struct.unpack(">I", image.read_bytes()[16:20])
        x = max(45, min(956-width,round(cx*1080-width/2)))
        args += ["-i", str(image)]
        filters.append(f"[{tag}][{3+j}:v]overlay={x}:{round(cy*1920)}:enable='between(t,{start},{end})'[t{j}]")
        tag = f"t{j}"
    if number == 1:
        args += ["-i", str(assets/"cross.png")]
        filters.append(f"[{tag}][{3+len(bubbles[number])}:v]overlay=526:946:enable='lt(t,3.45)'[fp]")
        tag = "fp"
    filters.append(f"[{tag}][2:v]overlay=0:0:enable='gte(t,{duration-2.5})',setsar=1,format=yuv420p[v]")
    dest = out / f"Giant_Refresh_{number:02d}_{slug}_{lang}.mp4"
    subprocess.run(args + ["-filter_complex",";".join(filters),"-map","[v]","-map","1:a:0","-t",str(duration),
                   "-c:v","libx264","-threads","3","-preset","fast","-crf","17","-maxrate","15M","-bufsize","30M",
                   "-c:a","copy","-movflags","+faststart","-metadata","artist=Star_1mp",
                   "-metadata:s:a:0","language="+("rus" if lang=="RU" else "eng"),str(dest)], check=True)
    streams = probe(dest)
    assert streams[0]["nb_frames"] == str(duration*24) and streams[1]["codec_name"] == "aac"
    print(dest.name,hashlib.sha256(dest.read_bytes()).hexdigest(), flush=True)
# A contact sheet sampled from the actual encoded film, including the localized overlays.
dest = out / f"Giant_Refresh_{number:02d}_{slug}_RU.mp4"
stamps={1:[.5,3.95,7.2,9.6,10.72,11.35,12.7,14,16.7],2:[1,3.5,7.7,11.5,13.3,15,17.2,19.8,21.9],3:[1,5.1,7.1,9.1,10.9,13.4,16.7,18.2,20.3],4:[1.1,3.6,5,7.65,10.7,15.3,18.1,21.6,23.8],5:[1,4.2,7.65,9.17,11.42,14,16.9,18.6,20.8]}
from PIL import Image, ImageDraw
im=Image.new("RGB",(3*216,3*406),(24,24,24));draw=ImageDraw.Draw(im)
for i,sec in enumerate(stamps[number]):
    pixels=subprocess.check_output(["ffmpeg","-v","error","-threads","1","-ss",str(sec),"-i",str(dest),"-frames:v","1","-vf","scale=216:384","-f","rawvideo","-threads","1","-pix_fmt","rgb24","-"])
    x,y=i%3*216,i//3*406
    im.paste(Image.frombytes("RGB",(216,384),pixels),(x,y));draw.text((x+6,y+387),f"{sec:.2f}s",fill="white")
im.save(out/(shot+".jpg"),quality=89)
