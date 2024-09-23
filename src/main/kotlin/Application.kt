package dev.limebeck

import arrow.continuations.SuspendApp
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addCommandLineSource
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import dev.limebeck.chatgpt.ChatGptClientImpl
import dev.limebeck.mattermost.MattermostClientImpl
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

    val chatGptClient = ChatGptClientImpl(
        apiKey = config.chatgpt.apiKey,
        model = config.chatgpt.model,
        temperature = config.chatgpt.temperature
    )

    val mattermostClient = MattermostClientImpl(
        apiToken = config.mattermost.apiToken,
        baseUrl = config.mattermost.baseUrl,
    )

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
        val response = chatGptClient.getCompletion(message.text).onFailure { t ->
            logger.error("<ea88cf0c> Произошла ошибка при обработке запроса $requestUuid", t)
        }
        mattermostClient.sendMessage(
            message.channelId,
            response.getOrElse { "Произошла ошибка при обработке запроса. Код ошибки $requestUuid" },
        )
    }
}
