# SMS Agent

SMS Agent 是一个轻量 Android 短信代理应用。

当前版本的目标很明确：

- 通过 Android Framework 的 `android.provider.Telephony.SMS_RECEIVED` 接收短信广播。
- 使用 `Telephony.Sms.Intents.getMessagesFromIntent(intent)` 读取系统解析后的短信。
- 将短信发件人、接收时间、短信内容和验证码提取结果转发到用户手动填写的远程 API 模板。
- 在本地持久化远程 API 模板、最近一次触发时间、最近一次转发结果和本地日志。
- 提供控制台式调试页面，用于查看、复制、清空日志，也可以手动写入测试步骤。
- 提供 Notification Inspector 调试模块，用于观察不同 Android ROM 的通知结构。

本项目不是默认短信应用，不是自动化工具，也不包含保活、队列、数据库或通知能力。

## 技术栈

- Kotlin
- XML Layout
- Gradle Kotlin DSL
- Material3
- OkHttp
- minSdk 26
- targetSdk 36

## 权限说明

运行时申请：

```text
android.permission.RECEIVE_SMS
```

网络请求需要：

```text
android.permission.INTERNET
```

应用不会申请读取联系人、读取通话记录、发送短信、通知、无障碍、前台服务等权限。

Notification Inspector 需要用户在系统“通知使用权限”中手动授权：

```text
Notification Listener
```

它只把通知结构写入本地调试控制台，不会把通知转发到 Bark，也不会解析验证码、过滤、去重或分发。

## 使用方式

1. 安装 APK。
2. 首次打开应用，授权短信接收权限。
3. 在界面中填写远程 API 模板，例如：

```text
https://api.day.app/你的Key/{title}/{body}
```

4. 点击“保存模板”。
5. 手机收到短信后，应用会通过 Bark API 发起一次 HTTP GET 请求。
6. 如果出现异常，点击“打开调试控制台”查看完整本地日志。

转发内容包括：

```text
发件人
时间
内容
```

## 远程 API 模板

推荐填写完整模板：

```text
https://api.day.app/你的Key/{title}/{body}
```

Bark 也支持只传一个正文参数，因此可以这样只发送标题内容：

```text
https://api.day.app/你的Key/{title}
```

可用常量：

```text
{title}   标题。验证码短信会尽量生成“验证码 123456”，普通短信为“短信来自 发件人”
{sender}  短信发件人
{time}    接收时间，格式为 yyyy-MM-dd HH:mm:ss
{body}    完整短信内容
{code}    从短信内容中提取到的 4 到 8 位数字验证码；未识别时为空
```

也可以填写自建 Bark 服务模板，例如：

```text
https://你的域名/你的Key/{title}/{body}
```

如果填写的是旧格式基础地址：

```text
https://api.day.app/你的Key
```

应用会兼容处理，自动按 Bark 的 `/{title}/{body}` 形式拼接。

## 验证码短信说明

应用现在会对短信正文做一次轻量验证码提取，匹配 4 到 8 位数字验证码，并提供 `{code}` 常量。

如果某条“验证码”没有转发，先看应用内“本地日志”：

- 如果没有“SMS_RECEIVED”日志，说明系统没有把这条内容作为普通短信广播给应用。它可能是 RCS、应用内通知、数据短信、运营商特殊通道，或被系统/厂商策略拦截。
- 如果有“SMS_RECEIVED”日志但转发失败，日志会显示远程 API 模板无效、网络异常或 HTTP 状态码。
- 如果有“SMS_RECEIVED”日志但 `{code}` 为空，说明短信内容里没有匹配到 4 到 8 位连续数字；此时仍可用 `{body}` 转发完整正文。

### HyperOS / MIUI 说明

小米 10S、Android 13、HyperOS/MIUI 等系统可能会对某些验证码短信做特殊处理，并且有意不向第三方应用分发对应的 `SMS_RECEIVED` 广播。

如果系统没有分发广播，普通第三方应用无法通过标准 Android Framework 收到这类短信。应用不会加入无障碍、通知监听、隐藏 API、Hook、读取系统短信数据库、伪装默认短信应用等绕过系统策略的方案。

可接受的排查方式是：

- 打开“调试控制台”，观察收到验证码时是否出现 `SMS_RECEIVED` 日志。
- 如果没有日志，说明系统没有把这条短信广播给应用。
- 如果有日志，再看验证码提取、模板渲染和 HTTP 结果。
- 在系统设置里允许后台运行、后台联网、自启动，关闭电池优化。

这类系统拦截不是网络转发问题，也不是模板问题；只有系统实际投递了 `SMS_RECEIVED`，应用才能处理。

## 调试控制台

