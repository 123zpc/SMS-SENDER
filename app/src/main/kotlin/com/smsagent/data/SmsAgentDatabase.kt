package com.smsagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SmsMessageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SmsAgentDatabase : RoomDatabase() {

    abstract fun smsMessageDao(): SmsMessageDao

    companion object {
        @Volatile
        private var instance: SmsAgentDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN systemSmsId INTEGER DEFAULT NULL")
            }
        }

        fun get(context: Context): SmsAgentDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmsAgentDatabase::class.java,
                    "sms_agent.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
