#!/system/bin/sh
# Restore only the saved bridge programs; retain the program-only rollback directory.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
saved=/data/local/tmp/pi2call/rollback
cd "$saved"
sha256sum -c rollback.sha256
sha256sum -c ../target.sha256
sh /vendor/bin/pi2call-setup.sh --stock
setprop ctl.stop aibox-handshake
for attempt in $(seq 1 20); do
    [ "$(getprop init.svc.aibox-handshake)" != running ] && break
    sleep 1
done
[ "$(getprop init.svc.aibox-handshake)" != running ]
mount -o remount,rw /vendor
mount -o remount,rw /
for name in handshake-probe aibox-handshake.sh; do
    case "$name" in
        handshake-probe) target=/data/local/tmp/pi5-protocol/bin/handshake-probe;;
        aibox-handshake.sh) target=/vendor/bin/aibox-handshake.sh;;
    esac
    cp "$saved/$name" "$target.rollback-new"
    chmod 755 "$target.rollback-new"
    chown root:root "$target.rollback-new"
    mv "$target.rollback-new" "$target"
    restorecon "$target"
    [ "$(sha256sum "$target" | cut -d ' ' -f 1)" = "$(sha256sum "$saved/$name" | cut -d ' ' -f 1)" ]
done
pm uninstall --user 0 org.bench.pi2call || true
rm -f /system/app/Pi2Call/Calls.apk /system/lib64/libpi2call_hci.so \
    /vendor/bin/pi2call-setup.sh /vendor/bin/pi2call-audio.sh /vendor/etc/init/pi2call.rc
rmdir /system/app/Pi2Call 2>/dev/null || true
sync
mount -o remount,ro /vendor
mount -o remount,ro /
echo 'Original bridge and Bluetooth restored. Reboot to finish removing Calls.'
