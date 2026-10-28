#!/system/bin/sh
# Apply dashboard presentation policy without changing display size, fps or audio.
set -eu
[ "$(getprop ro.product.device)" = rpi5 ]
[ "$(getprop ro.build.version.sdk)" = 36 ]
apply_package() {
    package=$1
    case "$package" in ''|*[!a-zA-Z0-9_.]*|.*|*..*) return 2;; esac
    case "$package" in *.*) ;; *) return 2;; esac
    pm path "$package" | grep -q '^package:' || return 0
    am compat enable --no-kill 310816437 "$package"
    case "$package" in
        com.google.android.youtube) cmd appops set "$package" PICTURE_IN_PICTURE allow;;
        *) cmd appops set "$package" PICTURE_IN_PICTURE ignore;;
    esac
    case "$package" in
        com.google.android.apps.maps|com.waze|app.vela)
            pm grant --user 0 "$package" android.permission.ACCESS_COARSE_LOCATION
            pm grant --user 0 "$package" android.permission.ACCESS_FINE_LOCATION
            for operation in COARSE_LOCATION FINE_LOCATION; do
                cmd appops set --uid "$package" "$operation" foreground
                cmd appops set "$package" "$operation" foreground
            done;;
    esac
}
if [ "${1:-}" = --pending ]; then
    setprop sys.boxhome.apps.pending 0
    queue=$(readlink -f /data/user/0/org.bench.boxhome/files/pending-policy)
    case "$queue" in /data/data/org.bench.boxhome/files/pending-policy|/data/user/0/org.bench.boxhome/files/pending-policy) ;; *) exit 2;; esac
    [ -d "$queue" ] || exit 0
    cd -P "$queue"
    case "$(pwd -P)" in /data/data/org.bench.boxhome/files/pending-policy|/data/user/0/org.bench.boxhome/files/pending-policy) ;; *) exit 2;; esac
    for request in ./*; do
        [ -f "$request" ] && [ ! -L "$request" ] || continue
        package=${request##*/}
        apply_package "$package"
        rm -f -- "$request"
    done
elif [ "${1:-}" = --all ]; then
    pm list packages -3 | while IFS= read -r line; do apply_package "${line#package:}"; done
else
    [ "$#" = 1 ] || exit 2
    apply_package "$1"
fi
