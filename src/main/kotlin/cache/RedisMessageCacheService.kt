package dev.limebeck.cache

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import dev.limebeck.chatgpt.Message
import dev.limebeck.mattermost.UserId

class RedisMessagesCacheService(
    endpoint: String
) : MessagesCacheService {
    private val client = newClient(Endpoint.from(endpoint))

    companion object {
        private val logger = LoggerFactory.getLogger(RedisMessagesCacheService::class.java)
    }

    init {
        logger.info("<a134e07c> Connected to $endpoint")
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun put(userId: UserId, messages: List<Message>) {
        client.set(userId.value, json.encodeToString(messages))
    }

    override suspend fun get(userId: UserId): List<Message>? =
        client.get(userId.value)?.let { json.decodeFromString<List<Message>?>(it) }
}