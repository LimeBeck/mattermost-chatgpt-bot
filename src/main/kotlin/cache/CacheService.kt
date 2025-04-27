package dev.limebeck.cache

import dev.limebeck.mattermost.UserId

interface CacheService {
    suspend fun put(userId: UserId, key: String, value: String, expirable: Boolean = false)
    suspend fun get(userId: UserId, key: String): String?
}

class InMemoryCacheService : CacheService {
    private val cache = mutableMapOf<UserId, MutableMap<String, String>>()

    override suspend fun put(userId: UserId, key: String, value: String, expirable: Boolean) {
        cache.getOrPut(userId,  { mutableMapOf() })[key] = value
    }

    override suspend fun get(userId: UserId, key: String): String? = cache[userId]?.get(key)
}
