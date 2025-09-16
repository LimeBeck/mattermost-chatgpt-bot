package dev.limebeck.chatgpt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface ChatGptClient {
    suspend fun getCompletion(
        message: Message,
        previousMessages: List<Message>,
    ): Result<Completion>
}

data class Completion(
    val text: String,
    val tokensConsumed: Long,
)

@Serializable
data class Message(
    val role: Role,
    val content: List<Content>,
)

@Serializable
sealed class Content {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : Content()

    @Serializable
    @SerialName("image_url")
    data class Image(
        @SerialName("image_url") val imageUrl: ImageUrl,
    ) : Content()

    @Serializable
    @SerialName("file")
    data class File(
        val file: FileData,
    ) : Content()
}

@Serializable
data class ImageUrl(val url: String)

@Serializable
data class FileData(
    val filename: String,
    @SerialName("file_data") val fileData: String,
)

@Serializable
enum class Role {
    @SerialName("assistant")
    ASSISTANT,

    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER,
}
