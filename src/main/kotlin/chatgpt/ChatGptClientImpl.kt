package dev.limebeck.chatgpt

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ChatGptClientImpl(
    private val apiKey: String,
    private val model: String,
    private val temperature: Float,
) : ChatGptClient {

    companion object {
        const val BASE_MODEL_NAME = "gpt-4o"
        private val logger = LoggerFactory.getLogger(ChatGptClientImpl::class.java)
    }

    private val client = HttpClient(Apache) {
        engine {
            followRedirects = true
            socketTimeout = 120_000
            connectTimeout = 10_000
            connectionRequestTimeout = 120_000
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    ChatGptClientImpl.logger.debug(message)
                }
            }
            level = LogLevel.ALL
            sanitizeHeader { it == "Authorization" }
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    encodeDefaults = true
                },
            )
        }

        defaultRequest {
            header("Authorization", "Bearer $apiKey")
        }
    }

    override suspend fun getCompletion(text: String, previousMessages: List<Message>): Result<Completion> =
        kotlin.runCatching {
            val url = "https://api.openai.com/v1/chat/completions"

            val messages = previousMessages + Message(Role.USER, text)

            logger.info("<18a8d465> Получаем ответ ChatGPT для запроса:\n" + messages.joinToString("\n") { "${it.role}: ${it.content.take(200)}" })

            val request = CompletionRequest(
                model = model,
                temperature = temperature,
                messages = messages
            )

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<CompletionResponse>()

            logger.info("<6f1106dd> Запрос успешно завершен, использовано ${response.usage.totalTokens} токенов")
            Completion(response.choices.first().message.content, response.usage.totalTokens)
        }

    @Serializable
    data class CompletionRequest(
        val model: String,
        val temperature: Float,
        val messages: List<Message>
    )

    @Serializable
    data class CompletionResponse(
        val choices: List<Choice>,
        val usage: Usage,
        val model: String,
    ) {
        @Serializable
        data class Choice(
            val message: Message,
            @SerialName("finish_reason")
            val finishReason: String,
        )

        @Serializable
        data class Usage(
            @SerialName("prompt_tokens") val promptTokens: Long,
            @SerialName("completion_tokens") val completionTokens: Long,
            @SerialName("total_tokens") val totalTokens: Long,
        )
    }
}