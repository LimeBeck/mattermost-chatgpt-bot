package chatgpt

import dev.limebeck.chatgpt.ChatGptClientImpl
import dev.limebeck.chatgpt.ImageUrl
import dev.limebeck.chatgpt.Role
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatGptSerializationTest {
    @Test
    fun `serialize completion request with image`() {
        val request =
            ChatGptClientImpl.CompletionRequest(
                model = "test-model",
                temperature = 0f,
                messages =
                    listOf(
                        ChatGptClientImpl.CompletionRequest.Message(
                            role = Role.USER,
                            content =
                                listOf(
                                    ChatGptClientImpl.CompletionRequest.Message.Content.Text("look at this"),
                                    ChatGptClientImpl.CompletionRequest.Message.Content.Image(
                                        ImageUrl("data:image/png;base64,AAA"),
                                    ),
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
        val json = Json {
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

        assertIs<ChatGptClientImpl.CompletionResponse.Success>(response)

        val content = response.choices.first().message.content
        assertEquals("Hello!", content)
    }

    @Test
    fun `deserialize completion response with error string content`() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }

        val payload =
            """
                {                                                                                                                                                                       
                    "error": {                                                                                                                                                          
                        "message": "You exceeded your current quota, please check your plan and billing details. For more information on this error, read the docs: https://platform.openai.com/docs/guides/error-codes/api-errors.",                                                                                                                                                             
                        "type": "insufficient_quota",                                                                                                                                   
                        "param": null,                                                                                                                                                  
                        "code": "insufficient_quota"                                                                                                                                    
                    }                                                                                                                                                                   
                }
            """.trimIndent()

        val response = json.decodeFromString<ChatGptClientImpl.CompletionResponse>(payload)

        assertIs<ChatGptClientImpl.CompletionResponse.Error>(response)

        assertEquals("insufficient_quota", response.error.type)
    }
}
