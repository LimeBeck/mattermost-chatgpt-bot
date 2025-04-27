package dev.limebeck.command

import dev.limebeck.ApplicationConfig
import dev.limebeck.context.RequestContext
import dev.limebeck.context.UserContextService
import dev.limebeck.mattermost.MattermostClient
import org.slf4j.LoggerFactory

class SetUserContextCommandProcessor(
    private val userContextService: UserContextService,
    private val mattermostClient: MattermostClient,
    private val config: ApplicationConfig
) : CommandProcessor {
    companion object {
        private val logger = LoggerFactory.getLogger(SetUserContextCommandProcessor::class.java)
    }

    override val name: String = "set"

    private val availableModelsMessagePart = config.chatgpt.availableModels.joinToString(", ") {
        "`${it.name}`" + if (it.multimodal) " (мультимодальная, работает с картинками)" else ""
    }

    private val helpMessage: String = """
        Команда `set` позволяет установить значение настройки для пользователя.
        Пример команды: `set model=gpt-4o`
        Доступные параметры:
        * `model` - модель ChatGPT. Доступны такие: $availableModelsMessagePart
        * `systemMessage` - системное сообщение, которое будет предопределять поведение ChatGPT.
    """.trimIndent()

    override suspend fun processCommand(ctx: RequestContext, arguments: List<String>) {
        logger.info("<43e36fa2> Set User Context Command for $ctx with arguments $arguments")
        val message = arguments.first().trim()
        if (message.lowercase().startsWith("help") || message.isBlank()) {
            mattermostClient.sendMessage(
                channelId = ctx.channelId,
                message = helpMessage
            )
            return
        }
        val (propertyName, value) = message.split("=", limit = 2)
        when (Properties.entries.find { it.alias.lowercase() == propertyName.lowercase() }) {
            Properties.MODEL_NAME -> {
                if (value.lowercase() in config.chatgpt.availableModels.map { it.name.lowercase() }) {
                    userContextService.updateUserContext(ctx.userId, ctx.userContext.copy(modelName = value))
                } else {
                    mattermostClient.sendMessage(
                        channelId = ctx.channelId,
                        message = "Неизвестная модель `${value}`.\n Доступные модели: $availableModelsMessagePart"
                    )
                    return
                }
            }

            Properties.SYSTEM_MESSAGE -> {
                userContextService.updateUserContext(
                    ctx.userId,
                    ctx.userContext.copy(systemMessage = value.takeIf { it.isNotBlank() })
                )
            }

            else -> {
                mattermostClient.sendMessage(
                    channelId = ctx.channelId,
                    message = "Неизвестная настройка `${propertyName}`.\n" + helpMessage
                )
                return
            }
        }
        mattermostClient.sendMessage(
            channelId = ctx.channelId,
            message = "Настройки успешно сохранены"
        )
    }

    enum class Properties(
        val alias: String,
    ) {
        MODEL_NAME("model"),
        SYSTEM_MESSAGE("systemMessage"),
    }
}