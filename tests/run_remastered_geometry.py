#!/usr/bin/env python3
"""Compare external classic MPQ and HD2 sprite geometry without bundling artwork."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('stardat', 'broodat', 'patch', 'samples'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--source-root', type=Path, default=ROOT,
                        help='Prepared StarDroid 1.5.2 source for original MPQ reader headers')
    parser.add_argument('--cxx', default=os.environ.get('CXX', 'g++'))
    parser.add_argument('--output', type=Path, default=ROOT / 'build-native/remastered-geometry')
    parser.add_argument('--report', type=Path, default=ROOT / 'validation/remastered-geometry.json')
    parser.add_argument('--previews', action='store_true', help='Write private PAM previews under --output')
    args = parser.parse_args()
    source = args.source_root.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    own = ['tools/remastered_geometry.cpp', 'native/remastered/assets.h',
           'native/remastered/geometry.h', 'tests/native_remastered_geometry.cpp',
           'tests/run_remastered_geometry.py']
    bound = {name: sha(ROOT / name) for name in own}
    engine = {str(p.relative_to(source)): sha(p) for p in sorted((source / 'native-engine').glob('*.h'))}
    if not engine:
        raise RuntimeError('Run scripts/prepare_native.py in --source-root first')
    samples = {p.name: sha(p) for p in sorted(args.samples.glob('main_*.anim'))}
    archives = {name: sha(getattr(args, name)) for name in ('stardat', 'broodat', 'patch')}
    steps = []

    def run(command):
        result = subprocess.run([str(x) for x in command], capture_output=True,
                                text=True, errors='replace', timeout=180)
        steps.append({'exit_code': result.returncode, 'output': result.stdout + result.stderr})
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        print(result.stdout, end='', flush=True)

    passed = False
    error = None
    summary = None
    try:
        flags = ['-std=c++17', '-O2', '-Wall', '-Wextra', '-Werror']
        run([args.cxx, *flags, ROOT / 'tests/native_remastered_geometry.cpp',
             '-o', output / 'native-remastered-geometry'])
        run([output / 'native-remastered-geometry'])
        run([args.cxx, *flags, '-isystem', source / 'native-engine',
             ROOT / 'tools/remastered_geometry.cpp', '-o', output / 'remastered-geometry'])
        raw_report = output / 'geometry-private.json'
        command = [output / 'remastered-geometry', args.stardat.resolve(), args.broodat.resolve(),
                   args.patch.resolve(), args.samples.resolve(), raw_report]
        if args.previews:
            command.append(output / 'private-previews')
        run(command)
        raw = json.loads(raw_report.read_text())
        summary = {'asset_count': raw['asset_count'], 'frame_count': raw['frame_count'],
                   'matching_frame_counts': raw['matching_frame_counts'], 'assets': []}
        for asset in raw['assets']:
            summary['assets'].append({k: v for k, v in asset.items() if k != 'frames'})
            for frame in asset['frames']:
                x, y = frame['preview_relative_position']
                fx, fy = frame['preview_mirrored_relative_position']
                width, height = frame['hd2_size']
                if x + fx + width != 0 or y != fy:
                    raise AssertionError('Mirrored rectangle does not preserve image center')
        if raw['matching_frame_counts'] != raw['asset_count']:
            raise AssertionError('Classic and HD2 animation frame counts differ')
        if bound != {name: sha(ROOT / name) for name in own}:
            raise AssertionError('Prototype sources changed during validation')
        if engine != {name: sha(source / name) for name in engine}:
            raise AssertionError('Engine headers changed during validation')
        if samples != {p.name: sha(p) for p in sorted(args.samples.glob('main_*.anim'))}:
            raise AssertionError('Animation inputs changed during validation')
        passed = True
    except Exception as exc:
        error = str(exc)
    report = {'passed': passed, 'scope': 'Original GRP/HD2 geometry comparison and deterministic preview placement only.',
              'source_sha256': bound, 'engine_header_sha256': engine, 'sample_sha256': samples,
              'archive_sha256': archives, 'result': summary, 'steps': steps, 'error': error,
              'limits': ['No retail HD rendering capture is available; placement uses documented ANIM canvas dimensions.',
                         'Software preview floors the unflipped edge once and mirrors the snapped rectangle.',
                         'Frame count agreement does not establish images.rel aliases or every image modifier.']}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'passed': passed, 'report': str(args.report), 'error': error}))
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
