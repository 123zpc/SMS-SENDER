package com.smsagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.smsagent.state.EventLogStore

class ConsoleActivity : Activity() {

    private lateinit var consoleOutput: TextView
    private lateinit var consoleInput: TextInputEditText
    private lateinit var appendInputButton: MaterialButton
    private lateinit var refreshButton: MaterialButton
    private lateinit var copyButton: MaterialButton
    private lateinit var clearButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)

        consoleOutput = findViewById(R.id.consoleOutput)
        consoleInput = findViewById(R.id.consoleInput)
        appendInputButton = findViewById(R.id.appendInputButton)
        refreshButton = findViewById(R.id.refreshButton)
        copyButton = findViewById(R.id.copyButton)
        clearButton = findViewById(R.id.clearConsoleButton)

        appendInputButton.setOnClickListener { appendManualInput() }
        refreshButton.setOnClickListener { renderConsole() }
        copyButton.setOnClickListener { copyConsole() }
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
}
