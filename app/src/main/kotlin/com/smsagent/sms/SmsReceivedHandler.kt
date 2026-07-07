package com.smsagent.sms

import android.content.BroadcastReceiver
import android.content.Context
import com.smsagent.network.SmsTriggerHttpClient
import com.smsagent.state.TriggerStateStore
import com.smsagent.util.ProcessNameProvider

object SmsReceivedHandler {

    fun handle(
        appContext: Context,
        receivedAtMillis: Long,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        TriggerStateStore.saveLastTriggerTime(appContext, receivedAtMillis)

        val metadata = SmsTriggerMetadata(
            receivedAtMillis = receivedAtMillis,
            pid = android.os.Process.myPid(),
            processName = ProcessNameProvider.getProcessName(appContext),
        )

        SmsTriggerHttpClient.sendSmsReceivedTrigger(
            metadata = metadata,
            onComplete = pendingResult::finish,
        )
    }
}
