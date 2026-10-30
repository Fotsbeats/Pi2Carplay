"""Adapt the pinned AOSP Car Dialer to the reference non-Automotive Android image.

Requires ANDROID_JAR, ANDROID_BUILD_TOOLS, DEX_TOOLS (smali/baksmali jars),
and PI2CALL_KEY_PASS for the supplied private signing key. Downloads only the
pinned upstream APK and the real Parcelable used by its Car UI library.
"""
from pathlib import Path
import argparse,base64,hashlib,os,re,urllib.request,zipfile
from build_java import ROOT,compile_dex,run
from car_dialer_manifest import add_overlay_permission

APK_URL='https://android.googlesource.com/platform/packages/apps/Car/DialerPrebuilt/+/22ddaf1a82d6db61aab314926e27880c0a258317/CarDialerApp.apk?format=TEXT'
APK_SHA='6a6f5744b3c7bdd5137384409fbef27234cc2a73be3daeb74d58f8fbb6d11f62'
UX_URL='https://android.googlesource.com/platform/packages/services/Car/+/refs/tags/android-13.0.0_r1/car-lib/src/android/car/drivingstate/CarUxRestrictions.java?format=TEXT'
UX_SHA='94ed62b4ad6589362d5367f92b71110f442db9c70c9dce3e0d2e8305d463539d'

def fetch(url,path,digest):
    if not path.exists():path.write_bytes(base64.b64decode(urllib.request.urlopen(url,timeout=45).read()))
    if hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise ValueError('Unexpected upstream input: '+str(path))

def replace_method(path,signature,body):
    text=path.read_text();pattern=r'(?m)^\.method '+re.escape(signature)+r'\n.*?^\.end method'
    text,count=re.subn(pattern,lambda _:'.method '+signature+'\n'+body+'\n.end method',text,flags=re.S)
    if count!=1:raise ValueError('Unexpected method: '+signature)
    path.write_text(text,newline='\n')

