package com.smsagent.network

import android.os.SystemClock
import android.util.Log
import com.smsagent.sms.SmsTriggerMetadata
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object SmsTriggerHttpClient {

    private const val TAG = "SmsTriggerHttpClient"
    private const val ENDPOINT =
        "https://api.day.app/vBwuDwbqsbfHdM5yk8fYL8/SMS_RECEIVED/Triggered"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    fun sendSmsReceivedTrigger(
        metadata: SmsTriggerMetadata,
        onComplete: () -> Unit,
    ) {
        val request = Request.Builder()
            .url(ENDPOINT)
            .get()
            .build()

        val startedAtNanos = SystemClock.elapsedRealtimeNanos()

        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    try {
                        val durationMillis = elapsedMillis(startedAtNanos)
                        Log.e(TAG, metadata.logLine("exception=${e.javaClass.name}", durationMillis), e)
                    } finally {
                        onComplete()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val durationMillis = elapsedMillis(startedAtNanos)
                            val httpResult = "HTTP ${it.code} ${it.message}"

                            if (it.isSuccessful) {
                                Log.i(TAG, "SMS Trigger Success")
                                Log.i(TAG, metadata.logLine(httpResult, durationMillis))
                            } else {
                                Log.w(TAG, metadata.logLine(httpResult, durationMillis))
                            }
                        }
                    } finally {
                        onComplete()
                    }
                }
            })
        } catch (throwable: Throwable) {
            val durationMillis = elapsedMillis(startedAtNanos)
            Log.e(TAG, metadata.logLine("exception=${throwable.javaClass.name}", durationMillis), throwable)
            onComplete()
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startedAtNanos)
    }
}
