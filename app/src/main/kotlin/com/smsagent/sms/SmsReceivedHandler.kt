package com.smsagent.sms

import android.content.BroadcastReceiver
import android.content.Context
import com.smsagent.dispatcher.SmsDispatcher
import com.smsagent.dispatcher.SmsSource

object SmsReceivedHandler {

    fun handle(
        appContext: Context,
        incomingSms: IncomingSms,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        SmsDispatcher.dispatch(
            context = appContext,
            sms = incomingSms,
            source = SmsSource.BROADCAST,
            onComplete = { pendingResult.finish() },
        )
    }
}
