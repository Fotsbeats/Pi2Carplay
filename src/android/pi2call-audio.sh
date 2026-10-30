#!/system/bin/sh
set -eu
umask 077
cd /data/local/tmp/pi2call
[ ! -f audio.log ] || mv audio.log audio.previous.log
exec > audio.log 2>&1
exec env -u LD_PRELOAD -u LD_LIBRARY_PATH CLASSPATH=/data/local/tmp/pi2call/call-audio.jar \
    app_process / org.bench.pi2call.CallAudio
