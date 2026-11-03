#!/usr/bin/env python3
"""Build/test the host-only HD2 body scene against exact StarDroid 1.5.2 source."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-root', type=Path, default=ROOT, help='Extracted matching StarDroid1.5.2 source ZIP; run scripts/prepare_native.py there first.')
    for name in ('stardat', 'broodat', 'patch', 'anim'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--output', type=Path, default=ROOT / 'build-native/remastered-scene')
    p.add_argument('--report', type=Path, default=ROOT / 'validation/remastered-scene.json')
    p.add_argument('--cxx', default=os.environ.get('CXX', 'g++'))
    a = p.parse_args()
    base = a.source_root.resolve()
    gradle = (base / 'app/build.gradle').read_text()
    if 'versionName "1.5.2"' not in gradle and "versionName '1.5.2'" not in gradle:
        p.error('--source-root must be the matching StarDroid1.5.2 source release, not the older GitHubmain tree')
    if not (base / 'native-engine/ui/ui.h').is_file():
        p.error('Run scripts/prepare_native.py inside --source-root first')
    out = a.output.resolve(); out.mkdir(parents=True, exist_ok=True)
    inputs = {name: getattr(a, name).resolve() for name in ('stardat', 'broodat', 'patch', 'anim')}
    if inputs['anim'].name != 'main_000.anim':
        p.error('--anim must be the extracted HD2/anim/main_000.anim Scourge sample')
    sources = [ROOT / 'tools/remastered_scene.cpp', Path(__file__).resolve(), ROOT / 'native/remastered/assets.h', ROOT / 'native/remastered/geometry.h']
    baseline_sources = []
    for directory in ('native', 'native-engine', 'vendor/libsmacker'):
        baseline_sources += [f for f in (base / directory).rglob('*') if f.is_file() and f.suffix in ('.h', '.cpp', '.c') and 'remastered' not in f.parts]
    baseline_sources += [base / 'app/build.gradle', base / 'scripts/prepare_native.py', base / 'app/src/main/assets/native/weave.scx']
    bound = {str(f.relative_to(ROOT)): sha(f) for f in sources}
    base_bound = {str(f.relative_to(base)): sha(f) for f in sorted(set(baseline_sources))}
    input_hashes = {name: sha(path) for name,path in inputs.items()}
    steps = []
    def run(name, command):
        start = time.monotonic()
        r = subprocess.run([str(x) for x in command], capture_output=True, text=True, errors='replace', timeout=300)
        steps.append(dict(name=name, seconds=round(time.monotonic()-start, 3), exitCode=r.returncode, output=r.stdout+r.stderr))
        print(r.stdout+r.stderr, flush=True)
        return r.returncode == 0
    executable, smacker = out / 'remastered-scene', out / 'smacker.o'
    with (ROOT.parent / 'native122-compile.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        ok = run('compile-smacker', ['gcc', '-O2', '-c', base / 'vendor/libsmacker/smacker.c', '-o', smacker])
        if ok:
            ok = run('compile-scene', [a.cxx, '-std=c++17', '-O0', '-g0', '-pthread', '-DOPENBW_ENABLE_UI', '-DOPENBW_NO_SDL_IMAGE', '-DOPENBW_NO_SDL_MIXER', '-I'+str(base / 'native'), *['-I'+str(base / 'native-engine' / d) for d in ('', 'ui', 'web')], ROOT / 'tools/remastered_scene.cpp', base / 'native/generated/wasm_backend.cpp', smacker, '-o', executable])
    if ok:
        ok = run('body-scene-checks', [executable, inputs['stardat'], inputs['broodat'], inputs['patch'], base / 'app/src/main/assets/native/weave.scx', inputs['anim'], out])
    result = {}
    for step in steps:
        for line in step['output'].splitlines():
            if line.startswith('RESULT '): result = json.loads(line[7:])
    stable = bound == {str(f.relative_to(ROOT)): sha(f) for f in sources} and base_bound == {str(f.relative_to(base)): sha(f) for f in sorted(set(baseline_sources))}
    inputs_stable = input_hashes == {name: sha(path) for name,path in inputs.items()}
    ok = ok and stable and inputs_stable and result.get('passed', False)
    report = dict(passed=ok, sourceVersion='1.5.2', prototypeSourceSha256=bound, baselineSourceSha256=base_bound,
        sourcesUnchangedDuringRun=stable, inputsUnchangedDuringRun=inputs_stable, inputSha256=input_hashes, steps=steps, result=result,
        limits=['Host-only body fixture; not integrated in Android and no playable APK.',
                'Only Scourge image0 is replaced; classic Wraith verifies opaque foreground occlusion.',
                'HD team tint uses shade5 (index4) of the owning player classic unit palette; not the retail Remastered color table.',
                'Shadows, overlays, selection, UI, remembered buildings and real-time lighting are outside this fixture.',
                'Fog visibility and darkness grid use the original engine; HD RGB uses a linear preview shade, not retail lighting.',
                'All EUD maps and any fixture body with an unsupported image modifier use the classic body pipeline.',
                'No physical-device performance or retail screenshot pixel-equivalence claim.'])
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(dict(passed=ok, report=str(a.report), checks=result.get('checks', 0))))
    return 0 if ok else 1

if __name__ == '__main__':
    raise SystemExit(main())
