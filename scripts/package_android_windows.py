#!/usr/bin/env python3
"""Package on Windows when the Gradle AAPT daemon is unavailable.

Uses the same SDK tools, Java sources, resource compiler, D8 and APK signer.
Set ANDROID_HOME (SDK 35), JAVA_HOME (JDK 17) and the five STARDROID signing
variables documented in app/build.gradle. Rebuild native libraries first.
"""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys,zipfile
root=Path(__file__).resolve().parents[1]
java=Path(os.environ['JAVA_HOME'])/'bin'
sdk=Path(os.environ['ANDROID_HOME']);bt=sdk/'build-tools/35.0.0'
android=sdk/'platforms/android-35/android.jar'
build=root/'build-native/windows-apk';build.mkdir(parents=True,exist_ok=True)
config=(root/'app/build.gradle').read_text(encoding='utf-8')
code=re.search(r'versionCode (\d+)',config)[1]
version=re.search(r"versionName '([^']+)'",config)[1]
for folder in ('compiled','generated','classes','dex'): (build/folder).mkdir(exist_ok=True)
env=os.environ.copy()
def run(*args): subprocess.run(list(map(str,args)),cwd=root,env=env,check=True)
if '--resource-cache' in sys.argv:
    from cached_android_resources import prepare
    prepare(root,build,int(code),version)
else:
    for resource in sorted((root/'app/src/main/res').rglob('*')):
        if resource.is_file():run(bt/'aapt2.exe','compile','--no-crunch','-o',build/'compiled',resource)
    run(bt/'aapt2.exe','link','-o',build/'resources.apk','-I',android,
        '--manifest',root/'app/src/main/AndroidManifest.xml','--java',build/'generated',
        '--auto-add-overlay','--min-sdk-version','26','--target-sdk-version','35',
        '--version-code',code,'--version-name',version,*sorted((build/'compiled').glob('*.flat')))
cfg=build/'generated/org/openbw/androidtest/BuildConfig.java';cfg.parent.mkdir(parents=True,exist_ok=True)
cfg.write_text('package org.openbw.androidtest; public final class BuildConfig {'
    'public static final boolean DEBUG=false;public static final String APPLICATION_ID="org.openbw.androidtest",BUILD_TYPE="release",'
    'VERSION_NAME='+json.dumps(version)+';public static final int VERSION_CODE='+code+';}\n',encoding='utf-8')
sources=sorted((root/'app/src/main/java').rglob('*.java'))+sorted((build/'generated').rglob('*.java'))
argfile=build/'sources.txt';argfile.write_text('\n'.join('"'+p.as_posix()+'"' for p in sources),encoding='utf-8')
run(java/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',build/'classes','@'+str(argfile))
run(java/'jar.exe','cf',build/'classes.jar','-C',build/'classes','.')
run(java/'java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',android,'--output',build/'dex',build/'classes.jar')
unsigned=build/'unsigned.apk';shutil.copyfile(build/'resources.apk',unsigned)
with zipfile.ZipFile(unsigned,'a') as z:
    for p in sorted((build/'dex').glob('*.dex')):z.write(p,p.name,compress_type=zipfile.ZIP_DEFLATED)
    for group in ('native','missions'):
        for p in sorted((root/'app/src/main/assets'/group).rglob('*')):
            if p.is_file():z.write(p,'assets/'+p.relative_to(root/'app/src/main/assets').as_posix(),compress_type=zipfile.ZIP_DEFLATED)
    for name in ('COPYING','NOTICE.txt'):z.write(root/'vendor/libsmacker'/name,'assets/libsmacker-'+name,compress_type=zipfile.ZIP_DEFLATED)
    for abi in ('arm64-v8a','x86_64'):
        for name in ('libopenbw.so','libsmacker.so'):z.write(root/'native-libs'/abi/name,'lib/'+abi+'/'+name,compress_type=zipfile.ZIP_STORED)
aligned=build/'aligned.apk'
if '--resource-cache' in sys.argv:
    from cached_android_resources import align_zip
    align_zip(unsigned,aligned)
    run(bt/'zipalign.exe','-c','-P','16','4',aligned)
else:
    run(bt/'zipalign.exe','-f','-P','16','4',unsigned,aligned)
out=root/'app/build/outputs/apk/release';out.mkdir(parents=True,exist_ok=True)
final=out/'app-release.apk'
required=('STARDROID_KEYSTORE','STARDROID_STORE_PASSWORD','STARDROID_KEY_ALIAS','STARDROID_KEY_PASSWORD')
if any(not env.get(k) for k in required):raise SystemExit('Release signing environment is incomplete')
run(java/'java.exe','-jar',bt/'lib/apksigner.jar','sign','--ks',env['STARDROID_KEYSTORE'],
    '--ks-key-alias',env['STARDROID_KEY_ALIAS'],'--ks-pass','env:STARDROID_STORE_PASSWORD','--key-pass','env:STARDROID_KEY_PASSWORD','--out',final,aligned)
run(java/'java.exe','-jar',bt/'lib/apksigner.jar','verify','--verbose',final)
run(bt/'zipalign.exe','-c','-P','16','4',final)
print('PASS signed APK:',final,'SHA256:',hashlib.sha256(final.read_bytes()).hexdigest())
