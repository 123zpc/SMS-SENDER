package com.smsagent.observer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import android.util.Log
import com.smsagent.dispatcher.SmsDispatcher
import com.smsagent.dispatcher.SmsSource
import com.smsagent.sms.IncomingSms
import com.smsagent.state.AgentStateStore
import com.smsagent.state.EventLogStore

class SmsObserver private constructor(
    private val appContext: Context,
    handler: Handler,
    private val handlerThread: HandlerThread,
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean) {
        handleChange(null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        handleChange(uri)
    }

    override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
        handleChange(uris.firstOrNull())
    }

    fun close() {
        appContext.contentResolver.unregisterContentObserver(this)
        handlerThread.quitSafely()
    }

    private fun seedLatestMessageId() {
        val latestId = queryLatestMessages(limit = 1).firstOrNull()?.id ?: return
        if (AgentStateStore.getLastObservedSmsId(appContext) == 0L) {
            AgentStateStore.saveLastObservedSmsId(appContext, latestId)
            EventLogStore.append(appContext, "ContentObserver", "已记录短信库初始位置：id=$latestId")
        }
    }

    private fun handleChange(uri: Uri?) {
        if (!hasReadSmsPermission(appContext)) {
            EventLogStore.append(appContext, "ContentObserver", "缺少 READ_SMS 权限，无法读取短信库")
            return
        }

        val lastSeenId = AgentStateStore.getLastObservedSmsId(appContext)
        val messages = queryLatestMessages(limit = QUERY_LIMIT)
        if (messages.isEmpty()) {
            return
        }

        val latestId = messages.maxOf { it.id }
        val newMessages = messages
            .filter { it.id > lastSeenId }
            .sortedBy { it.dateMillis }

        if (newMessages.isEmpty()) {
            return
        }

        AgentStateStore.saveLastObservedSmsId(appContext, latestId)
        EventLogStore.append(
            appContext,
            "ContentObserver",
            "短信库变化：uri=${uri ?: "NULL"}，new=${newMessages.size}，latestId=$latestId",
        )

        newMessages.forEach { providerMessage ->
            val incomingSms = IncomingSms.fromRaw(
                sender = providerMessage.sender,
                body = providerMessage.body,
                receivedAtMillis = providerMessage.dateMillis,
                systemSmsId = providerMessage.id,
            )
            SmsDispatcher.dispatch(
                context = appContext,
                sms = incomingSms,
                source = SmsSource.CONTENT_OBSERVER,
            )
        }
    }

    private fun queryLatestMessages(limit: Int): List<SmsProviderMessage> {
        return try {
            appContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                SMS_PROJECTION,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val result = mutableListOf<SmsProviderMessage>()

                while (cursor.moveToNext() && result.size < limit) {
                    result += SmsProviderMessage(
                        id = cursor.getLong(idIndex),
                        sender = cursor.getString(addressIndex),
                        body = cursor.getString(bodyIndex),
                        dateMillis = cursor.getLong(dateIndex),
                    )
                }

                result
            }.orEmpty()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Query inbox sms failed", throwable)
            EventLogStore.append(
                appContext,
                "ContentObserver",
                "读取短信库失败：${throwable.javaClass.name}",
            )
            emptyList()
        }
    }

    private data class SmsProviderMessage(
        val id: Long,
        val sender: String?,
        val body: String?,
        val dateMillis: Long,
    )

    companion object {
        private const val TAG = "SmsObserver"
        private const val QUERY_LIMIT = 10

        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )

        fun register(context: Context): SmsObserver? {
            val appContext = context.applicationContext
            if (!hasReadSmsPermission(appContext)) {
                EventLogStore.append(appContext, "ContentObserver", "未注册：缺少 READ_SMS 权限")
                return null
            }

            val handlerThread = HandlerThread("sms-content-observer").apply { start() }
            val observer = SmsObserver(
                appContext = appContext,
                handler = Handler(handlerThread.looper),
                handlerThread = handlerThread,
            )
            observer.seedLatestMessageId()
            appContext.contentResolver.registerContentObserver(
                Uri.parse("content://sms"),
                true,
                observer,
            )
            EventLogStore.append(appContext, "ContentObserver", "已注册 content://sms/inbox 监听")
            return observer
        }

        private fun hasReadSmsPermission(context: Context): Boolean {
            return context.checkSelfPermission(Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
