#!/system/bin/sh
set -eu
[ -f /system/priv-app/Pi2CarDialer/CarDialerApp.apk ] || exit 0
# This Pi has no cellular dialer role; select its Bluetooth-capable system UI explicitly.
cmd telecom set-system-dialer com.android.car.dialer/com.android.car.dialer.telecom.InCallServiceImpl
cmd telecom set-default-dialer com.android.car.dialer
