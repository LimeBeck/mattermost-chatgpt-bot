package dev.limebeck.mattermost

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import utils.splitMarkdown

class MattermostClientImpl(
    baseUrl: String,
    private val apiToken: String,
    private val chunkSize: Int,
) : MattermostClient {
    companion object {
        private val logger = LoggerFactory.getLogger(MattermostClientImpl::class.java)
        private const val API_PATH = "/api/v4"
    }

    private val baseUrl = baseUrl.removeSuffix("/")

    private val jsonMapper = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        defaultRequest {
            contentType(ContentType.Application.Json)
            bearerAuth(apiToken)
        }

        install(WebSockets) {
            pingInterval = 20_000
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    MattermostClientImpl.logger.debug(message)
                }
            }
            level = LogLevel.ALL
            sanitizeHeader { it == "Authorization" }
        }

        install(ContentNegotiation) {
            json(jsonMapper)
        }
    }

    private val flow = MutableSharedFlow<InternalEvent>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun launch() {
        val url = baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + API_PATH + "/websocket"

        scope.launch {
            while (isActive) {
                try {
                    client.webSocket(url) {
                        logger.info("<3eaf6bd6> WebSocket соединение установлено: $url")
                        for (frame in incoming) {
                            val message = frame as? Frame.Text
                            if (message != null) {
                                val text = message.readText()
                                logger.debug("<c362de7a> Получено сообщение из WebSocket: $text")
                                val event = jsonMapper.decodeFromString<InternalEvent>(text)
                                flow.emit(event)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("WebSocket error:", e)
                    delay(3000) // Пауза перед reconnection
                }
            }
        }
    }

    init {
        launch()
    }

    @Serializable
    data class InternalEvent(
        val event: String,
        val data: JsonElement,
        val broadcast: Broadcast,
        val seq: Long
    )

    @Serializable
    data class Broadcast(
        @SerialName("user_id") val userId: UserId?,
        @SerialName("channel_id") val channelId: ChannelId?,
        @SerialName("team_id") val teamId: TeamId?,
        @SerialName("connection_id") val connectionId: String?,
        @SerialName("omit_connection_id") val omitConnectionId: String?,
    )

    @Serializable
    data class Post(
        @SerialName("channel_id") val channelId: ChannelId,
        val message: String,
        @SerialName("user_id") val userId: UserId,
        val props: JsonObject? = null,
        @SerialName("file_ids") val fileIds: List<String>? = null,
        val metadata: PostMetadata? = null,
    )

    @Serializable
    data class PostMetadata(
        val files: List<FileMetadata> = emptyList(),
    ) {
        @Serializable
        data class FileMetadata(
            val id: String,
            val name: String? = null,
            @SerialName("mime_type") val mimeType: String? = null,
        )
    }

    @Serializable
    data class FileInfo(
        val id: String,
        val name: String,
        @SerialName("mime_type") val mimeType: String,
    )

    @Serializable
    data class PostToSend(
        @SerialName("channel_id") val channelId: ChannelId,
        val message: String,
        val props: JsonObject
    )

    override suspend fun receiveDirectMessages(): Flow<DirectMessage> =
        flow
            .filter { it.event == "posted" }
            .map { it to jsonMapper.decodeFromString<Post>(it.data.jsonObject["post"]?.jsonPrimitive?.content!!) }
            .filter { (event, post) ->
                post.props?.get("from_bot")?.jsonPrimitive?.booleanOrNull != true
                        && event.data.jsonObject["channel_type"]?.jsonPrimitive?.content == "D"
            }.map { (event, post) ->
                val fileMetadataById =
                    buildMap {
                        post.metadata?.files?.forEach { file ->
                            put(file.id, file)
                        }
                        post.fileIds?.forEach { fileId ->
                            putIfAbsent(fileId, PostMetadata.FileMetadata(id = fileId))
                        }
                    }

                val attachments =
                    fileMetadataById.values.mapNotNull { file ->
                        runCatching {
                            val info =
                                if (file.name == null || file.mimeType == null) {
                                    client.get("$baseUrl$API_PATH/files/${file.id}/info").body<FileInfo>()
                                } else {
                                    null
                                }

                            val bytes = client.get("$baseUrl$API_PATH/files/${file.id}").body<ByteArray>()

                            Attachment(
                                id = file.id,
                                name = file.name ?: info?.name ?: file.id,
                                mimeType = file.mimeType ?: info?.mimeType ?: "application/octet-stream",
                                data = bytes,
                            )
                        }.onFailure {
                            logger.error("<download-file-error> Ошибка загрузки файла ${file.id}", it)
                        }.getOrNull()
                    }

                DirectMessage(
                    channelId = post.channelId,
                    userId = post.userId,
                    userName = event.data.jsonObject["sender_name"]?.jsonPrimitive?.content ?: "unknown",
                    text = post.message,
                    attachments = attachments,
                )
            }.onEach { logger.info("<eb86d64d> Сообщение от пользователя ${it.userName}: ${it.text.take(200)}") }

    override suspend fun receiveNewChatStarted(): Flow<NewChatStartedEvent> = flow
        .filter { it.event == "direct_added" }
        .map { event -> event to event.data.jsonObject["creator_id"]?.jsonPrimitive?.content?.let { UserId(it) } }
        .filter { (event, userId) -> userId != null && event.broadcast.channelId != null }
        .map { (event, userId) ->
            NewChatStartedEvent(
                channelId = event.broadcast.channelId!!,
                userId = userId!!,
            )
        }.onEach { logger.info("<eeb2bb55> Новый чат с пользователем с ID ${it.userId}") }

    override suspend fun sendMessage(channelId: ChannelId, message: String) {
        logger.info("<3c60bc9a> Отправка сообщения в канал $channelId: $message")

        val messageChunks = splitMarkdown(message, chunkSize)

        try {
            for (chunk in messageChunks) {
                repeatOnException(10) {
                    val result = client.post("$baseUrl$API_PATH/posts") {
                        setBody(PostToSend(channelId, chunk, JsonObject(mapOf("from_bot" to JsonPrimitive(true)))))
                    }

                    if (result.status != HttpStatusCode.Created) {
                        throw RuntimeException("<b326ae01> Ошибка отправки сообщения в Mattermost status = ${result.status}. Ответ: ${result.bodyAsText()}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("<9ffa0dd3> Ошибка при отправке сообщения в Mattermost:", e)
        }
    }

    private suspend inline fun repeatOnException(retries: Int, crossinline block: suspend () -> Unit) {
        for (i in 1..retries) {
            try {
                block()
                return
            } catch (e: Exception) {
                logger.error("<a3c1e770> Got error #$i", e)
            }
        }
    }

    override suspend fun isMemberOfTeam(userId: UserId, teamId: TeamId): Boolean {
        val result = client.get("$baseUrl$API_PATH/teams/${teamId.value}/members/${userId.value}")
        return result.status == HttpStatusCode.OK
    }
}
