#!/system/bin/sh
# Never accepts arbitrary paths, drivers, interfaces or shell commands.
set -u
uid=$(id -u)
[ "$uid" = 0 ] || [ "$uid" = 2000 ] || { echo '需要 Root 或 Shizuku / ADB 授权'; exit 10; }
action=${1:-}; name=${2:-}; expected=${3:-}
case "$action" in
 host|cycle)
  port="$name"
  case "$port" in ''|*[!a-zA-Z0-9_.:-]*) echo '端口名称无效'; exit 11;; esac
  ports=$(dumpsys usb)
  printf '%s\n' "$ports"
  printf '%s\n' "$ports" | tr '{},' '\n\n\n' | sed -n 's/^[[:space:]]*id=\([^[:space:]]*\)[[:space:]]*$/\1/p' | grep -Fx "$port" >/dev/null || { echo '真实端口不存在，未执行'; exit 12; }
  printf '%s\n' "$ports" | grep -q 'is_simulation_active=true' && { echo '检测到模拟端口，拒绝操作'; exit 13; }
  if [ "$action" = host ]; then
   dumpsys usb set-port-roles "$port" source host || exit 14
   echo '已请求 source / host；请以刷新后的真实角色和枚举结果为准。'
  else
   # Restore even if interrupted; no reboot, controller unbind, or persistent property change.
   restore() { dumpsys usb enable-usb-data "$port" true; }
   trap restore EXIT HUP INT TERM
   dumpsys usb enable-usb-data "$port" false || exit 15
   sleep 2
   restore || exit 24
   trap - EXIT HUP INT TERM
   echo 'USB 数据连接重启请求已发送；旧 USB HAL 可能不支持，请查看端口和日志。'
  fi
  sleep 3
  dumpsys usb
  exit 0;;
 power|auto|reauth|probe|linkup|linkreset) ;;
 *) echo '不支持的操作'; exit 16;;
esac
[ "$uid" = 0 ] || { echo '此操作需要 Root；Shizuku 的 ADB 权限不能控制网卡接口或写入内核 USB 控制节点'; exit 10; }
case "$name" in ''|*[!0-9.-]*|*-*-*) echo 'USB 地址无效'; exit 17;; esac
printf '%s\n' "$name" | grep -Eq '^[0-9]+-[0-9]+(\.[0-9]+)*$' || exit 17
d="/sys/bus/usb/devices/$name"
[ -f "$d/idVendor" ] && [ -f "$d/idProduct" ] || { echo '设备已拔出'; exit 18; }
v=$(cat "$d/idVendor"); p=$(cat "$d/idProduct")
[ "$v:$p" = "$expected" ] || { echo '设备身份已变化，请重新选择'; exit 19; }
[ "$(cat "$d/bDeviceClass")" != 09 ] || { echo '禁止重置 USB Hub'; exit 20; }
# Only recognized Ethernet USB devices may be changed.
network=no
case "$v:$p" in 0bda:8152|0bda:8153|0bda:8155|0bda:8156|0bda:8157|0b95:1790|0b95:178a|0b95:772b|0b95:7720) network=yes;; esac
for i in "$d"/*:*; do
 drv=$(readlink "$i/driver" 2>/dev/null)
 case "${drv##*/}" in r8152|r8153_ecm|rtl8150|asix|ax88179_178a|aqc111|cdc_ether|cdc_ncm) network=yes;; esac
done
[ "$network" = yes ] || { echo '未确认是受支持的网卡，拒绝修改'; exit 21; }
case "$action" in
 linkup|linkreset)
  # Derive the interface from the selected USB device; never accept an arbitrary name.
  count=0; net=''; iface=''
  for i in "$d"/*:*; do
   for n in "$i"/net/*; do
    [ -d "$n" ] || continue
    count=$((count + 1)); net=${n##*/}; iface=$i
   done
  done
  [ "$count" = 1 ] || { echo "此网卡发现 $count 个网络接口，仅支持明确的单接口设备；未修改"; exit 26; }
  case "$net" in ''|*[!a-zA-Z0-9_.-]*) echo '接口名称无效'; exit 27;; esac
  n="/sys/class/net/$net"
  index=$(cat "$n/ifindex" 2>/dev/null)
  case "$index" in ''|*[!0-9]*) echo '无法确认接口身份'; exit 28;; esac
  same_interface() {
   [ "$(cat "$d/idVendor" 2>/dev/null):$(cat "$d/idProduct" 2>/dev/null)" = "$expected" ] &&
   [ "$(cat "$n/ifindex" 2>/dev/null)" = "$index" ] &&
   [ "$(readlink -f "$n/device" 2>/dev/null)" = "$(readlink -f "$iface" 2>/dev/null)" ]
  }
  same_interface || { echo '接口已移除或不属于选定网卡'; exit 28; }
  flags=$(cat "$n/flags" 2>/dev/null)
  case "$flags" in 0x*) hex=${flags#0x};; *) echo '无法读取接口管理状态'; exit 28;; esac
  case "$hex" in ''|*[!0-9a-fA-F]*) echo '接口管理状态无效'; exit 28;; esac
  was_up=$((flags & 1))
  restore_link() {
   if [ "$was_up" = 1 ]; then
    if same_interface; then ip link set dev "$net" up || echo '恢复原 UP 状态失败，请重新检查接口';
    else echo '接口身份变化，未向替换设备发送恢复命令'; fi
   fi
  }
  if [ "$action" = linkreset ]; then
   trap restore_link EXIT
   trap 'exit 31' HUP INT TERM
   same_interface || exit 28
   ip link set dev "$net" down || exit 29
   sleep 1
  fi
  same_interface || { echo '接口身份变化，停止操作'; exit 28; }
  ip link set dev "$net" up || { echo '启用接口失败'; exit 30; }
  trap - EXIT HUP INT TERM
  echo "已请求 $net 启用；观察 6 秒，不将命令成功等同于网络恢复。"
  for tick in 1 2 3; do
   sleep 2
   same_interface || { echo '观察期间设备已移除或接口身份变化'; exit 28; }
   echo "观察 $tick: flags=$(cat "$n/flags" 2>&1) operstate=$(cat "$n/operstate" 2>&1) carrier=$(cat "$n/carrier" 2>&1) speed=$(cat "$n/speed" 2>&1)"
  done
  ip address show dev "$net"
  ;;
 power) echo on > "$d/power/control" || exit 22; echo "本次连接已禁用网卡自动休眠：$(cat "$d/power/control")";;
 auto) echo auto > "$d/power/control" || exit 22; echo "已设置自动电源管理：$(cat "$d/power/control")";;
 reauth)
  restore() { [ ! -e "$d/authorized" ] || echo 1 > "$d/authorized"; }
  trap restore EXIT HUP INT TERM
  echo 0 > "$d/authorized" || exit 23
  sleep 1
  restore || exit 24
  trap - EXIT HUP INT TERM
  echo '已重新授权当前网卡，等待驱动重新探测'; sleep 3;;
 probe)
  for i in "$d"/*:*; do
   [ -d "$i" ] || continue
   if [ -L "$i/driver" ]; then echo "${i##*/} 已绑定驱动，跳过"; continue; fi
   echo "${i##*/}" > /sys/bus/usb/drivers_probe || exit 25
   echo "已请求探测 ${i##*/}"
  done;;
esac
exit 0
