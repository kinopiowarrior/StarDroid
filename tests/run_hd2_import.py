#!/usr/bin/env python3
"""Test optional HD2 pack import using the caller's 31-file sample ZIP and JDK 17."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA_DIR = ROOT / 'app/src/main/java/org/openbw/androidtest'
BODY = 'HD2/anim/main_000.anim'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def make_fixtures(work, sample, image_ids):
    with zipfile.ZipFile(sample) as source:
        body = source.read(BODY)
        dragoon_name = 'HD2/anim/main_122.anim'
        dragoon = source.read(dragoon_name)

    def write(name, entries, compression=zipfile.ZIP_DEFLATED):
        with zipfile.ZipFile(work / (name + '.zip'), 'w', compression=compression) as output:
            for path, data in entries:
                output.writestr(path, data)

    # These private files exist only for this test. The second valid pack uses a
    # case-varied canonical path to test case folding during import.
    write('subset', [(dragoon_name.upper(), dragoon)])
    write('unsupported', [('anim/main_000.anim', body)])
    write('unmapped', [('HD2/anim/main_361.anim', body), ('HD2/anim/main_508.anim', body)])
    block = bytes(16 * 1024**2)
    write('aggregate', [(f'HD2/anim/main_{i:03}.anim', block if count < 8 else b'x')
                          for count, i in enumerate(image_ids[:9])])
    write('traversal', [(BODY, body), ('HD2/anim/../../escape.anim', body)])
    with warnings.catch_warnings():
        warnings.simplefilter('ignore', UserWarning)
        write('duplicate', [(BODY, body), (BODY, body)])
    write('oversize', [(BODY, body + bytes(16 * 1024**2 + 1 - len(body)))])
    write('crc', [(BODY, body)], zipfile.ZIP_STORED)
    corrupt = bytearray((work / 'crc.zip').read_bytes())
    name_length, extra_length = struct.unpack_from('<HH', corrupt, 26)
    corrupt[30 + name_length + extra_length + 30] ^= 1
    (work / 'crc.zip').write_bytes(corrupt)
    (work / 'truncated.zip').write_bytes(sample.read_bytes()[:50000])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sample-zip', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--cxx', default=os.environ.get('CXX', 'c++'))
    parser.add_argument('--report', type=Path, default=ROOT / 'validation/hd2-import.json')
    args = parser.parse_args()
    sources = [JAVA_DIR / 'Hd2SpritePack.java', ROOT / 'tests/Hd2SpritePackTest.java', Path(__file__),
               JAVA_DIR / 'MainActivity.java', JAVA_DIR / 'NativeBridge.java', ROOT / 'native/runtime.cpp',
               *[ROOT / 'native/remastered' / header for header in ('runtime.h', 'geometry.h', 'assets.h')]]
    source_hashes = {str(p.relative_to(ROOT)): digest(p) for p in sources}
    sample_hash = digest(args.sample_zip)
    with tempfile.TemporaryDirectory(prefix='stardroid-hd2-import-') as tmp:
        work = Path(tmp)
        # The Java installer consumes image IDs from the native catalog via JNI on Android.
        # Call that compiled catalog directly for the equivalent JVM fixture.
        probe = r'''#include "runtime.h"
#include <iostream>
int main() {
    for (const auto& e : stardroid::remastered::live::bodies) std::cout << e.id << "\n";
}
'''
        subprocess.run([args.cxx, '-std=c++17', '-I'+str(ROOT/'native/remastered'),
                        '-x','c++','-','-o',work/'catalog'], input=probe, text=True, check=True)
        ids = [int(x) for x in subprocess.check_output([work/'catalog'],text=True).splitlines()]
        with zipfile.ZipFile(args.sample_zip) as source:
            sample_ids = {int(n[14:17]) for n in source.namelist() if n.startswith('HD2/anim/main_') and n.endswith('.anim')}
        if len(ids) != len(set(ids)) or set(ids) != sample_ids - {361, 508}:
            raise ValueError('Unexpected native sprite catalog or sample ZIP')
        make_fixtures(work, args.sample_zip, ids)
        subprocess.run([args.java_home / 'bin/javac', '--release', '8', '-encoding', 'UTF-8',
                        '-d', work, *sources[:2]], check=True)
        root = work / 'store'
        root.mkdir()
        run = subprocess.run([args.java_home / 'bin/java', '-cp', work,
                              'org.openbw.androidtest.Hd2SpritePackTest', root,
                              args.sample_zip.resolve(), work, ','.join(f'{i:03}' for i in ids)], text=True, capture_output=True)
        if run.returncode:
            raise RuntimeError(run.stdout + run.stderr)
    if digest(args.sample_zip) != sample_hash or any(digest(ROOT / k) != v for k, v in source_hashes.items()):
        raise RuntimeError('Inputs changed during validation')
    report = {'passed': True, 'sourceSha256': source_hashes, 'sampleZipSha256': sample_hash,
              'acceptedSampleImageIds': ids, 'result': run.stdout.strip(),
              'scope': 'Production Java ZIP importer on the host JVM with a test validation callback. '
                       'Checks preview 2 migration, extraction of every supported entry from the original ZIP, eight malformed/unsupported archives, failed validation and pointer commit, '
                       'atomic subset replacement and Classic/HD2 persistence across fresh JVM processes. '
                       'The Android picker, native image decode and on-device filesystem are separate checks.'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(run.stdout, end='')


if __name__ == '__main__':
    main()
