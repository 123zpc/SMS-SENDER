package com.smsagent.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val pendingResult = goAsync()
        try {
            val receivedAtMillis = System.currentTimeMillis()
            val incomingSms = IncomingSms.fromMessages(
                messages = Telephony.Sms.Intents.getMessagesFromIntent(intent),
                receivedAtMillis = receivedAtMillis,
            )
            SmsReceivedHandler.handle(
                appContext = context.applicationContext,
                incomingSms = incomingSms,
                pendingResult = pendingResult,
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "SMS_RECEIVED handling failed", throwable)
            pendingResult.finish()
        }
    }

    private companion object {
        private const val TAG = "SmsReceivedReceiver"
    }
}
