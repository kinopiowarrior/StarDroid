#!/usr/bin/env python3
"""Compile the Android C++ engine, original-asset decoders, and computer opponent."""
import pathlib,subprocess,os,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
os.environ.setdefault('SOURCE_DATE_EPOCH','1791849600')
abi=sys.argv[1] if len(sys.argv)>1 else 'arm64-v8a'
triple={'arm64-v8a':'aarch64-linux-android','x86_64':'x86_64-linux-android'}[abi]
tools=pathlib.Path(os.environ.get('STARDROID_ANDROID_TOOLS',os.environ.get('OPENBW_ANDROID_TOOLS',ROOT/'.tools')))
ndk=tools/'android-ndk-r27d';bin=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin';cxx=bin/(triple+'26-clang++');cc=bin/(triple+'26-clang')
# Materialized SDK files may lose executable mode bits; restore the tool directory.
for executable in bin.iterdir():
    if executable.is_file() and not os.access(executable, os.X_OK):
        executable.chmod(executable.stat().st_mode | 0o111)
stage=ROOT/'native-engine';out=ROOT/'native-libs'/abi;out.mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(x) for x in args],check=True,cwd=ROOT)
# Prepare once before parallel ABI builds; normal standalone builds still prepare.
if '--skip-prepare' not in sys.argv:run('python3',ROOT/'scripts/prepare_native.py')
base=[cxx,'-std=c++17','-O2','-fPIC','-Wno-deprecated-declarations']
run(cc,'-O2','-fPIC','-shared',ROOT/'vendor/libsmacker/smacker.c','-Wl,-soname,libsmacker.so','-Wl,-z,max-page-size=16384','-o',out/'libsmacker.so')
run(*base,'-shared','-static-libstdc++','-DOPENBW_ENABLE_UI','-DOPENBW_NO_SDL_IMAGE','-DOPENBW_NO_SDL_MIXER','-I'+str(stage),'-I'+str(stage/'ui'),'-I'+str(stage/'web'),ROOT/'native/runtime.cpp',ROOT/'native/generated/wasm_backend.cpp','-L'+str(out),'-lsmacker','-ljnigraphics','-ldl','-Wl,-z,max-page-size=16384','-Wl,-soname,libopenbw.so','-o',out/'libopenbw.so')
run(bin/'llvm-strip','--strip-unneeded',out/'libopenbw.so',out/'libsmacker.so')
print('Native libraries ready:',abi)
