#!/system/bin/sh
# No preauthorized hosts. USB/network ADB require the owner to approve the host.
[ -f /data/local/tmp/opencarplay-setup.complete ] && exit 0
setprop persist.adb.tcp.port 5555
setprop service.adb.tcp.port 5555
setprop persist.sys.usb.config adb
start vendor.usb_gadget-rpi
setprop sys.usb.config adb
