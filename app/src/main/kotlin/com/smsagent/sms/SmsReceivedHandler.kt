package com.smsagent.sms

import android.content.BroadcastReceiver
import android.content.Context
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
            "??",
            "SMS_RECEIVED????=${incomingSms.sender}???=${incomingSms.body.length}????=${incomingSms.verificationCode.ifBlank { "?" }}",
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
                "???????? API?${TimeFormatter.format(System.currentTimeMillis())}",
            )
            EventLogStore.append(
                appContext,
                "??",
                "????? API ???????",
            )
            pendingResult.finish()
            return
        }

        BarkForwardClient.forwardSms(
            remoteApiTemplate = remoteApiTemplate,
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
            EventLogStore.append(
                appContext,
                if (result.successful) "??" else "??",
                result.displayText(),
            )
        } finally {
            pendingResult.finish()
        }
    }
}
