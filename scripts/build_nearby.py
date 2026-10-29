#!/usr/bin/env python3
"""Build/sign the local multiplayer APK without Google Play services dependencies."""
import urllib.parse
import hashlib, os, pathlib, re, shutil, subprocess, sys, urllib.request, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
TOOLS=pathlib.Path(os.environ.get('STARDROID_ANDROID_TOOLS',os.environ.get('OPENBW_ANDROID_TOOLS',ROOT/'.tools'))).resolve()
def run(*args): subprocess.run([str(x) for x in args],cwd=ROOT,check=True)
def fetch(name,url,sha):
    path=TOOLS/name; TOOLS.mkdir(parents=True,exist_ok=True)
    if not path.exists(): urllib.request.urlretrieve(url,path)
    if hashlib.sha256(path.read_bytes()).hexdigest()!=sha: raise RuntimeError('Checksum mismatch: '+name)
    return path
gradle=TOOLS/'gradle-8.9'
if not gradle.exists():
    archive=fetch('gradle-8.9-bin.zip','https://downloads.gradle.org/distributions/gradle-8.9-bin.zip','d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab')
    with zipfile.ZipFile(archive) as z: z.extractall(TOOLS)
(gradle/'bin/gradle').chmod(0o755)
sdk=TOOLS/'sdk'; sdk.mkdir(exist_ok=True)
for parent,name,source in [('platforms','android-35','android-35'),('build-tools','35.0.0','android-15')]:
    dest=sdk/parent/name; dest.parent.mkdir(exist_ok=True)
    if not dest.exists(): dest.symlink_to(TOOLS/source,target_is_directory=True)
(ROOT/'local.properties').write_text('sdk.dir='+str(sdk)+'\n')
if '--skip-engine' not in sys.argv:
    run(sys.executable,ROOT/'scripts/prepare_native.py')
    for abi in ['arm64-v8a','x86_64']:run(sys.executable,ROOT/'scripts/build_native_engine.py',abi,'--skip-prepare')
assets=ROOT/'build-native/apk-assets'; shutil.rmtree(assets,ignore_errors=True); assets.mkdir(parents=True)
for name in ['native','missions']: shutil.copytree(ROOT/'app/src/main/assets'/name,assets/name)
for name in ['COPYING','NOTICE.txt']: shutil.copyfile(ROOT/'vendor/libsmacker'/name,assets/('libsmacker-'+name))
proxy=[]
for scheme in ['http','https']:
    u=urllib.parse.urlparse(os.environ.get(scheme.upper()+'_PROXY',''))
    if u.hostname:proxy.extend(['-D'+scheme+'.proxyHost='+u.hostname,'-D'+scheme+'.proxyPort='+str(u.port or 80)])
run(gradle/'bin/gradle',*proxy,'--no-daemon','--console=plain','clean','assembleRelease',*(['--offline'] if '--offline' in sys.argv else []),*(['lintRelease'] if '--lint' in sys.argv else []))
apk=ROOT/'app/build/outputs/apk/release/app-release.apk'
run('java','-jar',TOOLS/'android-15/lib/apksigner.jar','verify','--verbose','--print-certs',apk)
run(TOOLS/'android-15/zipalign','-c','-P','16','4',apk)
version=re.search(r"versionName '([^']+)'",(ROOT/'app/build.gradle').read_text(encoding='utf-8'))[1]
out=ROOT/'dist';out.mkdir(exist_ok=True);target=out/('StarDroid-'+version+'.apk');shutil.copyfile(apk,target)
(out/'SHA256SUMS.txt').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
print(target)
