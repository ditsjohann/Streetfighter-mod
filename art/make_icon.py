import math, sys
from PIL import Image
N=64; S=8
img=Image.new("RGB",(N,N)); px=img.load()
cx,cy=31.5,30.5
# background: radial rays burst
def lerp(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
for y in range(N):
    for x in range(N):
        dx,dy=x-cx,y-cy; r=math.hypot(dx,dy); a=math.atan2(dy,dx)
        ray=(int((a+math.pi)/(2*math.pi)*16))%2
        t=min(r/40,1)
        base=lerp((255,170,40),(70,10,40),t**0.8) if ray else lerp((240,90,30),(40,6,30),t**0.7)
        # quantize into bands for pixel look
        px[x,y]=base
# impact starburst
def star(rad_out,rad_in,pts,col):
    for y in range(N):
        for x in range(N):
            dx,dy=x-cx,y-cy; r=math.hypot(dx,dy); a=math.atan2(dy,dx)
            k=(math.cos(a*pts)+1)/2
            lim=rad_in+(rad_out-rad_in)*k**3
            if r<lim: px[x,y]=col
star(22,10,8,(255,120,20))
star(17,8,8,(255,210,60))
star(12,5,8,(255,250,200))
star(6,3,8,(255,255,255))

OUT=(20,8,14)
def fist(ox,oy,sleeve,sleeve_d,mirror):
    # local art, pointing right; width 30, height 20
    skin=(232,176,128); skin_l=(250,206,160); skin_d=(184,124,84); skin_dd=(140,88,60)
    art={}
    def rect(x0,y0,x1,y1,c):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1): art[(x,y)]=c
    # sleeve / forearm
    rect(0,5,11,15,sleeve); rect(0,5,11,6,lerp(sleeve,(255,255,255),.35)); rect(0,13,11,15,sleeve_d)
    rect(10,4,12,16,(240,240,240)); rect(10,14,12,16,(180,180,190))  # cuff wrap
    # wrist
    rect(13,6,14,14,skin_d)
    # fist block
    rect(15,3,27,17,skin)
    rect(15,3,27,5,skin_l)          # top light
    rect(15,15,27,17,skin_d)        # bottom shade
    rect(26,4,27,16,skin_d)         # front knuckle face shade
    # finger separations on front face
    for fy in (7,10):
        rect(22,fy,27,fy,skin_dd)
    rect(24,13,27,13,skin_dd)
    # knuckle highlights
    for fy in (5,8,11,14):
        art[(27,fy)]=skin_l; art[(26,fy)]=skin
    # thumb
    rect(16,11,22,13,skin_l); rect(16,14,23,14,skin_dd); rect(23,11,23,13,skin_dd); rect(17,12,21,12,skin)
    # outline
    keys=set(art)
    for (x,y) in list(keys):
        for ddx,ddy in ((1,0),(-1,0),(0,1),(0,-1)):
            if (x+ddx,y+ddy) not in keys and x+ddx>=0: art[(x+ddx,y+ddy)]=OUT
    for (x,y),c in art.items():
        X = ox-x if mirror else ox+x
        Y=oy+y
        if 0<=X<N and 0<=Y<N: px[X,Y]=c
def aura(ox,oy,col,mirror):
    for i,(l,y) in enumerate([(10,1),(14,0),(8,20),(12,21),(6,-1)]):
        for x in range(-l,0):
            X=ox-x if mirror else ox+x
            Y=oy+y+ (1 if i%2 else 0)*0
            if 0<=X<N: px[X,Y]=col
fist(-2,21,(40,90,210),(20,50,140),False)
fist(65,21,(200,30,40),(120,15,25),True)
# speed lines
for (y,x0,x1,c) in [(18,1,9,(150,235,255)),(20,1,4,(150,235,255)),(41,1,7,(150,235,255)),(43,1,10,(150,235,255)),(18,54,62,(255,140,140)),(20,59,62,(255,140,140)),(41,56,62,(255,140,140)),(43,53,62,(255,140,140))]:
    for x in range(x0,x1+1): px[x,y]=c
# sparks
for (x,y) in [(31,8),(32,8),(31,53),(32,53),(20,12),(43,12),(20,50),(43,50)]:
    px[x,y]=(255,255,220)
# "VS"? no. Frame
for i in range(N):
    for c in (0,N-1):
        px[i,c]=OUT; px[c,i]=OUT
big=img.resize((N*S,N*S),Image.NEAREST)
big.save(sys.argv[1])
