package dev.limebeck

import com.sksamuel.hoplite.ConfigAlias
import dev.limebeck.mattermost.TeamId
import com.sksamuel.hoplite.Masked
import kotlin.time.Duration


data class ApplicationConfig(
    val chatgpt: ChatGptConfig,
    val mattermost: MattermostConfig,
    val allowedTeams: List<TeamId>? = null,
    val cache: CacheConfig,
)

data class ChatGptConfig(
    val temperature: Float,
    @ConfigAlias("model")
    val defaultModel: String,
    val availableModels: Set<Model>,
    val apiKey: Masked,
) {
    data class Model(
        val name: String,
        val multimodal: Boolean = false,
    )
}

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