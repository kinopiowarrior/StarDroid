#!/usr/bin/env python3
"""Check original-resolution Android sprite samples against the standalone RGBA decoder."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
NAMES = [f'main_{i:03}.anim' for i in (0, 5, 13, 122, 228)]
SOURCES = ['native/remastered/assets.h', 'native/remastered/geometry.h', 'native/remastered/runtime.h',
           'tests/native_hd2_texels.cpp', 'tests/run_hd2_texels.py']


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--samples', required=True, type=Path, help='Extracted HD2/anim directory')
    parser.add_argument('--cxx', default=os.environ.get('CXX', 'g++'))
    parser.add_argument('--report', type=Path, default=ROOT / 'validation/hd2-texels.json')
    args = parser.parse_args()
    sources = {n: sha(ROOT / n) for n in SOURCES}
    data = {n: sha(args.samples / n) for n in NAMES}
    with tempfile.TemporaryDirectory() as work:
        exe = Path(work) / 'hd2-texels'
        subprocess.run([args.cxx, '-std=c++17', '-O2', '-Wall', '-Wextra', '-Werror',
                        ROOT / 'tests/native_hd2_texels.cpp', '-o', exe], check=True)
        output = subprocess.check_output([exe, args.samples.resolve()], text=True, timeout=120)
    if sources != {n: sha(ROOT / n) for n in SOURCES} or data != {n: sha(args.samples / n) for n in NAMES}:
        raise RuntimeError('Test inputs changed during run')
    result = json.loads(output)
    assert result['passed'] and result['frames'] > 0 and result['drawnTexelsCompared'] > 0
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps({'passed': True, 'sourceSha256': sources, 'inputSha256': data,
        'scope': 'Full diffuse/mask atlases and all cropped poses sampled on host using five private ANIM files. '
                 'Compares straight RGBA before screen compositing, including tint, flipped and clipped coordinates.',
        'result': result}, indent=2) + '\n')
    print(output, end='')


if __name__ == '__main__':
    main()
