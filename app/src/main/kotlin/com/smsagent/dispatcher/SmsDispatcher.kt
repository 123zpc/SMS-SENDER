package com.smsagent.dispatcher

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.smsagent.data.SmsAgentDatabase
import com.smsagent.data.SmsForwardStatus
import com.smsagent.data.SmsMessageDao
import com.smsagent.data.SmsMessageEntity
import com.smsagent.network.BarkForwardClient
import com.smsagent.network.BarkForwardResult
import com.smsagent.sms.IncomingSms
import com.smsagent.sms.SmsForwardMetadata
import com.smsagent.state.AgentStateStore
import com.smsagent.state.EventLogStore
import com.smsagent.util.ProcessNameProvider
import com.smsagent.worker.SmsRetryWorker
import java.util.concurrent.Executors

object SmsDispatcher {

    private const val TAG = "SmsDispatcher"
    private const val DEDUP_WINDOW_MILLIS = 30_000L
    private const val WAKE_LOCK_TIMEOUT_MILLIS = 15_000L
    private const val RETRY_BATCH_SIZE = 50

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sms-dispatcher").apply { isDaemon = false }
    }

    fun dispatch(
        context: Context,
        sms: IncomingSms,
        source: String,
        onComplete: (() -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                dispatchSync(appContext, sms, source)
            } catch (throwable: Throwable) {
                Log.e(TAG, "短信调度失败：source=$source", throwable)
                EventLogStore.append(
                    appContext,
                    "SmsDispatcher",
                    "短信调度异常：source=$source，error=${throwable.javaClass.name}",
                )
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun retryPendingMessages(context: Context): Int {
        val appContext = context.applicationContext
        val dao = SmsAgentDatabase.get(appContext).smsMessageDao()
        val messages = dao.getByStatuses(
            statuses = listOf(SmsForwardStatus.PENDING, SmsForwardStatus.FAILED),
            limit = RETRY_BATCH_SIZE,
        )

        var successCount = 0
        var failureCount = 0
        messages.forEach { entity ->
            val result = forwardStoredMessage(appContext, dao, entity, SmsSource.RETRY_WORKER)
            if (result.successful) {
                successCount += 1
            } else {
                failureCount += 1
            }
        }

        EventLogStore.append(
            appContext,
            "RetryWorker",
            "补发完成：success=$successCount，failed=$failureCount，total=${messages.size}",
        )
        return messages.size
    }

    private fun dispatchSync(context: Context, sms: IncomingSms, source: String) {
        val dao = SmsAgentDatabase.get(context).smsMessageDao()
        val currentTimeMillis = System.currentTimeMillis()
        val messageHash = (sms.sender + sms.body).hashCode()
        val sinceMillis = currentTimeMillis - DEDUP_WINDOW_MILLIS

        AgentStateStore.saveLastTriggerTime(context, sms.receivedAtMillis)

        val duplicateCount = dao.countRecentByHash(messageHash, sinceMillis)
        if (duplicateCount > 0) {
            EventLogStore.append(
                context,
                "SmsDispatcher",
                "30秒去重命中：source=$source，sender=${sms.sender}，hash=$messageHash",
            )
            Log.i(TAG, "Dedup ignored: source=$source sender=${sms.sender} hash=$messageHash")
            return
        }

        val entity = SmsMessageEntity(
            sender = sms.sender,
            body = sms.body,
            receivedAtMillis = sms.receivedAtMillis,
            messageHash = messageHash,
            source = source,
            status = SmsForwardStatus.PENDING,
            createdAtMillis = currentTimeMillis,
            updatedAtMillis = currentTimeMillis,
        )
        val id = dao.insert(entity)
        val storedMessage = entity.copy(id = id)

        EventLogStore.append(
            context,
            "SmsDispatcher",
            "已入库：source=$source，sender=${sms.sender}，length=${sms.body.length}，hash=$messageHash",
        )

        forwardStoredMessage(context, dao, storedMessage, source)
    }

    private fun forwardStoredMessage(
        context: Context,
        dao: SmsMessageDao,
        entity: SmsMessageEntity,
        source: String,
    ): BarkForwardResult {
        val sms = IncomingSms.fromRaw(
            sender = entity.sender,
            body = entity.body,
            receivedAtMillis = entity.receivedAtMillis,
        )
        val remoteApiTemplate = AgentStateStore.getRemoteApiTemplate(context)
        val metadata = SmsForwardMetadata(
            receivedAtMillis = sms.receivedAtMillis,
            pid = android.os.Process.myPid(),
            processName = ProcessNameProvider.getProcessName(context),
        )

        val result = if (remoteApiTemplate.isBlank()) {
            BarkForwardResult(
                successful = false,
                message = "未配置远程 API 模板",
                durationMillis = 0L,
            )
        } else {
            forwardWithWakeLock(context, remoteApiTemplate, sms, metadata)
        }

        val nextStatus = if (result.successful) SmsForwardStatus.SENT else SmsForwardStatus.FAILED
        dao.updateForwardState(
            id = entity.id,
            status = nextStatus,
            lastError = result.message.takeUnless { result.successful },
            attemptIncrement = 1,
            updatedAtMillis = System.currentTimeMillis(),
        )

        AgentStateStore.saveLastForwardResult(context, result.displayText())
        EventLogStore.append(
            context,
            if (result.successful) "成功" else "失败",
            "source=$source，id=${entity.id}，${result.displayText()}",
        )

        if (!result.successful && remoteApiTemplate.isNotBlank()) {
            SmsRetryWorker.enqueueOnConnected(context)
        }

        return result
    }

    private fun forwardWithWakeLock(
        context: Context,
        remoteApiTemplate: String,
        sms: IncomingSms,
        metadata: SmsForwardMetadata,
    ): BarkForwardResult {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmsAgent:ForwardSms",
        )

        try {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            return BarkForwardClient.forwardSmsSync(
                remoteApiTemplate = remoteApiTemplate,
                sms = sms,
                metadata = metadata,
            )
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
