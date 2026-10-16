# USB 网卡医生

面向已 Root 的 Android 手机的 USB 以太网诊断工具，重点排查 RTL8156 网卡插入后没有 USB 提示、偶发无法识别的问题。

包名：`cn.usbdoctor` · Android 8.0+ · Java 原生界面 · 无联网权限

## 下载

在 [Releases](https://github.com/liyongliao/usb-lan-doctor/releases) 下载已签名的 APK、源码包和中文使用说明。

## 功能

- 前台观察 USB 插拔、设备枚举、驱动绑定及网络接口。
- 分别显示 USB 总线速度与以太网协商速率。
- 通过 Root 请求 OTG 主机模式、重启 USB 数据连接。
- 对已确认的网卡执行重新授权、探测未绑定驱动、调整本次连接的自动休眠策略。
- 采集 USB、内核与以太网诊断日志，通过系统文件保存器导出 TXT。

## 使用

1. 安装 APK，在 SukiSU / KernelSU / Magisk 中允许应用使用 Root，然后点击“申请 / 检查 Root”。
2. 断开电脑 USB 线，把网卡接到手机，保持应用在前台。
3. 故障发生时先点击“采集完整诊断”。完全没有枚举时，可尝试“请求 OTG 主机模式”。
4. 已枚举但未创建网络接口时，可尝试重新授权或探测未绑定驱动。
5. 分别保存故障时和正常连接时的日志，用于对比。

完整说明见 [README.txt](README.txt)。

恢复请求执行完成不等于网卡已恢复，需检查实际枚举与联网状态。USB HAL 可能不支持重启数据连接。端口操作会中断 USB 连接，不应在 USB 存储读写期间使用。

工具不自动重启手机，不安装内核模块，不修改 SELinux 或开机配置。日志保存在本地；导出文件可能包含 IP 和设备信息。

## 构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0，以及 `zip`。不依赖 Gradle 或第三方库。

```sh
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
./build.sh
```

macOS 可省略环境变量并使用脚本默认路径。输出为 `dist/USB-LAN-Doctor.apk`，可用 `OUTPUT_DIR` 更改目录。

首次构建会创建本地测试签名 `build/local-test.jks`；签名文件不提交到仓库。请自行妥善保留签名，以便后续更新。不同签名无法直接覆盖已有安装；卸载会删除本地日志。

## 测试

```sh
python3 tests/test_recovery.py
```

15 项隔离测试覆盖设备身份校验、Hub 和路径保护、拔出处理、休眠策略、重新授权恢复，以及驱动探测。

实机测试需要已连接的专用 Android 手机，并已为主应用授予 Root：

```sh
./build.sh
bash tests/android/build-test.sh
adb install -r dist/USB-LAN-Doctor.apk
adb install -r build/test.apk
adb shell am instrument -w cn.usbdoctor.tests/cn.usbdoctor.SmokeTest
adb uninstall cn.usbdoctor.tests
```

2026-10-16 已在 Android 15 小米手机上验证安装、Root、真实端口读取、诊断采集与非法参数保护；网卡状态解析使用模拟数据验证。实际故障恢复、吞吐和手动 TXT 保存完整流程尚未验证。

## 参考

- [Android USB Host](https://developer.android.com/develop/connectivity/usb/host)
- [Linux USB authorization](https://docs.kernel.org/usb/authorization.html)
- [Linux USB power management](https://docs.kernel.org/driver-api/usb/power-management.html)
