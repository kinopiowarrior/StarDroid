#!/usr/bin/env python3
"""Reproduce native engine input from the pinned source and reviewed campaign patch."""
import hashlib, json, pathlib, re, subprocess, tarfile, tempfile, shutil
ROOT = pathlib.Path(__file__).resolve().parents[1]

def extract_source_archive(archive, destination):
    """Extract only ordinary source files into a newly created directory.

    Avoid tarfile's realpath-based data filter: some managed Windows hosts
    allow reading/writing a new directory but deny querying its final path.
    Reject links and unsafe path components before creating anything instead.
    The source archive's pinned SHA-256 is also verified before this runs.
    """
    reserved = {'CON', 'PRN', 'AUX', 'NUL', 'CONIN$', 'CONOUT$'}
    reserved.update('COM' + digit for digit in '123456789')
    reserved.update('LPT' + digit for digit in '123456789')
    with tarfile.open(archive) as source:
        for member in source:
            name = member.name
            path = pathlib.PurePosixPath(name)
            if (path.is_absolute() or '\\' in name or '\0' in name
                    or any(part == '..' for part in path.parts)):
                raise ValueError('Unsafe source archive path: ' + repr(name))
            for part in path.parts:
                if (':' in part or part.endswith(('.', ' '))
                        or part.split('.')[0].upper() in reserved):
                    raise ValueError('Unsafe source archive component: ' + repr(name))
            if not path.parts:
                if member.isdir():
                    continue
                raise ValueError('Empty source archive file path')
            if not (member.isdir() or member.isfile()):
                raise ValueError('Unsupported source archive entry: ' + repr(name))
            target = destination.joinpath(*path.parts)
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with source.extractfile(member) as incoming, target.open('xb') as outgoing:
                shutil.copyfileobj(incoming, outgoing)
            target.chmod((member.mode & 0o755) | 0o600)
def normalized_patch_counts(text):
    """Repair historical hand-edited hunk counts for Git's stricter parser."""
    lines = text.splitlines(keepends=True)
    for index, line in enumerate(lines):
        match = re.match(r'^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*?)(?:\r?\n)?$', line)
        if not match:
            continue
        old_count = new_count = 0
        for offset in range(index + 1, len(lines)):
            following = lines[offset]
            if following.startswith(('@@ ', 'diff ')):
                break
            if (following.startswith('--- ') and offset + 1 < len(lines)
                    and lines[offset + 1].startswith('+++ ')):
                break
            if following.startswith((' ', '-')):
                old_count += 1
            if following.startswith((' ', '+')):
                new_count += 1
        lines[index] = ('@@ -%s,%d +%s,%d @@%s\n'
                        % (match[1], old_count, match[2], new_count, match[3]))
    return ''.join(lines)

def apply_source_patch(directory, name):
    patch_path = ROOT / 'patches' / name
    patch_program = shutil.which('patch')
    if patch_program:
        subprocess.run([patch_program, '--batch', '--forward', '-p1', '-i', str(patch_path)],
                       cwd=directory, check=True, stdout=subprocess.DEVNULL)
        return
    git_program = shutil.which('git')
    if not git_program:
        raise RuntimeError('Preparing native sources requires GNU patch or Git')
    # GNU patch tolerates two mismatched context lines. Git needs an explicit
    # one-line minimum for the same historical std::min context mismatch.
    data = normalized_patch_counts(patch_path.read_text(encoding='utf-8')).encode('utf-8')
    subprocess.run([git_program, 'apply', '--no-index', '--whitespace=nowarn', '-C1', '-p1', '-'],
                   input=data, cwd=directory, check=True, stdout=subprocess.DEVNULL)

def write(path,text):
    if not path.exists() or path.read_text()!=text:path.write_text(text)
