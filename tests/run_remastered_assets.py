#!/usr/bin/env python3
"""Build and exercise the standalone Remastered reader without game data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cxx', default=os.environ.get('CXX', 'g++'))
    parser.add_argument('--sanitize', action='store_true')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'build-native/remastered-assets')
    parser.add_argument('--report', type=Path, default=ROOT / 'validation/remastered-assets.json')
    args = parser.parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    sources = ['native/remastered/assets.h', 'tests/native_remastered_assets.cpp',
               'tools/remastered_inspect.cpp', 'tests/run_remastered_assets.py']
    before = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in sources}
    commands = []
    logs = []
    environment = os.environ.copy()
    if args.sanitize:
        # LeakSanitizer cannot run under the workspace's ptrace supervisor.
        # Address/UB checks still fail the run; leak coverage is not claimed.
        environment['ASAN_OPTIONS'] = environment.get('ASAN_OPTIONS', '') + ':detect_leaks=0:halt_on_error=1'
        environment['UBSAN_OPTIONS'] = environment.get('UBSAN_OPTIONS', '') + ':halt_on_error=1:print_stacktrace=1'

    def run(command, expected=0):
        commands.append([str(value) for value in command])
        result = subprocess.run(commands[-1], cwd=ROOT, capture_output=True, text=True, timeout=180, env=environment)
        logs.append({'command': commands[-1], 'exit_code': result.returncode,
                     'stdout': result.stdout, 'stderr': result.stderr})
        if result.returncode != expected:
            raise RuntimeError('Command failed: ' + ' '.join(commands[-1]) + '\n' + result.stdout + result.stderr)
        return result

    flags = ['-std=c++17', '-Wall', '-Wextra', '-Werror', '-pedantic']
    flags += ['-O1', '-g', '-fsanitize=address,undefined', '-fno-omit-frame-pointer'] if args.sanitize else ['-O2']
    passed = False
    error = None
    try:
        run([args.cxx, *flags, ROOT / sources[1], '-o', output / 'native-remastered-assets'])
        run([args.cxx, *flags, ROOT / sources[2], '-o', output / 'remastered-inspect'])
        run([output / 'native-remastered-assets'])
        with tempfile.TemporaryDirectory(prefix='synthetic-', dir=output) as temp:
            temp = Path(temp)
            fixture, preview = temp / 'fixture.anim', temp / 'frame.pam'
            run([output / 'native-remastered-assets', '--write-fixture', fixture])
            original = fixture.read_bytes()
            result = run([output / 'remastered-inspect', fixture, '--frame', '0', '--preview', preview])
            info = json.loads(result.stdout)
            assert info['format'] == 'ANIM' and info['scale'] == 2 and info['frame_count'] > 0
            assert info['renderer_integrated'] is False and info['decoded_pixels'] > 0
            raw = preview.read_bytes()
            header, rgba = raw.split(b'ENDHDR\n', 1)
            assert header.startswith(b'P7\n') and b'TUPLTYPE RGB_ALPHA' in header
            assert len(rgba) == info['decoded_pixels'] * 4
            run([output / 'remastered-inspect', fixture, '--team-color', 'ff0000'])
            for tail in [['--frame', '-1'], ['--frame', '999999999999999999999999999999'],
                         ['--team-color', 'xxyyzz'], ['--frame', str(info['frame_count'])],
                         ['--preview', str(fixture)], ['--unknown', 'value']]:
                run([output / 'remastered-inspect', fixture, *tail], expected=2)
            assert fixture.read_bytes() == original, 'Inspection altered its input'
            broken = temp / 'truncated.anim'
            broken.write_bytes(original[:17])
            run([output / 'remastered-inspect', broken], expected=2)
            blank = bytearray(original)
            frame_offset = struct.unpack_from('<I', blank, 0x154)[0]
            struct.pack_into('<HH', blank, frame_offset + 8, 0, 0)
            blank_file, blank_preview = temp / 'blank.anim', temp / 'blank.pam'
            blank_file.write_bytes(blank)
            result = run([output / 'remastered-inspect', blank_file])
            assert json.loads(result.stdout)['decoded_pixels'] == 0
            run([output / 'remastered-inspect', blank_file, '--preview', blank_preview], expected=2)
            assert not blank_preview.exists(), 'An empty frame produced an invalid PAM'
        after = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in sources}
        assert before == after, 'Sources changed during validation'
        passed = True
    except Exception as exc:
        error = str(exc)
    report = {'passed': passed, 'sanitizers': args.sanitize, 'leak_check': False, 'source_sha256': before,
              'scope': 'Synthetic ANIM/DDS reader and developer CLI checks only; no licensed assets, Android device, CASC or game renderer validation.',
              'checks': logs, 'error': error}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'passed': passed, 'sanitizers': args.sanitize, 'commands': len(commands), 'report': str(args.report), 'error': error}))
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
