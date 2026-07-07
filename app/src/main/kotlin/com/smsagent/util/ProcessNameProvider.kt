package com.smsagent.util

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.FileInputStream

object ProcessNameProvider {

    fun getProcessName(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            readProcessNameFromProc() ?: context.packageName
        }
    }

    private fun readProcessNameFromProc(): String? {
        return runCatching {
            FileInputStream("/proc/self/cmdline").use { stream ->
                val buffer = ByteArray(256)
                val length = stream.read(buffer)
                if (length <= 0) {
                    null
                } else {
                    String(buffer, 0, length, Charsets.UTF_8)
                        .trim { it <= ' ' || it == '\u0000' }
                        .takeIf { it.isNotBlank() }
                }
            }
        }.getOrNull()
    }
}
