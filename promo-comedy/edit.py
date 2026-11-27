from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
import sys,json,subprocess as sp, numpy as np
from argparse import ArgumentParser
from project import project
from music import write as write_music
W,H=1080,1920
ASSETS=Path(__file__).resolve().parent / "assets"
def run(args):
    sp.run(['ffmpeg','-nostdin','-y','-loglevel','error','-filter_complex_threads','2',*map(str,args)],check=True)

glyph={}
def load_font(assets):
    for f in json.loads((assets/'fonts.json').read_text(encoding='utf-8'))['providers']:
        if f.get('type') != 'bitmap':
            continue
        image=Image.open(assets/Path(f['file']).name).convert('RGBA').getchannel('A')
        rows=f['chars']
        columns=max(map(len,rows))
        cw,ch=image.width//columns,image.height//len(rows)
        for j,row in enumerate(rows):
            for i,c in enumerate(row):
                if c=='\x00':
                    continue
                cell=image.crop((cw*i,ch*j,cw*(i+1),ch*(j+1)))
                if cw!=8:
                    cell=cell.resize((8,round(ch*8/cw)),Image.Resampling.NEAREST)
                bounds=cell.getbbox()
                if bounds:
                    # Retain bearings and the ascent so punctuation and Ё/Й stay aligned.
                    glyph[c]=(cell.crop((0,0,bounds[2],cell.height)),7-f['ascent'])

def mask(text, upper=True):
 lines=(text.upper() if upper else text).replace('’', "'").split('|'); widths=[sum((glyph[c][0].width+1 if c!=' ' else 4) for c in ln) for ln in lines]
 m=Image.new('L',(max(widths),len(lines)*12+7)); 
 for j,ln in enumerate(lines):
  x=(m.width-widths[j])//2
  for c in ln:
   if c==' ':x+=4;continue
   g,dy=glyph[c];m.paste(g,(x,4+j*12+dy));x+=g.width+1
 return m.crop(m.getbbox())
