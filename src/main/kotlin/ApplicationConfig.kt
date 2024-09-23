package dev.limebeck

import dev.limebeck.mattermost.TeamId


data class ApplicationConfig(
    val chatgpt: ChatGptConfig,
    val mattermost: MattermostConfig,
    val allowedTeams: List<TeamId>? = null,
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