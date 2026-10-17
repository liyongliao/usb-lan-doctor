#!/system/bin/sh
section() { echo; echo "========== $1 =========="; }
section DEVICE
getprop ro.product.model
getprop ro.build.version.release
uname -a
id
date
cat /proc/uptime
section USB
dumpsys usb
section DRIVERS
ls /sys/bus/usb/drivers
section NETWORK
ip address
ip route show table all
dumpsys ethernet
section USB_LINK_DETAILS
for n in /sys/class/net/*; do
 path=$(readlink -f "$n/device" 2>/dev/null)
 case "$path" in */usb[0-9]*/*) ;; *) continue;; esac
 name=${n##*/}
 echo "Interface: $name; device: $path"
 # Preserve read errors: admin DOWN and missing permission must not look like no cable.
 for f in ifindex flags operstate carrier carrier_changes carrier_up_count carrier_down_count speed duplex dormant; do
  echo "$f: $(cat "$n/$f" 2>&1)"
 done
 ip -details link show dev "$name" 2>&1
 if command -v ethtool >/dev/null 2>&1; then
  ethtool -i "$name" 2>&1
  ethtool "$name" 2>&1
  ethtool --show-eee "$name" 2>&1
 else echo 'ethtool 未安装；未读取 PHY/EEE 详情'; fi
done
section USB_POWER_DETAILS
for d in /sys/bus/usb/devices/*; do
 [ -f "$d/idVendor" ] || continue
 echo "USB ${d##*/}: $(cat "$d/idVendor" 2>&1):$(cat "$d/idProduct" 2>&1)"
 for f in bcdDevice speed power/control power/runtime_status power/autosuspend_delay_ms power/runtime_active_time power/runtime_suspended_time; do
  echo "$f: $(cat "$d/$f" 2>&1)"
 done
done
section TYPE_C
for f in /sys/class/usb_role/*/role /sys/class/typec/port*/data_role /sys/class/typec/port*/power_role /sys/class/typec/port*/port_type; do [ -f "$f" ] && echo "$f: $(cat "$f" 2>&1)"; done
section KERNEL_USB
# Filter before truncating; do not dump unrelated application logs.
dmesg 2>&1 | grep -Ei 'usb|dwc3|xhci|r815|rtl815|asix|ax881|typec|otg|ethernet|avc:.*usb' | tail -300
section ANDROID_USB
logcat -d -v threadtime -t 1500 UsbHostManager:V UsbPortManager:V UsbDeviceManager:V EthernetTracker:V EthernetNetworkFactory:V '*:S' 2>&1 | tail -250
exit 0
