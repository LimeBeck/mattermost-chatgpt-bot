package dev.limebeck.command

import dev.limebeck.chatgpt.ChatGptClient
import dev.limebeck.chatgpt.Message
import dev.limebeck.chatgpt.Role
import dev.limebeck.context.RequestContext
import dev.limebeck.context.UserContextService
import dev.limebeck.logger
import dev.limebeck.mattermost.MattermostClient

class CompletionCommandProcessor(
    private val gptClient: ChatGptClient,
    private val userContextService: UserContextService,
    private val mattermostClient: MattermostClient
) : CommandProcessor {
    override val name: String = "completion"

    override suspend fun processCommand(ctx: RequestContext, arguments: List<String>) {
        logger.info("<173c4c43> Обработка запроса клиента ${ctx.userName}")
        val response = gptClient.getCompletion(ctx.message.text, ctx.userContext.previousMessages).onFailure { t ->
            logger.error("<ea88cf0c> Произошла ошибка при обработке запроса ${ctx.requestUuid}", t)
        }.onSuccess { response ->
            userContextService.updateUserContext(
                ctx.userId, ctx.userContext.copy(
                    previousMessages = ctx.userContext.previousMessages
                            + Message(Role.USER, ctx.message.text)
                            + Message(Role.ASSISTANT, response.text)
                )
            )
        }

        mattermostClient.sendMessage(
            ctx.channelId,
            response.getOrNull()?.text?.let {
                val tokens = response.getOrThrow().tokensConsumed
                "$it\n\nВаш запрос потребил $tokens токенов"
            } ?: "Произошла ошибка при обработке запроса. Код ошибки ${ctx.requestUuid}",
        )
    }
}