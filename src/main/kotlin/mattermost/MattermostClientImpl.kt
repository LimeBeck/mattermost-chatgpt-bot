package dev.limebeck.mattermost

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class MattermostClientImpl(
    baseUrl: String,
    private val apiToken: String
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

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
            }
        }

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

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun startWebSocketSession(websocketUrl: String) = scope.launch {
        while (isActive) {
            try {
                client.webSocket(websocketUrl) {
                    logger.info("<3eaf6bd6> Установлено подключение по WebSocket к $websocketUrl")
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
                logger.error("<123dd0ba> WebSocket error:", e)
                // Логируем и ждем перед повторным подключением
                delay(3000)
            }
        }
    }

    private fun launch() {
        val url = baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + API_PATH + "/websocket"
        startWebSocketSession(url)
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
        val props: JsonObject? = null
    )

    @Serializable
    data class PostToSend(
        @SerialName("channel_id") val channelId: ChannelId,
        val message: String,
    )

    override suspend fun receiveDirectMessages(): Flow<DirectMessage> =
        flow
            .filter { it.event == "posted" }
            .map { it to jsonMapper.decodeFromString<Post>(it.data.jsonObject["post"]?.jsonPrimitive?.content!!) }
            .filter { (event, post) -> post.props?.get("from_bot")?.jsonPrimitive?.booleanOrNull != true }
            .map { (event, post) ->
                DirectMessage(
                    channelId = post.channelId,
                    userId = post.userId,
                    userName = event.data.jsonObject["sender_name"]?.jsonPrimitive?.content ?: "unknown",
                    text = post.message,
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
        val result = client.post("$baseUrl$API_PATH/posts") {
            setBody(PostToSend(channelId, message))
        }

        if (result.status != HttpStatusCode.Created) {
            throw RuntimeException("<b326ae01> Ошибка отправки сообщения в Mattermost status = ${result.status}")
        }
    }

    override suspend fun isMemberOfTeam(userId: UserId, teamId: TeamId): Boolean {
        val result = client.get("$baseUrl$API_PATH/teams/${teamId.value}/members/${userId.value}")
        return result.status == HttpStatusCode.OK
    }
}
