#!/usr/bin/env python3
"""Verify the HD2 sample catalog against caller-owned retail archives and test pack replacement."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = 'main_{:03d}.anim'


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as file:
        for block in iter(lambda: file.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('stardat', 'broodat', 'patch', 'samples'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--report', type=Path, default=ROOT/'validation/hd2-catalog3.json')
    parser.add_argument('--cxx', default=os.environ.get('CXX', 'g++'))
    args = parser.parse_args()
    samples = args.samples.resolve()
    archives = [args.stardat, args.broodat, args.patch]
    archive_hashes = {p.name: digest(p) for p in archives}
    input_hashes = {p.name: digest(p) for p in sorted(samples.glob('main_*.anim'))}
    paths = [ROOT/'tests/native_hd2_catalog3.cpp', Path(__file__).resolve(),
             *(ROOT/'native/remastered'/name for name in ('assets.h', 'geometry.h', 'runtime.h'))]
    # Include every prepared top-level engine header used by the classic MPQ/DAT reader.
    paths += sorted((ROOT/'native-engine').glob('*.h'))
    bound = {str(p.relative_to(ROOT)): digest(p) for p in paths}
    with tempfile.TemporaryDirectory(prefix='hd2-catalog3-') as work:
        tmp = Path(work)
        exe = tmp/'catalog'
        subprocess.run([args.cxx, '-std=c++17', '-O2', '-Wall', '-Wextra', '-Werror',
                        '-isystem', ROOT/'native-engine', ROOT/'tests/native_hd2_catalog3.cpp', '-o', exe], check=True)
        old, invalid, oversize = (tmp / d for d in ('previous-five', 'invalid', 'over-budget'))
        for directory in (old, invalid, oversize):
            directory.mkdir()
        for i in (0, 5, 13, 122, 228):
            shutil.copyfile(samples / BASE.format(i), old / BASE.format(i))
        # A malformed late member must preserve the previously selected generation.
        shutil.copyfile(samples / BASE.format(0), invalid / BASE.format(0))
        (invalid / BASE.format(893)).write_bytes(b'ANIM')
        for name in input_hashes:
            shutil.copyfile(samples/name, oversize/name)
        # Keep one valid frame of the large Dragoon atlas under a one-frame ID.
        # This exercises the decoded budget independently of the container byte limit.
        large = bytearray((samples / BASE.format(122)).read_bytes())
        struct.pack_into('<H', large, 0x14c, 1)
        (oversize / BASE.format(893)).write_bytes(large)
        out = subprocess.check_output([exe, *archives, samples, old, invalid, oversize], text=True, timeout=120)
    rows = []
    for line in out.splitlines():
        if line.startswith('IMAGE '):
            i, mod, frames, width, height, texels = map(int, line.split()[1:])
            rows.append(dict(imageId=i, modifier=mod, frames=frames, classicCanvas=[width, height], atlasTexels=texels))
    total, shadows, used, budget, old_bytes = map(int, out.split('RESULT ')[1].split())
    supported = {BASE.format(row['imageId']) for row in rows}
    expected_exclusions = {BASE.format(i) for i in (361,508)}
    assert len(rows) == total == 29 and shadows == 9
    assert set(input_hashes)-supported == expected_exclusions and supported <= set(input_hashes)
    assert bound == {str(p.relative_to(ROOT)): digest(p) for p in paths}
    assert input_hashes == {p.name: digest(p) for p in sorted(samples.glob('main_*.anim'))}
    assert archive_hashes == {p.name: digest(p) for p in archives}
    report = dict(passed=True, scope='Caller-owned classic image table and GRP dimensions, real HD2 samples, '
                  'partial-pack compatibility and rejection of bad or over-budget replacements. Rendering is tested separately.',
                  sourceSha256=bound, archiveSha256=archive_hashes, sampleSha256=input_hashes,
                  supported=rows, excludedPaletteRemaps=[361,508], residentBytes=used, maxResidentPayload=budget,
                  previousFiveResidentBytes=old_bytes, previousFiveAccepted=True, atomicFailureChecks=True)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2)+'\n')
    print(f'PASS {total-shadows} normal images, {shadows} shadows, {used} cache bytes, previous import and rollback checks')


if __name__ == '__main__':
    main()
