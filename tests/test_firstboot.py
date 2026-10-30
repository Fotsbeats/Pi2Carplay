"""Exercise the actual install/resume loop with dummy APK bytes and a fake pm."""
from pathlib import Path
import os,shutil,subprocess,tempfile,re,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
script=(ROOT/'src/android/opencarplay-firstboot.sh').read_text()
assert 'setprop persist.vendor.pi2video.fps 30' in script
assert 'setprop persist.vendor.pi2video.bitrate 4000' in script
assert '/system/bin/sh /system/bin/boxhome-video.sh' in script
assert script.index('[ ! -f "$marker" ] || exit 0') < script.index('SYSTEM_ALERT_WINDOW allow')
assert script.rindex('enabled_accessibility_services') > script.index('done\n/system/bin/sh /system/bin/boxhome-landscape.sh')
assert script.index('/system/bin/sh /vendor/bin/car-dialer-setup.sh') < script.index("printf 'home2.16-live-video-calling")
check=(ROOT/'src/capture/EncoderSelfCheck.java').read_text()
expected=re.search(r'args\[0\]\.equals\("([^"]+)"\)',check).group(1)
assert 'EncoderSelfCheck '+expected in script,'First-boot self-check must use its allowed scratch path'
assert 'setprop persist.adb.tcp.port 5555' in script
assert 'setprop service.adb.tcp.port 5555' in script
safety=ET.parse(ROOT/'src/android/safety-overlay/res/raw-v36/safety_center_config.xml')
group=safety.find(".//safety-sources-group[@id='BoxGoogleSafetySources']")
assert group is not None and group.get('type')=='stateful','Google status sources must be in a stateful group'
start=script.index('for package in /data/local/tmp/pi5-install-apps/*; do')
loop=script[start:script.index('/system/bin/sh /system/bin/boxhome-landscape.sh',start)]
mock='''#!/bin/sh
set -eu
case "$1" in
 path)
  [ -f "$STATE/committed" ] || exit 0
  for apk in "$STATE/installed"/*.apk; do echo "package:$apk"; done;;
 install-create) echo create >> "$STATE/calls"; echo 'Success: created install session [17]';;
 install-write) cp "$6" "$STATE/installed/$5"; echo 'Success';;
 install-commit)
  [ "${FAIL_COMMIT:-0}" = 0 ] || exit 1
  touch "$STATE/committed"; echo 'Success';;
 install-abandon) touch "$STATE/abandoned";;
 *) exit 99;;
esac
'''
with tempfile.TemporaryDirectory(prefix='opencarplay-firstboot-') as temp:
    root=Path(temp);(root/'bin').mkdir();pm=root/'bin/pm';pm.write_text(mock);pm.chmod(0o755)
    for scenario in ['fresh','already-installed','changed-split','failed-commit']:
        state=root/scenario;state.mkdir();(state/'installed').mkdir();package=state/'seed/app.example';package.mkdir(parents=True)
        for name,data in [('base.apk',b'program'),('split_arm64.apk',b'arm64-code')]:
            (package/name).write_bytes(data)
            if scenario in ['already-installed','changed-split']:shutil.copyfile(package/name,state/'installed'/name)
        if scenario in ['already-installed','changed-split']:(state/'committed').touch()
        if scenario=='changed-split':(state/'installed/split_arm64.apk').write_bytes(b'old-code')
        env=dict(os.environ,PATH=str(root/'bin')+os.pathsep+os.environ['PATH'],STATE=str(state),FAIL_COMMIT='1' if scenario=='failed-commit' else '0')
        actual='set -eu\n'+loop.replace('/data/local/tmp/pi5-install-apps',str(state/'seed'))
        result=subprocess.run(['sh','-c',actual],env=env,capture_output=True,text=True)
        if scenario=='failed-commit':
            assert result.returncode==6 and (state/'abandoned').exists() and len(list(package.glob('*.apk')))==2
        else:
            assert result.returncode==0,(scenario,result.stderr)
            assert not package.exists()
            assert (state/'calls').exists()==(scenario!='already-installed')
print('PASS: fresh install, exact resume, changed split and failed commit preserve/retry behavior')
