# Kernel source and build

The complete pinned Raspberry Vanilla kernel at commit `55b572d3ed0efe3308b26a9946433f4d2d2a72d4`, with the four project patches applied, lives in [`linux/`](linux/) on `main`. Configuration, patch scripts, external USB/HID module sources and the pinned firmware download manifest are alongside this README. Existing upstream licenses are retained.

The final role-switch patch was supplied from the working kernel project. It forces the requested controller mode when actual hardware mode differs even if the session-valid state already agrees. All four patches were checked against the exact upstream source; the role-switch repeat guard and host/device truth table pass. The supplied SD image and its kernel are unchanged. A bit-for-bit kernel rebuild has not been repeated.

## Build on Linux

Use Clang/LLD 22.1.3, LLVM tools, make, Python 3, bc, bison, flex, Perl, OpenSSL development files and dtc. Use the `linux/` source directory. It is already patched: do not apply the scripts again.

Obtain the six firmware files from `firmware-manifest.json`, preserving their relative paths under a separate firmware directory and checking their SHA256 values. These are separately licensed firmware dependencies.

From the extracted kernel source:

```sh
mkdir -p ../kernel-out
cp ../candidate.config ../kernel-out/.config
scripts/config --file ../kernel-out/.config --set-str EXTRA_FIRMWARE_DIR /absolute/path/to/firmware
make O=../kernel-out ARCH=arm64 LLVM=1 olddefconfig
make O=../kernel-out ARCH=arm64 LLVM=1 -j4 Image modules
make O=../kernel-out ARCH=arm64 LLVM=1 M="$PWD/../usb-adapter" modules
```

Keep `CONFIG_USB_NET_DRIVERS=y`, CDC-NCM, DWC2 dual-role, configfs, uinput and AArch32 support. The role-switch device-tree overlay is included. Rebuilt kernel, modules, symbol CRCs and startup configuration must remain matched. Stage and hash-check a rebuild on a separate card before selecting it; do not replace a working kernel merely to update Android or the launcher.
