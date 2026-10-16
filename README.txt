USB 网卡医生 1.1.0

导航：状态 / 恢复 / 权限 / 记录。按钮支持按下反馈；执行时显示进度，结果和权限不足会明确提示。

一、无需 Root 的用法
直接打开即可查看 Android 已枚举的 USB 设备、以太网连接、IP / DNS 和网络验证结果。
在“恢复”采集诊断，在“记录”导出 TXT。基础诊断不要求解锁手机或刷机。

二、Shizuku / 无线 ADB
Android 11+：安装 Shizuku，在其内部按提示启用无线调试、配对并启动。
Android 8–10：使用电脑 ADB 启动 Shizuku。
返回“权限”，点击“连接 / 授权 Shizuku”，允许 USB 网卡医生。
ADB 模式应显示 UID 2000；UID 0 是 Root 模式。旧 Root 服务必须先停止才能切换为无线调试模式。
手机重启后一般需重新启动 Shizuku。

Shizuku / ADB 通常能读取系统 USB 和以太网日志、端口状态，也能尝试系统允许的 OTG 主机模式和数据连接恢复。
这些操作仍取决于系统、厂商权限和 USB HAL。命令执行完成不代表硬件已经恢复。

三、仍需要 Root 的操作
网卡重新授权、未绑定驱动重探测、USB 网卡休眠策略，需要写入内核 sysfs 节点。
ADB 权限不能代替 Root。应用会拒绝权限不足的请求，不自动切换到 Root。
已有 Root 时可在“权限”验证授权，然后选择网卡操作。

四、排查建议
完全没有 USB 提示：先保存诊断，检查 OTG 转接头、手机 OTG 开关、供电与线材；再尝试端口恢复。
已枚举但无接口：检查内核驱动。本工具不补装驱动，不抢占网卡 USB 接口。
接口已创建但不上网：检查网线、交换机、IP / DNS 和网络验证。
USB 总线速度不等于以太网协商速率，2.5G 网卡不代表手机实际吞吐达到 2.5Gbps。
USB 访问授权只针对已枚举的设备，不能代替网卡驱动或解决未枚举问题。

五、记录和限制
日志在本机保存，导出可能包含设备、IP 和端口信息，分享前请检查。
不自动恢复、不修改 SELinux 或开机项、不自动重启手机，不安装内核模块。
端口恢复可能断开 USB ADB 和其他外设，请勿在 USB 存储读写期间操作。

公开源码与 APK：https://github.com/liyongliao/usb-lan-doctor
Shizuku：https://shizuku.rikka.app/zh-hans/guide/setup/
第三方依赖：Shizuku API 13.1.5（MIT）、AndroidX annotation 1.3.0（Apache 2.0），许可随源码和 APK 提供。

验证说明：21 项脚本测试通过；普通诊断、Root 真机诊断和状态解析通过。无线 ADB 直接执行脚本确认 UID 2000 读取和内核写入拒绝。Shizuku UserService 已在 UID 0 下连接成功，ADB 模式端到端验证本次按用户要求跳过。
