package dev.limebeck.command

import dev.limebeck.context.RequestContext
import dev.limebeck.context.UserContextService
import dev.limebeck.logger
import dev.limebeck.mattermost.DirectMessage
import dev.limebeck.mattermost.MattermostClient

class SessionEndCommandProcessor(
    private val mattermostClient: MattermostClient,
    private val userContextService: UserContextService
) : CommandProcessor {
    override val name: String = "end"

    override suspend fun processCommand(ctx: RequestContext, arguments: List<String>) {
        logger.info("<6715dde2> Очистка сессии клиента ${ctx.userName}")
        userContextService.updateUserContext(ctx.userId, ctx.userContext.copy(previousMessages = listOf()))
        mattermostClient.sendMessage(
            channelId = ctx.channelId,
            message = "Сессия успешно завершена",
        )
    }
}