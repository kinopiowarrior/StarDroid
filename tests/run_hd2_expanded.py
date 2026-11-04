#!/usr/bin/env python3
"""Check preview 3 sprite coverage through the native game renderer with original external media."""
import argparse
import io
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import time
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from run_hd2_live import sha, sources

ROOT = Path(__file__).resolve().parents[1]
IDS = (0,1,2,3,4,5,6,7,13,24,36,45,63,81,100,122,145,177,202,228,325,348,527,545,723,747,758,862,893)
SHADOWS = {1,6,81,100,145,177,202,348,893}
RUNNER = Path(__file__).resolve()
HARNESS = ROOT/'tests/native_hd2_expanded.cpp'


def rgb(path):
    with Image.open(path) as image:
        return np.array(image.convert('RGB'),dtype=np.int32)


def shadow_oracle(captures, anim):
    """Decode the real alpha atlas independently and check its exact black source-over blend."""
    data = anim.read_bytes()
    if data[:6] != b'ANIM\x02\x02':
        raise ValueError('Expected HD2 animation')
    cx,cy,frame = map(int,(captures/'shadow.txt').read_text().split())
    frame_count, reference, cw, ch, offset = struct.unpack_from('<HHHHI',data,0x14c)
    assert frame < frame_count and reference == 65535
    tx,ty,ox,oy,w,h,_ = struct.unpack_from('<HHhhHHI',data,offset+16*frame)
    for i in range(struct.unpack_from('<H',data,8)[0]):
        if data[12+32*i:44+32*i].split(b'\0',1)[0] == b'diffuse':
            address, size, _, _ = struct.unpack_from('<IIHH',data,0x158+i*12)
            with Image.open(io.BytesIO(data[address:address+size])) as tex:
                alpha = np.array(tex.convert('RGBA'),dtype=np.int32)[ty//2:ty//2+h//2,tx//2:tx//2+w//2,3]
            break
    else:
        raise RuntimeError('Missing shadow diffuse alpha')
    under = rgb(captures/'shadow-under.ppm')
    height, width = under.shape[:2]
    out = {}
    for flip,name in ((False,'front'),(True,'mirror')):
        left,top=(2*ox-cw)//4,(2*oy-ch)//4
        if flip:
            left=-left-alpha.shape[1]
        x,y=2*cx+left,2*cy+top
        assert x>=0 and y>=0 and x+alpha.shape[1]<=width and y+alpha.shape[0]<=height
        a=alpha[:,::-1] if flip else alpha
        effective=(a*120+127)//255
        expected=under.copy()
        region=expected[y:y+a.shape[0],x:x+a.shape[1]]
        region[:]=(region*(255-effective[:,:,None])+127)//255
        actual=rgb(captures/f'shadow-{name}.ppm')
        delta=np.max(np.abs(actual-expected),axis=2)
        image_changed=np.any(actual!=under,axis=2)
        assert np.any((a>0)&(a<255)) and image_changed.sum()>100
        if np.any(delta):
            bad_y,bad_x=np.unravel_index(int(np.argmax(delta)),delta.shape)
            raise RuntimeError(f'Shadow {name} mismatch in {np.count_nonzero(delta)} pixels; maximum at ({bad_x},{bad_y}) '
                               f'expected {expected[bad_y,bad_x]} got {actual[bad_y,bad_x]}')
        # The actual world underneath contains nonuniform high-resolution sprite coverage.
        # If the renderer flattened that background per logical pixel, the reference above would disagree.
        sub=under.reshape(height//2,2,width//2,2,3)
        varied=np.any(sub!=sub[:,:1,:,:1,:],axis=(1,3,4))
        touched=image_changed.reshape(height//2,2,width//2,2).any(axis=(1,3))
        cells=int((varied&touched).sum())
        assert cells>20, 'Shadow did not exercise high-resolution underlying sprite texels'
        out[name]={'changedOutputTexels':int(image_changed.sum()),'highDetailCellsUnderShadow':cells,'maximumRgbDifference':0}
    return out


def comparison(captures, dest):
    cx,cy=map(int,(captures/'building.txt').read_text().split())
    rx,ry=101,76
    cy=max(ry,min(480-ry,cy))
    bounds=(2*(cx-rx),2*(cy-ry),2*(cx+rx),2*(cy+ry))
    canvas=Image.new('RGB',(2*rx*4+30,ry*4+80),'#101820')
    draw=ImageDraw.Draw(canvas)
    try:
        f=ImageFont.truetype('DejaVuSans.ttf',18);sm=ImageFont.truetype('DejaVuSans.ttf',13)
    except OSError:
        f=sm=ImageFont.load_default()
    for i,(mode,title) in enumerate((('classic','Classic building'),('hd2','Imported HD2 building'))):
        left=10+i*(rx*4+10);draw.text((left,9),title,fill='#e9edf0',font=f)
        with Image.open(captures/f'building-{mode}.ppm') as im:
            canvas.paste(im.crop(bounds),(left,37))
    draw.text((10,ry*4+49),'Backwater Station · same original Infested Command Center, camera and animation pose',
              fill='#bdcbd4',font=sm)
    dest.parent.mkdir(parents=True,exist_ok=True);canvas.save(dest)


def binding():
    bound=sources()
    for p in (RUNNER,HARNESS):
        bound[str(p.relative_to(ROOT))]=sha(p)
    return bound


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for opt in ('stardat','broodat','patch','starcraft','anim-dir'):
        ap.add_argument('--'+opt,type=Path,required=True)
    ap.add_argument('--output',type=Path,default=ROOT/'build-native/hd2-expanded')
    ap.add_argument('--report',type=Path,default=ROOT/'validation/hd2-expanded.json')
    ap.add_argument('--comparison',type=Path,help='Optional private PNG outside the source tree')
    args=ap.parse_args()
    if args.comparison and args.comparison.resolve().is_relative_to(ROOT):
        ap.error('Keep game artwork outside the source tree')
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
    archive={k:getattr(args,k).resolve() for k in ('stardat','broodat','patch','starcraft')}
    originals=args.anim_dir.resolve();names=[f'main_{i:03d}.anim' for i in IDS]
    def inputs():
        return {**{k:sha(p) for k,p in archive.items()},**{n:sha(originals/n) for n in names}}
    bound=binding(); inp=inputs()
    steps=[]
    def run(label,argv):
        start=time.monotonic()
        proc=subprocess.run(list(map(str,argv)),capture_output=True,text=True,errors='replace',timeout=420)
        if proc.returncode:
            raise RuntimeError(label+' failed:\n'+proc.stderr[-2500:]+'\n'+'\n'.join(proc.stdout.splitlines()[-38:]))
        rows=[row for row in proc.stdout.splitlines() if row.startswith(('PASS ','IMAGE '))]
        if rows:
            print('\n'.join(rows),flush=True)
        steps.append({'name':label,'seconds':round(time.monotonic()-start,2),'results':rows})
        return rows
    native=ROOT/'native-engine';smacker=out/'smacker.o'
    run('compile libsmacker',['gcc','-O2','-c',ROOT/'vendor/libsmacker/smacker.c','-o',smacker])
    run('compile game harness',[os.environ.get('CXX','g++'),'-std=c++17','-O0','-g0','-pthread','-DOPENBW_ENABLE_UI',
        '-DOPENBW_NO_SDL_IMAGE','-DOPENBW_NO_SDL_MIXER',*['-I'+str(native/d) for d in ('','ui','web')],HARNESS,
        ROOT/'native/generated/wasm_backend.cpp',smacker,'-o',out/'expanded'])
    with tempfile.TemporaryDirectory(dir=out) as tmp:
        tmp=Path(tmp); packs=tmp/'single';caps=tmp/'private';caps.mkdir()
        for i,name in zip(IDS,names):
            directory=packs/str(i);directory.mkdir(parents=True)
            shutil.copyfile(originals/name,directory/name)
        rows=run('live renderer on original assets',[out/'expanded',*archive.values(),ROOT/'app/src/main/assets/native/weave.scx',
            originals,packs,caps])
        images={}
        for row in rows:
            if row.startswith('IMAGE '):
                _,ident,changed,mode,pose=row.split()
                images[int(ident)]={'changedOutputPixels':int(changed),'runtimeModifier':int(mode),'frame':int(pose)}
        assert set(images)==set(IDS) and all(im['changedOutputPixels']>0 for im in images.values())
        assert {i for i in images if images[i]['runtimeModifier']==10}==SHADOWS
        shadows=shadow_oracle(caps,originals/'main_001.anim')
        if args.comparison:comparison(caps,args.comparison)
    if bound!=binding() or inp!=inputs():raise RuntimeError('Sources or game archives changed during tests')
    receipt={'passed':True,'version':'1.5.4-hd2-preview3','sourceSha256':bound,'inputSha256':inp,
             'result':{'images':images,'shadow':shadows},'steps':steps,
             'scope':'Live host-native Android world rendering. Among the Ruins Larva, Backwater Station building/fog, stock unit/sprite/layer placements, real Broodling death and single-shadow RGBA oracle. Physical-device performance is separate.'}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(receipt,indent=2)+'\n')
    print(args.report,flush=True)

if __name__=='__main__':main()
