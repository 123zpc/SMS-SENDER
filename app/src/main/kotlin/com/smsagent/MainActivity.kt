package com.smsagent

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import com.smsagent.state.TriggerStateStore
import com.smsagent.util.TimeFormatter

class MainActivity : Activity() {

    private lateinit var permissionValue: TextView
    private lateinit var lastTriggerValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionValue = findViewById(R.id.permissionValue)
        lastTriggerValue = findViewById(R.id.lastTriggerValue)

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

        val lastTriggerTime = TriggerStateStore.getLastTriggerTime(this)
        lastTriggerValue.text = if (lastTriggerTime == 0L) {
            getString(R.string.last_trigger_empty)
        } else {
            TimeFormatter.format(lastTriggerTime)
        }
    }

    private fun hasReceiveSmsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        private const val RECEIVE_SMS_REQUEST_CODE = 1001
    }
}
