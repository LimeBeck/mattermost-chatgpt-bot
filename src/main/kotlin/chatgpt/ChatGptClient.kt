package dev.limebeck.chatgpt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

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
    @Serializable(with = ContentListSerializer::class)
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

object ContentListSerializer : KSerializer<List<Content>> {
    private val delegate = ListSerializer(Content.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<Content>,
    ) {
        encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): List<Content> {
        if (decoder is JsonDecoder) {
            val element: JsonElement = decoder.decodeJsonElement()
            val json = decoder.json
            return when {
                element is JsonArray -> element.map { json.decodeFromJsonElement(Content.serializer(), it) }
                element is JsonObject -> listOf(json.decodeFromJsonElement(Content.serializer(), element))
                element is JsonNull -> emptyList()
                element is JsonPrimitive -> listOf(Content.Text(element.contentOrNull ?: element.toString()))
                else -> emptyList()
            }
        }

        return decoder.decodeSerializableValue(delegate)
    }
}

@Serializable
enum class Role {
    @SerialName("assistant")
    ASSISTANT,

    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER,
}
