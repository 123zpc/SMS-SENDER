# SMS Agent

SMS Agent 是一个轻量 Android 短信代理应用。

当前版本的目标很明确：

- 通过 Android Framework 的 `android.provider.Telephony.SMS_RECEIVED` 接收短信广播。
- 使用 `Telephony.Sms.Intents.getMessagesFromIntent(intent)` 读取系统解析后的短信。
- 将短信发件人、接收时间和短信内容转发到用户手动填写的 Bark API。
- 在本地持久化 Bark API 地址、最近一次触发时间和最近一次转发结果。

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

## 使用方式

1. 安装 APK。
2. 首次打开应用，授权短信接收权限。
3. 在界面中填写 Bark API 地址，例如：

```text
https://api.day.app/你的Key
```

4. 点击“保存 Bark API”。
5. 手机收到短信后，应用会通过 Bark API 发起一次 HTTP GET 请求。

转发内容包括：

```text
发件人
时间
内容
```

## Bark API 格式

界面中填写的是 Bark API 基础地址：

```text
https://api.day.app/你的Key
```

应用会自动拼接：

```text
/{title}/{body}
```

也可以填写自建 Bark 服务地址，例如：

```text
https://你的域名/你的Key
```

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

部分国产系统可能还会有后台限制、联网限制或自启动限制。如果设备厂商系统拦截了短信广播或后台联网，需要在系统设置中允许该应用后台运行和联网。

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
