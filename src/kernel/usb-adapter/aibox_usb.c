// SPDX-License-Identifier: GPL-2.0
/* Pi adapter for the observed legacy notification and PTP control contracts. */
#include <linux/module.h>
#include <linux/usb/composite.h>
#include <linux/netlink.h>
#include <net/sock.h>
#include <linux/workqueue.h>

static struct sock *notify_socket;
static DEFINE_SPINLOCK(event_lock);
static unsigned int ready_retries;
static void retry_ready(struct work_struct *work);
static DECLARE_DELAYED_WORK(ready_work, retry_ready);
static bool gate_connect, connect_ready;
static DEFINE_MUTEX(gate_lock);
static LIST_HEAD(gated_functions);
module_param(gate_connect, bool, 0600);

static int send_notification(unsigned int state)
{
    const char *text = state == 1 ? "connected" : state == 2 ? "hidready" : "disconnected";
    size_t size = strlen(text) + 1;
    struct sk_buff *skb;
    struct nlmsghdr *header;

    if (!notify_socket)
        return -ENOTCONN;
    skb = nlmsg_new(size, GFP_ATOMIC);
    if (!skb)
        return -ENOMEM;
    /* The baseline module uses message type 26 and destination port ID 53. */
    header = nlmsg_put(skb, 0, 0, 26, size, 0);
    if (!header) {
        kfree_skb(skb);
        return -EMSGSIZE;
    }
    memcpy(nlmsg_data(header), text, size);
    return netlink_unicast(notify_socket, skb, 53, MSG_DONTWAIT);
}
static void retry_ready(struct work_struct *work)
{
    unsigned long flags;
    spin_lock_irqsave(&event_lock, flags);
    if (ready_retries) {
        if (send_notification(2) >= 0) {
            ready_retries = 0;
            pr_info("aibox: delivered pending HID readiness\n");
        } else if (--ready_retries) {
            schedule_delayed_work(&ready_work, msecs_to_jiffies(100));
        }
    }
    spin_unlock_irqrestore(&event_lock, flags);
}
int aibox_usb_notify(unsigned int state)
{
    unsigned long flags;
    int result;
    spin_lock_irqsave(&event_lock, flags);
    result = send_notification(state);
    if (state == 0 || state == 2) {
        ready_retries = state == 2 && result < 0 ? 100 : 0;
        if (ready_retries)
            mod_delayed_work(system_wq, &ready_work, msecs_to_jiffies(100));
        else
            cancel_delayed_work(&ready_work);
    }
    spin_unlock_irqrestore(&event_lock, flags);
    return result;
}
EXPORT_SYMBOL_GPL(aibox_usb_notify);

struct ptp_adapter {
    struct usb_function function;
    struct usb_interface_descriptor interface;
    struct usb_interface_descriptor alt1, alt2;
    unsigned int kind;
    struct list_head gate_node;
    struct usb_endpoint_descriptor fs_in, fs_out, fs_interrupt;
    struct usb_endpoint_descriptor hs_in, hs_out, hs_interrupt;
    struct usb_ep *in, *out, *interrupt;
};

static inline struct ptp_adapter *to_ptp(struct usb_function *f)
{
    return container_of(f, struct ptp_adapter, function);
}

static int set_connect_ready(const char *value, const struct kernel_param *param)
{
    bool ready;
    struct ptp_adapter *p, *next;
    int result = kstrtobool(value, &ready);
    if (result) return result;
    mutex_lock(&gate_lock);
    if (!ready && !list_empty(&gated_functions)) { result = -EBUSY; goto out; }
    if (ready) {
        list_for_each_entry_safe(p, next, &gated_functions, gate_node) {
            result = usb_function_activate(&p->function);
            if (result) goto out;
            p->function.bind_deactivated = false;
            list_del_init(&p->gate_node);
        }
    }
    connect_ready = ready;
out:
    mutex_unlock(&gate_lock);
    return result;
}
static const struct kernel_param_ops ready_ops = {
    .set = set_connect_ready, .get = param_get_bool,
};
module_param_cb(connect_ready, &ready_ops, &connect_ready, 0600);

static bool ptp_match(struct usb_function *f, const struct usb_ctrlrequest *ctrl,
                      bool config0)
{
    return (ctrl->bRequestType == 0x40 &&
            (ctrl->bRequest == 0x51 || ctrl->bRequest == 0x40)) ||
           (ctrl->bRequestType == 0xc0 && ctrl->bRequest == 0x53);
}

static int ptp_setup(struct usb_function *f, const struct usb_ctrlrequest *ctrl)
{
    struct usb_composite_dev *cdev = f->config->cdev;
    struct usb_request *request = cdev->req;
    unsigned int size = 0;
    int result;

    if (!ptp_match(f, ctrl, false))
        return -EOPNOTSUPP;
    if (ctrl->bRequestType == 0xc0) {
        __le32 value = cpu_to_le32(1);
        size = min_t(unsigned int, le16_to_cpu(ctrl->wLength), sizeof(value));
        memcpy(request->buf, &value, size);
    } else {
        if (le16_to_cpu(ctrl->wLength))
            return -EOPNOTSUPP;
        if (ctrl->bRequest == 0x51) {
            result = aibox_usb_notify(1);
            pr_info("aibox: switch request, notification result=%d\n", result);
        }
    }
    request->zero = 0;
    request->length = size;
    return usb_ep_queue(cdev->gadget->ep0, request, GFP_ATOMIC);
}

