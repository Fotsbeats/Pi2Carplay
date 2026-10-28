#!/system/bin/sh
# Apply only to the exact allocator build whose duplicate-close path was corrected.
source=/system/lib64/libminigbm_gralloc_gbm_mesa-fixed.so
target=/apex/com.android.hardware.graphics.allocator.minigbm_gbm_mesa/lib64/libminigbm_gralloc_gbm_mesa.so
original=76bc2ec1b3d66f3a149f8e20c0542f09455a269d2b91dc1cd69419e00895af3e
fixed=933a4d9cd38e27815dc62f02f432a7509b9a093d5383338c901c8719237591b2
[ "$(sha256sum "$source" | cut -d' ' -f1)" = "$fixed" ] || exit 1
actual=$(sha256sum "$target" | cut -d' ' -f1)
[ "$actual" = "$fixed" ] && exit 0
[ "$actual" = "$original" ] || exit 1
mount -o bind "$source" "$target" || exit 1
[ "$(sha256sum "$target" | cut -d' ' -f1)" = "$fixed" ] || exit 1
setprop sys.boxhome.graphicsfix applied
