package com.smsagent.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import com.smsagent.network.BarkForwardClient
import com.smsagent.network.BarkForwardResult
import com.smsagent.state.AgentStateStore
import com.smsagent.state.EventLogStore
import com.smsagent.util.ProcessNameProvider
import com.smsagent.util.TimeFormatter

object SmsReceivedHandler {

    fun handle(
        appContext: Context,
        incomingSms: IncomingSms,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        AgentStateStore.saveLastTriggerTime(appContext, incomingSms.receivedAtMillis)
        EventLogStore.append(
            appContext,
            "收到",
            "SMS_RECEIVED，发件人=${incomingSms.sender}，长度=${incomingSms.body.length}，验证码=${incomingSms.verificationCode.ifBlank { "无" }}",
        )

        val metadata = SmsForwardMetadata(
            receivedAtMillis = incomingSms.receivedAtMillis,
            pid = android.os.Process.myPid(),
            processName = ProcessNameProvider.getProcessName(appContext),
        )
        val remoteApiTemplate = AgentStateStore.getRemoteApiTemplate(appContext)
        if (remoteApiTemplate.isBlank()) {
            AgentStateStore.saveLastForwardResult(
                appContext,
                "失败：未配置远程 API，${TimeFormatter.format(System.currentTimeMillis())}",
            )
            EventLogStore.append(
                appContext,
                "失败",
                "未配置远程 API 模板，跳过转发",
            )
            pendingResult.finish()
            return
        }

        val wakeLock = acquireWakeLock(appContext)
        EventLogStore.append(
            appContext,
            "后台",
            "已提交短信 HTTP 转发任务，WakeLock=${wakeLock != null}",
        )
        BarkForwardClient.forwardSms(
            remoteApiTemplate = remoteApiTemplate,
            sms = incomingSms,
            metadata = metadata,
        ) { result ->
            finish(appContext, result, wakeLock, pendingResult)
        }
    }

    private fun finish(
        appContext: Context,
        result: BarkForwardResult,
        wakeLock: PowerManager.WakeLock?,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        try {
            AgentStateStore.saveLastForwardResult(appContext, result.displayText())
            EventLogStore.append(
                appContext,
                if (result.successful) "成功" else "失败",
                result.displayText(),
            )
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
            pendingResult.finish()
        }
    }

    private fun acquireWakeLock(appContext: Context): PowerManager.WakeLock? {
        return runCatching {
            val powerManager = appContext.getSystemService(PowerManager::class.java)
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${appContext.packageName}:SmsForward",
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
        }.getOrNull()
    }

    private const val WAKE_LOCK_TIMEOUT_MILLIS = 15_000L
}
