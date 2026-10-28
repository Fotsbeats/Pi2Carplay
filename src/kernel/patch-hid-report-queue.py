from pathlib import Path
import os

p = Path(os.environ['KERNEL_SOURCE']) / 'drivers/usb/gadget/function/f_hid.c'
s = p.read_text()
assert 'struct aibox_report' not in s, 'Patch already applied'
def replace(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new)

replace('struct f_hidg_req_list {', '''struct aibox_report {
	struct list_head node;
	unsigned int length;
	char *data;
};

struct f_hidg_req_list {''')
replace('\tunsigned int\t\t\tset_report_length;', '''	unsigned int			set_report_length;
	struct list_head		set_report_queue;
	unsigned int			set_report_queued;''')
replace('\tkfree(hidg->report_desc);\n\tkfree(hidg->set_report_buf);', '''	struct aibox_report *report, *next;
	list_for_each_entry_safe(report, next, &hidg->set_report_queue, node) {
		list_del(&report->node);
		kfree(report->data);
		kfree(report);
	}
	kfree(hidg->report_desc);
	kfree(hidg->set_report_buf);''')
replace('\thidg->set_report_buf = NULL;\n\n\tspin_unlock_irqrestore(&hidg->read_spinlock, flags);', '''	if (!list_empty(&hidg->set_report_queue)) {
		struct aibox_report *report = list_first_entry(&hidg->set_report_queue, struct aibox_report, node);
		list_del(&report->node);
		hidg->set_report_buf = report->data;
		hidg->set_report_length = report->length;
		hidg->set_report_queued--;
		kfree(report);
	} else {
		hidg->set_report_buf = NULL;
	}

	/* shortcut: like upstream, reads consume a report; use full report buffers (the bridge does). */
	spin_unlock_irqrestore(&hidg->read_spinlock, flags);''')
replace('\tchar *new_buf = NULL;\n\tunsigned long flags;', '\tstruct aibox_report *report;\n\tunsigned long flags;')
old = '''	spin_lock_irqsave(&hidg->read_spinlock, flags);

	new_buf = krealloc(hidg->set_report_buf, req->actual, GFP_ATOMIC);
	if (new_buf == NULL) {
		spin_unlock_irqrestore(&hidg->read_spinlock, flags);
		return;
	}
	hidg->set_report_buf = new_buf;

	hidg->set_report_length = req->actual;
	memcpy(hidg->set_report_buf, req->buf, req->actual);

	spin_unlock_irqrestore(&hidg->read_spinlock, flags);'''
new = '''	report = kzalloc(sizeof(*report), GFP_ATOMIC);
	if (!report) return;
	report->data = kmemdup(req->buf, req->actual, GFP_ATOMIC);
	if (!report->data) { kfree(report); return; }
	report->length = req->actual;
	spin_lock_irqsave(&hidg->read_spinlock, flags);
	if (!hidg->set_report_buf) {
		hidg->set_report_buf = report->data;
		hidg->set_report_length = report->length;
		kfree(report);
	} else if (hidg->set_report_queued < 64) {
		list_add_tail(&report->node, &hidg->set_report_queue);
		hidg->set_report_queued++;
	} else {
		kfree(report->data);
		kfree(report);
		pr_warn_ratelimited("aibox: HID output queue full\\n");
	}
	spin_unlock_irqrestore(&hidg->read_spinlock, flags);'''
replace(old,new)
replace('\topts = container_of(fi, struct f_hid_opts, func_inst);', '\tINIT_LIST_HEAD(&hidg->set_report_queue);\n\topts = container_of(fi, struct f_hid_opts, func_inst);')
# Discard old-session input when configuration is disabled, as the baseline does.
needle = '\thidg->disabled = true;\n\tspin_unlock_irqrestore(&hidg->read_spinlock, flags);'
replace(needle, '''	hidg->disabled = true;
	kfree(hidg->set_report_buf);
	hidg->set_report_buf = NULL;
	while (!list_empty(&hidg->set_report_queue)) {
		struct aibox_report *report = list_first_entry(&hidg->set_report_queue, struct aibox_report, node);
		list_del(&report->node);
		kfree(report->data);
		kfree(report);
	}
	hidg->set_report_queued = 0;
	spin_unlock_irqrestore(&hidg->read_spinlock, flags);''')
p.write_text(s)
print('Bounded FIFO for unread HID SET_REPORT packets applied')
