#!/system/bin/sh
# Only structured, read-only diagnostic output.
if [ ! -r /sys/bus/usb/devices ]; then echo 'LIMIT|USB sysfs 无读取权限'; fi
for d in /sys/bus/usb/devices/*; do
 [ -f "$d/idVendor" ] || continue
 n=${d##*/}; v=$(cat "$d/idVendor" 2>/dev/null); p=$(cat "$d/idProduct" 2>/dev/null)
 c=$(cat "$d/bDeviceClass" 2>/dev/null); [ "$c" = 09 ] && continue
 product=$(cat "$d/product" 2>/dev/null | tr '|\n' '  ')
 speed=$(cat "$d/speed" 2>/dev/null); power=$(cat "$d/power/control" 2>/dev/null)
 drivers=''; nets=''
 for i in "$d"/*:*; do
  [ -d "$i" ] || continue
  drv=$(readlink "$i/driver" 2>/dev/null); [ -n "$drv" ] && drivers="$drivers ${drv##*/}"
  for net in "$i"/net/*; do [ -d "$net" ] && nets="$nets ${net##*/}"; done
 done
 echo "DEV|$n|$v|$p|$product|$speed|$drivers|$nets|$power"
done
for n in /sys/class/net/*; do
 path=$(readlink -f "$n/device" 2>/dev/null)
 case "$path" in */usb[0-9]*/*) ;; *) continue;; esac
 name=${n##*/}; state=$(cat "$n/operstate" 2>/dev/null); carrier=$(cat "$n/carrier" 2>/dev/null)
 speed=$(cat "$n/speed" 2>/dev/null); duplex=$(cat "$n/duplex" 2>/dev/null)
 flags=$(cat "$n/flags" 2>/dev/null)
 echo "NET|$name|$state|$carrier|$speed|$duplex|$flags"
done
for r in /sys/class/usb_role/*/role; do [ -f "$r" ] && echo "ROLE|$r|$(cat "$r" 2>/dev/null)"; done
uid=$(id -u)
echo "UID|$uid"
if [ "$uid" = 0 ]; then echo 'ROOT|yes'; else echo 'ROOT|no'; fi
if [ "$uid" = 0 ] || [ "$uid" = 2000 ]; then dumpsys usb 2>&1; fi
exit 0
