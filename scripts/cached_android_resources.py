"""Reuse an exact-match resource cache when a platform's AAPT cannot run.

This is equivalent to retaining an incremental build's unchanged resources.
Every resource input is hashed. Only the typed versionCode in compiled AXML
changes; a changed resource or versionName requires a normal AAPT rebuild.
"""
from pathlib import Path
import hashlib,json,struct,zipfile

def version_manifest(data,code,version):
    data=bytearray(data)
    u16=lambda p:struct.unpack_from('<H',data,p)[0]
    u32=lambda p:struct.unpack_from('<I',data,p)[0]
    assert u16(0)==3 and u32(4)==len(data),'Invalid compiled manifest'
    pos=u16(2);strings=[];changes=0;version_ok=False
    while pos<len(data):
        kind,header,size=struct.unpack_from('<HHI',data,pos)
        assert size>=header and pos+size<=len(data)
        if kind==1:
            count=u32(pos+8);flags=u32(pos+16);start=pos+u32(pos+20)
            for i in range(count):
                at=start+u32(pos+header+4*i)
                if flags&0x100:
                    def length(p):
                        n=data[p];return (((n&127)<<8)|data[p+1],p+2) if n&128 else (n,p+1)
                    _,at=length(at);n,at=length(at);strings.append(bytes(data[at:at+n]).decode('utf-8'))
                else:
                    n=u16(at);at+=2
                    if n&0x8000:n=((n&0x7fff)<<16)|u16(at);at+=2
                    strings.append(bytes(data[at:at+2*n]).decode('utf-16-le'))
        elif kind==0x102:
            ext=pos+header;name=u32(ext+4)
            if strings[name]=='manifest':
                at=ext+u16(ext+8);stride=u16(ext+10);count=u16(ext+12)
                assert stride==20
                for i in range(count):
                    p=at+stride*i;namespace=u32(p);key=strings[u32(p+4)]
                    if namespace==0xffffffff or strings[namespace]!='http://schemas.android.com/apk/res/android':continue
                    if key=='versionCode':
                        assert data[p+15] in (0x10,0x11) and u32(p+8)==0xffffffff
                        struct.pack_into('<I',data,p+16,code);changes+=1
                    elif key=='versionName':
                        assert data[p+15]==3
                        version_ok=strings[u32(p+16)]==version
        pos+=size
    assert changes==1 and version_ok,'Cache cannot supply this version metadata'
    return bytes(data)

def prepare(root:Path,build:Path,code:int,version:str):
    cache=root/'vendor/android-resource-cache'
    manifest=json.loads((cache/'source-sha256.json').read_text(encoding='utf-8'))
    inputs={p.relative_to(root).as_posix() for p in (root/'app/src/main/res').rglob('*') if p.is_file()}
    inputs.add('app/src/main/AndroidManifest.xml')
    assert inputs==set(manifest['inputs']),'Resource files were added or removed; run AAPT'
    for path,digest in manifest['inputs'].items():
        assert hashlib.sha256((root/path).read_bytes()).hexdigest()==digest,'Resource changed; run AAPT: '+path
    pack=cache/'resources.apk'
    assert hashlib.sha256(pack.read_bytes()).hexdigest()==manifest['cacheSha256'],'Resource cache checksum mismatch'
    with zipfile.ZipFile(pack) as source,zipfile.ZipFile(build/'resources.apk','w',compression=zipfile.ZIP_DEFLATED) as dest:
        for item in source.infolist():
            data=source.read(item)
            if item.filename=='AndroidManifest.xml':data=version_manifest(data,code,version)
            dest.writestr(item.filename,data,compress_type=item.compress_type)
    rjava=cache/'R.java'
    assert hashlib.sha256(rjava.read_bytes()).hexdigest()==manifest['rJavaSha256']
    output=build/'generated/org/openbw/androidtest/R.java';output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(rjava.read_bytes())
    print('PASS exact-match Android resource cache; versionCode updated to',code,flush=True)

def align_zip(source:Path,destination:Path):
    """Write standard ZIP padding; SDK zipalign -c independently verifies it."""
    with zipfile.ZipFile(source) as incoming,zipfile.ZipFile(destination,'w') as outgoing:
        for entry in incoming.infolist():
            data=incoming.read(entry)
            entry.extra=b''
            if entry.compress_type==zipfile.ZIP_STORED:
                alignment=16384 if entry.filename.startswith('lib/') and entry.filename.endswith('.so') else 4
                offset=outgoing.fp.tell()+30+len(entry.filename.encode('utf-8'))
                # Android's 0xd935 field contains a uint16 alignment value
                # before the padding. Omitting it tells apksigner to ignore
                # the library's alignment if an earlier entry changes size.
                padding=(-offset-6)%alignment
                entry.extra=struct.pack('<HHH',0xd935,2+padding,alignment)+bytes(padding)
            outgoing.writestr(entry,data)
