#!/usr/bin/env python3
"""Test optional HD2 pack import using the caller's 31-file sample ZIP and JDK 17."""
import argparse
import hashlib
import json
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


def make_fixtures(work, sample):
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
    parser.add_argument('--report', type=Path, default=ROOT / 'validation/hd2-import.json')
    args = parser.parse_args()
    sources = [JAVA_DIR / 'Hd2SpritePack.java', ROOT / 'tests/Hd2SpritePackTest.java', Path(__file__)]
    source_hashes = {str(p.relative_to(ROOT)): digest(p) for p in sources}
    sample_hash = digest(args.sample_zip)
    with tempfile.TemporaryDirectory(prefix='stardroid-hd2-import-') as tmp:
        work = Path(tmp)
        make_fixtures(work, args.sample_zip)
        subprocess.run([args.java_home / 'bin/javac', '--release', '8', '-encoding', 'UTF-8',
                        '-d', work, *sources[:2]], check=True)
        root = work / 'store'
        root.mkdir()
        run = subprocess.run([args.java_home / 'bin/java', '-cp', work,
                              'org.openbw.androidtest.Hd2SpritePackTest', root,
                              args.sample_zip.resolve(), work], text=True, capture_output=True)
        if run.returncode:
            raise RuntimeError(run.stdout + run.stderr)
    if digest(args.sample_zip) != sample_hash or any(digest(ROOT / k) != v for k, v in source_hashes.items()):
        raise RuntimeError('Inputs changed during validation')
    report = {'passed': True, 'sourceSha256': source_hashes, 'sampleZipSha256': sample_hash,
              'acceptedSampleImageIds': [0, 5, 13, 122, 228], 'result': run.stdout.strip(),
              'scope': 'Production Java ZIP importer on the host JVM with a test validation callback. '
                       'Checks five real sample bodies, six malformed/unsupported archives, failed validation and pointer commit, '
                       'atomic subset replacement and Classic/HD2 persistence across fresh JVM processes. '
                       'The Android picker, native image decode and on-device filesystem are separate checks.'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(run.stdout, end='')


if __name__ == '__main__':
    main()
