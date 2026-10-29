package com.phantom.mesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.phantom.mesh.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE chat_id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE chat_id = :chatId LIMIT 1")
    fun observeChat(chatId: String): Flow<ChatEntity?>

    /** Active conversations sorted by most recent message */
    @Query("""
        SELECT c.* FROM chats c 
        WHERE c.is_archived = 0 
        ORDER BY MAX(c.last_message_timestamp, COALESCE((SELECT MAX(m.timestamp) FROM messages m WHERE m.chat_id = c.chat_id), c.created_at)) DESC
    """)
    fun observeActiveChats(): Flow<List<ChatEntity>>

    /** All chats (non-reactive, for polling sync) */
    @Query("""
        SELECT c.* FROM chats c 
        ORDER BY MAX(c.last_message_timestamp, COALESCE((SELECT MAX(m.timestamp) FROM messages m WHERE m.chat_id = c.chat_id), c.created_at)) DESC
    """)
    suspend fun getAllChats(): List<ChatEntity>

    @Query("""
        SELECT c.* FROM chats c 
        WHERE c.is_archived = 1 
        ORDER BY MAX(c.last_message_timestamp, COALESCE((SELECT MAX(m.timestamp) FROM messages m WHERE m.chat_id = c.chat_id), c.created_at)) DESC
    """)
    fun observeArchivedChats(): Flow<List<ChatEntity>>

    /** Find an existing 1:1 chat by sorted participant string */
    @Query("SELECT * FROM chats WHERE participants = :participantKey LIMIT 1")
    suspend fun findChatByParticipants(participantKey: String): ChatEntity?

    @Query("""
        UPDATE chats SET 
            last_message_preview = :preview, 
            last_message_timestamp = :timestamp,
            transport_type = :transport
        WHERE chat_id = :chatId
    """)
    suspend fun updateLastMessage(
        chatId: String,
        preview: String,
        timestamp: Long,
        transport: String
    )

    @Query("UPDATE chats SET is_muted = :isMuted WHERE chat_id = :chatId")
    suspend fun setChatMuted(chatId: String, isMuted: Boolean)

    @Query("UPDATE chats SET is_archived = :isArchived WHERE chat_id = :chatId")
    suspend fun setChatArchived(chatId: String, isArchived: Boolean)

    @Query("SELECT is_muted FROM chats WHERE chat_id = :chatId LIMIT 1")
    suspend fun isChatMuted(chatId: String): Boolean?

    @Query("UPDATE chats SET unread_count = unread_count + 1 WHERE chat_id = :chatId")
    suspend fun incrementUnread(chatId: String)

    @Query("UPDATE chats SET unread_count = 0 WHERE chat_id = :chatId")
    suspend fun clearUnread(chatId: String)

    @Query("DELETE FROM chats WHERE chat_id = :chatId")
    suspend fun deleteChat(chatId: String)

    // ── DURESS: Nuclear data wipe ──────────────────────────────
    @Query("DELETE FROM chats")
    suspend fun nukeAllChats()
}
