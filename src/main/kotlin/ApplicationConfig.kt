package dev.limebeck

import dev.limebeck.mattermost.TeamId
import com.sksamuel.hoplite.Masked
import kotlin.time.Duration


data class ApplicationConfig(
    val chatgpt: ChatGptConfig,
    val mattermost: MattermostConfig,
    val allowedTeams: List<TeamId>? = null,
    val cache: CacheConfig,
    val aggressiveModeUsers: List<String> = listOf(),
)

data class ChatGptConfig(
    val temperature: Float,
    val model: String,
    val apiKey: Masked,
)

data class MattermostConfig(
    val baseUrl: String,
    val apiToken: Masked,
    val chunkSize: Int = 16383,
)

sealed interface CacheConfig {
    data object InMemory : CacheConfig
    data class Redis(
        val endpoint: String,
        val expiration: Duration,
    ) : CacheConfig
}