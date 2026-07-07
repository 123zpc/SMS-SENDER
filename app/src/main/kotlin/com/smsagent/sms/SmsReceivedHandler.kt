package com.smsagent.sms

import android.content.BroadcastReceiver
import android.content.Context
import com.smsagent.network.BarkForwardClient
import com.smsagent.network.BarkForwardResult
import com.smsagent.state.AgentStateStore
import com.smsagent.util.ProcessNameProvider
import com.smsagent.util.TimeFormatter

object SmsReceivedHandler {

    fun handle(
        appContext: Context,
        incomingSms: IncomingSms,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        AgentStateStore.saveLastTriggerTime(appContext, incomingSms.receivedAtMillis)

        val metadata = SmsForwardMetadata(
            receivedAtMillis = incomingSms.receivedAtMillis,
            pid = android.os.Process.myPid(),
            processName = ProcessNameProvider.getProcessName(appContext),
        )
        val barkApiUrl = AgentStateStore.getBarkApiUrl(appContext)
        if (barkApiUrl.isBlank()) {
            AgentStateStore.saveLastForwardResult(
                appContext,
                "失败：未配置 Bark API，${TimeFormatter.format(System.currentTimeMillis())}",
            )
            pendingResult.finish()
            return
        }

        BarkForwardClient.forwardSms(
            barkApiUrl = barkApiUrl,
            sms = incomingSms,
            metadata = metadata,
        ) { result ->
            finish(appContext, result, pendingResult)
        }
    }

    private fun finish(
        appContext: Context,
        result: BarkForwardResult,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        try {
            AgentStateStore.saveLastForwardResult(appContext, result.displayText())
        } finally {
            pendingResult.finish()
        }
    }
}
