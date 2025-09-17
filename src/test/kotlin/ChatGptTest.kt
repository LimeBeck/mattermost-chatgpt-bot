import dev.limebeck.chatgpt.ChatGptClientImpl
import dev.limebeck.chatgpt.Content
import dev.limebeck.chatgpt.Message
import dev.limebeck.chatgpt.Role
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import kotlin.test.Test

@Tag("integration")
class ChatGptTest {
    @Test
    fun `Make chatgpt request`() {
        val client =
            ChatGptClientImpl(
                apiKey = "***",
                temperature = 1f,
                model = ChatGptClientImpl.BASE_MODEL_NAME,
            )

        val response =
            runBlocking {
                client.getCompletion(
                    Message(
                        Role.USER,
                        listOf(
                            Content.Text(
                                "Напиши чат бота Mattermost на языке Kotlin с использованием фремворка Ktor для работы с ChatGPT",
                            ),
                        ),
                    ),
                    emptyList(),
                )
            }

        println(response.getOrNull())
        assert(response.isSuccess)
    }
}
