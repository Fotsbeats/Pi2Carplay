"""Build experimental call controls/helper using the existing Java/SDK toolchain."""
from pathlib import Path
import argparse,os,zipfile
from build_java import ROOT,run,compile_dex

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--keystore',type=Path,required=True)
    args=parser.parse_args()
    android=Path(os.environ['ANDROID_JAR']);tools=Path(os.environ['ANDROID_BUILD_TOOLS'])
    source=ROOT/'src/calling';work=ROOT/'build/calling';work.mkdir(parents=True,exist_ok=True)
    dex=compile_dex(list(source.glob('*.java')),work,android,tools)
    run('javac','--release','8','-d',work/'tests',source/'CallDemand.java',ROOT/'tests/CallDemandTest.java')
    run('java','-ea','-cp',work/'tests','org.bench.pi2call.CallDemandTest')
    with zipfile.ZipFile(work/'call-audio.jar','w',zipfile.ZIP_STORED) as jar:jar.write(dex,'classes.dex')
    exe='.exe' if os.name=='nt' else ''
    run(tools/('aapt'+exe),'package','-f','-M',source/'AndroidManifest.xml','-I',android,'-F',work/'unsigned.apk')
    with zipfile.ZipFile(work/'unsigned.apk','a') as apk:apk.write(dex,'classes.dex')
    signing=['--ks-pass','env:PI2CALL_KEY_PASS'] if 'PI2CALL_KEY_PASS' in os.environ else []
    run('java','-jar',tools/'lib/apksigner.jar','sign','--ks',args.keystore,*signing,'--out',work/'calls.apk',work/'unsigned.apk')
    run('java','-jar',tools/'lib/apksigner.jar','verify',work/'calls.apk')
