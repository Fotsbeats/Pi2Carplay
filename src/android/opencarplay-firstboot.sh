#!/system/bin/sh
# Fresh image only: defaults are literals, never imported from an existing user.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ] || exit 2
[ "$(getprop ro.build.version.sdk)" = 36 ] || exit 2
marker=/data/local/tmp/opencarplay-setup.complete
[ ! -f "$marker" ] || exit 0
umask 077
exec >> /data/local/tmp/opencarplay-firstboot.log 2>&1
for key in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global "$key" 0; done
for key in min_refresh_rate peak_refresh_rate; do settings put system "$key" 60.0; done
settings put global device_provisioned 1
settings put secure user_setup_complete 1
settings put secure enabled_accessibility_services org.bench.boxhome/.NavigationService
settings put secure accessibility_enabled 1
settings put secure location_mode 3
settings put global adb_enabled 1
setprop persist.adb.tcp.port 5555
setprop service.adb.tcp.port 5555
setprop persist.vendor.gps.device box-carplay-gps
setprop persist.vendor.audio.device hdmi0
cmd uimode night yes
wm size reset # CarPlay applies its negotiated content size when the head unit connects.
wm density 240
wm fixed-to-user-rotation enabled
wm set-ignore-orientation-request true
wm user-rotation lock 0
cmd package set-home-activity --user 0 org.bench.boxhome/.Home
am start -n org.bench.boxhome/.Home

if ! CLASSPATH=/data/local/tmp/pi5-display-capture.jar app_process / EncoderSelfCheck /data/local/tmp/pi5-encoder-selfcheck.bin; then
    setprop persist.vendor.aibox.auto 0
    echo 'Encoder check failed; USB recovery remains available'
    exit 3
fi
rm -f /data/local/tmp/pi5-encoder-selfcheck.bin
for package in /data/local/tmp/pi5-install-apps/*; do
    [ -d "$package" ] || continue
    set -- "$package"/*.apk
    [ -f "$1" ] || continue
    name=$(basename "$package")
    expected=$(sha256sum "$package"/*.apk | cut -d ' ' -f 1 | sort)
    installed=$(pm path "$name" | while IFS= read -r line; do
        apk=${line#package:}
        [ -f "$apk" ] && sha256sum "$apk"
    done | cut -d ' ' -f 1 | sort)
    if [ "$expected" != "$installed" ]; then
        created=$(pm install-create -r --dexopt-compiler-filter verify)
        session=$(printf '%s\n' "$created" | sed -n 's/^Success: created install session \[\([0-9]*\)\]$/\1/p')
        [ -n "$session" ] || exit 4
        for apk in "$@"; do
            if ! pm install-write -S "$(stat -c %s "$apk")" "$session" "$(basename "$apk")" "$apk"; then
                pm install-abandon "$session" || true
                exit 5
            fi
        done
        if ! pm install-commit "$session"; then
            pm install-abandon "$session" || true
            exit 6
        fi
    fi
    # Remove installers only after the complete split-APK set is verified.
    installed=$(pm path "$name" | while IFS= read -r line; do
        apk=${line#package:}
        [ -f "$apk" ] && sha256sum "$apk"
    done | cut -d ' ' -f 1 | sort)
    [ "$expected" = "$installed" ] || exit 7
    rm -f "$package"/*.apk
    rmdir "$package"
done
/system/bin/sh /system/bin/boxhome-landscape.sh
for package in com.brave.browser com.android.vending; do cmd appops set "$package" RUN_ANY_IN_BACKGROUND ignore; done

# Generate a minimal Brave profile; there is no copied browser profile or identifier.
uid=$(pm list packages -U com.brave.browser | sed -n 's/^package:com.brave.browser uid:\([0-9]*\)$/\1/p')
case "$uid" in ''|*[!0-9]*) exit 8;; esac
root=/data/user/0/com.brave.browser
if [ ! -e "$root/app_chrome/Default/Preferences" ]; then
    mkdir -p "$root/app_chrome/Default" "$root/shared_prefs"
    printf '%s\n' '{"brave":{"new_tab_page":{"show_background_image":false,"show_brave_news":false,"show_stats":false,"show_rewards":false,"show_brave_vpn":false},"today":{"opted_in":false},"news":{"opt_in_trial":false},"brave_ads":{"sponsored":{"enabled":false}},"ai_chat":{"autocomplete_provider_enabled":false}},"network_prediction_options":2}' > "$root/app_chrome/Default/Preferences"
    printf '%s\n' '{"brave":{"p3a":{"enabled":false},"stats":{"reporting_enabled":false}},"user_experience_metrics":{"reporting_enabled":false}}' > "$root/app_chrome/Local State"
    chown -R "$uid:$uid" "$root/app_chrome" "$root/shared_prefs"
    restorecon -RF "$root/app_chrome" "$root/shared_prefs"
fi
cmd package set-home-activity --user 0 org.bench.boxhome/.Home
am start -n org.bench.boxhome/.Home
printf 'gpu60-4mbps-home2.13\n' > "$marker"
# Retain compatibility with local identity guards for the now-promoted baseline.
printf 'opencarplay-public\n' > /data/local/tmp/pi5-experimental-setup.complete
sync
setprop persist.vendor.aibox.auto 1
start aibox-handshake
echo 'First boot finished; connect your own Wi-Fi and sign into apps'
