#!/system/bin/sh
# Reapply the fixed dashboard policy after boot/PC fallback; never alter USB or bridge geometry.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
wm fixed-to-user-rotation enabled
wm set-ignore-orientation-request true
wm user-rotation lock 0

/system/bin/sh /system/bin/boxhome-app-policy.sh --all

# Keep PiP clear of the 104px rail; 86dp leaves 129px at density 240.
device_config put systemui pip_stashing false
cmd overlay fabricate --target com.android.systemui --name BoxPip com.android.systemui:string/config_defaultPictureInPictureScreenEdgeInsets 0x03 86x16
cmd overlay enable --user 0 com.android.shell:BoxPip
