#!/usr/bin/env python3
"""Exercise APK ZIP alignment across different payload/header remainders."""
from pathlib import Path
import importlib.util, struct, tempfile, zipfile

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('cache', root/'scripts/cached_android_resources.py')
cache = importlib.util.module_from_spec(spec); spec.loader.exec_module(cache)
base = root/'build-native'; base.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(dir=base, prefix='alignment87-') as tmp:
    source, output = Path(tmp)/'input.apk', Path(tmp)/'aligned.apk'
    checks = 0
    for size in range(65):
        entries = {'prefix.dat': bytes(size), 'resources.arsc': b'resources',
                   'lib/arm64-v8a/libtest.so': bytes(123), 'lib/x86_64/libtest.so': bytes(127)}
        with zipfile.ZipFile(source, 'w') as z:
            for name, data in entries.items(): z.writestr(name, data)
        cache.align_zip(source, output)
        raw = output.read_bytes()
        with zipfile.ZipFile(output) as z:
            for entry in z.infolist():
                align = 16384 if entry.filename.endswith('.so') else 4
                name_len, extra_len = struct.unpack_from('<HH', raw, entry.header_offset+26)
                extra_at = entry.header_offset+30+name_len
                assert (extra_at+extra_len) % align == 0
                header, length, declared = struct.unpack_from('<HHH', raw, extra_at)
                assert header == 0xd935 and length+4 == extra_len and declared == align
                assert z.read(entry) == entries[entry.filename]
                checks += 1
    print('PASS', checks, 'ZIP alignment/metadata/content checks across 65 payload sizes')
