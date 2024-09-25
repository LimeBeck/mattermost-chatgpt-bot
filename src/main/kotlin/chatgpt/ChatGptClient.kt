package dev.limebeck.chatgpt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface ChatGptClient {
    suspend fun getCompletion(text: String, previousMessages: List<Message>): Result<String>
}

@Serializable
data class Message(
    val role: Role,
    val content: String,
)

@Serializable
enum class Role {
    @SerialName("assistant")
    ASSISTANT,

    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER
}