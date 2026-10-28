// SPDX-License-Identifier: GPL-2.0
/* ARM32 report-registration ABI observed in the current bridge, using modern HID core. */
#include <linux/module.h>
#include <linux/miscdevice.h>
#include <linux/fs.h>
#include <linux/hid.h>
#include <linux/compat.h>
#include <linux/uaccess.h>
#include <linux/slab.h>

struct report32 { __u32 pointer, length, id; };
struct remove32 { __u32 all, id; };
struct remote_hid {
    struct list_head node;
    struct hid_device *hid;
    u8 *descriptor;
    u32 length, id;
};
struct hid_session { struct mutex lock; struct list_head devices; unsigned count; };
static struct miscdevice reverse_device;

static int remote_parse(struct hid_device *hid)
{
    struct remote_hid *remote = hid->driver_data;
    return hid_parse_report(hid, remote->descriptor, remote->length);
}
static int remote_start(struct hid_device *hid) { return 0; }
static void remote_stop(struct hid_device *hid) { }
static int remote_open(struct hid_device *hid) { return 0; }
static void remote_close(struct hid_device *hid) { }
static int remote_request(struct hid_device *hid, unsigned char report,
                          __u8 *buf, size_t len, unsigned char type, int request)
{ return -EOPNOTSUPP; }
static const struct hid_ll_driver remote_driver = {
    .parse = remote_parse, .start = remote_start, .stop = remote_stop,
    .open = remote_open, .close = remote_close, .raw_request = remote_request,
};
static void remote_delete(struct hid_session *session, struct remote_hid *remote)
{
    list_del(&remote->node);
    hid_destroy_device(remote->hid);
    kfree(remote->descriptor);
    kfree(remote);
    session->count--;
}
static int reverse_open(struct inode *inode, struct file *file)
{
    struct hid_session *session = kzalloc(sizeof(*session), GFP_KERNEL);
    if (!session) return -ENOMEM;
    mutex_init(&session->lock);
    INIT_LIST_HEAD(&session->devices);
    file->private_data = session;
    return 0;
}
static int reverse_release(struct inode *inode, struct file *file)
{
    struct hid_session *session = file->private_data;
    struct remote_hid *remote, *next;
    list_for_each_entry_safe(remote, next, &session->devices, node)
        remote_delete(session, remote);
    kfree(session);
    return 0;
}
static long reverse_ioctl(struct file *file, unsigned int command, unsigned long argument)
{
    struct hid_session *session = file->private_data;
    struct remote_hid *remote, *next, *found = NULL;
    struct report32 report;
    struct remove32 remove;
    void *data;
    int result = 0;

    if (command != 0x12703 && command != 0x12704 && command != 0x12705)
        return -ENOTTY;
    if (command == 0x12704) {
        if (copy_from_user(&remove, (void __user *)argument, sizeof(remove))) return -EFAULT;
    } else {
        if (copy_from_user(&report, (void __user *)argument, sizeof(report))) return -EFAULT;
        if (!report.length || report.length > HID_MAX_DESCRIPTOR_SIZE) return -EINVAL;
    }
    mutex_lock(&session->lock);
    if (command == 0x12704) {
        list_for_each_entry_safe(remote, next, &session->devices, node)
            if (remove.all || remove.id == remote->id) remote_delete(session, remote);
        goto done;
    }
    list_for_each_entry(remote, &session->devices, node)
        if (remote->id == report.id) { found = remote; break; }
    data = memdup_user(compat_ptr(report.pointer), report.length);
    if (IS_ERR(data)) { result = PTR_ERR(data); goto done; }
    if (command == 0x12705) {
        result = found ? hid_input_report(found->hid, HID_INPUT_REPORT, data, report.length, 1) : -ENOENT;
        kfree(data);
        goto done;
    }
    if (!found && session->count >= 16) { kfree(data); result = -ENOSPC; goto done; }
    remote = kzalloc(sizeof(*remote), GFP_KERNEL);
    if (!remote) { kfree(data); result = -ENOMEM; goto done; }
    remote->descriptor = data;
    remote->length = report.length;
    remote->id = report.id;
    remote->hid = hid_allocate_device();
    if (IS_ERR(remote->hid)) { result = PTR_ERR(remote->hid); goto free_remote; }
    remote->hid->driver_data = remote;
    remote->hid->ll_driver = &remote_driver;
    remote->hid->bus = BUS_USB;
    remote->hid->vendor = 0x1238;
    remote->hid->product = 0xffffffff;
    remote->hid->dev.parent = reverse_device.this_device;
    strscpy(remote->hid->name, "AIBox remote HID", sizeof(remote->hid->name));
    result = hid_add_device(remote->hid);
    if (result) { hid_destroy_device(remote->hid); goto free_remote; }
    if (found) remote_delete(session, found);
    list_add_tail(&remote->node, &session->devices);
    session->count++;
    pr_info("aibox: remote HID registered id=%u descriptor=%u\n", report.id, report.length);
    goto done;
free_remote:
    kfree(remote->descriptor);
    kfree(remote);
done:
    mutex_unlock(&session->lock);
    return result;
}
static long reverse_compat_ioctl(struct file *file, unsigned int command, unsigned long argument)
{ return reverse_ioctl(file, command, (unsigned long)compat_ptr(argument)); }
static const struct file_operations reverse_operations = {
    .owner = THIS_MODULE, .open = reverse_open, .release = reverse_release,
    .unlocked_ioctl = reverse_ioctl, .compat_ioctl = reverse_compat_ioctl,
};
static struct miscdevice reverse_device = {
    .minor = MISC_DYNAMIC_MINOR, .name = "sd_reverse_hid",
    .fops = &reverse_operations, .mode = 0600,
};
static int __init reverse_init(void) { return misc_register(&reverse_device); }
static void __exit reverse_exit(void) { misc_deregister(&reverse_device); }
module_init(reverse_init);
module_exit(reverse_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Current AIBox ARM32 remote HID ABI adapter");
