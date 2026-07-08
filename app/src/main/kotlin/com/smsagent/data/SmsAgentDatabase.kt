package com.smsagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SmsMessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SmsAgentDatabase : RoomDatabase() {

    abstract fun smsMessageDao(): SmsMessageDao

    companion object {
        @Volatile
        private var instance: SmsAgentDatabase? = null

        fun get(context: Context): SmsAgentDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmsAgentDatabase::class.java,
                    "sms_agent.db",
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
