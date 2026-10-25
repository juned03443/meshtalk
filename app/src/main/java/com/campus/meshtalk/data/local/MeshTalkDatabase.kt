package com.campus.meshtalk.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.campus.meshtalk.data.local.dao.*
import com.campus.meshtalk.data.local.entity.*

@Database(
    entities = [
        MessageEntity::class,
        SeenPacketEntity::class,
        QueuedMessageEntity::class,
        ContactEntity::class,
        ConnectionRequestEntity::class,
        BlockedUserEntity::class,
        RelayPacketEntity::class,
        SignalingOutboxEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class MeshTalkDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun seenPacketDao(): SeenPacketDao
    abstract fun queuedMessageDao(): QueuedMessageDao
    abstract fun contactDao(): ContactDao
    abstract fun connectionRequestDao(): ConnectionRequestDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun relayPacketDao(): RelayPacketDao
    abstract fun signalingOutboxDao(): SignalingOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: MeshTalkDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add keyChanged to contacts table
                db.execSQL("ALTER TABLE contacts ADD COLUMN keyChanged INTEGER NOT NULL DEFAULT 0")

                // 2. Add lastSentAt and sendAttempts to connection_requests table
                db.execSQL("ALTER TABLE connection_requests ADD COLUMN lastSentAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE connection_requests ADD COLUMN sendAttempts INTEGER NOT NULL DEFAULT 0")

                // 3. Drop old unique index on connection_requests and create non-unique pair index
                db.execSQL("DROP INDEX IF EXISTS index_connection_requests_fromMeshId_toMeshId")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_requests_fromMeshId_toMeshId ON connection_requests(fromMeshId, toMeshId)")

                // 4. Create signaling_outbox table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signaling_outbox (
                        id TEXT NOT NULL PRIMARY KEY,
                        requestId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        targetMeshId TEXT NOT NULL,
                        payloadBytes BLOB NOT NULL,
                        attempts INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        nextAttemptAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS emergency_sos")
                db.execSQL("DROP TABLE IF EXISTS groups")
                db.execSQL("DROP TABLE IF EXISTS group_members")
                db.execSQL("DROP TABLE IF EXISTS user_reports")
            }
        }

        fun getInstance(context: Context): MeshTalkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshTalkDatabase::class.java,
                    "meshtalk_db"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
