package dev.limebeck.context

import dev.limebeck.chatgpt.Message
import kotlinx.serialization.Serializable

@Serializable
data class UserContext(
    val modelName: String,
    val systemMessage: String?,
    val previousMessages: List<Message> = listOf(),
)