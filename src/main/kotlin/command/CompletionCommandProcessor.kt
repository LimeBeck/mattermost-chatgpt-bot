package dev.limebeck.command

import dev.limebeck.chatgpt.*
import dev.limebeck.context.RequestContext
import dev.limebeck.context.UserContextService
import dev.limebeck.logger
import dev.limebeck.mattermost.MattermostClient
import java.util.*

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

        ctx.message.attachments
            .filter { !it.mimeType.startsWith("image") || it.mimeType.lowercase() != "application/pdf" }
            .takeIf { it.isNotEmpty() }
            ?.let {
                logger.warn("<ea812cf0c> Пользователь приложил недопустимый тип файла (${it.joinToString { a -> a.mimeType }})")
                mattermostClient.sendMessage(
                    ctx.channelId,
                    "Добавлен недопустимый тип файла (${it.joinToString { a -> a.mimeType }}). " +
                            "Я могу работать только с изображениями и pdf",
                )
                return
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
                                    fileData = "data:${attachment.mimeType};base64,$base64",
                                ),
                            ),
                        )
                    }
                }
            }

        val response = gptClient.getCompletion(
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

        response.onSuccess { r ->
            mattermostClient.sendMessage(
                ctx.channelId,
                "${r.text}\n\nВаш запрос потребил ${r.tokensConsumed} токенов",
            )
        }.onFailure { t ->
            mattermostClient.sendMessage(
                ctx.channelId,
                "Произошла ошибка при обработке запроса. Код ошибки ${ctx.requestUuid}.\n${t.message}",
            )
        }
    }
}
