package dev.limebeck

import arrow.continuations.SuspendApp
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addCommandLineSource
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import dev.limebeck.chatgpt.ChatGptClientImpl
import dev.limebeck.chatgpt.Message
import dev.limebeck.chatgpt.Role
import dev.limebeck.mattermost.MattermostClientImpl
import dev.limebeck.cache.InMemoryMessagesCacheService
import dev.limebeck.cache.RedisMessagesCacheService
import org.slf4j.LoggerFactory
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.getOrThrow

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
        apiKey = config.chatgpt.apiKey,
        model = config.chatgpt.model,
        temperature = config.chatgpt.temperature
    )

    val mattermostClient = MattermostClientImpl(
        apiToken = config.mattermost.apiToken,
        baseUrl = config.mattermost.baseUrl,
    )

    val messagesCacheService = when (config.cache) {
        is CacheConfig.InMemory -> InMemoryMessagesCacheService()
        is CacheConfig.Redis -> RedisMessagesCacheService(config.cache.endpoint)
    }


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
                    message = """
                        ## Я - умный чат-бот
                        
                        Сейчас я работаю через модель ${config.chatgpt.model}
                        
                        Доступные команды:
                        * `help` - вывести это сообщение
                        * `end` - сбросить контекст (завершить этот чат и начать новый)
                    """.trimIndent(),
                )
            }

            else -> {
                logger.info("<173c4c43> Обработка запроса клиента ${message.userName}")
                val previousMessages = messagesCacheService.get(message.userId) ?: emptyList()
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
                    response.getOrNull()?.text?.let { it + "\n\nВаш запрос потребил ${response.getOrThrow().tokensConsumed} токенов" } 
                        ?: "Произошла ошибка при обработке запроса. Код ошибки $requestUuid",
                )
            }
        }
    }
}
