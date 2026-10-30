"""POSIX host: exercise the real settings script with isolated Android command stubs."""
from pathlib import Path
import os,subprocess,tempfile

ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='pi2video-policy-') as temp:
    work=Path(temp)
    stubs={
        'getprop':'''#!/bin/sh
case "$1" in
ro.product.device) echo rpi5;; ro.build.version.sdk) echo 36;;
persist.vendor.pi2video.fps) printf '%s' "$TEST_FPS";;
persist.vendor.pi2video.bitrate) printf '%s' "$TEST_BITRATE";;
sys.pi2video.ceiling) printf '%s' "$TEST_CEILING";; esac
''',
        'settings':'#!/bin/sh\nprintf "settings %s\\n" "$*" >> "$TEST_LOG"\n',
        'setprop':'#!/bin/sh\nprintf "setprop %s\\n" "$*" >> "$TEST_LOG"\n',
    }
    for name,source in stubs.items():
        path=work/name;path.write_text(source);path.chmod(0o755)
    firstboot=(ROOT/'src/android/opencarplay-firstboot.sh').read_text()
    defaults=firstboot[firstboot.index('if [ -z "$(getprop persist.vendor.pi2video.fps)"'):firstboot.index('/system/bin/sh /system/bin/boxhome-video.sh')]
    for fps,bitrate,expected in [('', '',2),('60','8000',0)]:
        log=work/'commands';log.write_text('')
        env=dict(os.environ,PATH=str(work)+os.pathsep+os.environ['PATH'],TEST_LOG=str(log),TEST_FPS=fps,TEST_BITRATE=bitrate,TEST_CEILING='')
        subprocess.run(['sh','-c',defaults],env=env,check=True)
        assert len(log.read_text().splitlines())==expected,'Firstboot overwrote a saved selection'
    for fps,bitrate,ceiling,render,requested,rate in [
        ('','','',30,30,4000),('60','8000','60',60,60,8000),
        ('60','4000','30',30,60,4000),('30','8000','24',24,30,8000),
        ('120','6000','bad',30,30,4000),('60','8000','99999',60,60,8000),
    ]:
        log=work/'commands';log.write_text('')
        env=dict(os.environ,PATH=str(work)+os.pathsep+os.environ['PATH'],TEST_LOG=str(log),TEST_FPS=fps,TEST_BITRATE=bitrate,TEST_CEILING=ceiling)
        subprocess.run(['sh',str(ROOT/'src/android/boxhome-video.sh')],env=env,check=True)
        lines=log.read_text().splitlines()
        assert f'settings put system min_refresh_rate {render}.0' in lines
        assert f'settings put system peak_refresh_rate {render}.0' in lines
        assert f'setprop sys.boxhome.video.applied_fps {requested}' in lines
        assert f'setprop sys.boxhome.video.applied_bitrate {rate}' in lines
        assert not any('wm ' in line or 'usb' in line for line in lines)
print('PASS: video defaults, live selections, receiver caps and invalid settings; no geometry/USB changes')
