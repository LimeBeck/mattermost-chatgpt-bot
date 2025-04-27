package dev.limebeck.cache

import dev.limebeck.mattermost.UserId
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class RedisCacheService(
    endpoint: String,
    private val expiration: Duration = 1.hours,
) : CacheService {
    private val client = newClient(Endpoint.from(endpoint))

    companion object {
        private val logger = LoggerFactory.getLogger(RedisCacheService::class.java)
    }

    init {
        logger.info("<a134e07c> Connected to $endpoint")
    }

    override suspend fun put(userId: UserId, key: String, value: String, expirable: Boolean) {
        val cacheKey = "${userId.value}:${key}"
        client.set(cacheKey, value)
        if (expirable) {
            client.expire(cacheKey, expiration.inWholeSeconds.toULong())
        }
    }

    override suspend fun get(userId: UserId, key: String): String? =
        client.get("${userId.value}:${key}")
}