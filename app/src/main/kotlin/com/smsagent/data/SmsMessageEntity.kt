package com.smsagent.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["messageHash", "receivedAtMillis"]),
        Index(value = ["status"]),
    ],
)
data class SmsMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val systemSmsId: Long? = null,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val messageHash: Int,
    val source: String,
    val status: String,
    val lastError: String? = null,
    val attemptCount: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
