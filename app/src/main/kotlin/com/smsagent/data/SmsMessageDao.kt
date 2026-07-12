package com.smsagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SmsMessageDao {

    @Query(
        """
        SELECT COUNT(*)
        FROM sms_messages
        WHERE messageHash = :messageHash
          AND receivedAtMillis >= :sinceMillis
        """,
    )
    fun countRecentByHash(messageHash: Int, sinceMillis: Long): Int

    @Insert
    fun insert(message: SmsMessageEntity): Long

    @Query(
        """
        SELECT *
        FROM sms_messages
        WHERE status IN (:statuses)
        ORDER BY receivedAtMillis ASC
        LIMIT :limit
        """,
    )
    fun getByStatuses(statuses: List<String>, limit: Int): List<SmsMessageEntity>

    @Query(
        """
        UPDATE sms_messages
        SET status = :status,
            lastError = :lastError,
            attemptCount = attemptCount + :attemptIncrement,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    fun updateForwardState(
        id: Long,
        status: String,
        lastError: String?,
        attemptIncrement: Int,
        updatedAtMillis: Long,
    )

    @Query("SELECT * FROM sms_messages WHERE systemSmsId = :systemSmsId LIMIT 1")
    fun getBySystemSmsId(systemSmsId: Long): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages WHERE receivedAtMillis >= :sinceMillis ORDER BY receivedAtMillis DESC")
    fun getRecentMessages(sinceMillis: Long): List<SmsMessageEntity>

    @Query("UPDATE sms_messages SET systemSmsId = :systemSmsId, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    fun updateSystemSmsId(id: Long, systemSmsId: Long, updatedAtMillis: Long)
}
