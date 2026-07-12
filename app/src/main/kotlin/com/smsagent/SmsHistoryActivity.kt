package com.smsagent

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.smsagent.dispatcher.SmsDispatcher
import com.smsagent.dispatcher.SmsSource
import com.smsagent.sms.IncomingSms
import kotlin.concurrent.thread

class SmsHistoryActivity : Activity() {

    private lateinit var backButton: ImageButton
    private lateinit var selectAllPanel: View
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var smsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var forwardSelectedButton: MaterialButton

    private lateinit var adapter: SmsHistoryAdapter
    private var smsItems: List<SmsHistoryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_history)

        backButton = findViewById(R.id.backButton)
        selectAllPanel = findViewById(R.id.selectAllPanel)
        selectAllCheckbox = findViewById(R.id.selectAllCheckbox)
        smsRecyclerView = findViewById(R.id.smsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        loadingProgress = findViewById(R.id.loadingProgress)
        forwardSelectedButton = findViewById(R.id.forwardSelectedButton)

        smsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SmsHistoryAdapter(emptyList()) { selectedCount ->
            updateForwardButtonState(selectedCount)
        }
        smsRecyclerView.adapter = adapter

        backButton.setOnClickListener { finish() }

        selectAllPanel.setOnClickListener {
            val nextChecked = !selectAllCheckbox.isChecked
            selectAllCheckbox.isChecked = nextChecked
            adapter.selectAll(nextChecked)
        }

        selectAllCheckbox.setOnClickListener {
            adapter.selectAll(selectAllCheckbox.isChecked)
        }

        forwardSelectedButton.setOnClickListener {
            forwardSelectedSms()
        }

        checkPermissionAndLoadSms()
    }

    private fun checkPermissionAndLoadSms() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadSmsFromInbox()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_SMS_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                loadSmsFromInbox()
            } else {
                emptyStateText.visibility = View.VISIBLE
            }
        }
    }

    private fun loadSmsFromInbox() {
        loadingProgress.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
        selectAllPanel.visibility = View.GONE

        thread {
            val list = mutableListOf<SmsHistoryItem>()
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date", "read")
            try {
                contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow("_id")
                    val addressCol = cursor.getColumnIndexOrThrow("address")
                    val bodyCol = cursor.getColumnIndexOrThrow("body")
                    val dateCol = cursor.getColumnIndexOrThrow("date")
                    val readCol = cursor.getColumnIndexOrThrow("read")

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val sender = cursor.getString(addressCol).orEmpty()
                        val body = cursor.getString(bodyCol).orEmpty()
                        val timestamp = cursor.getLong(dateCol)
                        val isRead = cursor.getInt(readCol) == 1
                        list.add(SmsHistoryItem(id, sender, body, timestamp, isRead))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            runOnUiThread {
                loadingProgress.visibility = View.GONE
                smsItems = list
                adapter.updateItems(smsItems)

                if (list.isEmpty()) {
                    emptyStateText.visibility = View.VISIBLE
                } else {
                    emptyStateText.visibility = View.GONE
                    selectAllPanel.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateForwardButtonState(selectedCount: Int) {
        if (selectedCount > 0) {
            forwardSelectedButton.isEnabled = true
            forwardSelectedButton.text = getString(R.string.sms_history_forward_selected, selectedCount)
        } else {
            forwardSelectedButton.isEnabled = false
            forwardSelectedButton.text = getString(R.string.sms_history_forward_none)
        }

        selectAllCheckbox.isChecked = selectedCount > 0 && selectedCount == smsItems.size
    }

    private fun forwardSelectedSms() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        selected.forEach { item ->
            val incomingSms = IncomingSms.fromRaw(
                sender = item.sender,
                body = item.body,
                receivedAtMillis = item.timestamp
            )
            SmsDispatcher.dispatch(this, incomingSms, SmsSource.MANUAL)
        }

        Toast.makeText(
            this,
            getString(R.string.sms_history_forward_trigger_toast, selected.size),
            Toast.LENGTH_LONG
        ).show()

        adapter.selectAll(false)
    }

    private companion object {
        private const val REQUEST_READ_SMS_PERMISSION = 2002
    }
}
