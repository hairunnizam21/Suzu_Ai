package io.suzuai.app.data

import io.suzuai.app.data.db.ChatDao
import io.suzuai.app.data.db.ChatEntity
import io.suzuai.app.data.db.MessageDao
import io.suzuai.app.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Glue between the UI layer and Room. The server is the source of truth for
 * what the agent actually "saw" — but the client keeps an offline mirror so
 * the user can browse history while disconnected.
 */
class ChatRepository(
    private val chats: ChatDao,
    private val messages: MessageDao,
) {
    fun observeChats(): Flow<List<ChatEntity>> = chats.observeAll()

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = messages.observeByChat(chatId)

    suspend fun ensureChat(id: String, title: String) {
        val existing = chats.byId(id)
        val now = System.currentTimeMillis()
        if (existing == null) {
            chats.upsert(ChatEntity(id = id, title = title, createdAt = now, updatedAt = now))
        } else if (existing.title != title && existing.title == "New chat") {
            chats.rename(id, title, now)
        } else {
            chats.touch(id, now)
        }
    }

    suspend fun renameChat(id: String, title: String) {
        chats.rename(id, title, System.currentTimeMillis())
    }

    suspend fun deleteChat(id: String) {
        chats.delete(id)
    }

    suspend fun appendMessage(
        chatId: String,
        role: String,
        content: String,
        toolCallsJson: String = "[]",
        toolUseId: String? = null,
        toolName: String? = null,
    ): Long {
        // Defensive: make sure the parent chat exists. This prevents the
        // SQLITE_CONSTRAINT_FOREIGNKEY (code 787) crash that surfaces if a
        // streamed event arrives before the chat row is materialised.
        val now = System.currentTimeMillis()
        chats.insertIfMissing(
            ChatEntity(id = chatId, title = "New chat", createdAt = now, updatedAt = now)
        )
        chats.touch(chatId, now)
        return messages.insert(
            MessageEntity(
                chatId = chatId,
                role = role,
                content = content,
                toolCallsJson = toolCallsJson,
                toolUseId = toolUseId,
                toolName = toolName,
                createdAt = now,
            )
        )
    }
}