def patch(tree):
    inject=tree/'com/android/car/dialer/inject'
    # There is no VHAL/CarService on this image. Keep those optional managers absent.
    replace_method(inject/'DialerModules_AppModule_ProvideCarFactory.smali','public static provideCar(Landroid/content/Context;)Landroid/car/Car;',
                   '    .locals 0\n    const/4 p0, 0x0\n    return-object p0')
    replace_method(inject/'DialerModules_ActivityModule_ProvideCarPackageManagerFactory.smali','public static provideCarPackageManager(Landroid/car/Car;)Landroid/car/content/pm/CarPackageManager;',
                   '    .locals 0\n    const/4 p0, 0x0\n    return-object p0')
    projection=tree/'com/android/car/dialer/telecom/ProjectionCallHandler.smali'
    replace_method(projection,'public start()V','    .locals 0\n    return-void')
    # Retain the upstream Car UI fallback for hosts without driving-state services.
    ux=tree/'com/android/car/ui/utils/CarUxRestrictionsUtil.smali'
    replace_method(ux,'public constructor <init>(Landroid/content/Context;)V','''    .locals 5
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    new-instance v0, Ljava/util/WeakHashMap;
    invoke-direct {v0}, Ljava/util/WeakHashMap;-><init>()V
    invoke-static {v0}, Ljava/util/Collections;->newSetFromMap(Ljava/util/Map;)Ljava/util/Set;
    move-result-object v0
    iput-object v0, p0, Lcom/android/car/ui/utils/CarUxRestrictionsUtil;->mObservers:Ljava/util/Set;
    new-instance v0, Lcom/android/car/ui/utils/CarUxRestrictionsUtil$$ExternalSyntheticLambda0;
    invoke-direct {v0, p0}, Lcom/android/car/ui/utils/CarUxRestrictionsUtil$$ExternalSyntheticLambda0;-><init>(Lcom/android/car/ui/utils/CarUxRestrictionsUtil;)V
    iput-object v0, p0, Lcom/android/car/ui/utils/CarUxRestrictionsUtil;->mListener:Landroid/car/drivingstate/CarUxRestrictionsManager$OnUxRestrictionsChangedListener;
    new-instance v0, Landroid/car/drivingstate/CarUxRestrictions$Builder;
    const/4 v1, 0x0
    const-wide/16 v2, 0x0
    invoke-direct {v0, v1, v1, v2, v3}, Landroid/car/drivingstate/CarUxRestrictions$Builder;-><init>(ZIJ)V
    invoke-virtual {v0}, Landroid/car/drivingstate/CarUxRestrictions$Builder;->build()Landroid/car/drivingstate/CarUxRestrictions;
    move-result-object v0
    iput-object v0, p0, Lcom/android/car/ui/utils/CarUxRestrictionsUtil;->mCarUxRestrictions:Landroid/car/drivingstate/CarUxRestrictions;
    return-void''')
    # Null means no CarService. A configured digit limit must not dereference it.
    vm=tree/'com/android/car/dialer/ui/dialpad/DialpadRestrictionViewModel.smali'
    text=vm.read_text();matches=list(re.finditer(r'    if-eqz p1, (:cond_[0-9a-f]+)',text));assert len(matches)==1
    match=matches[0]
    vm.write_text(text[:match.end()]+'\n    if-eqz p2, '+match[1]+text[match.end():],newline='\n')
    # The upstream alternate layout moves Call beside the keypad on short canvases.
    dialpad=tree/'com/android/car/dialer/ui/dialpad/DialpadFragment.smali'
    text=dialpad.read_text();needle='    iput-boolean p3, p0, Lcom/android/car/dialer/ui/dialpad/DialpadFragment;->mHasTypeDown:Z'
    assert text.count(needle)==1
    text=text.replace(needle,'''    if-eqz p3, :pi2car_layout
    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getResources()Landroid/content/res/Resources;
    move-result-object p3
    invoke-virtual {p3}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
    move-result-object p3
    iget p3, p3, Landroid/content/res/Configuration;->screenHeightDp:I
    const/16 v0, 0x258
    if-ge p3, v0, :pi2car_tall
    const/4 p3, 0x0
    goto :pi2car_layout
    :pi2car_tall
    const/4 p3, 0x1
    :pi2car_layout
'''+needle)
    dialpad.write_text(text,newline='\n')

