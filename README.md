# USB 网卡医生

Android USB 以太网诊断工具，用于排查 RTL8156 等网卡插入后没有 USB 提示、偶发无法识别、已枚举但无法联网的问题。

包名：`cn.usbdoctor` · Android 8.0+ · 原生 Java 界面 · 无联网权限

[下载最新版 APK](https://github.com/liyongliao/usb-lan-doctor/releases/latest) · [使用说明](README.txt)

## 1.2.0 更新

- 修正“等待网线”误判：分别显示接口未启用、驱动未报告链路、链路状态未知、链路已建立、等待就绪。
- `carrier` 读取失败不再等同于未连接；Android 注册了以太网也不再替代 USB 网卡的链路判断。多接口状态不一致时分别展示，断链时不显示残留协商速率。
- 新增 Root 下的“启用网卡网络接口”“重启网卡网络接口”，只接受从选定 USB 网卡推导出的单一接口，校验 VID/PID、接口归属与 ifindex；失败时尝试恢复原 UP 状态，设备身份变化时停止。
- 新增管理标志、链路变化计数、运行时电源状态、可用时的只读 ethtool / EEE 诊断；分开显示 IP、DNS、路由和系统互联网验证。
- 保留四栏导航及按钮反馈。已完成构建、签名校验、33 项恢复脚本测试和 15 项链路分类测试；**1.2.0 尚未在故障手机实测，不能保证代替重启恢复网络**。

## 1.1.0 更新

- **四栏导航**：状态、恢复、权限、记录，采用悬浮圆角底栏、线条图标及选中高亮。
- **操作反馈**：按钮按下波纹、轻微缩放和触感反馈；全局进度提示、禁用状态及明确的失败原因。
- **普通诊断**：无需 Root 即可读取 Android USB 枚举和以太网网络状态。
- **Shizuku 接入**：通过官方 UserService 在 ADB 或 Root 身份下执行应用自带的有限操作，显示实际 UID。
- 保留 Root 诊断和网卡恢复能力，增加 ADB 权限边界与真实端口精确匹配检查。

## 不同权限能做什么

| 功能 | 普通应用 | Shizuku / 无线 ADB（UID 2000） | Root（UID 0） |
| --- | --- | --- | --- |
| Android USB 设备枚举、以太网/IP/DNS/网络验证 | 支持 | 支持 | 支持 |
| USB 系统服务、端口角色、过滤后的系统日志 | 受限 | 通常支持 | 通常支持 |
| 请求 OTG 主机模式、重启 USB 数据连接 | 不支持 | 尝试系统服务，受 ROM / HAL 限制 | 同样受 HAL 限制 |
| 网卡接口启用 / 重启、休眠策略、重新授权、驱动重探测 | 不支持 | 不支持 | 支持相应内核节点时可尝试 |
| 补装内核缺失的网卡驱动、修复线材或供电 | 不支持 | 不支持 | 本工具不提供 |

**Shizuku 不等于 Root。** 无线调试启动的 Shizuku 使用 ADB 权限；Root 启动的 Shizuku 才具有 Root 权限。应用检查实际权限，不把已安装或已授权 Shizuku 当成 Root。

“请求已执行”不代表网卡已经恢复。系统可能接受请求但硬件状态未改变，或者 USB HAL 不支持操作；应核对刷新后的端口、枚举和联网状态。

## 推荐使用流程

1. 直接打开应用查看“状态”，先使用普通诊断。故障时在“恢复”采集诊断，再到“记录”导出。
2. 无 Root 手机可安装 [Shizuku](https://shizuku.rikka.app/download/)。Android 11+ 可在 Shizuku 内通过无线调试配对并启动；Android 8–10 可借助电脑 ADB 启动。
3. 在“权限”点击“连接 / 授权 Shizuku”，允许本应用。确认显示 **Shizuku · ADB / UID 2000**。
4. 手机重启后通常需要重新启动 Shizuku；如旧 Root 服务还在运行，应先在 Shizuku 停止旧服务，再用无线调试启动。
5. 网卡完全未枚举时，可在“恢复”尝试请求 OTG 主机模式。已识别但重启手机才联网时，先保存故障诊断，按下方链路状态排查，不直接归因为网线或缺驱动。
6. 已有 Root 的设备，可以在“权限”验证 Root，再对已确认的网卡操作内核节点。

USB 总线速度和以太网协商速率是两回事。USB 访问授权只允许应用访问已枚举的设备，不能代替内核网卡驱动，也不能让尚未枚举的设备出现。本应用不抢占 USB 网卡接口。

## 已识别网卡，但重启手机才有网络

先在故障状态采集诊断，再做单项恢复，随后再次采集。重启后正常时也保存一份，便于比较。无需先拔掉网卡换接电脑；可以在手机内操作和导出。

| 状态 | 含义与下一步 |
| --- | --- |
| 接口未启用 | IFF_UP 未置位；Root 可先尝试“启用网卡网络接口” |
| 驱动未报告链路 | carrier=0；可能涉及协商、初始化、休眠或物理连接，不能断定没插网线；Root 可尝试“重启网卡网络接口” |
| 链路状态未知 | carrier 不可读或无效；先用 Shizuku / Root 取得诊断，不猜测物理连接 |
| 链路已建立 | carrier=1；继续检查对应接口 IP、路由和系统以太网状态，互联网验证单独判断 |
| 等待就绪 | carrier=1，但 operstate 为 dormant/testing；需要检查协议与系统状态 |

重启接口未改善时，可单独尝试“禁止网卡休眠（本次连接）”，再尝试“重新授权网卡 / 重新识别”，每一步保留前后记录。重启接口会短暂断网，不修改 MAC、静态 IP 或协商速率。工具不会因 carrier=0 自动进行恢复；Root 操作也不保证解决内核或固件问题。

## 电脑 ADB 诊断

应用不内置另一套 ADB 配对客户端；无线调试通过 Shizuku 集成。也可直接在电脑运行以下只读命令：

```sh
adb shell dumpsys usb
adb shell dumpsys ethernet
adb shell ip address
```

在连接电脑时，USB 端口通常处于从设备模式；换接网卡后再观察 HOST 模式。切换端口或数据连接会中断该端口上的外设与 USB ADB，请避免 USB 存储正在读写时操作。

## 隐私与实现

不自动重启、不自动恢复、不修改 SELinux 或开机项，不安装内核模块。日志保存在本地，导出可能包含设备信息、IP 和端口历史，分享前请检查。

Shizuku 服务仅执行 APK 中的固定诊断和恢复脚本，不接受任意命令；脚本通过标准输入传给 shell，避免 ADB 身份无法读取应用私有目录的问题。普通模式不使用已保存的 Root 授权，Shizuku 断开不会自动切换为 Root。

## 构建

需要 JDK 17、Android SDK Platform 35 / Build Tools 35.0.0，以及 `curl`、`zip`、`unzip`、`shasum`。无需 Gradle。

```sh
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
./build.sh
```

输出 `dist/USB-LAN-Doctor.apk`；使用 `OUTPUT_DIR` 可改变输出目录。脚本下载并校验 Shizuku API 13.1.5 和 AndroidX annotation 1.3.0，依赖哈希及许可位于 `third_party/`，许可证同时打入 APK。

首次构建会生成本地签名 `build/local-test.jks`；私钥不会提交。保留原包名和发布签名，Release 可覆盖旧版并保留日志；自行构建的不同签名 APK 不能直接覆盖已有安装。

## 验证

```sh
python3 tests/test_recovery.py
sh tests/test_link_state.sh
```

33 项隔离脚本测试覆盖原有权限和 USB 保护，以及网卡接口归属、启停顺序、失败恢复和接口替换。15 项 Java 测试覆盖不可读 carrier、管理状态 DOWN、dormant/testing 和残留速率。

`tests/android/SmokeTest.java` 用于已有 Root 的设备；`NoRootTest.java` 验证普通模式、四栏导航、Shizuku UID 2000、系统诊断和内核写入拒绝。两者不执行真实 USB 端口切换或网卡重置。运行时应先安装同签名测试 APK，再启动对应 instrumentation；部分小米系统需要随后用 `adb shell am start -n cn.usbdoctor/.MainActivity` 拉起界面。

1.1.0 已在 Android 15 真机验证普通权限诊断、Root 诊断、真实 USB 端口读取、状态解析和非法参数保护；Shizuku UserService 已验证连接及 UID 0 身份。通过无线 ADB 直接执行脚本已确认 UID 2000 的诊断及内核写入拒绝。按用户要求，本次跳过 Shizuku ADB 模式端到端验证；不将 Root 模式结果等同于无 Root 验证。具体验证结果见对应 Release。实际故障恢复、所有厂商 ROM 兼容性、吞吐以及手动 TXT 保存完整流程不作保证。

## 参考与第三方许可

- [Android USB Host](https://developer.android.com/develop/connectivity/usb/host)
- [Shizuku 官方开发文档：权限差异与 UserService](https://github.com/RikkaApps/Shizuku-API)
- [Shizuku 无线调试启动说明](https://shizuku.rikka.app/zh-hans/guide/setup/)
- [AOSP USB 服务命令实现](https://android.googlesource.com/platform/frameworks/base/+/HEAD/services/usb/java/com/android/server/usb/UsbService.java)
- [Linux interface operational states](https://www.kernel.org/doc/html/latest/networking/operstates.html)
- [Linux USB authorization](https://docs.kernel.org/usb/authorization.html)
- [Linux USB power management](https://docs.kernel.org/driver-api/usb/power-management.html)
- [第三方组件声明](third_party/NOTICE.txt)