static void ptp_disable(struct usb_function *f)
{
    struct ptp_adapter *p = to_ptp(f);
    if (p->kind) return;
    if (p->in && p->in->enabled) usb_ep_disable(p->in);
    if (p->out && p->out->enabled) usb_ep_disable(p->out);
    if (p->interrupt && p->interrupt->enabled) usb_ep_disable(p->interrupt);
}

static int ptp_alt(struct usb_function *f, unsigned int interface, unsigned int alt)
{
    struct ptp_adapter *p = to_ptp(f);
    struct usb_ep *endpoints[] = {p->in, p->out, p->interrupt};
    int i, result;
    /* The baseline mux/VSC modules advertise endpoints but have no bulk service. */
    if (p->kind) return alt <= (p->kind == 2 ? 2 : 0) ? 0 : -EINVAL;
    if (alt) return -EINVAL;
    ptp_disable(f);
    for (i = 0; i < ARRAY_SIZE(endpoints); i++) {
        result = config_ep_by_speed(f->config->cdev->gadget, f, endpoints[i]);
        if (!result) result = usb_ep_enable(endpoints[i]);
        if (result) { ptp_disable(f); return result; }
    }
    return 0;
}

static int ptp_bind(struct usb_configuration *config, struct usb_function *f)
{
    struct ptp_adapter *p = to_ptp(f);
    struct usb_gadget *gadget = config->cdev->gadget;
    struct usb_descriptor_header *fs[9] = { (void *)&p->interface };
    struct usb_descriptor_header *hs[9] = { (void *)&p->interface };
    int id = usb_interface_id(config, f);
    if (id < 0) return id;
    p->interface.bInterfaceNumber = id;
    p->alt1.bInterfaceNumber = p->alt2.bInterfaceNumber = id;
    p->in = usb_ep_autoconfig(gadget, &p->fs_in);
    p->out = usb_ep_autoconfig(gadget, &p->fs_out);
    if (!p->kind) p->interrupt = usb_ep_autoconfig(gadget, &p->fs_interrupt);
    if (!p->in || !p->out || (!p->kind && !p->interrupt)) return -ENODEV;
    p->hs_in.bEndpointAddress = p->fs_in.bEndpointAddress;
    p->hs_out.bEndpointAddress = p->fs_out.bEndpointAddress;
    p->hs_interrupt.bEndpointAddress = p->fs_interrupt.bEndpointAddress;
    if (p->kind == 2) {
        fs[1] = hs[1] = (void *)&p->alt1;
        fs[2] = (void *)&p->fs_in; hs[2] = (void *)&p->hs_in;
        fs[3] = (void *)&p->fs_out; hs[3] = (void *)&p->hs_out;
        fs[4] = hs[4] = (void *)&p->alt2;
        fs[5] = (void *)&p->fs_out; hs[5] = (void *)&p->hs_out;
        fs[6] = (void *)&p->fs_in; hs[6] = (void *)&p->hs_in;
    } else {
        fs[1] = (void *)&p->fs_in; hs[1] = (void *)&p->hs_in;
        fs[2] = (void *)&p->fs_out; hs[2] = (void *)&p->hs_out;
        if (!p->kind) { fs[3] = (void *)&p->fs_interrupt; hs[3] = (void *)&p->hs_interrupt; }
    }
    id = usb_assign_descriptors(f, fs, hs, NULL, NULL);
    if (!id && f->bind_deactivated) {
        mutex_lock(&gate_lock);
        list_add_tail(&p->gate_node, &gated_functions);
        mutex_unlock(&gate_lock);
    }
    return id;
}

static void ptp_unbind(struct usb_configuration *c, struct usb_function *f)
{
    struct ptp_adapter *p = to_ptp(f);
    mutex_lock(&gate_lock);
    list_del_init(&p->gate_node);
    mutex_unlock(&gate_lock);
    usb_free_all_descriptors(f);
}

static void ptp_free(struct usb_function *f) { kfree(to_ptp(f)); }

