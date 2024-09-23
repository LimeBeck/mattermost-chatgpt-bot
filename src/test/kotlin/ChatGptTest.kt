import dev.limebeck.chatgpt.ChatGptClientImpl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import kotlin.test.Test

@Tag("integration")
class ChatGptTest {
    @Test
    fun `Make chatgpt request`() {
        val client = ChatGptClientImpl(
            apiKey = "***",
            temperature = 1f,
            model = ChatGptClientImpl.BASE_MODEL_NAME
        )

        val response = runBlocking {
            client.getCompletion("Напиши чат бота Mattermost на языке Kotlin с использованием фремворка Ktor для работы с ChatGPT")
        }

        println(response.getOrNull())
        assert(response.isSuccess)
    }
}