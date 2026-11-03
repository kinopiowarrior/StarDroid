#!/usr/bin/env python3
"""Validate caller-owned ANIM samples against Pillow DDS decode and NumPy crops.

No licensed input or generated artwork is added to the source tree. The JSON
report contains hashes/geometry/check counts only. Optional previews stay in the
caller-supplied directory. Requires Pillow and NumPy for independent comparison.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import time
import numpy as np
from PIL import Image, __version__ as pillow_version

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ['native/remastered/assets.h', 'tests/native_remastered_real_assets.cpp',
           'tests/run_remastered_real_assets.py']


def sha(data):
    return hashlib.sha256(data).hexdigest()


def reference(data):
    """Independent container interpretation; Pillow handles embedded DDS pixels."""
    assert data[:4] == b'ANIM' and data[4:6] == bytes([2, 2])
    count, entries = struct.unpack_from('<HH', data, 8)
    assert entries == 1
    frame_count, reference_id, width, height, frame_at = struct.unpack_from('<HHHHI', data, 0x14c)
    assert reference_id == 0xffff
    layers = []
    for i in range(count):
        name = data[12+i*32:44+i*32].split(b'\0')[0].decode('ascii')
        offset, size, w, h = struct.unpack_from('<IIHH', data, 0x158+i*12)
        if not offset:
            continue
        payload = data[offset:offset+size]
        if payload[:4] == b'DDS ':
            with Image.open(io.BytesIO(payload)) as im:
                rgba = np.asarray(im.convert('RGBA')).copy()
            encoding = payload[84:88].decode('ascii')
        elif payload[:4] == b'BMP ':
            gray = np.frombuffer(payload[4:4+w*h], dtype=np.uint8).reshape(h, w)
            rgba = np.empty((h, w, 4), dtype=np.uint8)
            rgba[:, :, :3] = gray[:, :, None]
            rgba[:, :, 3] = 255
            encoding = 'BMP mask'
        else:
            raise AssertionError('Unknown real-sample payload ' + repr(payload[:4]))
        assert rgba.shape == (h, w, 4)
        layers.append(dict(index=i, name=name, width=w, height=h, rgba=rgba, encoding=encoding))
    frames = []
    for i in range(frame_count):
        x, y, ox, oy, w, h, _ = struct.unpack_from('<HHhhHHI', data, frame_at+i*16)
        frames.append([x//2, y//2, w//2, h//2, ox//2, oy//2])
    return [width, height], layers, frames


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--samples', type=Path, required=True, help='Directory containing main_*.anim')
    p.add_argument('--game-build', default='unspecified')
    p.add_argument('--cxx', default=os.environ.get('CXX', 'g++'))
    p.add_argument('--sanitize', action='store_true')
    p.add_argument('--output-dir', type=Path, default=ROOT/'build-native/remastered-real-assets')
    p.add_argument('--report', type=Path, default=ROOT/'validation/remastered-real-assets.json')
    p.add_argument('--previews', type=Path, help='Optional private directory outside the repository')
    args = p.parse_args()
    if args.previews and args.previews.resolve().is_relative_to(ROOT):
        p.error('--previews must be outside the repository')
    source_hashes = {name: sha((ROOT/name).read_bytes()) for name in SOURCES}
    samples = sorted(args.samples.glob('main_*.anim'))
    if not samples:
        p.error('No main_*.anim samples found')
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if args.previews:
        args.previews.mkdir(parents=True, exist_ok=True)
    executable = output/'native-remastered-real-assets'
    flags = ['-std=c++17', '-Wall', '-Wextra', '-Werror', '-pedantic']
    flags += ['-O1', '-g', '-fsanitize=address,undefined', '-fno-omit-frame-pointer'] if args.sanitize else ['-O2']
    environment = os.environ.copy()
    if args.sanitize:
        environment['ASAN_OPTIONS'] = environment.get('ASAN_OPTIONS', '') + ':detect_leaks=0:halt_on_error=1'
        environment['UBSAN_OPTIONS'] = environment.get('UBSAN_OPTIONS', '') + ':halt_on_error=1:print_stacktrace=1'
    subprocess.run([args.cxx, *flags, str(ROOT/SOURCES[1]), '-o', str(executable)], check=True, env=environment)
    results = []
    passed = False
    error = None
    start = time.monotonic()
    try:
        for sample in samples:
            data = sample.read_bytes()
            canvas, layers, frames = reference(data)
            with tempfile.TemporaryDirectory(prefix='asset-', dir=output) as tmp:
                tmp = Path(tmp)
                run = subprocess.run([str(executable), str(sample.resolve()), str(tmp)],
                    capture_output=True, text=True, check=True, env=environment, timeout=120)
                native = json.loads(run.stdout)
                assert native['scale'] == 2 and native['canvas_hd'] == canvas
                assert native['frames'] == frames, sample.name + ': frame geometry mismatch'
                assert len(native['layers']) == len(layers)
                layer_results = []
                decoded = {}
                for expected, actual in zip(layers, native['layers']):
                    assert all(actual[k] == expected[k] for k in ('index', 'name', 'width', 'height'))
                    pixels = (tmp/f"layer-{expected['index']}.rgba").read_bytes()
                    image = np.frombuffer(pixels, dtype=np.uint8).reshape(expected['height'], expected['width'], 4)
                    delta = np.abs(image.astype(np.int16)-expected['rgba'].astype(np.int16))
                    # BC endpoint expansion is exact. DDS libraries can differ by
                    # one RGB level in interpolation; alpha must match exactly.
                    assert int(delta[:, :, :3].max()) <= 1, sample.name + ': decoded RGB mismatch'
                    assert not np.any(delta[:, :, 3]), sample.name + ': decoded alpha mismatch'
                    decoded[expected['name']] = image
                    layer_results.append(dict(name=expected['name'], encoding=expected['encoding'],
                        width=expected['width'], height=expected['height'], rgba_sha256=sha(pixels),
                        reference_max_rgb_delta=int(delta[:, :, :3].max()),
                        reference_differing_rgb_channels=int(np.count_nonzero(delta[:, :, :3]))))
                raw_frames = {name: (tmp/filename).read_bytes() for name, filename in
                    [('plain', 'frames.rgba'), ('red', 'frames-red.rgba'), ('blue', 'frames-blue.rgba')]}
                at = 0
                visible_frames = 0
                empty_frames = 0
                preview_indices = sorted(set([0, len(frames)//2, len(frames)-1]))
                for i, (x, y, w, h, ox, oy) in enumerate(frames):
                    if not w or not h:
                        empty_frames += 1
                        continue
                    plain = decoded['diffuse'][y:y+h, x:x+w]
                    assert plain.shape == (h, w, 4)
                    count = w*h*4
                    if np.any(plain[:, :, 3]):
                        visible_frames += 1
                    for name, color in [('plain', None), ('red', [255, 0, 0]), ('blue', [0, 0, 255])]:
                        expected = plain.copy()
                        if color and 'teamcolor' in decoded:
                            mask = decoded['teamcolor'][y:y+h, x:x+w, :1].astype(np.uint32)
                            factor = 255*(255-mask) + np.array(color, dtype=np.uint32)*mask
                            expected[:, :, :3] = (plain[:, :, :3].astype(np.uint32)*factor+32512)//65025
                        actual = raw_frames[name][at:at+count]
                        assert actual == expected.tobytes(), f'{sample.name}: frame {i} {name} crop/tint mismatch'
                        if args.previews and i in preview_indices:
                            Image.fromarray(expected).save(args.previews/f'{sample.stem}-frame-{i}-{name}.png')
                    at += count
                assert all(at == len(raw) for raw in raw_frames.values())
                assert sha(sample.read_bytes()) == sha(data), 'Input changed during validation'
                results.append(dict(file=sample.name, input_sha256=sha(data), input_bytes=len(data),
                    canvas_hd=canvas, frame_count=len(frames), nonempty_frames=len(frames)-empty_frames,
                    alpha_visible_frames=visible_frames, empty_frames=empty_frames,
                    resident_pixel_bytes=native['resident_pixel_bytes'], layers=layer_results,
                    all_frame_pixels_sha256={name:sha(raw) for name,raw in raw_frames.items()}))
                print(json.dumps(dict(file=sample.name, passed=True, frames=len(frames), layers=len(layers))), flush=True)
        assert source_hashes == {name:sha((ROOT/name).read_bytes()) for name in SOURCES}, 'Source changed during run'
        passed = True
    except Exception as exc:
        error = str(exc)
    report = dict(passed=passed, game_build=args.game_build, sanitizers=args.sanitize,
        leak_check=False, source_sha256=source_hashes, sample_count=len(results),
        frame_count=sum(x['frame_count'] for x in results),
        layer_count=sum(len(x['layers']) for x in results),
        reference=dict(decoder='Pillow', version=pillow_version, numpy_version=np.__version__,
                       allowed_rgb_delta=1, allowed_alpha_delta=0),
        scope='External HD2 samples: all present layers, all frame geometry, every diffuse crop and red/blue team-color result. No lighting, gameplay anchor, Android renderer or device validation.',
        results=results, elapsed_seconds=round(time.monotonic()-start,3), error=error)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(dict(passed=passed, report=str(args.report), error=error)))
    return 0 if passed else 1

if __name__ == '__main__':
    raise SystemExit(main())
