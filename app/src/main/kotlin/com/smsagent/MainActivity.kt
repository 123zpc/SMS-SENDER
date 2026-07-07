package com.smsagent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.smsagent.notification.NotificationInspectorPermission
import com.smsagent.state.AgentStateStore
import com.smsagent.state.EventLogStore
import com.smsagent.util.TimeFormatter

class MainActivity : Activity() {

    private lateinit var permissionValue: TextView
    private lateinit var notificationListenerValue: TextView
    private lateinit var barkApiInputLayout: TextInputLayout
    private lateinit var barkApiInput: TextInputEditText
    private lateinit var saveBarkApiButton: MaterialButton
    private lateinit var openAppSettingsButton: MaterialButton
    private lateinit var openNotificationListenerSettingsButton: MaterialButton
    private lateinit var openConsoleButton: MaterialButton
    private lateinit var clearLogButton: MaterialButton
    private lateinit var lastTriggerValue: TextView
    private lateinit var lastForwardResultValue: TextView
    private lateinit var eventLogValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionValue = findViewById(R.id.permissionValue)
        notificationListenerValue = findViewById(R.id.notificationListenerValue)
        barkApiInputLayout = findViewById(R.id.barkApiInputLayout)
        barkApiInput = findViewById(R.id.barkApiInput)
        saveBarkApiButton = findViewById(R.id.saveBarkApiButton)
        openAppSettingsButton = findViewById(R.id.openAppSettingsButton)
        openNotificationListenerSettingsButton = findViewById(R.id.openNotificationListenerSettingsButton)
        openConsoleButton = findViewById(R.id.openConsoleButton)
        clearLogButton = findViewById(R.id.clearLogButton)
        lastTriggerValue = findViewById(R.id.lastTriggerValue)
        lastForwardResultValue = findViewById(R.id.lastForwardResultValue)
        eventLogValue = findViewById(R.id.eventLogValue)

        barkApiInput.setText(AgentStateStore.getRemoteApiTemplate(this))
        saveBarkApiButton.setOnClickListener { saveBarkApiUrl() }
        openAppSettingsButton.setOnClickListener { openAppSettings() }
        openNotificationListenerSettingsButton.setOnClickListener { openNotificationListenerSettings() }
        openConsoleButton.setOnClickListener { openConsole() }
        clearLogButton.setOnClickListener { clearEventLog() }
        requestReceiveSmsPermissionIfNeeded()
        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECEIVE_SMS_REQUEST_CODE) {
            renderState()
        }
    }

    private fun requestReceiveSmsPermissionIfNeeded() {
        if (!hasReceiveSmsPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), RECEIVE_SMS_REQUEST_CODE)
        }
    }

    private fun renderState() {
        permissionValue.text = if (hasReceiveSmsPermission()) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        notificationListenerValue.text = if (NotificationInspectorPermission.isEnabled(this)) {
            getString(R.string.notification_listener_granted)
        } else {
            getString(R.string.notification_listener_denied)
        }

        val lastTriggerTime = AgentStateStore.getLastTriggerTime(this)
        lastTriggerValue.text = if (lastTriggerTime == 0L) {
            getString(R.string.last_trigger_empty)
        } else {
            TimeFormatter.format(lastTriggerTime)
        }

        lastForwardResultValue.text = AgentStateStore.getLastForwardResult(this)
            .ifBlank { getString(R.string.last_forward_empty) }

        eventLogValue.text = EventLogStore.getEventText(this)
            .ifBlank { getString(R.string.event_log_empty) }
    }

    private fun saveBarkApiUrl() {
        val value = barkApiInput.text?.toString()?.trim().orEmpty()
        if (!isHttpUrl(value)) {
            barkApiInputLayout.error = getString(R.string.bark_api_error)
            return
        }

        barkApiInputLayout.error = null
        AgentStateStore.saveRemoteApiTemplate(this, value)
        EventLogStore.append(this, "配置", getString(R.string.remote_api_saved_log))
        Toast.makeText(this, R.string.remote_api_saved, Toast.LENGTH_SHORT).show()
        renderState()
    }

    private fun clearEventLog() {
        EventLogStore.clear(this)
        renderState()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun openNotificationListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openConsole() {
        startActivity(Intent(this, ConsoleActivity::class.java))
    }

    private fun hasReceiveSmsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }

    private companion object {
        private const val RECEIVE_SMS_REQUEST_CODE = 1001
    }
}
