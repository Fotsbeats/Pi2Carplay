from pathlib import Path
import os
p=Path(os.environ['KERNEL_SOURCE']) / 'drivers/gpu/drm/vc4/vc4_plane.c'
s=p.read_text()
old='struct drm_crtc *txp_crtc;'
assert s.count(old)==1
s=s.replace(old,'struct drm_crtc *txp_crtc = NULL;')
needle='\tnon_txp_crtc_mask = GENMASK(drm->mode_config.num_crtc - 1, 0) -'
assert s.count(needle)==1
s=s.replace(needle,'\tif (!txp_crtc)\n\t\treturn -ENODEV;\n\n'+needle)
p.write_text(s)
print('Applied explicit absent-TXP CRTC error path for Clang 22 build')
