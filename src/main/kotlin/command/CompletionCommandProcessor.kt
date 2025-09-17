package dev.limebeck.command

import dev.limebeck.chatgpt.ChatGptClient
import dev.limebeck.chatgpt.Content
import dev.limebeck.chatgpt.FileData
import dev.limebeck.chatgpt.ImageUrl
import dev.limebeck.chatgpt.Message
import dev.limebeck.chatgpt.Role
import dev.limebeck.context.RequestContext
import dev.limebeck.context.UserContextService
import dev.limebeck.logger
import dev.limebeck.mattermost.MattermostClient
import java.util.Base64

class CompletionCommandProcessor(
    private val gptClient: ChatGptClient,
    private val userContextService: UserContextService,
    private val mattermostClient: MattermostClient,
) : CommandProcessor {
    override val name: String = "completion"

    override suspend fun processCommand(
        ctx: RequestContext,
        arguments: List<String>,
    ) {
        logger.info("<173c4c43> Обработка запроса клиента ${ctx.userName}")
        val previousMessages =
            if (ctx.userContext.previousMessages.isEmpty() && ctx.userContext.systemMessage != null) {
                listOf(
                    Message(
                        role = Role.SYSTEM,
                        content = listOf(Content.Text(ctx.userContext.systemMessage)),
                    ),
                )
            } else {
                ctx.userContext.previousMessages
            }
        val contents =
            buildList {
                if (ctx.message.text.isNotBlank()) {
                    add(Content.Text(ctx.message.text))
                }
                ctx.message.attachments.forEach { attachment ->
                    val base64 = Base64.getEncoder().encodeToString(attachment.data)
                    if (attachment.mimeType.startsWith("image")) {
                        add(
                            Content.Image(
                                ImageUrl("data:${attachment.mimeType};base64,$base64"),
                            ),
                        )
                    } else {
                        add(
                            Content.File(
                                FileData(
                                    filename = attachment.name,
                                    fileData = base64,
                                ),
                            ),
                        )
                    }
                }
            }

        val response =
            gptClient.getCompletion(
                Message(Role.USER, contents),
                previousMessages,
            ).onFailure { t ->
                logger.error("<ea88cf0c> Произошла ошибка при обработке запроса ${ctx.requestUuid}", t)
            }.onSuccess { response ->
                userContextService.updateUserContext(
                    ctx.userId,
                    ctx.userContext.copy(
                        previousMessages =
                            previousMessages +
                                Message(Role.USER, contents) +
                                Message(Role.ASSISTANT, listOf(Content.Text(response.text))),
                    ),
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
