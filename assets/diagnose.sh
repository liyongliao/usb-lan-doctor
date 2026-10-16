#!/system/bin/sh
section() { echo; echo "========== $1 =========="; }
section DEVICE
getprop ro.product.model
getprop ro.build.version.release
uname -a
id
section USB
dumpsys usb
section DRIVERS
ls /sys/bus/usb/drivers
section NETWORK
ip address
ip route show table all
dumpsys ethernet
section TYPE_C
for f in /sys/class/usb_role/*/role /sys/class/typec/port*/data_role /sys/class/typec/port*/power_role /sys/class/typec/port*/port_type; do [ -f "$f" ] && echo "$f: $(cat "$f" 2>&1)"; done
section KERNEL_USB
# Filter before truncating; do not dump unrelated application logs.
dmesg 2>&1 | grep -Ei 'usb|dwc3|xhci|r815|rtl815|asix|ax881|typec|otg|ethernet|avc:.*usb' | tail -300
section ANDROID_USB
logcat -d -v threadtime -t 1500 UsbHostManager:V UsbPortManager:V UsbDeviceManager:V EthernetTracker:V EthernetNetworkFactory:V '*:S' 2>&1 | tail -250
exit 0
