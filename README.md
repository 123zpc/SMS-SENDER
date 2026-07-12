# 📱 SMS SENDER

[![Android Build](https://github.com/123zpc/SMS-SENDER/actions/workflows/android-build.yml/badge.svg)](https://github.com/123zpc/SMS-SENDER/actions)
[![Release Version](https://img.shields.io/badge/release-v1.2.0-blue.svg)](https://github.com/123zpc/SMS-SENDER/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

**SMS SENDER** 是一款专为高可靠性、极低功耗设计的 Android 短信自动转发与防漏补偿系统。项目基于现代 **Material 3** 规范构建，旨在打通移动终端与各种云端推送（如 Bark 等）的通信桥梁。无论是个人的验证码提取、通知分发还是系统状态监控，SMS SENDER 均能提供工业级的稳定性保障。

---

## ⚡ 核心架构与工程设计

### 1. 🌐 双轨并发冗余监听架构 (Dual-Track Redundant System)
为应对国产 Android 手机对后台进程、广播分发以及系统休眠的限制，SMS SENDER 采用**双轨并发冗余监听架构**，从根本上杜绝漏发：
*   **第一轨（系统广播捕获）**：基于 `Telephony.SMS_RECEIVED` 静态广播，在系统分发层面秒级拉起，无需 App 常驻前台即可瞬时捕获短信流。
*   **第二轨（收件箱数据库监视）**：依托 `ContentObserver` 实时监视 `content://sms/inbox` 系统收件箱变动。确保即使在系统广播丢失或被系统策略拦截时，也能直接在数据库写入层捞取短信。

### 2. 🛡️ Room 级毫秒防抖与高并发去重 (Room-based Deduplication)
双轨并发势必带来重复数据的处理挑战。SMS SENDER 在本地集成 **Room 数据库**，设计了毫秒级去重引擎：
*   利用 `(发件人 + 内容).hashCode()` 生成消息的唯一哈希指纹。
*   每次接收到新消息时，在毫秒级内检索近 30 秒的指纹记录，重复指纹消息一律予以物理忽略，确保下游推送不被短信刷屏。

### 3. 🔋 智能休眠与极低功耗保活 (Ultra-low Power Keeping)
*   **短时唤醒锁 (Short-lived WakeLock)**：系统仅在 OkHttp 网络请求发起至响应/异常的极短生命周期内持有 `WakeLock`，请求完毕立刻释放，避免 CPU 处于高耗电工作态。
*   **低功耗前台服务**：采用 `IMPORTANCE_MIN` 最低优先级通知维持前台服务优先级。不设置死循环，不做高频无意义轮询，完美契合 HyperOS / MIUI 等系统的绿色功耗要求。

### 4. 🔄 网络恢复补偿重试 (Event-driven Retries via WorkManager)
*   当转发时遇到无网络或接口异常，短信记录入库标记为 `FAILED`。
*   系统不会常驻高能耗定时器去重新连接，而是使用 **WorkManager** 挂起一个一次性约束任务——仅当系统检测到网络重新连接（`NetworkType.CONNECTED`）时执行一次补偿扫库，自动将积压的失败记录打包重发，极致省电且优雅。

---

## 🎨 全新 UI/UX 设计理念

SMS SENDER 基于 **深空科技蓝 (#2563EB)** 与 **落日珊瑚橙 (#F97316)** 的冷暖双色美学设计，并融合了 iOS **液态玻璃** 的动态视觉质感：
*   **配置 (Config)**：将 API 配置及后台授权置于第一屏，采用大圆角 `TextInputLayout`，提供即开即用体验。
*   **历史 (History)**：支持一键多选历史短信，批量触发**手动补发**。支持发信人/时间还原为垂直双行排布，去除了冗余的通知栏小图标，清爽干练。
*   **信息 (Info)**：集成了关于软件（开发者 ZPC，GitHub 极简跳转，当前版本）以及系统权限的实时状态监控，对小米“通知类短信”进行一键跳转支持。
*   **日志 (Console)**：内置全功能白底黑字控制台，带有 **`16dp` 圆角包围**。右侧以**一体化组件形式内嵌发送按钮**，不仅杜绝了高度错落，更支持一键复制与日志导出。

---

## ⚙️ 远程推送 API 配置

在首屏 API 配置框中，您可以填写符合您推送接收端的 HTTP API URL。

### 推荐模板 (以 Bark 为例)
```text
https://api.day.app/您的Key/{title}/{body}
```

### 可用动态占位符
| 占位符 | 描述 | 示例 |
|---|---|---|
| `{title}` | 智能生成的标题（验证码短信为“验证码 123456”，普通短信为“发件人号码”） | 验证码 654321 |
| `{sender}` | 发件人号码 | +86 10086 |
| `{time}` | 消息接收的格式化本地时间 (yyyy-MM-dd HH:mm:ss) | 2026-07-12 21:05:00 |
| `{body}` | 完整的短信正文内容 | 【中国移动】您的验证码是 654321，请于 5 分钟内输入。 |
| `{code}` | 智能正则提取的 4 到 8 位验证码数字，未匹配时为空 | 654321 |

---

## 🔒 权限指南 & 厂商适配

### 声明权限
项目在配置文件中声明了以下权限，完全符合 Android 安全规范：
*   **运行时申请**：`RECEIVE_SMS`、`READ_SMS`、`POST_NOTIFICATIONS` (Android 13+，用于展示保活通知)
*   **系统功能支持**：`INTERNET`、`WAKE_LOCK`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`

### 💡 小米 (HyperOS / MIUI) 专属适配说明
在小米等系统上，短信验证码等敏感信息通常会被系统判定为“通知类短信”并单独拦截。
*   **系统机制**：Android 标准的动态权限申请对该私有权限无效。
*   **智能引导**：SMS SENDER 内部实现了小米 AppOps 操作码 (`10018`) 的深度探测。若检测到未开启，会在首次装载时智能弹出拦截引导，点击“去开启”一键直达小米权限管理页，只需找到“短信”并设置为允许即可。
*   **弹窗防骚扰**：若您暂不需要此项，点击“暂不开启”后系统将记住您的选择，不再重复弹出。

---

## 🛠️ 本地编译与持续集成 (CI)

### 命令行构建
确保您本地的 Java 版本为 **Java 17** 及以上。在项目根目录下执行：
```bash
# Windows 环境
.\gradlew.bat assembleDebug

# Linux / MacOS 环境
chmod +x ./gradlew
./gradlew assembleDebug
```
构建出的 Debug 包输出在：`app/build/outputs/apk/debug/app-debug.apk` office/debug 目录。

### GitHub Actions
项目配置了自动构建 CI 工作流 (`.github/workflows/android-build.yml`)：
*   **多环境支持**：集成了 CI 环境识别机制。在 GitHub Actions 构建时，全自动剔除国内阿里云 Maven 代理源，无缝切换至全球官方源以解决海外网络下可能引发的构建卡死。
*   **制品交付**：每次代码推送都会自动触发编译，并将生成的 `app-debug.apk` 压缩并上传为 GitHub Actions Artifact，方便您随时下载测试最新版本。

---

## 📝 开源许可

本项目基于 **[MIT License](LICENSE)** 协议开源。在遵守开源协议的前提下，欢迎各位开发者进行二次分发或提出 PR！
