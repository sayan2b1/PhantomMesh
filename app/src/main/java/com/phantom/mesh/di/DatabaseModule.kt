package com.phantom.mesh.di

import android.content.Context
import androidx.room.Room
import com.phantom.mesh.data.local.PhantomMeshDatabase
import com.phantom.mesh.data.local.dao.CallLogDao
import com.phantom.mesh.data.local.dao.ChatDao
import com.phantom.mesh.data.local.dao.DropZoneDao
import com.phantom.mesh.data.local.dao.MeshQueueDao
import com.phantom.mesh.data.local.dao.MessageDao
import com.phantom.mesh.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room database and all DAO singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE users ADD COLUMN security_question TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE users ADD COLUMN security_answer_hash TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_message_id TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_content TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_sender TEXT DEFAULT NULL") } catch (_: Throwable) {}
        }
    }

    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE users ADD COLUMN security_question TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE users ADD COLUMN security_answer_hash TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_message_id TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_content TEXT DEFAULT NULL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_sender TEXT DEFAULT NULL") } catch (_: Throwable) {}
        }
    }

    private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS call_logs (
                        call_id TEXT NOT NULL PRIMARY KEY,
                        peer_username TEXT NOT NULL,
                        peer_address TEXT NOT NULL,
                        call_type TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        duration_seconds INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_timestamp ON call_logs (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_peer_username ON call_logs (peer_username)")
            } catch (_: Throwable) {}
            try {
                db.execSQL("ALTER TABLE chats ADD COLUMN is_muted INTEGER NOT NULL DEFAULT 0")
            } catch (_: Throwable) {}
        }
    }

    private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE chats ADD COLUMN draft_message TEXT DEFAULT NULL")
            } catch (_: Throwable) {}
        }
    }

    private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN is_edited INTEGER NOT NULL DEFAULT 0")
            } catch (_: Throwable) {}
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN edited_at INTEGER DEFAULT NULL")
            } catch (_: Throwable) {}
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PhantomMeshDatabase {
        return Room.databaseBuilder(
            context,
            PhantomMeshDatabase::class.java,
            PhantomMeshDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserDao(db: PhantomMeshDatabase): UserDao = db.userDao()

    @Provides
    fun provideChatDao(db: PhantomMeshDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: PhantomMeshDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideDropZoneDao(db: PhantomMeshDatabase): DropZoneDao = db.dropZoneDao()

    @Provides
    fun provideMeshQueueDao(db: PhantomMeshDatabase): MeshQueueDao = db.meshQueueDao()

    @Provides
    fun provideCallLogDao(db: PhantomMeshDatabase): CallLogDao = db.callLogDao()
}
