#!/system/bin/sh
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
cd /data/local/tmp/pi2call/dialer-rollback
sha256sum -c setup.sha256
mount -o remount,rw /
mount -o remount,rw /vendor
cp pi2call-setup.sh /vendor/bin/pi2call-setup.sh
chmod 755 /vendor/bin/pi2call-setup.sh
restorecon /vendor/bin/pi2call-setup.sh
cp call-audio.jar /data/local/tmp/pi2call/call-audio.jar
chmod 644 /data/local/tmp/pi2call/call-audio.jar
restorecon /data/local/tmp/pi2call/call-audio.jar
setprop bluetooth.profile.pbap.client.enabled "$(cat pbap-property)"
if [ -f class-property ]; then
    setprop bluetooth.device.class_of_device "$(cat class-property)"
fi
cmd appops set com.android.car.dialer SYSTEM_ALERT_WINDOW default || true
pm uninstall-system-updates com.android.car.dialer || true
pm disable-user --user 0 com.android.car.dialer || true
cmd telecom set-default-dialer default || true
cmd telecom set-system-dialer default || true
cmd role remove-role-holder --user 0 android.app.role.DIALER com.android.car.dialer || true
rm -f /system/priv-app/Pi2CarDialer/CarDialerApp.apk \
    /system/etc/permissions/pi2call-car-dialer.xml \
    /system/etc/sysconfig/pi2call-car-dialer.xml \
    /system/etc/default-permissions/pi2call-car-dialer.xml \
    /system/overlay/Pi2CarDialerFramework.apk /system/overlay/Pi2CarDialerTelecom.apk \
    /vendor/bin/car-dialer-setup.sh
rmdir /system/priv-app/Pi2CarDialer || true
sync
mount -o remount,ro /vendor
mount -o remount,ro / || true
echo 'Dialer removed; working audio retained. Reboot to restore the previous system dialer configuration.'
