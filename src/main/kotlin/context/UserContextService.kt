package dev.limebeck.context

import dev.limebeck.ApplicationConfig
import dev.limebeck.cache.CacheService
import dev.limebeck.chatgpt.Message
import dev.limebeck.mattermost.UserId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

interface UserContextService {
    suspend fun getUserContext(userId: UserId): UserContext
    suspend fun updateUserContext(userId: UserId, context: UserContext)
}

class CachedUserContextService(
    private val cacheService: CacheService,
    private val applicationConfig: ApplicationConfig
) : UserContextService {
    companion object {
        private const val KEY_MESSAGES = "messages"
        private const val KEY_MODEL = "model"
        private const val KEY_SYSTEM_MESSAGE = "SYSTEM_MESSAGE"

        private val logger = LoggerFactory.getLogger(CachedUserContextService::class.java)!!

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }

    override suspend fun getUserContext(userId: UserId): UserContext {
        logger.debug("<e2d1208c> Fetching user context for user $userId")
        val model = cacheService.get(userId, KEY_MODEL) ?: applicationConfig.chatgpt.defaultModel
        val systemMessage = cacheService.get(userId, KEY_SYSTEM_MESSAGE)
        val previousMessages = cacheService.get(userId, KEY_MESSAGES)
            ?.let { json.decodeFromString<List<Message>?>(it) }
            ?: listOf()

        return UserContext(
            model,
            systemMessage,
            previousMessages
        )
    }

    override suspend fun updateUserContext(userId: UserId, context: UserContext) {
        logger.debug("<e2d1208c> Updating user context for user {}: {}", userId, context)
        cacheService.put(userId, KEY_MODEL, context.modelName)
        cacheService.put(userId, KEY_MESSAGES, json.encodeToString(context.previousMessages), expirable = true)
        context.systemMessage?.let { cacheService.put(userId, KEY_SYSTEM_MESSAGE, it) }
    }
}