def art(text,title=False,anchor=.5):
 m=mask(text); k=min(8 if title else 7,max(3,860//m.width));m=m.resize((m.width*k,m.height*k),Image.Resampling.NEAREST)
 w,h=m.size
 if title:
  pad=22;sz=(w+2*pad,h+2*pad);alpha=Image.new('L',sz);alpha.paste(m,(pad,pad))
  outline=alpha.filter(ImageFilter.MaxFilter(15));rgba=Image.new('RGBA',sz);shadow=Image.new('RGBA',sz,'#432900');shadow.putalpha(outline.filter(ImageFilter.GaussianBlur(8)));rgba.alpha_composite(shadow)
  rgba.paste('#291705',(0,0,*sz),outline)
  for y in range(h):
   t=y/max(1,h-1);color=tuple(round(a*(1-t)+b*t) for a,b in zip((255,237,137),(218,143,38)))
   rgba.paste(color+(255,),(pad,pad+y,pad+w,pad+y+1),m.crop((0,y,w,y+1)))
  return rgba
 pad=19;ww=w+2*pad;hh=h+2*pad
 # Slide the body inside safe margins, keeping the balloon tip aimed at its speaker.
 left=min(max(30,anchor*W-ww/2),W-30-ww);tail=int(np.clip(anchor*W-left,27,ww-27))
 rgba=Image.new('RGBA',(ww,hh+30));d=ImageDraw.Draw(rgba)
 d.rounded_rectangle((0,0,ww-1,hh-1),radius=21,fill='white',outline='#191919',width=5)
 d.polygon([(tail-17,hh-5),(tail+18,hh-5),(tail-7,hh+27)],fill='#191919')
 d.polygon([(tail-11,hh-7),(tail+10,hh-7),(tail-5,hh+14)],fill='white')
 rgba.paste('#131313',(pad,pad,pad+w,pad+h),m)
 return rgba

def logo(image,text,x,y,scale,color,outline=3,upper=True):
 m=mask(text,upper=upper)
 m=m.resize((m.width*scale,m.height*scale),Image.Resampling.NEAREST)
 x0,y0=round(x-m.width/2),round(y-m.height/2)
 # Put the outline outside the bitmap so small punctuation stays readable.
 a=Image.new('L',image.size);a.paste(m,(x0,y0))
 image.paste('#11151c',(0,0,W,H),a.filter(ImageFilter.MaxFilter(outline*2+1)))
 image.paste(color,(0,0,W,H),a)

def call_to_action(doc,film,lang):
 p=Image.new('RGBA',(W,H),(10,20,18,194))
 def paste_center(pic,x,y):
  p.alpha_composite(pic,(round(x-pic.width/2),round(y-pic.height/2)))
 def art_file(name,width):
  im=Image.open(ASSETS/name).convert('RGBA')
  im=im.crop(im.getbbox())
  return im.resize((width,round(im.height*width/im.width)),Image.Resampling.NEAREST)
 paste_center(art_file('logo.png',720),W/2,737)
 logo(p,film['feature'][lang],W/2,978,4,'#e8eed1')
 logo(p,('ДОСТУПНО НА' if lang=='ru' else 'AVAILABLE ON'),W/2,1084,5,'#eaf1eb')
 for name,x,color in [('MODRINTH',307,'#37ed95'),('CURSEFORGE',746,'#ff945b')]:
  source=art_file(name.lower()+'_white.png',43)
  brand_icon=Image.new('RGBA',source.size,color)
  brand_icon.putalpha(source.getchannel('A'))
  paste_center(brand_icon,x-(144 if name=='MODRINTH' else 176),1162)
  logo(p,name,x+14,1161,5,color)
 ImageDraw.Draw(p).rectangle((430,1237,650,1240),fill=(190,226,189,155))
 paste_center(art_file('author.png',66),355,1315)
 logo(p,('АВТОР' if lang=='ru' else 'BY') + ' Star 1mp',578,1314,5,'#eff4ed',upper=False)
 return p

def render(doc,key,lang,raw,output,music,fit):
 film=doc['films'][key];name=film['name'];heading=film['title'];shots=film['shots']
 wd=output/'.work'/(key+'_'+lang);wd.mkdir(parents=True,exist_ok=True)
 segments=[]
 for idx,shot in enumerate(shots):
  src=shot['source'];effect=shot.get('effect');lines=shot.get('dialogue',[])
  file=raw/(src+'.mp4');n=round(shot['duration']*30);duration=n/30
  srcdur=float(sp.check_output(['ffprobe','-v','error','-show_entries','stream=duration','-select_streams','v:0','-of','csv=p=0',str(file)]))
  seek=shot.get('seek',0);available=srcdur-seek;take=shot.get('length',available)
  if not 0<take<=available+.04:
   raise ValueError(f"Invalid input slice for {file}")
  args=['-threads','2','-ss',seek,'-t',take,'-i',file]
  sizing=('scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920' if fit=='crop'
          else 'scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2')
  graph=[f'[0:v]{sizing},setpts={duration/take:.7f}*PTS,fps=30,tpad=stop_mode=clone:stop_duration=0.1,trim=end_frame={n},setsar=1[v0]']
  if effect=='portrait':
   # The three doubles pose for the shutter. The photo develops in the shot.
   graph[0]=graph[0].replace(f'trim=end_frame={n},setsar=1',f"trim=end_frame=99,tpad=stop_mode=clone:stop_duration={max(0,n-99)/30:.5f},trim=end_frame={n},hue=s='max(0,min(1,(3.37-t)/0.17))',drawbox=x=24:y=24:w=iw-48:h=ih-48:color=white@0.95:t=12:enable='gte(t,3.25)',drawbox=color=white@0.38:t=fill:enable='between(t,3.23,3.29)',setsar=1")
  if effect in ('stage','white-reveal'):
   graph[0]=graph[0].replace(',setsar=1',',eq=gamma=1.09:saturation=1.04,setsar=1')
  if effect=='white-reveal':
   graph[0]=graph[0].replace(',setsar=1',',fade=t=in:st=0:d=0.20:color=white,setsar=1')
  overlays=[]
  if idx==0:overlays.append((art(heading[lang],True),.5,.145,0,1.85,True))
  for cue in lines:
   start,end,x,y=(cue[k] for k in ("start","end","x","y"))
   # Include the exit in the same shot, even on cues that run up to the cut.
   end=min(duration-.033,max(end+.10,start+.6))
   overlays.append((art(cue['text'][lang],anchor=x),x,y,start,end,False))
  if idx==len(shots)-1:
   # Reuse the last moving frame under the edition card.
   graph[0]+=f';[v0]tpad=stop_mode=clone:stop_duration=2.60[vpad]'
   overlays.append((call_to_action(doc,film,lang),.5,0,duration,duration+2.6,None))
  prev='vpad' if idx==len(shots)-1 else 'v0'
  for j,(im,x,y,t,e,ishead) in enumerate(overlays,1):
   pic=wd/f'{idx}_{j}.png';im.save(pic);args+=['-loop','1','-framerate','30','-i',pic]
   if ishead is None:left=top=0
   else:
    left=round(np.clip(W*x-im.width/2,30,W-im.width-30))
    top=round(H*y-(im.height/2 if ishead else im.height))
   fade_in,fade_out=(.40,.48) if ishead is None else (.17,.27) if ishead else (.20,.29)
   fade_in=min(fade_in,(e-t)/3)
   fade_out=min(fade_out,(e-t)/3)
   y=str(top) if ishead is None else f"{top}+12*max(0,1-(t-{t})/{fade_in})-7*max(0,1-({e}-t)/{fade_out})"
   graph.append(f"[{j}:v]format=rgba,fade=t=in:st={t:.4f}:d={fade_in:.4f}:alpha=1,fade=t=out:st={e-fade_out:.4f}:d={fade_out:.4f}:alpha=1[g{j}]")
   graph.append(f"[{prev}][g{j}]overlay=x={left}:y='{y}':shortest=1:"
                f"enable='between(t,{t:.4f},{e:.4f})'[v{j}]")
   prev=f'v{j}'
  if idx==len(shots)-1:
   graph.append(f'[{prev}]fade=t=out:st={duration+2.35:.4f}:d=0.25,format=yuv420p[base]')
  else:
   graph.append(f'[{prev}]format=yuv420p[base]')
  ofile=wd/f'{idx}.mp4'
  run(args+['-filter_complex',';'.join(graph),'-map','[base]','-an','-c:v','libx264','-threads','3','-preset','veryfast','-crf','20','-maxrate','5200k','-bufsize','9600k',ofile]);segments.append(ofile)
 listing=wd/'all.txt'
 def quote(path):
  return "'" + str(path).replace("'", "'\\''") + "'"
 listing.write_text(''.join('file '+quote(p)+'\n' for p in segments),encoding='utf-8')
 dest=output/(f'{name}_{lang.upper()}.mp4')
 run(['-f','concat','-safe','0','-i',listing,'-i',music,'-map','0:v','-map','1:a','-c:v','copy','-af','loudnorm=I=-16:TP=-2.1:LRA=8','-ar','48000','-c:a','aac','-b:a','192k','-movflags','+faststart','-shortest',dest])

if __name__=='__main__':
 parser=ArgumentParser(description="Edit Minecraft footage into localized 9:16 shorts.")
 parser.add_argument('--project',type=Path,required=True)
 parser.add_argument('--raw',type=Path,required=True)
 parser.add_argument('--assets',type=Path,required=True)
 parser.add_argument('--out',type=Path,required=True)
 parser.add_argument('--film',help='Render just this film key')
 parser.add_argument('--lang',choices=['ru','en','both'],default='both')
 parser.add_argument('--fit',choices=['pad','crop'],default='pad')
 parser.add_argument('--soundtrack',type=Path,help='Supplied final mix, requires --film')
 args=parser.parse_args()
 if args.soundtrack and not args.film:
  parser.error('--soundtrack requires --film')
 doc=project(args.project)
 assets=args.assets.resolve()
 load_font(assets)
 output=args.out.resolve()
 work=output/'.work'
 work.mkdir(parents=True,exist_ok=True)
 for key in ([args.film] if args.film else doc['films']):
  film=doc['films'][key]
  music=args.soundtrack or work/(key+'-mix.wav')
  if not args.soundtrack:
   write_music(film['music'],film['shots'],film.get('effects',[]),assets,music,film.get('sound_design'))
  for lang in (['en','ru'] if args.lang=='both' else [args.lang]):
   render(doc,key,lang,args.raw.resolve(),output,music,args.fit)

