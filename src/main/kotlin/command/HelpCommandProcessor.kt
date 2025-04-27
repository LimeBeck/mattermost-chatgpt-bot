package dev.limebeck.command

import dev.limebeck.ApplicationConfig
import dev.limebeck.CacheConfig
import dev.limebeck.context.RequestContext
import dev.limebeck.context.UserContext
import dev.limebeck.mattermost.MattermostClient

class HelpCommandProcessor(
    private val mattermostClient: MattermostClient,
    private val config: ApplicationConfig,
) : CommandProcessor {
    override val name: String = "help"

    override suspend fun processCommand(ctx: RequestContext, arguments: List<String>) {
        mattermostClient.sendMessage(
            channelId = ctx.channelId,
            message = createHelpMessage(config, ctx.userContext),
        )
    }
}

val `$` = '$'

fun createHelpMessage(config: ApplicationConfig, userContext: UserContext) = """
    Я - умный чат-бот. Сейчас я работаю через модель ${userContext.modelName}. 
    Меня можно спрашивать о чем угодно, главное - помнить о безопасности данных.
    Контекст чата сохраняется${
    when (config.cache) {
        is CacheConfig.InMemory -> " в памяти до рестарта сервера."
        is CacheConfig.Redis -> ", но не долго - всего " + config.cache.expiration.inWholeMinutes + " минут."
    }
}
    ${if(userContext.systemMessage.isNullOrBlank()) { "" } else { "Задано системное сообщение: ${userContext.systemMessage}" }}
    
    Доступные команды:
    * `$`$`help` - вывести это сообщение
    * `$`$`end` - сбросить контекст (завершить этот чат и начать новый)
    * `$`$`set` - настроить переменные для пользователя
""".trimIndent()
