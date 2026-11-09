import colorsys
from PIL import Image
HANDLE={(0x89,0x67,0x27),(0x68,0x4e,0x1e),(0x49,0x36,0x15),(0x28,0x1e,0x0b)}
STONE={(0x8f,0x8f,0x8f),(0x7f,0x7f,0x7f),(0x74,0x74,0x74),(0x68,0x68,0x68),(0x5e,0x5e,0x5e),(0x51,0x51,0x51)}
def _Y(c): return 0.299*c[0]+0.587*c[1]+0.114*c[2]
def shift_lum(c,hue,sat_mul=1.0,dark_hue=0.0,lum_mul=1.0,sat_cap=1.0):
    if c==(255,255,255): return c
    h,s,v=colorsys.rgb_to_hsv(*[x/255 for x in c]); Yt=min(255,_Y(c)*lum_mul)
    hh=(hue/360+dark_hue/360*(1-v))%1; s=min(sat_cap,s*sat_mul)
    while True:
        lo,hi=0.0,1.0
        for _ in range(30):
            m=(lo+hi)/2
            if _Y([x*255 for x in colorsys.hsv_to_rgb(hh,s,m)])<Yt: lo=m
            else: hi=m
        cc=tuple(round(x*255) for x in colorsys.hsv_to_rgb(hh,s,(lo+hi)/2))
        if _Y(cc)>=Yt-1.5 or s<0.02: return cc
        s*=0.9
def recolor_lum(path,hue,keep=HANDLE|STONE,**kw):
    im=Image.open(path).convert('RGBA'); px=im.load(); out=im.copy(); o=out.load()
    for y in range(im.height):
        for x in range(im.width):
            p=px[x,y]
            if p[3]==0 or p[:3] in keep: continue
            o[x,y]=shift_lum(p[:3],hue,**kw)+(255,)
    return out
def bevel(px,body,pal,outline=None):
    if outline:
        for (x,y) in body:
            for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                q=(x+dx,y+dy)
                if q not in body: px[q]=outline[0] if (dx<0 or dy<0) else outline[1]
    for (x,y) in body:
        up=(x,y-1) not in body; lf=(x-1,y) not in body; dn=(x,y+1) not in body; rt=(x+1,y) not in body
        c=pal[2]
        if up or lf: c=pal[1]
        if up and lf: c=pal[0]
        if dn or rt: c=pal[3]
        if dn and rt: c=pal[4]
        px[x,y]=c
def check(im):
    w,h=im.size; px=im.load(); op=[(x,y) for y in range(h) for x in range(w) if px[x,y][3]>0]
    tl=[_Y(px[x,y]) for x,y in op if x+y<w-1]; br=[_Y(px[x,y]) for x,y in op if x+y>w-1]
    return dict(colors=len({px[x,y][:3] for x,y in op}),semi=sum(px[x,y][3]<255 for x,y in op),TL=round(sum(tl)/max(1,len(tl))),BR=round(sum(br)/max(1,len(br))))
def preview(images,path,scale=12):
    W=sum(i.width*scale+16 for i in images)+16; H=max(i.height for i in images)*scale+32
    out=Image.new('RGBA',(W,H),(58,58,66,255)); x=16
    for im in images:
        big=im.resize((im.width*scale,im.height*scale),Image.NEAREST); chk=Image.new('RGBA',big.size); cp=chk.load()
        for yy in range(big.height):
            for xx in range(big.width): cp[xx,yy]=(88,88,98,255) if (xx//scale+yy//scale)%2 else (104,104,114,255)
        chk.alpha_composite(big); out.alpha_composite(chk,(x,16)); x+=big.width+16
    out.save(path)