def hook(tree,path,signature,owner,method,argument):
    target=tree/path;text=target.read_text();pattern=r'(?m)^\.method '+re.escape(signature)+r'\n.*?^\.end method'
    match=re.search(pattern,text,re.S)
    if not match:raise ValueError('Missing lifecycle hook: '+signature)
    body=match[0];registers=re.search(r'\.registers (\d+)',body);count=int(registers[1]);parameters=2 if '(' in signature and signature.split('(',1)[1].split(')',1)[0] else 1
    saved=count-parameters;body=body[:registers.start()]+'.registers '+str(count+1)+body[registers.end():]
    first=re.search(r'(?m)^    (?:invoke-|const(?:/| )|iget-|iput-|sget-|move(?:-| )|new-|return-)',body)
    if not first:raise ValueError('Missing hook entry')
    body=body[:first.start()]+'    move-object v'+str(saved)+', p0\n\n'+body[first.start():]
    body=body.replace('    return-void','    invoke-static {v'+str(saved)+'}, Lcom/android/car/dialer/ui/'+owner+';->'+method+'('+argument+')V\n\n    return-void')
    target.write_text(text[:match.start()]+body+text[match.end():],newline='\n')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--keystore',type=Path,required=True);args=parser.parse_args()
    work=ROOT/'build/car-dialer';work.mkdir(parents=True,exist_ok=True)
    tools=Path(os.environ['ANDROID_BUILD_TOOLS']);dex_tools=Path(os.environ['DEX_TOOLS']);android=Path(os.environ['ANDROID_JAR'])
    apk=work/'upstream.apk';source=work/'CarUxRestrictions.upstream.java'
    fetch(APK_URL,apk,APK_SHA);fetch(UX_URL,source,UX_SHA)
    with zipfile.ZipFile(apk)as z:(work/'original.dex').write_bytes(z.read('classes.dex'))
    tree=work/'smali'
    run('java','-jar',dex_tools/'baksmali.jar','disassemble',work/'original.dex','-o',tree)
    patch(tree)
    hook(tree,Path('com/android/car/dialer/DialerApplication.smali'),'public onCreate()V','PiPhoneUi','init','Landroid/app/Application;')
    service=Path('com/android/car/dialer/telecom/InCallServiceImpl.smali')
    for signature,method in [('public onCreate()V','attach'),('public onDestroy()V','detach'),('public onCallAdded(Landroid/telecom/Call;)V','changed'),('public onCallRemoved(Landroid/telecom/Call;)V','changed'),('public onCallAudioStateChanged(Landroid/telecom/CallAudioState;)V','changed')]:
        hook(tree,service,signature,'PiCallBar',method,'Landroid/telecom/InCallService;')
    # Source-retained platform annotations are not in the public Android SDK.
    text=source.read_text()
    text=re.sub(r'^import (?:static )?(?:android.annotation.IntDef|android.car.annotation.AddedInOrBefore|com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport(?:\.BOILERPLATE_CODE)?);\n','',text,flags=re.M)
    text=re.sub(r'@IntDef\(.*?\}\)','',text,flags=re.S)
    text=re.sub(r'@(?:AddedInOrBefore|ExcludeFromCodeCoverageGeneratedReport)\([^\n]*\)','',text)
    java=work/'CarUxRestrictions.java';java.write_text(text,newline='\n')
    dex=compile_dex([java],work/'ux',android,tools)
    run('java','-jar',dex_tools/'baksmali.jar','disassemble',dex,'-o',tree)
    ui_dex=compile_dex(list((ROOT/'src/calling-ui').glob('*.java')),work/'ui',android,tools)
    run('java','-jar',dex_tools/'baksmali.jar','disassemble',ui_dex,'-o',tree)
    run('java','-jar',dex_tools/'smali.jar','assemble',tree,'-o',work/'replacement.dex')
    icon=ROOT/'src/android/car-dialer-icon';exe='.exe' if os.name=='nt' else ''
    run(tools/('aapt'+exe),'package','-f','-M',icon/'AndroidManifest.xml','-S',icon/'res','-I',android,'-F',work/'icon.apk')
    with zipfile.ZipFile(work/'icon.apk')as z:icon_xml=z.read('res/drawable/ic_app_icon.xml')
    with zipfile.ZipFile(apk)as src,zipfile.ZipFile(work/'unsigned.apk','w')as dst:
        assert 'res/e_.xml' in src.namelist(),'Pinned launcher icon path changed'
        for item in src.infolist():
            if item.filename=='classes.dex' or re.match(r'META-INF/(?:MANIFEST.MF|[^/]+\.(?:SF|RSA|DSA|EC))$',item.filename,re.I):continue
            value=src.read(item.filename)
            if item.filename=='res/e_.xml':value=icon_xml
            if item.filename=='AndroidManifest.xml':value=add_overlay_permission(value)
            dst.writestr(item,value)
        dst.write(work/'replacement.dex','classes.dex')
    signing=['--ks-pass','env:PI2CALL_KEY_PASS'] if 'PI2CALL_KEY_PASS' in os.environ else []
    run('java','-jar',tools/'lib/apksigner.jar','sign','--ks',args.keystore,*signing,'--out',work/'CarDialerApp.apk',work/'unsigned.apk')
    run('java','-jar',tools/'lib/apksigner.jar','verify',work/'CarDialerApp.apk')
    print('Built AOSP Car Dialer; device UI, Telecom and vehicle validation still required.')
