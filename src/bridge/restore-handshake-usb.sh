#!/system/bin/sh
set -eu
[ "$(getprop ro.product.device)" = rpi5 ] || exit 2
g=/config/usb_gadget/g1
stop vendor.usb_gadget-rpi
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    [ "$(getprop init.svc.vendor.usb_gadget-rpi)" = stopped ] && break
    sleep 1
done
[ "$(getprop init.svc.vendor.usb_gadget-rpi)" = stopped ]
standby=0
if [ -f /data/local/tmp/pi5-hu-standby ] && [ "${AIBOX_FORCE_PC_USB:-0}" != 1 ]; then
    standby=1
fi
[ -z "$(cat "$g/UDC")" ] || echo '' > "$g/UDC"
for link in "$g/configs/b.1/aibox-ptp" "$g/configs/b.2/aibox-audio" "$g/configs/b.2/aibox-hid"; do
    [ ! -L "$link" ] || rm "$link"
done
for config in 2 3 4; do
    for name in aibox-ptp aibox-mux aibox-vsc; do
        link="$g/configs/b.$config/$name"
        [ ! -L "$link" ] || rm "$link"
    done
    if [ -d "$g/configs/b.$config" ]; then
        rmdir "$g/configs/b.$config/strings/0x409"
        rmdir "$g/configs/b.$config"
    fi
done
echo 0x18d1 > "$g/idVendor"
echo 0x4ee7 > "$g/idProduct"
echo 0x0440 > "$g/bcdDevice"
echo Raspberry > "$g/strings/0x409/manufacturer"
echo 'Pi 5' > "$g/strings/0x409/product"
# HAL is stopped before replacing its accessory/ADB links.
for link in "$g/configs/b.1"/function*; do
    [ ! -L "$link" ] || rm "$link"
done
ln -s "$g/functions/ffs.adb" "$g/configs/b.1/function0"
echo device > /sys/class/usb_role/1000480000.usb-role-switch/role
if [ "$standby" = 1 ]; then
    echo 'HU standby: USB enumeration paused; network ADB remains available'
    exit 0
fi
start vendor.usb_gadget-rpi
setprop sys.usb.config adb
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    [ "$(getprop sys.usb.ffs.ready)" = 1 ] || continue
    [ -n "$(cat "$g/UDC")" ] || echo 1000480000.usb > "$g/UDC"
    sleep 1
    [ -n "$(cat "$g/UDC")" ] && break
done
[ "$(getprop sys.usb.ffs.ready)" = 1 ]
[ "$(cat "$g/UDC")" = 1000480000.usb ]
echo 'Restored default USB identity and ADB function'
