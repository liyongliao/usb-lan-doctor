USB 网卡医生 v1.0
交付日期：2026-10-16

用途
针对小米手机连接 ULT-unite RTL8156 USB 网卡后完全没有 USB 提示、偶发不能识别的情况，提供本地诊断与按需恢复。
应用包名：cn.usbdoctor
最低 Android 8.0（API 26），本次在你的 Android 15 小米 22127RK46C 上验证。

安装与 Root
APK 已安装到当前手机，应用名称为“USB 网卡医生”。
本次已验证应用通过 SukiSU 获取 Root（uid=0）。若以后授权失效，请在 SukiSU 的超级用户列表允许该应用，然后点击“申请 / 检查 Root”。
无联网权限；日志仅保存在手机本地，导出由系统文件保存器处理。

下次故障时
1. 拔掉电脑 USB 线，将网卡直接接到手机，打开应用并保持前台。
2. 先点击“采集完整诊断”，保存尚未恢复时的状态。
3. 完全没有 USB 枚举：先尝试“请求 OTG 主机模式”。此操作请求 source / host，适合手机向直接连接的网卡供电。
4. 仍不能识别时，可尝试“重启 USB 数据连接”。系统 USB HAL 可能不支持，结果会写入记录。该操作会短暂中断此端口全部 USB 数据连接；不要在 USB 存储读写期间使用。
5. 若已枚举网卡但没有网络接口，可使用“重新授权网卡 / 重新识别”或“重新探测未绑定的驱动”。
6. 若怀疑运行时休眠，可选择“禁止网卡休眠（本次连接）”。此设置可能增加耗电；“设置网卡自动电源管理”将 power/control 设为 auto，并非恢复任意历史设置。
7. 点击“导出诊断 TXT”保存日志。建议分别保存故障时和重启手机后正常连接时的日志用于对比。

结果如何判断
USB 枚举、驱动绑定、以太网链路、IP 与系统网络注册是不同阶段。
“命令已执行”或“请求已发送”并不证明网卡已恢复，必须看实际设备与网络状态。
USB 总线速度与网卡以太网协商速率不同：例如 USB 480 Mb/s 和以太网 2500 Mb/s 可以同时出现，实际吞吐受 USB 侧限制。
工具不能补齐缺失的内核驱动，也不能修复供电、线材或网卡硬件问题。
本工具不自动重启手机，不加载内核模块，不修改 SELinux 或开机启动配置。
仅在前台观察，不在后台自动恢复。

已完成验证
- APK v2/v3 签名验证通过，已安装并启动。
- 实机 Root uid=0、真实 USB 端口读取、完整诊断日志采集通过。
- 当前 USB 角色与支持的角色组合分开解析，避免误把 DEVICE 判断为 HOST。
- RTL8156 已绑定/未绑定状态、USB 与以太网速率分别显示：模拟状态的实机解析测试通过。
- 实机 Android shell 下验证非法设备路径被拒绝。
- 15 项隔离脚本测试通过，包括设备身份变化、Hub 保护、设备拔出、非网卡拒绝、重新授权恢复、探测已绑定接口时跳过。
- 临时测试 APK 已卸载，只保留正式工具。
尚未验证：接入真实网卡后的故障恢复效果、实际网络吞吐和手动 TXT 保存完整流程。

源码构建
源码 ZIP 内包含 Java、Manifest、资源、Shell 脚本和测试。
依赖：JDK 17、Android SDK platform android-35、build-tools 35.0.0；不依赖 Gradle 或第三方库。
运行 ./build.sh；可通过 ANDROID_HOME 指定 SDK，通过 OUTPUT_DIR 指定 APK 输出目录（默认为源码目录下 dist）。
在 macOS 上自动查找 JDK 17；其他系统请自行设置 JAVA_HOME。
运行 python3 tests/test_recovery.py 可执行隔离恢复脚本测试。
android 下的测试脚本用于已连接且已授予 Root 的专用实机测试，属于开发用途，不需要日常安装。
源码默认生成新的本地测试签名；若与手机已安装版本签名不同，需要先卸载原应用再安装，卸载会删除应用本地日志。当前签名保留在本次工作目录，不包含在源码 ZIP 中。

参考文档
Android USB Host：https://developer.android.com/develop/connectivity/usb/host
Linux USB 授权：https://docs.kernel.org/usb/authorization.html
Linux USB 电源管理：https://docs.kernel.org/driver-api/usb/power-management.html
