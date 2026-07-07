# SMS Agent

SMS Agent is a first-phase Android verification project. Its only goal is to verify that Android Framework can wake the app process through `android.provider.Telephony.SMS_RECEIVED` and that the receiver can send one HTTP GET request after the broadcast is delivered.

This project is not an SMS app, not an automation tool, not an SMS forwarder, and does not process SMS content.

## Build

Open the project with the latest stable Android Studio, then sync Gradle.

Command-line build:

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Workflow: `Android Build`

Every push runs:

```bash
./gradlew assembleDebug
```

After a successful build, GitHub Actions uploads:

```text
app/build/outputs/apk/debug/app-debug.apk
```

as the `app-debug.apk` artifact.

## Install

Download the `app-debug.apk` artifact from a successful GitHub Actions run and install it on a physical Android device that can receive SMS messages.

## Permissions

Runtime permission requested:

```text
android.permission.RECEIVE_SMS
```

Manifest permission required for the HTTP GET request:

```text
android.permission.INTERNET
```

No other runtime permissions are requested.

## Behavior

On first launch, the app requests `RECEIVE_SMS` and displays:

```text
Permission:
Granted
```

or:

```text
Permission:
Denied
```

The app also displays the last `SMS_RECEIVED` trigger time in:

```text
yyyy-MM-dd HH:mm:ss
```

When any SMS is received, the manifest-registered receiver listens only for:

```text
android.provider.Telephony.SMS_RECEIVED
```

The receiver calls:

```text
Telephony.Sms.Intents.getMessagesFromIntent(intent)
```

It does not parse PDUs directly and does not use `SmsMessage.createFromPdu()`.

The receiver uses `goAsync()`, sends the HTTP GET request with OkHttp, and calls `PendingResult.finish()` after the HTTP request completes.

Request URL:

```text
https://api.day.app/vBwuDwbqsbfHdM5yk8fYL8/SMS_RECEIVED/Triggered
```

Successful sends log:

```text
SMS Trigger Success
```

The SMS trigger log includes current time, PID, process name, thread name, HTTP result, and HTTP duration.
