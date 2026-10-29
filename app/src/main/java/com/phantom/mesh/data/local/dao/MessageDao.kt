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

    /** All messages across all chats (for full local database backup) */
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<MessageEntity>

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

    /** Get IDs of all unread incoming messages in a chat */
    @Query("""
        SELECT message_id FROM messages 
        WHERE chat_id = :chatId 
          AND sender_id != :currentUserId 
          AND delivery_status != 'read'
        ORDER BY timestamp ASC
    """)
    suspend fun getUnreadMessageIds(chatId: String, currentUserId: String): List<String>

    /** Mark all incoming messages in a chat as read */
    @Query("""
        UPDATE messages 
        SET delivery_status = 'read' 
        WHERE chat_id = :chatId 
          AND (sender_id != :currentUserId AND sender_id != :currentUsername)
          AND delivery_status != 'read'
    """)
    suspend fun markIncomingMessagesAsRead(chatId: String, currentUserId: String, currentUsername: String)

    /** Backward-compatible overload */
    @Query("""
        UPDATE messages 
        SET delivery_status = 'read' 
        WHERE chat_id = :chatId 
          AND sender_id != :currentUserId 
          AND delivery_status != 'read'
    """)
    suspend fun markIncomingMessagesAsRead(chatId: String, currentUserId: String)

    /** Mark all sent messages in a chat as read (by chatId) */
    @Query("""
        UPDATE messages 
        SET delivery_status = 'read' 
        WHERE chat_id = :chatId 
          AND (sender_id = :myUserId OR sender_id = :myUsername OR target_id != :myUserId)
          AND delivery_status != 'read'
    """)
    suspend fun markSentMessagesAsReadByChat(chatId: String, myUserId: String, myUsername: String)

    /** Mark all sent messages as delivered in a chat */
    @Query("""
        UPDATE messages 
        SET delivery_status = 'delivered' 
        WHERE chat_id = :chatId 
          AND (sender_id = :myUserId OR sender_id = :myUsername OR target_id != :myUserId)
          AND delivery_status IN ('saved', 'mesh_sent')
    """)
    suspend fun markSentMessagesAsDeliveredByChat(chatId: String, myUserId: String, myUsername: String)

    /** Mark sent messages in a chat as read if sent on or before readTimestamp (with 5s clock-skew tolerance) */
    @Query("""
        UPDATE messages 
        SET delivery_status = 'read' 
        WHERE chat_id = :chatId 
          AND (sender_id = :myUserId OR sender_id = :myUsername OR target_id != :myUserId)
          AND timestamp <= (:readTimestamp + 5000)
          AND delivery_status != 'read'
    """)
    suspend fun markSentMessagesAsReadBefore(chatId: String, myUserId: String, myUsername: String, readTimestamp: Long)

    /** Mark sent messages in a chat as delivered if sent on or before deliveredTimestamp (with 5s clock-skew tolerance) */
    @Query("""
        UPDATE messages 
        SET delivery_status = 'delivered' 
        WHERE chat_id = :chatId 
          AND (sender_id = :myUserId OR sender_id = :myUsername OR target_id != :myUserId)
          AND timestamp <= (:deliveredTimestamp + 5000)
          AND delivery_status IN ('saved', 'mesh_sent')
    """)
    suspend fun markSentMessagesAsDeliveredBefore(chatId: String, myUserId: String, myUsername: String, deliveredTimestamp: Long)

    /** Mark all sent messages to a peer as read when peer sends read receipt ACK */
    @Query("""
        UPDATE messages 
        SET delivery_status = 'read' 
        WHERE (sender_id = :myUserId OR sender_id = :myUsername)
          AND (target_id = :peerId OR target_id = :peerUsername OR chat_id = :chatId)
          AND delivery_status != 'read'
    """)
    suspend fun markChatMessagesAsReadByPeer(
        myUserId: String,
        peerId: String,
        myUsername: String = "",
        peerUsername: String = "",
        chatId: String = ""
    )

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
