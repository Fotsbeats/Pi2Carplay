#!/system/bin/sh
# Automatic three-minute CarPlay discovery, then hold a connected session.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ] || exit 2
[ "$(uname -r)" = 6.12.77-aibox-usb ] || exit 3
auto=$(getprop persist.vendor.aibox.auto)
[ "$auto" = 1 ] || [ "$(getprop persist.vendor.aibox.handshake)" = 1 ] || exit 0
umask 077
out=/data/local/tmp/pi5-handshake/$(cat /proc/sys/kernel/random/boot_id)
mkdir -p "$out" /cache/tmp/suding
exec >> "$out/wrapper.log" 2>&1
rm -f "$out/session-established"
export AIBOX_SESSION_FILE="$out/session-established"
echo "Starting CarPlay bridge; automatic=$auto"
setprop persist.vendor.aibox.handshake 0
duration=150
if [ "$auto" = 1 ]; then
    export AIBOX_CONTINUOUS=1
    duration=0
else
    for attempt in $(seq 1 60); do
        ip -4 address show dev wlan0 | grep -q 'inet ' && break
        sleep 1
    done
    ip -4 address show dev wlan0 | grep -q 'inet ' || { echo 'Wi-Fi unavailable; keeping normal USB'; exit 8; }
fi
logcat -b all -v threadtime -r 1024 -n 2 -f "$out/protocol.log" -s sdCarplaySvc &
log_pid=$!
dmesg > "$out/kernel.log"
capture_pid=
audio_pid=
watchdog_pid=
probe_pid=
old_size=
cleanup() {
    [ -z "$probe_pid" ] || kill "$probe_pid" 2>/dev/null || true
    kill "$log_pid" ${watchdog_pid:+"$watchdog_pid"} 2>/dev/null || true
    [ -z "$capture_pid" ] || kill "$capture_pid" 2>/dev/null || true
    [ -z "$audio_pid" ] || kill "$audio_pid" 2>/dev/null || true
    ip -6 rule del pref 10500 oif eth1 lookup 2999 2>/dev/null || true
    ip -6 route del fe80::/64 dev eth1 table 2999 2>/dev/null || true
    if [ -n "$capture_pid" ]; then wm size "${old_size:-reset}"; fi
    rm -f /data/local/tmp/pi5-video.fifo /data/local/tmp/pi5-audio.fifo
    if [ "$auto" = 1 ] && [ ! -f "$out/session-established" ]; then
        echo 'No CarPlay session established; restoring normal PC USB/ADB'
        export AIBOX_FORCE_PC_USB=1
    fi
    sh /vendor/bin/restore-handshake-usb.sh >> "$out/restore.log" 2>&1 || true
    dmesg > "$out/kernel-end.log"
    sync
}
trap cleanup EXIT
trap 'exit 143' TERM INT
export AIBOX_HOLD_USB=1
sh /vendor/bin/prepare-handshake-usb.sh
busfile=/sys/devices/platform/axi/1000480000.usb/usb5/busnum
[ -r "$busfile" ] || { echo 'Expected DWC2 host bus is missing'; exit 4; }
bus=$(cat "$busfile")
export AIBOX_OTG_BUS=$bus
if [ -f /data/local/tmp/pi5-live-video ]; then
    export AIBOX_PHYSICAL_SIZE=$(wm size | sed -n 's/Physical size: //p')
    old_size=$(wm size | sed -n 's/Override size: //p')
    rm -f /data/local/tmp/pi5-video.fifo
    mkfifo /data/local/tmp/pi5-video.fifo
    export AIBOX_GEOMETRY_FILE="$out/display-geometry"
    rm -f "$AIBOX_GEOMETRY_FILE" "$AIBOX_GEOMETRY_FILE.tmp"
    # Each fresh session must publish its own validated SDK geometry before capture starts.
    (
        for attempt in $(seq 1 1800); do
            [ ! -f "$AIBOX_GEOMETRY_FILE" ] || break
            sleep 0.1
        done
        [ -f "$AIBOX_GEOMETRY_FILE" ] || exit 9
        read -r width height fps x y content_width content_height < "$AIBOX_GEOMETRY_FILE"
        case "$width $height $fps $x $y $content_width $content_height" in *[!0-9\ ]*) exit 10;; esac
        [ -n "$content_height" ] || exit 10
        wm size "${content_width}x${content_height}"
        CLASSPATH=/data/local/tmp/pi5-display-capture.jar exec app_process / DisplayCapture \
            /data/local/tmp/pi5-video.fifo "$duration" \
            "$width" "$height" "$fps" "$x" "$y" "$content_width" "$content_height"
    ) > "$out/capture.log" 2>&1 &
    capture_pid=$!
    export AIBOX_VIDEO_PIPE=/data/local/tmp/pi5-video.fifo
    if [ -f /data/local/tmp/pi5-live-audio ]; then
        rm -f /data/local/tmp/pi5-audio.fifo
        mkfifo /data/local/tmp/pi5-audio.fifo
        CLASSPATH=/data/local/tmp/pi5-audio-capture.jar app_process / AudioCapture \
            /data/local/tmp/pi5-audio.fifo "$duration" > "$out/audio.log" 2>&1 &
        audio_pid=$!
        export AIBOX_AUDIO_PIPE=/data/local/tmp/pi5-audio.fifo
    fi
elif [ -f /data/local/tmp/pi5-screen-test.h264 ]; then
    export AIBOX_VIDEO_FILE=/data/local/tmp/pi5-screen-test.h264
fi
/system/bin/env LD_LIBRARY_PATH=/data/local/tmp/pi5-protocol/lib LD_PRELOAD=/data/local/tmp/pi5-protocol/lib/libHidTrace.so \
    /data/local/tmp/pi5-protocol/bin/linker /data/local/tmp/pi5-protocol/bin/handshake-probe &
probe_pid=$!
if [ "$auto" = 1 ]; then
    (sleep 180; [ -f "$out/session-established" ] || kill "$probe_pid" 2>/dev/null || true) &
else
    (sleep 190; kill "$probe_pid" 2>/dev/null || true) &
fi
watchdog_pid=$!
wait "$probe_pid"
echo 'CarPlay bridge session ended'
