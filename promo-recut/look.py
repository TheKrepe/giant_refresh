"""Sample every cut, overlay and reaction in the two localized renders."""
from pathlib import Path
from itertools import accumulate
from PIL import Image, ImageDraw
import subprocess as sp
import sys,json

n, folder, out = sys.argv[1:]
film=json.loads(Path(__file__).with_name('films.json').read_text())['films'][n]
cuts=list(accumulate([0]+[round(s['duration']*30)/30 for s in film['shots']]))
marks=[.12]
for i, shot in enumerate(film['shots']):
    a,b=cuts[i:i+2]
    marks.extend([a+.08,(a+b)/2,b-.10])
    for line in shot['dialogue']:
        marks.extend([a+line['start']+.09,a+(line['start']+line['end'])/2,a+line['end']-.12])
marks.extend([cuts[-1]+.16,cuts[-1]+.72,cuts[-1]+2.0,cuts[-1]+2.46])
stamps=sorted({round(t,2) for t in marks})
files=sorted(Path(folder).glob('*.mp4'));assert len(files)==2
w,h=125,222
rows=(len(stamps)+7)//8
page=Image.new('RGB',(w*8,(h+18)*rows*2), '#19211d')
draw=ImageDraw.Draw(page)
for j,p in enumerate(files):
    for i,t in enumerate(stamps):
        a=sp.check_output(['ffmpeg','-nostdin','-v','error','-threads','1','-ss',str(t),'-i',str(p),
            '-frames:v','1','-vf',f'scale={w}:{h}','-threads','1','-f','rawvideo','-pix_fmt','rgb24','-'])
        x,y=i%8*w,(i//8+j*rows)*(h+18)
        page.paste(Image.frombytes('RGB',(w,h),a),(x,y))
        draw.text((x+4,y+h+2),f'{p.stem[-2:]} {t:.2f}',fill='white')
page.save(out,quality=84)
