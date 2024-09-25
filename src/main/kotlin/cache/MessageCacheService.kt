package dev.limebeck.cache

import dev.limebeck.chatgpt.Message
import dev.limebeck.mattermost.UserId

interface MessagesCacheService {
    suspend fun put(userId: UserId, messages: List<Message>)
    suspend fun get(userId: UserId): List<Message>?
}

class InMemoryMessagesCacheService : MessagesCacheService {
    private val cache = mutableMapOf<UserId, List<Message>>()

    override suspend fun put(userId: UserId, messages: List<Message>) {
        cache[userId] = messages
    }

    override suspend fun get(userId: UserId): List<Message>? = cache[userId]
}
