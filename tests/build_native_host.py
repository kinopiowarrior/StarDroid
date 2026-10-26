#!/usr/bin/env python3
"""Build desktop native campaign/controls harnesses; game files stay external."""
from pathlib import Path
import subprocess
import sys
import os

root = Path(__file__).resolve().parents[1]
name = sys.argv[1] if len(sys.argv) > 1 else 'controls'
if name not in ('controls', 'engine', 'gameplay', 'presentation', 'feedback', 'missions', 'saves', 'campaigns', 'revision40', 'revision41', 'revision42', 'revision43', 'revision44', 'revision45', 'revision46', 'revision54', 'revision55', 'revision56', 'revision57', 'revision58', 'revision59', 'revision77', 'revision78', 'campaign79', 'abilities79', 'controls80', 'production80', 'campaign_ai81', 'replay81', 'replay82', 'supply81', 'addon_sound81', 'resource_hud11', 'upgrades', 'placement', 'multiplayer'):
    raise SystemExit('Usage: build_native_host.py [controls|engine|gameplay|presentation|feedback|missions|saves|campaigns]')
out = root / 'build-native/host'
out.mkdir(parents=True, exist_ok=True)
if '--skip-prepare' not in sys.argv:
    subprocess.run([sys.executable, str(root / 'scripts/prepare_native.py')], check=True)
sanitizer = ['-fsanitize=address', '-fno-omit-frame-pointer'] if '--asan' in sys.argv else []
suffix = '-asan' if sanitizer else ''
smacker = out / ('smacker' + suffix + '.o')
subprocess.run(['gcc', '-O2'] + sanitizer + ['-c', str(root / 'vendor/libsmacker/smacker.c'),
                '-o', str(smacker)], check=True)
stage = root / 'native-engine'
command = [os.environ.get('CXX','g++'), '-std=c++17', ('-O0' if '--fast' in sys.argv else '-O1' if name in ('gameplay','feedback','missions','saves','campaigns','revision40', 'revision41', 'revision42', 'revision43', 'revision44', 'revision45', 'revision46', 'revision54', 'revision55', 'revision56', 'revision57', 'revision58', 'revision59', 'revision77', 'revision78', 'campaign79', 'abilities79', 'controls80', 'production80', 'campaign_ai81', 'replay81', 'replay82', 'supply81', 'addon_sound81', 'resource_hud11', 'upgrades', 'placement', 'multiplayer') else '-O2'), '-g1', '-rdynamic', '-pthread', '-DOPENBW_ENABLE_UI',
           '-DOPENBW_NO_SDL_IMAGE', '-DOPENBW_NO_SDL_MIXER']
command += sanitizer
command += ['-I' + str(p) for p in (stage, stage / 'ui', stage / 'web')]
command += [str(root / ('tests/native_' + name + '.cpp')),
            str(root / 'native/generated/wasm_backend.cpp'), str(smacker),
            '-o', str(out / ('native-' + name + suffix))]
subprocess.run(command, check=True)
print(out / ('native-' + name + suffix))
