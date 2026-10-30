"""Constrain keyboard frames and width metrics in the target Android16 services JAR."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]).resolve()
work = source.parent / (source.stem + '-rail-insets')
work.mkdir(exist_ok=True)
smali = Path(os.environ['DEX_TOOLS'])
bin_dir = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
def run(*args):
    subprocess.run([str(a) for a in args], check=True)

target = 'Lcom/android/server/wm/WindowManagerService;'
with zipfile.ZipFile(source) as jar:
    entries = []
    for name in jar.namelist():
        if not name.endswith('.dex') or target.encode() not in jar.read(name):
            continue
        candidate = work / name
        candidate.write_bytes(jar.read(name))
        classes = subprocess.check_output([str(bin_dir / ('java' + suffix)), '-jar',
            str(smali / 'baksmali.jar'), 'list', 'classes', str(candidate)], text=True)
        if target in classes.splitlines():
            entries.append(name)
    assert len(entries) == 1
    entry = entries[0]
    dex = work / entry
    dex.write_bytes(jar.read(entry))
run(bin_dir / ('java' + suffix), '-jar', smali / 'baksmali.jar', 'disassemble', dex,
    '--classes', target, '-o', work / 'wm-smali')
p = work / 'wm-smali/com/android/server/wm/WindowManagerService.smali'
text = p.read_text()
pattern = r'\.method public getWindowInsets\(ILandroid/os/IBinder;Landroid/view/InsetsState;\)V\n.*?\.end method'
matches = re.findall(pattern, text, flags=re.S)
assert len(matches) == 1 and '.registers 7' in matches[0]
original = matches[0]
marker = '    invoke-virtual {p0, p1, p3}, Lcom/android/server/wm/InsetsPolicy;->getInsetsForWindowMetrics(Lcom/android/server/wm/WindowToken;Landroid/view/InsetsState;)V'
assert original.count(marker) == 1 and 'boxKeyboardRailInsets' not in original
assert not re.search(r'\bv[34567]\b', original)
# Capture the requester before WMS clears Binder identity; display contexts lack window tokens.
replacement = original.replace('    .registers 7', '''    .registers 8
    invoke-static {}, Landroid/os/Binder;->getCallingUid()I
    move-result v3
''')
replacement = replacement.replace(marker, marker + '''
    iget-object p0, p0, Lcom/android/server/wm/InsetsPolicy;->mDisplayContent:Lcom/android/server/wm/DisplayContent;
    invoke-virtual {p0}, Lcom/android/server/wm/DisplayContent;->getDisplayId()I
    move-result p1
    if-nez p1, :box_metrics_done
    iget-object p0, p0, Lcom/android/server/wm/DisplayContent;->mInputMethodWindow:Lcom/android/server/wm/WindowState;
    if-eqz p0, :box_metrics_done
    iget p0, p0, Lcom/android/server/wm/WindowState;->mOwnerUid:I
    if-ne p0, v3, :box_metrics_done
    const/16 p0, 0x7db
    invoke-static {p3, p0}, Lcom/android/server/wm/WindowState;->boxKeyboardRailInsets(Landroid/view/InsetsState;I)Landroid/view/InsetsState;
    move-result-object p0
    if-eq p0, p3, :box_metrics_done
    const/4 p1, 0x1
    invoke-virtual {p3, p0, p1}, Landroid/view/InsetsState;->set(Landroid/view/InsetsState;Z)V
    :box_metrics_done
''')
text = text.replace(original, replacement)
p.write_text(text)
run(bin_dir / ('java' + suffix), '-jar', smali / 'smali.jar', 'assemble', work / 'wm-smali', '-o', work / 'patch.dex')
merge = (ROOT / 'src/android/DexPolicyMerge.java').read_text().replace('DisplayPolicy', 'WindowManagerService')
(work / 'DexPolicyMerge.java').write_text(merge)
classpath = os.pathsep.join(str(x) for x in [smali / 'dexlib2.jar', smali / 'guava.jar', work])
run(bin_dir / ('javac' + suffix), '-cp', classpath, '-d', work, work / 'DexPolicyMerge.java')
run(bin_dir / ('java' + suffix), '-cp', classpath, 'DexPolicyMerge', dex, work / 'patch.dex', work / 'merged.dex')
# Only keyboard windows receive the inset during layout; the raw display state is untouched.
target = 'Lcom/android/server/wm/WindowState;'
run(bin_dir / ('java' + suffix), '-jar', smali / 'baksmali.jar', 'disassemble', dex,
    '--classes', target, '-o', work / 'overlay-smali')
p = work / 'overlay-smali/com/android/server/wm/WindowState.smali'
text = p.read_text()
pattern = r'\.method public getInsetsState\(Z\)Landroid/view/InsetsState;\n.*?\.end method'
matches = re.findall(pattern, text, flags=re.S)
assert len(matches) == 1 and '.registers 4' in matches[0]
original = matches[0]
assert original.count('    return-object p0') == 2
assert not re.search(r'\bv[234]\b', original)
assert 'boxKeyboardRailInsets' not in text
replacement = original.replace('    .registers 4', '    .registers 5' + '''
    iget-object v2, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;
    iget v2, v2, Landroid/view/WindowManager$LayoutParams;->type:I
    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getDisplayId()I
    move-result v0
    if-eqz v0, :box_keyboard_display
    const/4 v2, 0x0
    :box_keyboard_display
''')
replacement = replacement.replace('    return-object p0', '''
    invoke-static {p0, v2}, Lcom/android/server/wm/WindowState;->boxKeyboardRailInsets(Landroid/view/InsetsState;I)Landroid/view/InsetsState;
    move-result-object p0
    return-object p0
''')
text += '\n' + (ROOT / 'src/android/rail-insets.smali').read_text()
p.write_text(text.replace(original, replacement))
run(bin_dir / ('java' + suffix), '-jar', smali / 'smali.jar', 'assemble', work / 'overlay-smali', '-o', work / 'overlay.dex')
merge = (ROOT / 'src/android/DexPolicyMerge.java').read_text().replace('DisplayPolicy', 'WindowState')
(work / 'DexPolicyMerge.java').write_text(merge)
run(bin_dir / ('javac' + suffix), '-cp', classpath, '-d', work, work / 'DexPolicyMerge.java')
run(bin_dir / ('java' + suffix), '-cp', classpath, 'DexPolicyMerge', work / 'merged.dex', work / 'overlay.dex', work / 'final.dex')
out = work / 'services-rail-insets.jar'
with zipfile.ZipFile(source) as jar, zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as result:
    for info in jar.infolist():
        result.writestr(info, (work / 'final.dex').read_bytes() if info.filename == entry else jar.read(info))
with zipfile.ZipFile(source) as jar, zipfile.ZipFile(out) as result:
    assert result.testzip() is None
    assert all(jar.read(n) == result.read(n) for n in jar.namelist() if n != entry)
print(out, hashlib.sha256(out.read_bytes()).hexdigest())