应用提供独立的“调试控制台”页面，风格类似 IDE 控制台。

控制台会显示：

- 手动保存模板等输入事件
- 是否收到 `SMS_RECEIVED`
- 发件人
- 短信正文长度
- 是否识别到验证码
- 转发结果
- HTTP 状态或异常类型
- Notification Inspector 输出的通知结构

控制台支持：

- 刷新
- 复制全部日志
- 清空日志
- 手动写入测试步骤、设备状态或复现现象

排查问题时，建议先在控制台手动写入当前设备和系统状态，例如：

```text
小米 10S / Android 13 / HyperOS，已关闭电池优化，测试银行验证码短信
```

然后发送测试短信，再复制完整控制台日志。

## Notification Inspector

Notification Inspector 是一个研究模块，用于观察 HyperOS、MIUI、ColorOS、OriginOS、Pixel 等系统的通知结构差异。

它实现了：

```text
NotificationListenerService
```

服务名称：

```text
NotificationInspectorService
```

授权后，它监听所有通知，不做过滤。任何 App 的通知都会写入调试控制台。

每条通知会输出：

- 收到时间
- Package Name
- Application Label
- Notification ID
- Post Time
- Category
- Channel ID
- Visibility
- Priority
- Flags
- Ticker Text
- `EXTRA_TITLE`
- `EXTRA_TEXT`
- `EXTRA_BIG_TEXT`
- `EXTRA_SUB_TEXT`
- `EXTRA_SUMMARY_TEXT`
- `EXTRA_INFO_TEXT`
- Extras 中全部 Key
- Extras 中每个 Value

字段为空时输出：

```text
NULL
```

控制台示例：

```text
[Notification]
Package=com.android.mms
Title=【百度】
Text=验证码：958948
BigText=【百度】验证码：958948（有效30分钟）
Category=msg
Visibility=PRIVATE
Extras={
  android.title=【百度】
  android.text=验证码：958948
}
```

明确不做：

- 不向 Bark 推送通知
- 不解析验证码
- 不做过滤
- 不实现 Dispatcher
- 不去重
- 不修改短信转发逻辑

## 关闭应用后的转发说明

应用使用 Manifest 静态注册的 `BroadcastReceiver` 接收 `SMS_RECEIVED`。

在通常情况下，即使界面关闭、应用不在最近任务中，只要满足以下条件，系统仍可在收到短信时拉起应用进程并完成转发：

- 应用已安装且未被卸载。
- 用户已授予 `RECEIVE_SMS` 权限。
- 用户已保存有效的 Bark API 地址。
- 设备网络可用。
- 系统没有禁止该应用后台启动或联网。

Android 系统存在一个重要限制：

如果用户在系统设置中“强行停止”应用，系统通常不会再向该应用分发广播，直到用户再次手动打开应用。这是 Android Framework 的系统行为，应用侧不能绕过。

部分国产系统可能还会有后台限制、联网限制或自启动限制。如果设备厂商系统拦截了短信广播或后台联网，需要在系统设置中允许该应用后台运行、后台联网、自启动或关闭电池优化。

应用界面提供“打开应用后台设置”入口，用来快速进入系统应用详情页。不同厂商的后台权限入口名称不同，需要按实际系统页面开启相关权限。

## 日志

收到短信并转发时，日志会包含：

- 当前时间
- PID
- Process Name
- Thread Name
- HTTP 请求结果
- HTTP 耗时

转发成功时打印：

```text
SMS Forward Success
```

转发失败时打印完整异常。

应用内也会保存最近的本地日志，便于判断：

- 是否收到 `SMS_RECEIVED`
- 发件人
- 正文长度
- 是否识别到验证码
- 是否执行转发
- HTTP 结果

## 编译方式

使用 Android Studio 最新稳定版打开项目并同步 Gradle。

命令行编译：

```bash
./gradlew assembleDebug
```

生成路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Workflow 名称：

```text
Android Build
```

每次 push 自动执行：

```bash
./gradlew assembleDebug
```

构建成功后上传 Artifact：

```text
app-debug.apk
```

Artifact 来源路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 版本

- `0.0.1`：第一阶段验证版本，仅验证 `SMS_RECEIVED` 可触发并发送一次 HTTP GET。
- `0.1.0`：支持配置 Bark API，持久化配置，并转发短信内容。
- `0.2.0`：支持远程 API 模板常量、本地日志、验证码提取和更清晰的后台设置入口。
- `0.3.0`：新增控制台式日志页面，支持复制、清空、刷新和手动写入调试记录。
- `0.4.0`：新增 Notification Inspector 调试模块，用于观察不同 Android ROM 的通知结构。
