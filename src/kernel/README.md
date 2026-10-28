# Kernel source provenance

Base: raspberry-vanilla/android_kernel_brcm_rpi at55b572d3ed0efe3308b26a9946433f4d2d2a72d4, Linux6.12.77. Fetch the upstream source from https://github.com/raspberry-vanilla/android_kernel_brcm_rpi at that exact commit; the archive is not duplicated here. USB adapter/HID sources, role-switch DTS, selected configuration and retained VC4/HID patches are in this directory. Set KERNEL_SOURCE to an extracted source tree before running the patch scripts. Matching firmware must be provided at CONFIG_EXTRA_FIRMWARE_DIR.

The distributed working kernel is the final role-switch build. Its final Mac-side DWC2 correction also forces the requested hardware mode when session-valid state already agrees but the controller's actual mode disagrees. Exact final kernel patch/source reconciliation remains pending; the earlier retained kernel tree must not be described as an exact source snapshot of that final binary. Do not rebuild/replace the image kernel from this incomplete reconstruction yet. Binary module/kernel ABI and the USB role/enumeration gates must remain matched.

This source inventory is deliberately explicit about that remaining release blocker. The local clean image is a test candidate, not a completed public kernel-source compliance package.
