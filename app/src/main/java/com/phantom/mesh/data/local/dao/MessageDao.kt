package com.phantom.mesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.phantom.mesh.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE message_id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    /** Paginated message stream for a chat — latest first, reactive */
    @Query("""
        SELECT * FROM messages 
        WHERE chat_id = :chatId 
        ORDER BY timestamp DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun observeMessages(chatId: String, limit: Int = 50, offset: Int = 0): Flow<List<MessageEntity>>

    /** All messages for a chat (non-paginated, for export/sync) */
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForChat(chatId: String): List<MessageEntity>

    /** Update delivery status for hop-state read receipts */
    @Query("UPDATE messages SET delivery_status = :status WHERE message_id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    /** Batch update delivery status (for ACK packets covering multiple messages) */
    @Query("UPDATE messages SET delivery_status = :status WHERE message_id IN (:messageIds)")
    suspend fun batchUpdateDeliveryStatus(messageIds: List<String>, status: String)

    /** Update hop count when forwarding through mesh */
    @Query("UPDATE messages SET hop_count = :hopCount WHERE message_id = :messageId")
    suspend fun updateHopCount(messageId: String, hopCount: Int)

    /** Find undelivered messages for a target (for mesh retry on reconnection) */
    @Query("""
        SELECT * FROM messages 
        WHERE target_id = :targetId 
          AND delivery_status IN ('saved', 'mesh_sent')
        ORDER BY timestamp ASC
    """)
    suspend fun getUndeliveredMessagesForTarget(targetId: String): List<MessageEntity>

    /** Count unread messages in a chat */
    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE chat_id = :chatId 
          AND sender_id != :currentUserId 
          AND delivery_status != 'read'
    """)
    suspend fun countUnreadInChat(chatId: String, currentUserId: String): Int

    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    /** Delete a single message locally ("Delete for Me") */
    @Query("DELETE FROM messages WHERE message_id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    /** Mark a message as deleted for everyone (WhatsApp style) */
    @Query("UPDATE messages SET content = :deletedText, media_local_uri = null, media_cloud_url = null, data_type = 'deleted', mime_type = null WHERE message_id = :messageId")
    suspend fun markMessageAsDeleted(messageId: String, deletedText: String = "🚫 This message was deleted")

    @Query("SELECT * FROM messages WHERE message_id = :messageId OR message_id LIKE '%' || :token || '%' LIMIT 1")
    suspend fun getMessageByIdOrToken(messageId: String, token: String): MessageEntity?

    @Query("UPDATE messages SET content = :deletedText, media_local_uri = null, media_cloud_url = null, data_type = 'deleted', mime_type = null WHERE message_id = :messageId OR message_id LIKE '%' || :token || '%'")
    suspend fun markMessageAsDeletedByToken(messageId: String, token: String, deletedText: String = "🚫 This message was deleted")

    // ── DURESS: Nuclear data wipe ──────────────────────────────
    @Query("DELETE FROM messages")
    suspend fun nukeAllMessages()
}
