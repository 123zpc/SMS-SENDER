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
import java.util.Locale

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
            val traceId = AgentStateStore.nextDispatchTraceId(appContext)
            try {
                dispatchSync(appContext, sms, source, traceId)
            } catch (throwable: Throwable) {
                Log.e(TAG, "短信调度失败：${formatTraceLabel(traceId)} source=$source", throwable)
                EventLogStore.append(
                    appContext,
                    "SmsDispatcher",
                    "${formatTraceLabel(traceId)} 调度异常：source=$source，error=${throwable.javaClass.name}",
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
            val traceId = AgentStateStore.nextDispatchTraceId(appContext)
            EventLogStore.append(
                appContext,
                "RetryWorker",
                "${formatTraceLabel(traceId)} 开始补发：dbId=${entity.id}，sender=${entity.sender}，attempt=${entity.attemptCount + 1}",
            )
            val result = forwardStoredMessage(
                context = appContext,
                dao = dao,
                entity = entity,
                source = SmsSource.RETRY_WORKER,
                traceId = traceId,
            )
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

    private fun dispatchSync(context: Context, sms: IncomingSms, source: String, traceId: Long) {
        val db = SmsAgentDatabase.get(context)
        val dao = db.smsMessageDao()
        val currentTimeMillis = System.currentTimeMillis()
        
        AgentStateStore.saveLastTriggerTime(context, sms.receivedAtMillis)

        // 1. 如果是来自内容观测者 ContentObserver (带有 systemSmsId)
        if (sms.systemSmsId != null) {
            val existingBySystemId = dao.getBySystemSmsId(sms.systemSmsId)
            if (existingBySystemId != null) {
                if (existingBySystemId.status == SmsForwardStatus.SENT) {
                    EventLogStore.append(context, "SmsDispatcher", "${formatTraceLabel(traceId)} Observer跳过：系统短信ID ${sms.systemSmsId} 已经推送成功")
                    return
                } else {
                    EventLogStore.append(context, "SmsDispatcher", "${formatTraceLabel(traceId)} Observer补发：系统短信ID ${sms.systemSmsId} 未发送成功，当前状态为 ${existingBySystemId.status}")
                    forwardStoredMessage(context, dao, existingBySystemId, source, traceId)
                    return
                }
            }

            // 查最近 1 分钟内的记录做智能匹配，看 Broadcast 是否已经处理过并入库了
            val recentMessages = dao.getRecentMessages(currentTimeMillis - 60_000L)
            val matchedEntity = findMatchedMessage(sms, recentMessages)
            if (matchedEntity != null) {
                // 将当时没有绑定系统 ID 的记录进行绑定
                dao.updateSystemSmsId(matchedEntity.id, sms.systemSmsId, currentTimeMillis)
                EventLogStore.append(context, "SmsDispatcher", "${formatTraceLabel(traceId)} Observer关联：匹配到最近的广播记录 dbId=${matchedEntity.id}，更新系统ID为 ${sms.systemSmsId}")
                
                if (matchedEntity.status == SmsForwardStatus.SENT) {
                    EventLogStore.append(context, "SmsDispatcher", "${formatTraceLabel(traceId)} Observer跳过：关联的记录已推送成功")
                    return
                } else {
                    EventLogStore.append(context, "SmsDispatcher", "${formatTraceLabel(traceId)} Observer补发：关联的记录未推送，当前状态为 ${matchedEntity.status}")
                    val updatedEntity = matchedEntity.copy(systemSmsId = sms.systemSmsId, updatedAtMillis = currentTimeMillis)
                    forwardStoredMessage(context, dao, updatedEntity, source, traceId)
                    return
                }
            }

            // 数据库没有任何匹配记录，说明是全新短信，入库并推送
            insertAndForward(context, dao, sms, source, traceId, currentTimeMillis)
            return
        }



        // 3. 如果是广播 (BROADCAST) 或手动发送 (MANUAL)
        val recentMessages = dao.getRecentMessages(currentTimeMillis - DEDUP_WINDOW_MILLIS)
        val matchedEntity = findMatchedMessage(sms, recentMessages)
        if (matchedEntity != null) {
            EventLogStore.append(context, "SmsDispatcher", "${formatTraceLabel(traceId)} 广播去重：最近已有匹配记录 dbId=${matchedEntity.id}，当前状态为 ${matchedEntity.status}")
            if (matchedEntity.status != SmsForwardStatus.SENT) {
                forwardStoredMessage(context, dao, matchedEntity, source, traceId)
            }
            return
        }

        // 全新短信入库并推送
        insertAndForward(context, dao, sms, source, traceId, currentTimeMillis)
    }

    private fun insertAndForward(
        context: Context,
        dao: SmsMessageDao,
        sms: IncomingSms,
        source: String,
        traceId: Long,
        currentTimeMillis: Long
    ) {
        val messageHash = (sms.sender + sms.body).hashCode()
        val entity = SmsMessageEntity(
            systemSmsId = sms.systemSmsId,
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
            "${formatTraceLabel(traceId)} 已入库：dbId=$id，source=$source，sender=${sms.sender}，systemSmsId=${sms.systemSmsId ?: "NULL"}"
        )

        forwardStoredMessage(
            context = context,
            dao = dao,
            entity = storedMessage,
            source = source,
            traceId = traceId,
        )
    }

    private fun findMatchedMessage(sms: IncomingSms, recentMessages: List<SmsMessageEntity>): SmsMessageEntity? {
        if (recentMessages.isEmpty()) return null
        val smsCode = sms.verificationCode
        
        return recentMessages.firstOrNull { msg ->
            // 1. 如果都有验证码且相同，则是同一条短信
            val msgCode = com.smsagent.sms.VerificationCodeExtractor.extract(msg.body)
            if (smsCode.isNotBlank() && msgCode.isNotBlank() && smsCode == msgCode) {
                return@firstOrNull true
            }
            
            // 2. 如果正文忽略空格后完全相同，或是包含关系
            val cleanSmsBody = sms.body.replace("\\s".toRegex(), "").lowercase()
            val cleanMsgBody = msg.body.replace("\\s".toRegex(), "").lowercase()
            if (cleanSmsBody.contains(cleanMsgBody) || cleanMsgBody.contains(cleanSmsBody)) {
                return@firstOrNull true
            }
            
            // 3. 如果发件人相似，且内容高度一致
            if (isSameSender(sms.sender, msg.sender)) {
                if (Math.abs(cleanSmsBody.length - cleanMsgBody.length) < 20) {
                    val commonPrefixLen = cleanSmsBody.commonPrefixWith(cleanMsgBody).length
                    if (commonPrefixLen > 10 && commonPrefixLen > cleanSmsBody.length * 0.7) {
                        return@firstOrNull true
                    }
                }
            }
            
            false
        }
    }

    private fun isSameSender(sender1: String, sender2: String): Boolean {
        if (sender1 == sender2) return true
        val clean1 = sender1.removePrefix("+86").replace("\\D".toRegex(), "")
        val clean2 = sender2.removePrefix("+86").replace("\\D".toRegex(), "")
        if (clean1.isNotBlank() && clean2.isNotBlank() && clean1 == clean2) return true
        return false
    }



    private fun forwardStoredMessage(
        context: Context,
        dao: SmsMessageDao,
        entity: SmsMessageEntity,
        source: String,
        traceId: Long,
    ): BarkForwardResult {
        val sms = IncomingSms.fromRaw(
            sender = entity.sender,
            body = entity.body,
            receivedAtMillis = entity.receivedAtMillis,
        )
        val remoteApiTemplate = AgentStateStore.getRemoteApiTemplate(context)
        val metadata = SmsForwardMetadata(
            traceId = traceId,
            receivedAtMillis = sms.receivedAtMillis,
            pid = android.os.Process.myPid(),
            processName = ProcessNameProvider.getProcessName(context),
        )
        EventLogStore.append(
            context,
            "SmsDispatcher",
            "${formatTraceLabel(traceId)} 开始发送：dbId=${entity.id}，source=$source，title=${sms.title}",
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
            "${formatTraceLabel(traceId)} 发送完成：source=$source，dbId=${entity.id}，sender=${entity.sender}，${result.displayText()}",
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

    private fun formatTraceLabel(traceId: Long): String {
        return String.format(Locale.US, "转发#%05d", traceId)
    }
}
