#!/system/bin/sh
# Apply validated video selections without changing physical modes, geometry or USB.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
setprop sys.boxhome.video.pending 0
fps=$(getprop persist.vendor.pi2video.fps)
bitrate=$(getprop persist.vendor.pi2video.bitrate)
case "$fps" in 30|60) ;; *) fps=30;; esac
case "$bitrate" in 4000|8000) ;; *) bitrate=4000;; esac
ceiling=$(getprop sys.pi2video.ceiling)
render=$fps
case "$ceiling" in ''|*[!0-9]*) ;; *)
    if [ "$ceiling" -gt 0 ] && [ "$ceiling" -le 60 ] && [ "$ceiling" -lt "$render" ]; then render=$ceiling; fi;;
esac
settings put system min_refresh_rate "$render.0"
settings put system peak_refresh_rate "$render.0"
setprop sys.pi2video.render_fps "$render"
setprop sys.boxhome.video.applied_fps "$fps"
setprop sys.boxhome.video.applied_bitrate "$bitrate"
