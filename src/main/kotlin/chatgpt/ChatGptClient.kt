package dev.limebeck.chatgpt

interface ChatGptClient {
    suspend fun getCompletion(text: String): Result<String>
}