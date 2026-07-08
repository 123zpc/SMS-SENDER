package com.smsagent.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.smsagent.MainActivity
import com.smsagent.R
import com.smsagent.observer.SmsObserver
import com.smsagent.state.EventLogStore

class KeepAliveService : Service() {

    private var smsObserver: SmsObserver? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        registerSmsObserverIfPossible()
        EventLogStore.append(this, "KeepAlive", "低功耗前台服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerSmsObserverIfPossible()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        smsObserver?.close()
        smsObserver = null
        EventLogStore.append(this, "KeepAlive", "低功耗前台服务已停止")
        super.onDestroy()
    }

    private fun registerSmsObserverIfPossible() {
        if (smsObserver != null) {
            return
        }

        smsObserver = SmsObserver.register(this)
    }

    private fun startAsForeground() {
        createNotificationChannel()
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.keep_alive_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_notifications_24)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "sms_agent_keep_alive"
        private const val NOTIFICATION_ID = 10001

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Start keep alive service failed", throwable)
                EventLogStore.append(
                    appContext,
                    "KeepAlive",
                    "启动前台服务失败：${throwable.javaClass.name}",
                )
            }
        }
    }
}
