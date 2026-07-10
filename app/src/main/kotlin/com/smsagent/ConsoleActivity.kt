package com.smsagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.smsagent.state.EventLogStore
import com.smsagent.util.TimeFormatter

class ConsoleActivity : Activity() {

    private lateinit var consoleOutput: TextView
    private lateinit var consoleInput: TextInputEditText
    private lateinit var appendInputButton: MaterialButton
    private lateinit var refreshButton: MaterialButton
    private lateinit var copyButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var clearButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)

        consoleOutput = findViewById(R.id.consoleOutput)
        consoleInput = findViewById(R.id.consoleInput)
        appendInputButton = findViewById(R.id.appendInputButton)
        refreshButton = findViewById(R.id.refreshButton)
        copyButton = findViewById(R.id.copyButton)
        exportButton = findViewById(R.id.exportButton)
        clearButton = findViewById(R.id.clearConsoleButton)

        appendInputButton.setOnClickListener { appendManualInput() }
        refreshButton.setOnClickListener { renderConsole() }
        copyButton.setOnClickListener { copyConsole() }
        exportButton.setOnClickListener { exportConsole() }
        clearButton.setOnClickListener { clearConsole() }

        renderConsole()
    }

    override fun onResume() {
        super.onResume()
        renderConsole()
    }

    private fun appendManualInput() {
        val text = consoleInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            return
        }

        EventLogStore.append(this, "输入", text)
        consoleInput.text?.clear()
        renderConsole()
    }

    private fun renderConsole() {
        consoleOutput.text = EventLogStore.getConsoleText(this)
            .ifBlank { getString(R.string.console_empty) }
    }

    private fun copyConsole() {
        val text = EventLogStore.getConsoleText(this)
        if (text.isBlank()) {
            Toast.makeText(this, R.string.console_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.console_title), text))
        Toast.makeText(this, R.string.console_copied, Toast.LENGTH_SHORT).show()
    }

    private fun clearConsole() {
        EventLogStore.clear(this)
        renderConsole()
    }

    private fun exportConsole() {
        val text = EventLogStore.getExportText(this)
        if (text.isBlank()) {
            Toast.makeText(this, R.string.console_export_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = TimeFormatter.format(System.currentTimeMillis())
            .replace(" ", "_")
            .replace(":", "-")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TITLE,
                getString(R.string.console_export_default_name, timestamp),
            )
        }
        startActivityForResult(intent, REQUEST_EXPORT_LOG)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT_LOG || resultCode != RESULT_OK) {
            return
        }

        val targetUri = data?.data ?: return
        saveConsoleToUri(targetUri)
    }

    private fun saveConsoleToUri(targetUri: Uri) {
        val text = EventLogStore.getExportText(this)
        if (text.isBlank()) {
            Toast.makeText(this, R.string.console_export_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val success = runCatching {
            contentResolver.openOutputStream(targetUri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                requireNotNull(writer) { "output stream unavailable" }
                writer.write(text)
            }
        }.isSuccess

        Toast.makeText(
            this,
            if (success) R.string.console_export_success else R.string.console_export_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private companion object {
        private const val REQUEST_EXPORT_LOG = 1001
    }
}
