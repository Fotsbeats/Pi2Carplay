# Pi2Carplay

![Pi2Carplay](assets/Pi2CarplayBanner.png)

Run Android apps on your car's wired CarPlay display using hardware you control. Pi2Carplay brings the CarPlay bridge to a Raspberry Pi 5, replacing an off-the-shelf "AI box" with a ready-to-flash Android image and source you can adapt.

Includes a dashboard launcher, editable favorites, split screen, audio and touch. The bridge reads the display's negotiated resolution and usable area.

The bridge uses the Pi's GPU for screen capture and color conversion, with H.264 encoding on the CPU. Video defaults to **4 Mbps / 30 FPS**. Home settings offers live **4/8 Mbps** and **30/60 FPS** controls; the receiver's negotiated frame rate remains the ceiling. Sustained performance depends on the apps and display resolution.

**Early testing release. More vehicles and display sizes need testing. Wired CarPlay only.**

The [v0.2.0 release notes](docs/releases/v0.2.0/README.md) cover calling, live video controls and UI fixes. The combined build passed vehicle testing; fresh-image hardware boot remains untested.

## What you need

- Raspberry Pi 5 with **2GB RAM or more**. 4GB is recommended.
- Fast **U3 microSD, 16GB or larger**, and a card reader.
- Display with a micro-HDMI cable and a USB mouse for first boot. A keyboard is optional. Technically you can setup without this and bring Pi right to car but makes things easier to log in and configure android first.
- Reliable power. Some vehicle USB ports cannot supply enough power. Pi 5 needs 5v 3a Minimum. Pi2Carplay uses a single connected to a wired carplay port for Data and power from the Pi's usb-c connector
- A **wired CarPlay** USB data port and a data-capable cable to the Pi's **USB-C port**.
- Wi-Fi or a phone hotspot for app downloads, sign-in and streaming.

## Get started

1. Download the `.img.gz` image from [Releases](https://github.com/Fotsbeats/Pi2Carplay/releases).
2. Open [Raspberry Pi Imager](https://www.raspberrypi.com/software/), select **Raspberry Pi 5**, then **Use custom** under the operating system choices. Select the downloaded image.
3. Select your microSD and write it. **This erases the card.** Skip Raspberry Pi OS customization and let verification finish.
4. Put the card in the Pi. Connect the display, mouse and power. Allow several minutes for first-boot app installation, then connect Wi-Fi and sign into your apps.
5. Shut down the Pi. Move it to the vehicle and connect its **USB-C port to the CarPlay data port**. Power it on and allow about a minute for Android to appear. The Pi's USB-A ports are for accessories.

## Fix “This device isn’t Play Protect certified”

If Play Store or YouTube blocks access, register **your Pi’s own Google Services Framework ID**. Connect ADB to the Pi, run `adb shell`, then `su 0`, and query the ID on this image:

```sh
sqlite3 -readonly /data/user/0/com.google.android.gms/databases/gservices.db "SELECT value FROM main WHERE name='android_id';"
```

Submit that value at [Google’s device registration page](https://www.google.com/android/uncertified/), using the **same Google account signed into the Pi**. Allow time for registration to apply, then reopen the apps. This registers custom-ROM access; it does not confer official hardware certification.

If access remains blocked, an old compliance response may still be cached. In the root shell, back up only these caches, then reopen Play Store and YouTube:

```sh
backup=/data/local/tmp/google-compliance-$(date +%s)
mkdir -m 700 "$backup" || exit 1
for pkg in com.android.vending com.google.android.youtube com.google.android.gms; do
    am force-stop "$pkg"
done
mv /data/user/0/com.google.android.gms/files/gmscompliance/shared/cache.pb "$backup/services.pb"
mv /data/user/0/com.android.vending/cache/gmscompliance.pb "$backup/store.pb"
mv /data/user/0/com.google.android.youtube/cache/gmscompliance.pb "$backup/youtube.pb"
```

Skip missing cache files. Keep Google services storage intact so your account and registered ID are preserved. Never include your ID, account data or these backups in a release image.

## Build and adapt
Includes an Agents.md so you can customize this to your needs, change launcher themes and configure Android to your liking.

Based on [KonstaKANG AOSP 16](https://konstakang.com/devices/rpi5/AOSP16/) and [Raspberry Vanilla](https://github.com/raspberry-vanilla). The source can be adapted to other Pi Android builds and Android versions; these are not drop-in image upgrades. See [AGENTS.md](AGENTS.md) for the bridge contracts to preserve.

Project source and bundled components have separate licenses. The Android base includes non-commercial components, and the protocol SDK remains a binary dependency. See [LICENSE-SCOPE.md](LICENSE-SCOPE.md).