stage = ROOT / 'native-engine'
up = json.loads((ROOT/'UPSTREAM.json').read_text())
assert hashlib.sha256((ROOT/'patches/campaign.patch').read_bytes()).hexdigest() == json.loads((ROOT/'CAMPAIGN-BUILD.json').read_text())['patchSha256']
archive = ROOT/up['source_archive']['file']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == up['source_archive']['sha256']
# Reconstruct the ordered patch stack from pinned input. Later fixes may touch
# earlier patch context, so reverse-dry-run detection is no longer sufficient.
# Promote changed bytes only, keeping incremental builds fast.
from prepare_presentation import prepare as presentation
from prepare_feedback import prepare as feedback
stage.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='openbw-native-source-') as temp:
    fresh = pathlib.Path(temp)
    extract_source_archive(archive, fresh)
    for name in ('campaign.patch', 'native-gameplay.patch'):
        apply_source_patch(fresh, name)
    presentation(fresh)
    feedback(fresh)
    for name in ('native-revision.patch', 'native-missions.patch', 'native-polish33.patch', 'native-saves34.patch', 'native-campaign36.patch', 'native-finale37.patch', 'native-controls38.patch', 'native-polish39.patch', 'native-polish40.patch', 'native-polish41.patch', 'native-polish42.patch', 'native-ai43.patch', 'native-polish44.patch', 'native-ai45.patch', 'native-polish46.patch', 'native-polish54.patch', 'native-polish55.patch', 'native-polish56.patch', 'native-polish57.patch', 'native-polish58.patch', 'native-polish59.patch', 'native-upgrades1.patch', 'native-placement68.patch', 'native-scroll69.patch', 'native-polish77.patch', 'native-polish78.patch', 'native-campaign79.patch', 'native-unit-names79.patch', 'native-fog-target79.patch', 'native-ai-abilities79.patch', 'native-controls80.patch', 'native-ai-production80.patch', 'native-supply81.patch', 'native-addon-sound81.patch', 'native-replay82.patch', 'native-map-invincibility11.patch', 'native-score-names11.patch', 'native-bunker-ai11.patch', 'native-nukes11.patch', 'native-wireframes11.patch', 'native-hero-rescue11.patch', 'native-score-slots11.patch', 'native-desktop11.patch', 'native-hotkeys11.patch', 'native-assimilator11.patch', 'native-detectors11.patch'):
        apply_source_patch(fresh, name)
    for source in fresh.rglob('*'):
        if not source.is_file(): continue
        target = stage / source.relative_to(fresh)
        if not target.exists() or target.read_bytes() != source.read_bytes():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source,target)
# Native builds retain the same entry points but use real pointers and exceptions.
f=stage/'web/sandbox.h'
write(f,f.read_text().replace('std::min(200,st.supply_available[u->owner][ri].raw_value / 2)', 'std::min<int>(200,st.supply_available[u->owner][ri].raw_value / 2)'))
out=ROOT/'native/generated';out.mkdir(parents=True,exist_ok=True)
for name in ['wasm_main.cpp','wasm_backend.cpp']:
    text=(stage/'web'/name).read_text()
    text=re.sub(r'__attribute__\(\(import_module\("env"\), import_name\("[^"]+"\)\)\)', '', text)
    text=text.replace('__attribute__((export_name(#name))) ', '')
    text=text.replace('fprintf(stderr, "openbw fatal: %s\\n", str.c_str()); abort();','throw std::runtime_error(str.c_str());')
    write(out/name,text)
print('Native engine source prepared')
# Retry deferred creep cleanup when the engine's bounded recession pool is full.
# Keep the dead provider alive until its remaining tiles have been queued.
p=stage/'bwgame.h';s=p.read_text();s=s.replace('error("update_dead_unit: fixme some creep stuff?");','if (!remove_creep_provider(u)) return;\n\t\t\tu_unset_status_flag(u, unit_t::status_flag_4000);');write(p,s)
# Native HUD owns a fixed 128px minimap, independently of the source map size.
p=stage/'ui/ui.h';s=p.read_text();anchor='\t\tif (minimap_scale > 0 && minimap_scale != 1) {'
if 'OPENBW_NATIVE_MINIMAP' not in s:s=s.replace(anchor,'\t\t// OPENBW_NATIVE_MINIMAP\n\t\tconst size_t largest = std::max(minimap_width, minimap_height);\n\t\tminimap_width = (minimap_width * 128 + largest / 2) / largest;\n\t\tminimap_height = (minimap_height * 128 + largest / 2) / largest;\n'+anchor,1)
write(p,s)
