package dev.limebeck

import arrow.continuations.SuspendApp
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addCommandLineSource
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import dev.limebeck.cache.InMemoryMessagesCacheService
import dev.limebeck.cache.RedisMessagesCacheService
import dev.limebeck.chatgpt.ChatGptClientImpl
import dev.limebeck.chatgpt.Message
import dev.limebeck.chatgpt.Role
import dev.limebeck.mattermost.MattermostClientImpl
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Application

val logger = LoggerFactory.getLogger(Application::class.java)

@OptIn(ExperimentalUuidApi::class)
fun main(args: Array<String>) = SuspendApp {

    val configLoader = ConfigLoaderBuilder
        .default()
        .addEnvironmentSource()
        .addCommandLineSource(args)
        .addFileSource("config.yaml", optional = true)
        .build()

    val config = configLoader.loadConfigOrThrow<ApplicationConfig>()

    logger.info("<b5e80b85> Загружен конфиг: $config")

    val gptService = ChatGptClientImpl(
        apiKey = config.chatgpt.apiKey.value,
        model = config.chatgpt.model,
        temperature = config.chatgpt.temperature
    )

    val mattermostClient = MattermostClientImpl(
        apiToken = config.mattermost.apiToken.value,
        baseUrl = config.mattermost.baseUrl,
        chunkSize = config.mattermost.chunkSize,
    )

    val messagesCacheService = when (config.cache) {
        is CacheConfig.InMemory -> InMemoryMessagesCacheService()
        is CacheConfig.Redis -> RedisMessagesCacheService(config.cache.endpoint, config.cache.expiration)
    }

    val helpMessage = """
        Я - умный чат-бот. Сейчас я работаю через модель ${config.chatgpt.model}. 
        Меня можно спрашивать о чем угодно, главное - помнить о безопасности данных.
        Контекст чата сохраняется${
        when (config.cache) {
            is CacheConfig.InMemory -> " в памяти до рестарта сервера."
            is CacheConfig.Redis -> ", но не долго - всего " + config.cache.expiration.inWholeMinutes + " минут."
        }
    }
        
        Доступные команды:
        * `help` - вывести это сообщение
        * `end` - сбросить контекст (завершить этот чат и начать новый)
    """.trimIndent()


    val jobs = buildList {
        launch {
            mattermostClient.receiveNewChatStarted().collect { newChatStarted ->
                mattermostClient.sendMessage(
                    channelId = newChatStarted.channelId,
                    message = "Привет!\n$helpMessage",
                )
            }
        }
        launch {
            mattermostClient.receiveDirectMessages().collect { message ->
                val requestUuid = Uuid.random().toString()
                config.allowedTeams?.let {
                    val inAllowedTeam = it.any { team ->
                        mattermostClient.isMemberOfTeam(message.userId, team)
                    }
                    if (!inAllowedTeam) {
                        mattermostClient.sendMessage(
                            message.channelId,
                            "К сожалению, у вас еще нет доступа. Функционал работает в режиме тестирования.",
                        )
                        return@collect
                    }
                }

                when {
                    message.text.equals("end", ignoreCase = true) -> {
                        logger.info("<6715dde2> Очистка сессии клиента ${message.userName}")
                        messagesCacheService.put(message.userId, emptyList())
                        mattermostClient.sendMessage(
                            channelId = message.channelId,
                            message = "Сессия успешно завершена",
                        )
                    }

                    message.text.equals("help", ignoreCase = true) -> {
                        mattermostClient.sendMessage(
                            channelId = message.channelId,
                            message = helpMessage,
                        )
                    }

                    else -> {
                        logger.info("<173c4c43> Обработка запроса клиента ${message.userName}")
                        val passiveAggressiveMode = message.userName in config.aggressiveModeUsers
                        val previousMessages = messagesCacheService.get(message.userId)?.takeIf { it.isNotEmpty() }
                            ?: if (passiveAggressiveMode) {
                                listOf(Message(Role.SYSTEM, "Действуй как пассивно-агрессивный сеньор разработчик c учетом запроса пользователя"))
                            } else {
                                emptyList()
                            }

                        val response = gptService.getCompletion(message.text, previousMessages).onFailure { t ->
                            logger.error("<ea88cf0c> Произошла ошибка при обработке запроса $requestUuid", t)
                        }.onSuccess { response ->
                            messagesCacheService.put(
                                userId = message.userId,
                                messages = previousMessages
                                        + Message(Role.USER, message.text)
                                        + Message(Role.ASSISTANT, response.text),
                            )
                        }

                        mattermostClient.sendMessage(
                            message.channelId,
                            response.getOrNull()?.text?.let {
                                val tokens = response.getOrThrow().tokensConsumed
                                "$it\n\nВаш запрос потребил $tokens токенов"
                            } ?: "Произошла ошибка при обработке запроса. Код ошибки $requestUuid",
                        )
                    }
                }
            }
        }.also { add(it) }
    }

    jobs.joinAll()
}
