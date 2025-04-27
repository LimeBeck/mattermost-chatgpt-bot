package dev.limebeck

import arrow.continuations.SuspendApp
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addCommandLineSource
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import dev.limebeck.cache.InMemoryCacheService
import dev.limebeck.cache.RedisCacheService
import dev.limebeck.chatgpt.ChatGptClientImpl
import dev.limebeck.command.*
import dev.limebeck.context.CachedUserContextService
import dev.limebeck.context.RequestContext
import dev.limebeck.context.UserContextService
import dev.limebeck.mattermost.MattermostClient
import dev.limebeck.mattermost.MattermostClientImpl
import kotlinx.coroutines.*
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
        model = config.chatgpt.defaultModel,
        temperature = config.chatgpt.temperature
    )

    val mattermostClient = MattermostClientImpl(
        apiToken = config.mattermost.apiToken.value,
        baseUrl = config.mattermost.baseUrl,
        chunkSize = config.mattermost.chunkSize,
    )

    val cacheService = when (config.cache) {
        is CacheConfig.InMemory -> InMemoryCacheService()
        is CacheConfig.Redis -> RedisCacheService(config.cache.endpoint, config.cache.expiration)
    }

    val userContextService = CachedUserContextService(cacheService, config)

    val commandSystem = CommandSystem(
        mattermostClient = mattermostClient,
        processors = listOf(
            HelpCommandProcessor(mattermostClient, config),
            SessionEndCommandProcessor(mattermostClient, userContextService),
            CompletionCommandProcessor(gptService, userContextService, mattermostClient),
            SetUserContextCommandProcessor(userContextService, mattermostClient, config)
        )
    )

    val jobs = listOf(
        launch {
            handleNewChatStarted(
                mattermostClient = mattermostClient,
                userContextService = userContextService,
                config = config,
            )
        },
        launch {
            handleNewMessage(
                mattermostClient = mattermostClient,
                userContextService = userContextService,
                commandSystem = commandSystem,
                config = config
            )
        }
    )

    jobs.joinAll()
}

suspend fun handleNewChatStarted(
    mattermostClient: MattermostClient,
    userContextService: UserContextService,
    config: ApplicationConfig
) {
    mattermostClient.receiveNewChatStarted().collect { newChatStarted ->
        val userContext = userContextService.getUserContext(newChatStarted.userId)
        mattermostClient.sendMessage(
            channelId = newChatStarted.channelId,
            message = "Привет!\n${createHelpMessage(config, userContext)}",
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun handleNewMessage(
    mattermostClient: MattermostClient,
    userContextService: UserContextService,
    commandSystem: CommandSystem,
    config: ApplicationConfig,
) {
    val innerCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(10))
    mattermostClient.receiveDirectMessages().collect { message ->
        innerCoroutineScope.launch {
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
                    return@launch
                }
            }

            val userContext = userContextService.getUserContext(message.userId)

            val ctx = RequestContext(
                channelId = message.channelId,
                userId = message.userId,
                userName = message.userName,
                userContext = userContext,
                requestUuid = requestUuid,
                message = message
            )

            val command = parseCommand(message.text)
            commandSystem.execute(ctx, command)
        }
    }
}