static struct usb_function *adapter_alloc(unsigned int kind)
{
    struct ptp_adapter *p = kzalloc(sizeof(*p), GFP_KERNEL);
    struct usb_endpoint_descriptor bulk = {
        .bLength = USB_DT_ENDPOINT_SIZE, .bDescriptorType = USB_DT_ENDPOINT,
        .bmAttributes = USB_ENDPOINT_XFER_BULK, .wMaxPacketSize = cpu_to_le16(64)};
    if (!p) return ERR_PTR(-ENOMEM);
    p->kind = kind;
    INIT_LIST_HEAD(&p->gate_node);
    p->function.bind_deactivated = !kind && gate_connect && !connect_ready;
    p->interface = (struct usb_interface_descriptor){
        .bLength = USB_DT_INTERFACE_SIZE, .bDescriptorType = USB_DT_INTERFACE,
        .bNumEndpoints = 3, .bInterfaceClass = USB_CLASS_STILL_IMAGE,
        .bInterfaceSubClass = 1, .bInterfaceProtocol = 1};
    p->fs_in = bulk; p->fs_in.bEndpointAddress = USB_DIR_IN;
    p->fs_out = bulk; p->fs_out.bEndpointAddress = USB_DIR_OUT;
    p->fs_interrupt = (struct usb_endpoint_descriptor){
        .bLength = USB_DT_ENDPOINT_SIZE, .bDescriptorType = USB_DT_ENDPOINT,
        .bEndpointAddress = USB_DIR_IN, .bmAttributes = USB_ENDPOINT_XFER_INT,
        .wMaxPacketSize = cpu_to_le16(64), .bInterval = 10};
    p->hs_in = p->fs_in; p->hs_in.wMaxPacketSize = cpu_to_le16(512);
    p->hs_out = p->fs_out; p->hs_out.wMaxPacketSize = cpu_to_le16(512);
    p->hs_interrupt = p->fs_interrupt;
    if (kind) {
        p->interface.bNumEndpoints = kind == 2 ? 0 : 2;
        p->interface.bInterfaceClass = 255;
        p->interface.bInterfaceSubClass = kind == 2 ? 253 : 254;
        p->interface.bInterfaceProtocol = kind == 2 ? 1 : 2;
        p->alt1 = p->alt2 = p->interface;
        p->alt1.bAlternateSetting = 1; p->alt2.bAlternateSetting = 2;
        p->alt1.bNumEndpoints = p->alt2.bNumEndpoints = 2;
    }
    p->function.name = kind == 2 ? "aibox_vsc" : kind == 1 ? "aibox_mux" : "aibox_ptp";
    p->function.bind = ptp_bind; p->function.unbind = ptp_unbind;
    p->function.set_alt = ptp_alt; p->function.disable = ptp_disable;
    p->function.setup = ptp_setup; p->function.req_match = ptp_match;
    p->function.free_func = ptp_free;
    return &p->function;
}
static struct usb_function *ptp_alloc(struct usb_function_instance *i) { return adapter_alloc(0); }
static struct usb_function *mux_alloc(struct usb_function_instance *i) { return adapter_alloc(1); }
static struct usb_function *vsc_alloc(struct usb_function_instance *i) { return adapter_alloc(2); }

static void instance_release(struct usb_function_instance *instance) { kfree(instance); }
static void ptp_item_release(struct config_item *item)
{
    usb_put_function_instance(container_of(to_config_group(item),
                              struct usb_function_instance, group));
}
static struct configfs_item_operations ptp_item_operations = { .release = ptp_item_release };
static const struct config_item_type ptp_instance_type = {
    .ct_owner = THIS_MODULE, .ct_item_ops = &ptp_item_operations };
static struct usb_function_instance *ptp_instance(void)
{
    struct usb_function_instance *instance = kzalloc(sizeof(*instance), GFP_KERNEL);
    if (!instance) return ERR_PTR(-ENOMEM);
    instance->free_func_inst = instance_release;
    config_group_init_type_name(&instance->group, "", &ptp_instance_type);
    return instance;
}

static struct usb_function_driver ptp_driver = {
    .name = "aibox_ptp", .mod = THIS_MODULE,
    .alloc_inst = ptp_instance, .alloc_func = ptp_alloc};
static struct usb_function_driver mux_driver = {
    .name = "aibox_mux", .mod = THIS_MODULE,
    .alloc_inst = ptp_instance, .alloc_func = mux_alloc};
static struct usb_function_driver vsc_driver = {
    .name = "aibox_vsc", .mod = THIS_MODULE,
    .alloc_inst = ptp_instance, .alloc_func = vsc_alloc};

static int __init adapter_init(void)
{
    struct netlink_kernel_cfg cfg = {0};
    int result;
    notify_socket = netlink_kernel_create(&init_net, 26, &cfg);
    if (!notify_socket) return -EADDRINUSE;
    result = usb_function_register(&ptp_driver);
    if (result) goto socket_error;
    result = usb_function_register(&mux_driver);
    if (result) goto ptp_error;
    result = usb_function_register(&vsc_driver);
    if (!result) return 0;
    usb_function_unregister(&mux_driver);
ptp_error:
    usb_function_unregister(&ptp_driver);
socket_error:
    netlink_kernel_release(notify_socket); notify_socket = NULL;
    return result;
}
static void __exit adapter_exit(void)
{
    usb_function_unregister(&ptp_driver);
    usb_function_unregister(&mux_driver);
    usb_function_unregister(&vsc_driver);
    cancel_delayed_work_sync(&ready_work);
    netlink_kernel_release(notify_socket);
}
module_init(adapter_init);
module_exit(adapter_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Pi CarPlay bring-up PTP control and legacy netlink adapter; no PTP bulk service");
