#!/system/bin/sh
# Refuse a different codec build after an OS/APEX upgrade.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
source=/system/lib64/box-media/libavutil.so
target=/apex/com.android.hardware.media.c2.ffmpeg/lib64/libavutil.so
original=b19268f16641d48b9abd72d80ff28235b8e5c4398a6bf8ceac7c32a4765267b0
corrected=74b06396bb47a18fbf7921cdce78a7cf3e2e5fc28c79d071cf95d171a50a5af8
[ "$(sha256sum "$source" | cut -d ' ' -f 1)" = "$corrected" ] || exit 1
current=$(sha256sum "$target" | cut -d ' ' -f 1)
[ "$current" != "$corrected" ] || exit 0
if [ "$current" != "$original" ]; then
    log -t BoxMedia 'Skipped SAND30 correction: unrecognized codec build'
    exit 0
fi
nsenter -t 1 -m -- mount -o bind,ro "$source" "$target"
