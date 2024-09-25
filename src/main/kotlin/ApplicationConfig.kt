package dev.limebeck

import dev.limebeck.mattermost.TeamId


data class ApplicationConfig(
    val chatgpt: ChatGptConfig,
    val mattermost: MattermostConfig,
    val allowedTeams: List<TeamId>? = null,
    val cache: CacheConfig,
)

data class ChatGptConfig(
    val temperature: Float,
    val model: String,
    val apiKey: String,
)

data class MattermostConfig(
    val baseUrl: String,
    val apiToken: String
)

sealed interface CacheConfig {
    data object InMemory : CacheConfig
    data class Redis(val endpoint: String) : CacheConfig
}