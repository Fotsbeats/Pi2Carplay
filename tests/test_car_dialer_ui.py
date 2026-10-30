"""On-device dialpad fit/input check. Requires explicit ANDROID_SERIAL; places no call."""
import os,re,subprocess,time,xml.etree.ElementTree as ET

ADB=os.environ.get('ADB','adb');SERIAL=os.environ['ANDROID_SERIAL']
def adb(*args):
    return subprocess.check_output([ADB,'-s',SERIAL,*args],timeout=15)
def idle_call():
    state=adb('shell','su','0','env','-u','LD_PRELOAD','-u','LD_LIBRARY_PATH','CLASSPATH=/data/local/tmp/pi2call/call-audio.jar','app_process','/','org.bench.pi2call.CallAudio','--status').decode()
    assert 'No call' in state,'Leave the device without an active phone call for this input test'
def ui():
    adb('shell','uiautomator dump /data/local/tmp/pi2call-dialer-test.xml')
    return ET.fromstring(adb('exec-out','cat','/data/local/tmp/pi2call-dialer-test.xml'))
def bounds(node):return list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
def tap(node):
    idle_call()
    x,y,r,b=bounds(node);adb('shell','input','tap',str((x+r)//2),str((y+b)//2));time.sleep(.2)
def find(tree,name):return next(n for n in tree.iter('node')if n.attrib.get('resource-id')=='com.android.car.dialer:id/'+name)

idle_call();tree=ui();tap(next(n for n in tree.iter('node')if n.attrib.get('text')=='Dialpad'));tree=ui()
left,top,right,bottom=bounds(next(iter(tree)))
toolbar_bottom=bounds(find(tree,'car_ui_toolbar_background'))[3]
for name in ['one','two','three','four','five','six','seven','eight','nine','star','zero','pound','call_button']:
    node=find(tree,name);x,y,r,b=bounds(node)
    assert node.attrib['enabled']=='true' and left<=x<r<=right and toolbar_bottom<=y<b<=bottom,name+' clipped or disabled'
assert any(n.attrib.get('text')=='Dial a number' for n in tree.iter('node')),'Leave an empty dialpad before this test'
for name in ['one','five','zero']:tap(find(tree,name))
tree=ui()
assert any(re.sub(r'\D','',n.attrib.get('text',''))=='150' for n in tree.iter('node')),'Digits did not reach the number field'
delete=find(tree,'delete_button')
for _ in range(3):tap(delete)
tree=ui();assert any(n.attrib.get('text')=='Dial a number' for n in tree.iter('node')),'Backspace did not clear test digits'
print('PASS: all dialpad controls fit below the toolbar, digits enter and backspace clears; no call placed')
