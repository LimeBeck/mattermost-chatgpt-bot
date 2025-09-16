package chatgpt

import dev.limebeck.chatgpt.ChatGptClientImpl
import dev.limebeck.chatgpt.Content
import dev.limebeck.chatgpt.ImageUrl
import dev.limebeck.chatgpt.Message
import dev.limebeck.chatgpt.Role
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChatGptSerializationTest {
    @Test
    fun `serialize completion request with image`() {
        val request =
            ChatGptClientImpl.CompletionRequest(
                model = "test-model",
                temperature = 0f,
                messages =
                    listOf(
                        Message(
                            role = Role.USER,
                            content =
                                listOf(
                                    Content.Text("look at this"),
                                    Content.Image(ImageUrl("data:image/png;base64,AAA")),
                                ),
                        ),
                    ),
            )

        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                classDiscriminator = "type"
            }

        val serialized = json.encodeToJsonElement(request)

        val contents =
            serialized.jsonObject["messages"]!!
                .jsonArray
                .first()
                .jsonObject["content"]!!
                .jsonArray

        assertTrue(
            contents.any { part ->
                val obj = part.jsonObject
                obj["type"]?.jsonPrimitive?.content == "image_url" &&
                    obj["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ==
                    "data:image/png;base64,AAA"
            },
        )
    }

    @Test
    fun `deserialize completion response with string content`() {
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                classDiscriminator = "type"
            }

        val payload =
            """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "Hello!"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 2,
                "total_tokens": 3
              },
              "model": "test-model"
            }
            """.trimIndent()

        val response = json.decodeFromString<ChatGptClientImpl.CompletionResponse>(payload)

        val content = response.choices.first().message.content
        assertEquals(1, content.size)
        val textPart = assertIs<Content.Text>(content.first())
        assertEquals("Hello!", textPart.text)
    }
}
