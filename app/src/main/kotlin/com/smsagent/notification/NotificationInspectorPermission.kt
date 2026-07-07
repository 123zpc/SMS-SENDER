package com.smsagent.notification

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NotificationInspectorPermission {

    fun isEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, NotificationInspectorService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()

        return enabledListeners
            .split(':')
            .any { flattenedName ->
                ComponentName.unflattenFromString(flattenedName) == componentName
            }
    }
}
