package com.smsagent.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.smsagent.dispatcher.SmsDispatcher
import com.smsagent.state.EventLogStore

class SmsRetryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {
            val total = SmsDispatcher.retryPendingMessages(applicationContext)
            Log.i(TAG, "Retry worker finished: total=$total")
            Result.success()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Retry worker failed", throwable)
            EventLogStore.append(
                applicationContext,
                "RetryWorker",
                "补发任务异常：${throwable.javaClass.name}",
            )
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "SmsRetryWorker"
        private const val UNIQUE_WORK_NAME = "sms_retry_when_network_connected"

        fun enqueueOnConnected(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequest.Builder(SmsRetryWorker::class.java)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            EventLogStore.append(context, "RetryWorker", "已注册网络恢复补发任务")
        }
    }
}
