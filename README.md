# SMS Agent

SMS Agent 是一个用于短信触发验证与转发实验的 Android 项目。

当前版本：`0.5.0`

## 核心能力

- 三轨接收：`SMS_RECEIVED` 广播、短信通知、`content://sms/inbox` ContentObserver。
- 统一调度：所有入口进入 `SmsDispatcher`。
- 30 秒精准去重：按 `(sender + body).hashCode()` 查询 Room，30 秒内重复消息直接忽略。
- Room 持久化：短信记录先入库为 `PENDING`，转发后更新为 `SENT` 或 `FAILED`。
- 同步 HTTP GET：使用 OkHttp，不使用 Retrofit。
- 短时 WakeLock：仅在 OkHttp 请求发起到响应或异常结束期间持有。
- 低功耗前台服务：使用 `IMPORTANCE_MIN` 静默通知维持基本进程优先级，不写死循环、不做高频 Timer。
- 事件驱动补发：WorkManager 只在网络恢复并满足 `NetworkType.CONNECTED` 时执行一次补发，不做定时轮询。
- 调试控制台：记录广播、通知、短信库观察、去重、转发和补发结果。

## 远程 API 模板

推荐填写：

```text
https://api.day.app/你的Key/{title}/{body}
```

只发送标题：

```text
https://api.day.app/你的Key/{title}
```

可用常量：

```text
{title}   验证码短信尽量生成“验证码 123456”，普通短信为“短信来自 发件人”
{sender}  发件人
{time}    接收时间，格式 yyyy-MM-dd HH:mm:ss
{body}    完整短信正文
{code}    从正文中提取的 4 到 8 位数字验证码，未识别时为空
```

## 权限说明

运行时权限：

```text
android.permission.RECEIVE_SMS
android.permission.READ_SMS
android.permission.POST_NOTIFICATIONS
```

Manifest 权限：

```text
android.permission.INTERNET
android.permission.WAKE_LOCK
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_DATA_SYNC
```

Notification Listener 需要用户在系统“通知使用权限”页面手动授权。

## 三轨接收说明

### 轨 1：SMS_RECEIVED

Manifest 静态注册 `SmsReceivedReceiver`，只监听：

```text
android.provider.Telephony.SMS_RECEIVED
```

Receiver 使用：

```kotlin
Telephony.Sms.Intents.getMessagesFromIntent(intent)
```

不会手动解析 PDU。

### 轨 2：Notification Listener

`NotificationInspectorService` 继续完整打印所有通知结构。

同时对已知系统短信应用包名提取通知标题和正文，进入 `SmsDispatcher`。通知轨道只用于短信通知兜底，不会把所有通知转发到 Bark。

### 轨 3：ContentObserver

`KeepAliveService` 注册 `SmsObserver`，监听：

```text
content://sms/inbox
```

需要 `READ_SMS` 权限。首次注册会记录当前短信库位置，避免把历史短信批量转发。

## 去重与补发

`SmsDispatcher` 计算：

```kotlin
(sender + body).hashCode()
```

然后查询 Room：

```text
currentTimeMillis - 30_000L
```

若 30 秒内已有相同 `messageHash`，直接忽略。否则入库为 `PENDING` 并立即同步转发。

转发失败后，记录为 `FAILED`，并注册一次 WorkManager 任务。该任务只在网络满足 `CONNECTED` 后执行，扫描 `PENDING/FAILED` 记录并补发。

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

每次 Push 自动执行：

```bash
./gradlew assembleDebug
```

构建成功后上传：

```text
app/build/outputs/apk/debug/app-debug.apk
```

作为 GitHub Actions Artifact。

## 厂商系统说明

HyperOS、MIUI、ColorOS、OriginOS 等系统可能限制后台启动、后台联网、通知监听或短信广播分发。SMS Agent 已尽力通过广播、通知、短信库观察三轨提高接收稳定性，但如果用户强行停止应用，或系统策略拒绝分发短信/通知/短信库变化，普通第三方应用无法绕过系统限制。

建议在系统设置中允许：

- 后台运行
- 后台联网
- 自启动
- 通知使用权限
- 关闭对本应用的电池优化

## 版本记录

- `0.4.0`：Notification Inspector 调试模块。
- `0.5.0`：三轨接收、Room 数据库、30 秒去重、短时 WakeLock、低功耗前台服务、网络恢复补发。
