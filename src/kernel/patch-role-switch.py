from pathlib import Path
import os

p = Path(os.environ['KERNEL_SOURCE']) / 'drivers/usb/dwc2/drd.c'
s = p.read_text()
old = '\tif (!already && hsotg->dr_mode == USB_DR_MODE_OTG)\n'
new = '''	/* Older cores can report A-session valid while still forced to device. */
	if (hsotg->dr_mode == USB_DR_MODE_OTG &&
	    (!already || dwc2_is_host_mode(hsotg) != (role == USB_ROLE_HOST)))
'''
assert s.count(old) == 1, 'Unexpected source or patch already applied'
p.write_text(s.replace(old, new))
