"""Build the launcher/capture/audio sources with a local Android SDK and JDK.

ANDROID_JAR must point to the Android30 platform JAR. ANDROID_BUILD_TOOLS points
to an SDK build-tools directory containing aapt, lib/d8.jar and apksigner.
Supply your own keystore to sign the launcher; no project private key is shipped.
"""
from pathlib import Path
import argparse,os,subprocess,zipfile

ROOT=Path(__file__).resolve().parents[1]
def run(*args):subprocess.run([str(a) for a in args],check=True)
def compile_dex(sources,out,android,tools):
    classes=out/'classes';dex=out/'dex';classes.mkdir(parents=True,exist_ok=True);dex.mkdir(exist_ok=True)
    run('javac','-encoding','UTF-8','--release','8','-cp',android,'-d',classes,*sources)
    run('java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',android,'--output',dex,*classes.rglob('*.class'))
    return dex/'classes.dex'

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--keystore',required=True,type=Path);args=p.parse_args()
    android=Path(os.environ['ANDROID_JAR']);tools=Path(os.environ['ANDROID_BUILD_TOOLS']);out=ROOT/'build'
    assert android.is_file() and args.keystore.is_file()
    for folder,exclude,name in [('capture',{'FramePipelineTest.java','DisplayGeometryTest.java'},'display-capture.jar'),('audio',set(),'audio-capture.jar')]:
        dex=compile_dex([s for s in (ROOT/'src'/folder).glob('*.java') if s.name not in exclude],out/folder,android,tools)
        with zipfile.ZipFile(out/name,'w',zipfile.ZIP_STORED) as z:z.write(dex,'classes.dex')
    source=ROOT/'src/launcher';work=out/'launcher';dex=compile_dex(list(source.glob('*.java')),work,android,tools)
    for test,sources in [('LayoutRulesCheck',['LayoutRules.java']),('HomeAppsCheck',['HomeApps.java'])]:
        run('javac','-encoding','UTF-8','--release','8','-d',work/'tests',*[source/n for n in sources],source/'tests'/(test+'.java'))
        run('java','-ea','-cp',work/'tests','org.bench.boxhome.'+test)
    exe='.exe' if os.name=='nt' else ''
    run(tools/('aapt'+exe),'package','-f','-M',source/'AndroidManifest.xml','-S',source/'res','-A',source/'assets','-I',android,'-F',work/'unsigned.apk')
    with zipfile.ZipFile(work/'unsigned.apk','a') as z:z.write(dex,'classes.dex')
    run('java','-jar',tools/'lib/apksigner.jar','sign','--ks',args.keystore,'--out',out/'box-home.apk',work/'unsigned.apk')
    run('java','-jar',tools/'lib/apksigner.jar','verify',out/'box-home.apk')
