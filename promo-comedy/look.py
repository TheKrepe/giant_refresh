"""Contact sheet around the dramatic beats, in both language versions."""
from pathlib import Path
import sys
from PIL import Image,ImageDraw
import subprocess as sp
key, folder, to = sys.argv[1:]
samples = {
    'gate':(0.15,1.25,4.4,7.65,9.62,10.9,12.55,14.75,15.54,16.35,17.44,17.94),
    'hatch':(0.15,1.5,3.52,5.35,6.86,7.68,8.92,11.45,12.80,13.95,16.35,17.82),
    'bell':(0.15,0.49,3.7,5.35,6.85,8.80,9.85,10.65,11.13,13.65,16.35,17.82),
}[key]
paths = sorted(Path(folder).glob('*.mp4'))
assert len(paths)==2, paths
width,height=136,242
image=Image.new('RGB',(width*6,(height+19)*4),(19,25,22))
draw=ImageDraw.Draw(image)
for li,p in enumerate(paths):
    for i,t in enumerate(samples):
        frame=sp.check_output(['ffmpeg','-nostdin','-v','error','-threads','1','-ss',str(t),'-i',str(p),
              '-frames:v','1','-vf',f'scale={width}:{height}','-threads','1','-f','rawvideo','-pix_fmt','rgb24','-'])
        x,y=(i%6)*width,(i//6+li*2)*(height+19)
        image.paste(Image.frombytes('RGB',(width,height),frame),(x,y))
        draw.text((x+6,y+height+2),f'{p.stem[-2:]}  {t:.2f}',fill='white')
image.save(to, quality=87)
