package com.smsagent

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.smsagent.state.AgentStateStore
import com.smsagent.util.TimeFormatter

class MainActivity : Activity() {

    private lateinit var permissionValue: TextView
    private lateinit var barkApiInputLayout: TextInputLayout
    private lateinit var barkApiInput: TextInputEditText
    private lateinit var saveBarkApiButton: MaterialButton
    private lateinit var lastTriggerValue: TextView
    private lateinit var lastForwardResultValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionValue = findViewById(R.id.permissionValue)
        barkApiInputLayout = findViewById(R.id.barkApiInputLayout)
        barkApiInput = findViewById(R.id.barkApiInput)
        saveBarkApiButton = findViewById(R.id.saveBarkApiButton)
        lastTriggerValue = findViewById(R.id.lastTriggerValue)
        lastForwardResultValue = findViewById(R.id.lastForwardResultValue)

        barkApiInput.setText(AgentStateStore.getBarkApiUrl(this))
        saveBarkApiButton.setOnClickListener { saveBarkApiUrl() }
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

        val lastTriggerTime = AgentStateStore.getLastTriggerTime(this)
        lastTriggerValue.text = if (lastTriggerTime == 0L) {
            getString(R.string.last_trigger_empty)
        } else {
            TimeFormatter.format(lastTriggerTime)
        }

        lastForwardResultValue.text = AgentStateStore.getLastForwardResult(this)
            .ifBlank { getString(R.string.last_forward_empty) }
    }

    private fun saveBarkApiUrl() {
        val value = barkApiInput.text?.toString()?.trim().orEmpty()
        if (!isHttpUrl(value)) {
            barkApiInputLayout.error = getString(R.string.bark_api_error)
            return
        }

        barkApiInputLayout.error = null
        AgentStateStore.saveBarkApiUrl(this, value)
        Toast.makeText(this, R.string.bark_api_saved, Toast.LENGTH_SHORT).show()
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
