#!/system/bin/sh
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
pipe=/dev/box-carplay-gps
[ ! -L "$pipe" ] || exit 2
if [ ! -p "$pipe" ]; then
    [ ! -e "$pipe" ] && [ ! -L "$pipe" ] || exit 2
    mkfifo "$pipe"
fi
chown gps:system "$pipe"
chmod 0660 "$pipe"
restorecon "$pipe"
