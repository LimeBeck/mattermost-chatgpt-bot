package dev.limebeck.command

import dev.limebeck.context.RequestContext
import dev.limebeck.mattermost.MattermostClient
import org.slf4j.LoggerFactory

class CommandSystem(
    private val mattermostClient: MattermostClient,
    private val processors: List<CommandProcessor>,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(CommandSystem::class.java)
    }

    suspend fun execute(
        ctx: RequestContext,
        command: Command,
    ) {
        val processor = processors.find { it.name.uppercase() == command.name.uppercase() }
        try {
            processor?.processCommand(ctx, command.arguments) ?: run {
                logger.error("<e1ce722e> $ctx Command ${command.name} was not found")
                mattermostClient.sendMessage(
                    channelId = ctx.channelId,
                    message = "Комманда `${command.name} не найдена. Код ошибки ${ctx.requestUuid}`",
                )
            }
        } catch (e: Exception) {
            logger.error("<18b1afa5> Exception in command ${command.name}", e)
            mattermostClient.sendMessage(
                channelId = ctx.channelId,
                message = "Проишла ошибка при обработке команды ${command.name}. Код ошибки ${ctx.requestUuid}",
            )
        }
    }
}

