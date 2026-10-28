from pathlib import Path
import os

p = Path(os.environ['KERNEL_SOURCE']) / 'drivers/usb/gadget/function/f_hid.c'
s = p.read_text()
assert 'aibox_hid_notify' not in s, 'Patch already applied'
needle = 'static void hidg_disable(struct usb_function *f)'
assert s.count(needle) == 1
helper = '''/* Preserve the baseline readiness events without requiring the adapter for normal HID. */
extern int aibox_usb_notify(unsigned int state);
static void aibox_hid_notify(unsigned int state)
{
	int (*notify)(unsigned int) = symbol_get(aibox_usb_notify);
	if (notify) {
		notify(state);
		symbol_put(aibox_usb_notify);
	}
}

'''
s = s.replace(needle, helper + needle)
needle = '\thidg->req = NULL;\n\tspin_unlock_irqrestore(&hidg->write_spinlock, flags);\n}'
assert s.count(needle) == 1
s = s.replace(needle, needle[:-1] + '\taibox_hid_notify(0);\n}')
needle = '\t\twake_up(&hidg->write_queue);\n\t}\n\treturn 0;\ndisable_out_ep:'
assert s.count(needle) == 1
s = s.replace(needle, '\t\twake_up(&hidg->write_queue);\n\t}\n\taibox_hid_notify(2);\n\treturn 0;\ndisable_out_ep:')
assert s.count('aibox_hid_notify(0);') == 1
assert s.count('aibox_hid_notify(2);') == 1
p.write_text(s)
print('HID readiness/disconnect events added to existing driver')
