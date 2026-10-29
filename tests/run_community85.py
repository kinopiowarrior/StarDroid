#!/usr/bin/env python3
"""Build and run community regressions with caller-owned classic archives.

Usage: run_community85.py CORE_DIR STARCRAFT_MPQ BROODWAR_MPQ [--skip-build]
CORE_DIR contains STARDAT.MPQ, BROODAT.MPQ and patch_rt.mpq.
Requires a host C++ compiler and JDK 17. No game data enters the report.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('core', type=Path)
parser.add_argument('starcraft', type=Path)
parser.add_argument('broodwar', type=Path)
parser.add_argument('--skip-build', action='store_true')
args = parser.parse_args()
core, original, expansion = (p.resolve() for p in (args.core, args.starcraft, args.broodwar))
out = root / 'build-native/community85'
out.mkdir(parents=True, exist_ok=True)
map_file = root / 'app/src/main/assets/native/weave.scx'
cases = []
completed = False

def run(name, command):
    log = out / (name + '.log')
    executable = Path(command[0])
    if executable.parent == root/'build-native/host' and executable.is_file():
        executable.chmod(executable.stat().st_mode | 0o111)
    try:
        with log.open('w') as output:
            result = subprocess.run(list(map(str, command)), cwd=root, stdout=output, stderr=subprocess.STDOUT)
    except OSError as error:
        cases.append({'name':name, 'passed':False, 'results':[str(error)]})
        raise
    text = log.read_text(errors='replace')
    cases.append({'name': name, 'passed': result.returncode == 0,
                  'results': [line for line in text.splitlines() if line.startswith(('PASS ', 'RESULT ', 'FAIL '))]})
    print(('PASS ' if result.returncode == 0 else 'FAIL ') + name, flush=True)
    if result.returncode:
        print(text[-6000:])
        raise RuntimeError(name)

try:
    names = ['community85', 'community_ai85', 'community_macro85', 'resource_hud11']
    if not args.skip_build:
        run('prepare', [sys.executable, root/'scripts/prepare_native.py'])
        # Host harnesses share one smacker object; compile them sequentially.
        for name in names:
            run('build-'+name, [sys.executable, root/'tests/build_native_host.py', name, '--skip-prepare'])
    game = root/'build-native/host/native-community85'
    ai = root/'build-native/host/native-community_ai85'
    for mode in ['core', 'presentation', 'mouse', 'burrow', 'traps', 'trap-arcs', 'projectile-edges', 'campaign-burrow']:
        run(mode, [game, core, original, mode, expansion])
    for mode in ['direct', 'island', 'air', 'sweep']:
        run('ai-'+mode, [ai, core, map_file, original, mode, expansion])
    for race in range(3):
        run('macro-'+str(race), [root/'build-native/host/native-community_macro85', core, map_file, race, 60000])
    run('resource-hud', [root/'build-native/host/native-resource_hud11', core])
    for name in ['detectors11', 'dunes11']:
        run(name, [sys.executable, root/('tests/test_'+name+'.py'), '--cxx', 'g++'])
    run('desktop', [sys.executable, root/'tests/run_desktop_input11.py'])
    java = root/'app/src/main/java/org/openbw/androidtest'
    stub = out/'java/org/openbw/androidtest/NativeBridge.java'
    stub.parent.mkdir(parents=True, exist_ok=True)
    stub.write_text('package org.openbw.androidtest; final class NativeBridge { static byte[] archiveText(String path){return null;} }\n')
    run('language-build', ['javac', '-d', out/'classes', java/'GameLanguage.java', java/'PortText.java', root/'tests/Community85Test.java', stub])
    run('language', ['java', '-cp', out/'classes', 'org.openbw.androidtest.Community85Test', root/'app/src/main/assets/native/ui-languages.tsv'])
    completed = True
finally:
    sources = [root/'patches/native-community85.patch', root/'native/runtime.cpp', root/'native/save_state.h',
               root/'tests/native_community85.cpp', root/'tests/native_community_ai85.cpp',
               root/'tests/native_community_macro85.cpp', Path(__file__)]
    report = {'build':85, 'simulation':86, 'passed':completed and bool(cases) and all(c['passed'] for c in cases),
              'physicalAndroidTested':False, 'actualSpanishArchivesTested':False, 'cases':cases,
              'sourceSha256':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
    (root/'validation/community85.json').write_text(json.dumps(report, indent=2)+'\n')
