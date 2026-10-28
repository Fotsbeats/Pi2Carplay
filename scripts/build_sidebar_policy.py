"""Patch only WindowState.setFrames on the supplied current Android16 services JAR."""
import os
import hashlib
import re
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[1]
HERE=ROOT/'src/android'
# Supply baksmali.jar, smali.jar, dexlib2.jar and guava.jar; no bundled private tools.
smali=Path(os.environ['DEX_TOOLS'])
jdk=Path(os.environ['JAVA_HOME'])
exe='.exe' if os.name=='nt' else ''
original=Path(sys.argv[1]).resolve();work=original.parent/(original.stem+'-sidebar');work.mkdir(exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True)
with zipfile.ZipFile(original) as source:
    matches=[n for n in source.namelist() if n.endswith('.dex') and b'Lcom/android/server/wm/WindowState;' in source.read(n)]
    assert len(matches)==1
    entry=matches[0];dex=work/entry;dex.write_bytes(source.read(entry))
run(jdk/('bin/java'+exe),'-jar',smali/'baksmali.jar','disassemble',dex,'--classes','Lcom/android/server/wm/WindowState;','-o',work/'smali')
p=work/'smali/com/android/server/wm/WindowState.smali';s=p.read_text()
match=re.search(r'\.method public setFrames\(Landroid/window/ClientWindowFrames;II\)V\n    \.registers (\d+)\n',s)
assert match and int(match.group(1))>=5
marker=match.group(0)
assert s.count(marker)==1
s=s.replace(marker,marker+'''\n    iget-object v0, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;
    invoke-static {p1, v0}, Lcom/android/server/wm/WindowState;->boxConstrainFrames(Landroid/window/ClientWindowFrames;Landroid/view/WindowManager$LayoutParams;)V
''')
helper='''
.method private static boxConstrainFrames(Landroid/window/ClientWindowFrames;Landroid/view/WindowManager$LayoutParams;)V
    .registers 5
    iget v0, p1, Landroid/view/WindowManager$LayoutParams;->type:I
    const/16 v1, 0x64
    if-ge v0, v1, :box_done
    const-string v0, "sys.boxhome.rail.ready"
    const/4 v1, 0x0
    invoke-static {v0, v1}, Landroid/os/SystemProperties;->getBoolean(Ljava/lang/String;Z)Z
    move-result v0
    if-eqz v0, :box_done
'''
for i,field in enumerate(('displayFrame','parentFrame','frame')):
    helper+=f'''
    iget-object v0, p0, Landroid/window/ClientWindowFrames;->{field}:Landroid/graphics/Rect;
    iget v1, v0, Landroid/graphics/Rect;->left:I
    const/16 v2, 0x68
    if-ge v1, v2, :box_rect_{i}
    iget v1, v0, Landroid/graphics/Rect;->right:I
    if-gt v1, v2, :box_clamp_{i}
    invoke-virtual {{v0}}, Landroid/graphics/Rect;->width()I
    move-result v1
    add-int/2addr v1, v2
    iput v1, v0, Landroid/graphics/Rect;->right:I
    :box_clamp_{i}
    iput v2, v0, Landroid/graphics/Rect;->left:I
    :box_rect_{i}
'''
helper+='    :box_done\n    return-void\n.end method\n'
s+=helper;p.write_text(s)
assert s.count('->boxConstrainFrames(')==1
run(jdk/('bin/java'+exe),'-jar',smali/'smali.jar','assemble',work/'smali','-o',work/'replacement.dex')
merge=(HERE/'DexPolicyMerge.java').read_text()
merge=merge.replace('DisplayPolicy','WindowState')
(work/'DexPolicyMerge.java').write_text(merge)
classpath=os.pathsep.join(str(x) for x in [smali/'dexlib2.jar',smali/'guava.jar',work])
run(jdk/('bin/javac'+exe),'-cp',classpath,'-d',work,work/'DexPolicyMerge.java')
run(jdk/('bin/java'+exe),'-cp',classpath,'DexPolicyMerge',dex,work/'replacement.dex',work/'merged.dex')
out=work/'services-sidebar.jar'
with zipfile.ZipFile(original) as source,zipfile.ZipFile(out,'w',zipfile.ZIP_STORED) as target:
    for info in source.infolist():target.writestr(info,(work/'merged.dex').read_bytes() if info.filename==entry else source.read(info))
with zipfile.ZipFile(original) as source,zipfile.ZipFile(out) as result:
    assert result.testzip() is None
    assert all(source.read(n)==result.read(n) for n in source.namelist() if n!=entry)
print('Patched only WindowState; other JAR entries unchanged:',out,hashlib.file_digest(out.open('rb'),'sha256').hexdigest())
