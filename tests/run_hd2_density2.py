#!/usr/bin/env python3
"""Compare the installed presentation design against preview 1 and decoded HD2 texels.

Build the same small campaign harness twice, once against each complete prepared
source tree. Raster captures and the exact game state are compared privately.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
HARNESS = ROOT / 'tests/native_hd2_density2.cpp'
IDS = [0, 5, 13, 122, 228]


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as src:
        for chunk in iter(lambda: src.read(1024*1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def source_hashes(root):
    inputs = [p for sub in ['native-engine','native','vendor/libsmacker'] for p in (root / sub).rglob('*')
              if p.is_file() and p.suffix in ['.cpp','.c','.hpp','.h']]
    inputs += [root / 'scripts/prepare_native.py', *sorted((root / 'patches').glob('*.patch'))]
    return {str(p.relative_to(root)):sha(p) for p in sorted(inputs)}


def compile_scene(root, out, baseline):
    native = root/'native-engine'
    subprocess.run(['gcc','-O2','-c',root/'vendor/libsmacker/smacker.c','-o',out/'smacker.o'], check=True, timeout=120)
    args = [os.environ.get('CXX','g++'),'-std=c++17','-O1','-g0','-pthread','-DOPENBW_ENABLE_UI',
            '-DOPENBW_NO_SDL_IMAGE','-DOPENBW_NO_SDL_MIXER','-I'+str(root/'native'),
            *['-I'+str(native / d) for d in ('','ui','web')]]
    if baseline:
        args.append('-DHD2_TEST_PREVIEW1')
    subprocess.run([*args, HARNESS, root/'native/generated/wasm_backend.cpp',out/'smacker.o','-o',out/'scene'],
                   timeout=400, check=True)


def load_reference(path, frame, tint):
    # Read container fields and ask Pillow to decode the actual DDS independently
    # of both StarDroid implementations. A mask can also be raw ANIM BMP bytes.
    data = path.read_bytes()
    if data[:6] != b'ANIM\x02\x02':
        raise ValueError('Expected HD2 animation')
    count, entries = struct.unpack_from('<HH', data, 8)
    frames, reference, cw, ch, offset = struct.unpack_from('<HHHHI', data, 0x14c)
    if entries != 1 or reference != 65535 or frame >= frames:
        raise ValueError('Wrong HD2 container or pose')
    tx, ty, ox, oy, w, h, _ = struct.unpack_from('<HHhhHHI', data, offset+16*frame)
    layers = {}
    for i in range(count):
        name = data[12+i*32:44+i*32].split(b'\0',1)[0].decode('ascii')
        address, size, sw, sh = struct.unpack_from('<IIHH', data, 0x158+i*12)
        if name not in ('diffuse','teamcolor') or address == 0:
            continue
        payload = data[address:address+size]
        if payload[:4] == b'DDS ':
            with Image.open(io.BytesIO(payload)) as im:
                layers[name] = np.array(im.convert('RGBA'), dtype=np.int32)
        elif payload[:4] == b'BMP ' and name == 'teamcolor':
            layers[name] = np.frombuffer(payload[4:4+sw*sh], dtype=np.uint8).reshape(sh,sw,1).astype(np.int32)
        else:
            raise ValueError('Unexpected reference encoding')
    # Floor odd signed positioning once. Source art is sampled once per texel.
    left, top = (2*ox-cw)//4, (2*oy-ch)//4
    cut = (slice(ty//2,ty//2+h//2), slice(tx//2,tx//2+w//2))
    art = layers['diffuse'][cut].copy()
    if 'teamcolor' in layers:
        mask = layers['teamcolor'][cut][:,:,:1]
        art[:,:,:3] = (art[:,:,:3] * (255*(255-mask)+np.array(tint)*mask)+32512)//65025
    return left, top, art


def read_rgb(folder, name):
    with Image.open(folder/(name+'.ppm')) as im:
        return np.array(im.convert('RGB'), dtype=np.int32)


def oracle(folder, anim, placement, name, flip, tint, background_name='background'):
    left, top, art = load_reference(anim, placement['frame'], tint)
    if flip:
        left = -left-art.shape[1]
        art = art[:, ::-1]
    cx,cy = placement['center']; x = 2*cx+left; y = 2*cy+top
    w,h = art.shape[1],art.shape[0]
    def region(label):
        return read_rgb(folder,label)[y:y+h,x:x+w,:]
    background = region(background_name); rendered = region(name)
    alpha = art[:,:,3:4]
    expected = (art[:,:,:3]*alpha + background*(255-alpha) + 127)//255
    pixel_diff = np.max(np.abs(rendered-expected),axis=2)
    opaque = alpha[:,:,0] == 255; partial = (alpha[:,:,0] > 0) & ~opaque
    # This authored Dragoon is unobscured: every crop texel, including the
    # translucent outline over its original selection circle, must match.
    opaque_n=int(opaque.sum());partial_n=int(partial.sum())
    assert opaque_n>500 and partial_n>20
    assert int(pixel_diff.max())==0, (name,'source mismatch',int(pixel_diff.max()),int(np.count_nonzero(pixel_diff)))
    return {'exactOpaqueTexels':opaque_n,'exactPartialAlphaTexels':partial_n,
            'maxRgbDifference':int(pixel_diff.max())}


def varied_quads(image):
    quads = image.reshape(image.shape[0]//2,2,image.shape[1]//2,2,3)
    return np.any(quads!=quads[:,0:1,:,0:1,:],axis=(1,3,4))


def compare(old, new, anim, image_path):
    p1 = json.loads((old/'placement.json').read_text()); p2 = json.loads((new/'placement.json').read_text())
    assert p1.pop('density') == 1 and p2.pop('density') == 2 and p1 == p2
    assert sha(old/'continuation.bin') == sha(new/'continuation.bin'), 'Synchronized state diverged under 2x presentation'
    assert read_rgb(old,'body').shape==(480,640,3) and read_rgb(new,'body').shape==(960,1280,3)
    tests = [oracle(new,anim,p2,'body',p2['flipped'],p2['tint']),
             oracle(new,anim,p2,'flipped',not p2['flipped'],p2['tint']),
             oracle(new,anim,p2,'owner',p2['flipped'],p2['otherTint']),
             oracle(new,anim,p2,'selected',p2['flipped'],p2['tint'],'selected-background')]
    cx,cy = p2['center']; rx,ry = 44,48
    # Classic terrain cannot account for variation inside a 2x2 world pixel.
    y=slice(2*(cy-ry),2*(cy+ry)); x=slice(2*(cx-rx),2*(cx+rx))
    hd = read_rgb(new,'body'); bg = read_rgb(new,'background'); sd = read_rgb(old,'body');
    detailed = int(np.count_nonzero(varied_quads(hd[y,x]) & ~varied_quads(bg[y,x])))
    assert detailed >= 200, 'The Dragoon resembles an upscaled low-density image'
    # With identical simulation and camera, every classic health/shield bar
    # pixel below the Dragoon should still be present at exactly four texels.
    old_sel = read_rgb(old,'selected'); new_sel = read_rgb(new,'selected')
    gx,gy,gw,gh=p2['gauge']
    gauge=old_sel[gy:gy+gh,gx:gx+gw]
    bar_samples=gw*gh*4
    assert bar_samples>100 and np.any(gauge!=sd[gy:gy+gh,gx:gx+gw]), 'Selected shield and health gauge missing'
    assert np.array_equal(gauge.repeat(2,0).repeat(2,1),new_sel[2*gy:2*(gy+gh),2*gx:2*(gx+gw)]), 'Gauge shifted or missing'
    # The classic drag rectangle crosses actual body texels. It draws after
    # RGBA conversion and must cover all four subpixels of every touched cell.
    old_drag=read_rgb(old,'drag');new_drag=read_rgb(new,'drag')
    line=old_drag[cy,cx-30:cx+31]
    assert len(np.unique(line,axis=0))==1 and np.any(line!=sd[cy,cx-30:cx+31]), 'Drag border was not exercised'
    actual_line=new_drag[2*cy:2*cy+2,2*(cx-30):2*(cx+31)]
    assert np.all(actual_line==line[0]), 'Drag border leaked underlying HD texels'
    assert np.array_equal(sd,read_rgb(new,'reduced')), 'Bounded 1x target no longer matches preview 1'
    if image_path:
        # Both columns show the same map rectangle with identical final zoom.
        panels=[]
        for folder,d in [(old,1),(new,2)]:
            im=Image.open(folder/'selected.ppm'); crop=im.crop((d*(cx-rx),d*(cy-ry),d*(cx+rx),d*(cy+ry)))
            panels.append(crop.resize((rx*8,ry*8),Image.Resampling.NEAREST))
        canvas=Image.new('RGB',(2*rx*8+30,ry*8+74),'#111820'); draw=ImageDraw.Draw(canvas)
        try:
            font=ImageFont.truetype('DejaVuSans.ttf',16); small=ImageFont.truetype('DejaVuSans.ttf',12)
        except OSError: font=small=ImageFont.load_default()
        for i,label in enumerate(('Preview 1','Current preview · HD2 texels')):
            dx=10+i*(rx*8+10);draw.text((dx,9),label,font=font,fill='#f1f5f9');canvas.paste(panels[i],(dx,36))
        draw.text((10,ry*8+47),'First Strike · same Dragoon, pose and camera · 4× display enlargement',font=small,fill='#cbd5e1')
        canvas.save(image_path)
    return {'mission':'First Strike','verifiedFirstStrikePose':p2,'sourcePixelChecks':dict(zip(['normal','mirrored','ownerTint','selectionCircle'],tests)),
            'worldPixelsContainingDistinctSubpixels':detailed,'exactSelectedGaugeSubpixels':bar_samples,'exactDragBorderSubpixels':int(actual_line.size//3),
            'reducedTargetMatchesPreview1':True,
            'renderTicksPerPresentation':1,'comparedSimulationSteps':32,'identicalWorldContinuation':True,
            'preview1Size':[640,480],'currentSize':[1280,960]}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for n in ['baseline','stardat','broodat','patch','starcraft','anim-dir']:
        parser.add_argument('--'+n,type=Path,required=True)
    parser.add_argument('--comparison',type=Path,help='Optional private PNG outside the source tree')
    parser.add_argument('--report',type=Path,default=ROOT/'validation/hd2-density2.json');args=parser.parse_args()
    if args.comparison and args.comparison.resolve().is_relative_to(ROOT): parser.error('Keep the visual outside the repository')
    old=args.baseline.resolve();inputs={n:getattr(args,n).resolve() for n in ['stardat','broodat','patch','starcraft']}
    sprite=args.anim_dir.resolve();anim_paths=[sprite/f'main_{i:03d}.anim' for i in IDS]
    input_hash=lambda: {**{n:sha(p) for n,p in inputs.items()},**{p.name:sha(p) for p in anim_paths}}
    bound=input_hash();src=source_hashes(ROOT);base=source_hashes(old)
    extra=[HARNESS,Path(__file__).resolve(),ROOT/'app/src/main/java/org/openbw/androidtest/Hd2FrameSize.java',
           ROOT/'tests/Hd2FrameSizeTest.java',ROOT/'app/src/main/java/org/openbw/androidtest/NativeSurface.java']
    harness={str(p.relative_to(ROOT)):sha(p) for p in extra}
    perf={}
    with tempfile.TemporaryDirectory(prefix='hd2-density-') as tmp:
        tmp=Path(tmp)
        # Exercise the same five-file generation held by an upgraded preview 2
        # installation even when the supplied ZIP contains additional artwork.
        legacy_pack = tmp / 'five-sprites'
        legacy_pack.mkdir()
        for original in anim_paths:
            copied = legacy_pack / original.name
            shutil.copyfile(original, copied)
            if sha(copied) != bound[original.name]:
                raise RuntimeError('Sprite changed while preparing the compatibility fixture')
        java=tmp/'java';java.mkdir()
        java_home=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
        tool=lambda name: str(java_home/name) if java_home else name
        subprocess.run([tool('javac'),'-d',java,extra[2],extra[3]],check=True,timeout=90)
        java_result=subprocess.run([tool('java'),'-cp',str(java),'org.openbw.androidtest.Hd2FrameSizeTest'],
                                   capture_output=True,text=True,check=True,timeout=60).stdout.strip()
        for name,root,is_old in [('old',old,True),('new',ROOT,False)]:
            work=tmp/name;work.mkdir();compile_scene(root,work,is_old)
            run=subprocess.run([work/'scene',*inputs.values(),legacy_pack,work],capture_output=True,text=True,errors='replace',timeout=120)
            if run.returncode:raise RuntimeError(run.stderr[-3000:]+run.stdout[-2000:])
            line=next(s for s in run.stdout.splitlines() if s.startswith('TIMING '))
            perf[name]={'medianMs':float(line.split()[1]),'slowestMs':float(line.split()[2])}
        if args.comparison:args.comparison.parent.mkdir(exist_ok=True,parents=True)
        result=compare(tmp/'old',tmp/'new',legacy_pack/'main_122.anim',args.comparison)
    assert src==source_hashes(ROOT) and base==source_hashes(old) and bound==input_hash()
    assert all(sha(ROOT/p)==h for p,h in harness.items())
    report={'passed':True,'scope':'Host CPU game presentation using the original five-file import, original archived First Strike Dragoon and Pillow DDS reference. Timing uses 12 warmed O1 draws for the same view. Android Canvas/device performance is not measured.',
        'sourceSha256':{**src,**harness},'preview1SourceDifferences':{n:h for n,h in base.items() if src.get(n)!=h},
        'inputSha256':bound,'result':result,'androidDensityBounds':java_result,'hostTiming':perf}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'passed':True,'result':result,'hostTiming':perf}))

if __name__=='__main__':main()
