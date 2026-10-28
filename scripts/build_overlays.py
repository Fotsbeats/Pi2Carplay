"""Build the supplied navigation/SafetyCenter resource overlays with your key."""
from pathlib import Path
import argparse,os,subprocess

ROOT=Path(__file__).resolve().parents[1]
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--keystore',type=Path,required=True);args=p.parse_args()
    tools=Path(os.environ['ANDROID_BUILD_TOOLS']);android=Path(os.environ['ANDROID_JAR'])
    assert args.keystore.is_file() and android.is_file()
    exe='.exe' if os.name=='nt' else ''
    for name in ['navigation-overlay','safety-overlay']:
        source=ROOT/'src/android'/name;work=ROOT/'build'/name;work.mkdir(parents=True,exist_ok=True)
        subprocess.run([str(tools/('aapt'+exe)),'package','-f','-M',str(source/'AndroidManifest.xml'),'-S',str(source/'res'),'-I',str(android),'-F',str(work/'unsigned.apk')],check=True)
        subprocess.run(['java','-jar',str(tools/'lib/apksigner.jar'),'sign','--ks',str(args.keystore),'--out',str(work/'overlay.apk'),str(work/'unsigned.apk')],check=True)
        subprocess.run(['java','-jar',str(tools/'lib/apksigner.jar'),'verify',str(work/'overlay.apk')],check=True)
