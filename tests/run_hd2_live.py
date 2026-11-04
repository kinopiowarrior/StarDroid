#!/usr/bin/env python3
"""Test optional HD2 bodies in the normal game renderer using caller-owned assets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
IDS = (0, 5, 13, 122, 228)


def sha(path):
    with path.open('rb') as source:
        digest = hashlib.sha256()
        for chunk in iter(lambda: source.read(1 << 20), b''):
            digest.update(chunk)
        return digest.hexdigest()


def inputs(directory):
    return {f.name: sha(f) for i in IDS for f in [directory / f'main_{i:03d}.anim']}


def sources():
    names = [Path(__file__).resolve(), ROOT / 'tests/native_hd2_live.cpp', ROOT / 'scripts/prepare_native.py',
             ROOT / 'app/src/main/assets/native/weave.scx']
    for directory in ('native-engine', 'native', 'vendor/libsmacker'):
        names.extend(f for f in (ROOT / directory).rglob('*') if f.suffix in ('.h', '.cpp', '.c', '.hpp'))
    names.extend((ROOT / 'patches').glob('*.patch'))
    return {str(path.relative_to(ROOT)): sha(path) for path in sorted(set(names))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('stardat', 'broodat', 'patch', 'starcraft', 'anim-dir'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'build-native/hd2-live')
    parser.add_argument('--report', type=Path, default=ROOT / 'validation/hd2-live.json')
    parser.add_argument('--captures', type=Path, help='Optional private directory outside the source tree')
    args = parser.parse_args()
    if args.captures and args.captures.resolve().is_relative_to(ROOT):
        parser.error('Keep captures outside the source tree')
    if not (ROOT / 'native-engine/ui/ui.h').exists():
        parser.error('Run python3 scripts/prepare_native.py first')
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    archive_names = ('stardat', 'broodat', 'patch', 'starcraft')
    archives = {k: getattr(args, k).resolve() for k in archive_names}
    archive_hash = {k: sha(p) for k,p in archives.items()}
    anim_dir = args.anim_dir.resolve()
    anim_hash = inputs(anim_dir)
    bound = sources()
    steps = []

    def run(label, command):
        started = time.monotonic()
        proc = subprocess.run([str(x) for x in command], text=True, errors='replace',
                              capture_output=True, timeout=360)
        if proc.returncode != 0:
            raise RuntimeError(f'{label} failed:\n{proc.stdout[-3000:]}\n{proc.stderr[-4000:]}')
        lines = [s for s in proc.stdout.splitlines() if s.startswith(('PASS ', 'RESULT '))]
        steps.append({'name': label, 'seconds': round(time.monotonic()-started,2), 'output': lines})
        for line in lines:
            print(line, flush=True)
        return lines

    native = ROOT/'native-engine'
    run('compile libsmacker', ['gcc','-O2','-c',ROOT/'vendor/libsmacker/smacker.c','-o',out/'smacker.o'])
    run('compile renderer harness', [os.environ.get('CXX','g++'),'-std=c++17','-O0','-g0','-pthread',
        '-DOPENBW_ENABLE_UI','-DOPENBW_NO_SDL_IMAGE','-DOPENBW_NO_SDL_MIXER',
        *['-I'+str(native/d) for d in ('','ui','web')],ROOT/'tests/native_hd2_live.cpp',
        ROOT/'native/generated/wasm_backend.cpp',out/'smacker.o','-o',out/'hd2-live'])
    with tempfile.TemporaryDirectory(dir=out) as tmp:
        work = Path(tmp)
        for folder in ('one','broken','wrong','empty'):
            (work/folder).mkdir()
        shutil.copyfile(anim_dir/'main_000.anim',work/'one/main_000.anim')
        (work/'broken/main_000.anim').write_bytes(b'ANIM\x02')
        shutil.copyfile(anim_dir/'main_122.anim',work/'wrong/main_000.anim')
        command = [out/'hd2-live', *archives.values(), ROOT/'app/src/main/assets/native/weave.scx',anim_dir,work]
        if args.captures:
            args.captures.mkdir(parents=True, exist_ok=True)
            command.append(args.captures.resolve())
        log = run('normal framebuffer and simulation', command)
    result = json.loads(next(line[7:] for line in log if line.startswith('RESULT ')))
    if not result['passed'] or bound != sources() or anim_hash != inputs(anim_dir) or archive_hash != {k:sha(p) for k,p in archives.items()}:
        raise RuntimeError('Validation did not finish on stable inputs')
    receipt = {'passed':True, 'scope':'Host test using the Android native game renderer and campaign loader. Five normal body images on the 2x Android presentation target, including original foreground GRP coverage. The camera and input use the logical viewport. No physical device or performance result.',
               'sourceSha256':bound,'archiveSha256':archive_hash,'sampleSha256':anim_hash,'steps':steps,'result':result}
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(receipt,indent=2)+'\n')
    print(args.report)


if __name__ == '__main__':
    main()
