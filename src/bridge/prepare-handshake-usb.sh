#!/system/bin/sh
# Connection probe: PTP, audio/HID, PTP+mux and PTP+mux+VSC configurations.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ] || exit 2
[ "$(uname -r)" = 6.12.77-aibox-usb ] || exit 3
g=/config/usb_gadget/g1
stop vendor.usb_gadget-rpi
sleep 2
echo none > /sys/class/usb_role/1000480000.usb-role-switch/role
[ -z "$(cat "$g/UDC")" ] || echo '' > "$g/UDC"
echo 0 > /sys/module/aibox_usb/parameters/connect_ready
echo "${AIBOX_HOLD_USB:-0}" > /sys/module/aibox_usb/parameters/gate_connect
for link in "$g/configs/b.1/function0" "$g/configs/b.1/function-aibox-hid" "$g/configs/b.1/aibox-ptp" "$g/configs/b.2/aibox-audio" "$g/configs/b.2/aibox-hid" "$g/configs/b.2/aibox-ptp"; do
    [ ! -L "$link" ] || rm "$link"
done
for config in 3 4; do
    for name in aibox-ptp aibox-mux aibox-vsc; do
        link="$g/configs/b.$config/$name"
        [ ! -L "$link" ] || rm "$link"
    done
done
mkdir -p "$g/functions/aibox_ptp.usb0" "$g/functions/uac1.aibox"
mkdir -p "$g/functions/aibox_mux.usb0" "$g/functions/aibox_vsc.usb0"
mkdir -p "$g/functions/hid.aibox" "$g/configs/b.2/strings/0x409"
echo 0 > "$g/functions/hid.aibox/protocol"
echo 0 > "$g/functions/hid.aibox/subclass"
echo 1 > "$g/functions/hid.aibox/no_out_endpoint"
echo 1024 > "$g/functions/hid.aibox/report_length"
cat /data/local/tmp/aibox-hid-report.bin > "$g/functions/hid.aibox/report_desc"
echo 0 > "$g/functions/uac1.aibox/c_chmask"
echo 3 > "$g/functions/uac1.aibox/p_chmask"
echo 44100 > "$g/functions/uac1.aibox/p_srate"
echo 2 > "$g/functions/uac1.aibox/p_ssize"
echo 0x05ac > "$g/idVendor"
echo 0x12a8 > "$g/idProduct"
echo 0x1001 > "$g/bcdDevice"
echo 'Apple Inc.' > "$g/strings/0x409/manufacturer"
echo iPhone > "$g/strings/0x409/product"
getprop ro.serialno > "$g/strings/0x409/serialnumber"
echo 0xc0 > "$g/configs/b.1/bmAttributes"
echo PTP > "$g/configs/b.1/strings/0x409/configuration"
echo 0xc0 > "$g/configs/b.2/bmAttributes"
echo 'iPod USB Interface' > "$g/configs/b.2/strings/0x409/configuration"
[ -L "$g/configs/b.1/aibox-ptp" ] || ln -s "$g/functions/aibox_ptp.usb0" "$g/configs/b.1/aibox-ptp"
[ -L "$g/configs/b.2/aibox-audio" ] || ln -s "$g/functions/uac1.aibox" "$g/configs/b.2/aibox-audio"
[ -L "$g/configs/b.2/aibox-hid" ] || ln -s "$g/functions/hid.aibox" "$g/configs/b.2/aibox-hid"
# shortcut: an extra PTP interface carries config-2 vendor requests; replace with descriptor-free dispatch if the HU rejects it.
ln -s "$g/functions/aibox_ptp.usb0" "$g/configs/b.2/aibox-ptp"
for config in 3 4; do
    mkdir -p "$g/configs/b.$config/strings/0x409"
    echo 0xc0 > "$g/configs/b.$config/bmAttributes"
    echo 'PTP + Apple Mobile Device' > "$g/configs/b.$config/strings/0x409/configuration"
    ln -s "$g/functions/aibox_ptp.usb0" "$g/configs/b.$config/aibox-ptp"
    ln -s "$g/functions/aibox_mux.usb0" "$g/configs/b.$config/aibox-mux"
done
ln -s "$g/functions/aibox_vsc.usb0" "$g/configs/b.4/aibox-vsc"
echo 1000480000.usb > "$g/UDC"
sleep 2
# ADB's FunctionFS worker can clear the first bind when its link is removed.
[ -n "$(cat "$g/UDC")" ] || echo 1000480000.usb > "$g/UDC"
[ -c /dev/hidg0 ]
echo 'Four initial USB configurations bound; no media projection attached'
