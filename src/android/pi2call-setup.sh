#!/system/bin/sh
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
exec >> /data/local/tmp/pi2call/startup.log 2>&1
if [ "${1:-}" = --stock ]; then
    echo 'Restoring stock Bluetooth'
    setprop persist.vendor.pi2call.enabled 0
    setprop sys.pi2call.started 0
    svc bluetooth disable || true
    for attempt in $(seq 1 10); do
        dumpsys bluetooth_manager | grep -q 'state: OFF' && break
        sleep 1
    done
    setprop ctl.stop pi2call-audio
    setprop ctl.stop pi2call-bluetooth
    setprop bluetooth.profile.hfp.hf.enabled "$(cat /data/local/tmp/pi2call/rollback/hf-property)"
    setprop bluetooth.hfp.hf_client_features.config "$(cat /data/local/tmp/pi2call/rollback/features-property)"
    if [ -f /data/local/tmp/pi2call/dialer-rollback/pbap-property ]; then
        setprop bluetooth.profile.pbap.client.enabled "$(cat /data/local/tmp/pi2call/dialer-rollback/pbap-property)"
    fi
    if [ -f /data/local/tmp/pi2call/dialer-rollback/class-property ]; then
        setprop bluetooth.device.class_of_device "$(cat /data/local/tmp/pi2call/dialer-rollback/class-property)"
    fi
    btpid=$(pidof com.android.bluetooth || true)
    case "$btpid" in ''|*[!0-9]*) ;; *) kill -TERM "$btpid";; esac
    sleep 1
    setprop ctl.start vendor.bluetooth-rpi
    svc bluetooth enable
    exit 0
fi
[ "$(getprop persist.vendor.pi2call.enabled)" = 1 ] || exit 0
echo 'Starting experimental calling'
sha256sum -c /data/local/tmp/pi2call/target.sha256 || exec sh /vendor/bin/pi2call-setup.sh --stock
mkdir -p /data/vendor/pi2call
chown root:bluetooth /data/vendor/pi2call
chmod 0770 /data/vendor/pi2call
setprop sys.pi2call.started 0
setprop sys.pi2call.ready 0
svc bluetooth disable
for attempt in $(seq 1 10); do
    dumpsys bluetooth_manager | grep -q 'state: OFF' && break
    sleep 1
done
# Advertise mSBC codec negotiation, retaining CVSD fallback.
setprop bluetooth.profile.hfp.hf.enabled true
setprop bluetooth.hfp.hf_client_features.config 247
if [ -f /system/priv-app/Pi2CarDialer/CarDialerApp.apk ]; then
    sh /vendor/bin/car-dialer-setup.sh || echo 'Dialer configuration failed; retaining working audio services'
    setprop bluetooth.profile.pbap.client.enabled true
    # Preserve service bits; identify this HFP client as A/V hands-free, not a smartphone.
    setprop bluetooth.device.class_of_device 90,4,8
fi
# AdapterService caches its enabled profile list for the lifetime of its process.
btpid=$(pidof com.android.bluetooth || true)
case "$btpid" in ''|*[!0-9]*) ;; *) kill -TERM "$btpid";; esac
sleep 1
setprop ctl.stop vendor.bluetooth-rpi
setprop ctl.start pi2call-bluetooth
sleep 1
svc bluetooth enable
for attempt in $(seq 1 10); do
    dumpsys bluetooth_manager | grep -q 'state: ON' && break
    sleep 1
done
setprop ctl.start pi2call-audio
for attempt in $(seq 1 30); do
    if [ "$(getprop init.svc.pi2call-bluetooth)" = stopped ] || [ "$(getprop init.svc.pi2call-audio)" = stopped ]; then
        echo 'A call service stopped during startup'
        exec sh /vendor/bin/pi2call-setup.sh --stock
    fi
    if [ "$(getprop init.svc.pi2call-bluetooth)" = running ] && [ "$(getprop sys.pi2call.ready)" = 1 ]; then
        setprop sys.pi2call.started 1
        echo 'Native calling services ready'
        exit 0
    fi
    sleep 1
done
echo 'Calling startup timed out'
exec sh /vendor/bin/pi2call-setup.sh --stock
