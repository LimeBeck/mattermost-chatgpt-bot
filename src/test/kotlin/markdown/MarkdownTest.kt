package markdown

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import utils.splitMarkdown
import kotlin.test.Test

@Tag("unit")
class MarkdownTest {

    @Test
    fun `Format markdown`() = runBlocking {
        val markdownText = """
        # Заголовок

        Это первый абзац с текстом, который не превышает лимит чанка.

        ```kotlin
        // Пример кода на Kotlin, который очень длинный и требует разбивки на несколько чанков.
        fun veryLongFunction() {
            // Предположим, что тут много строк кода, и они очень длинные,
            // поэтому весь блок кода превышает заданный размер чанка и его нужно разбить.
            println("Строка 1")
            println("Строка 2")
            println("Строка 3")
            println("Строка 4")
            println("Строка 5")
            println("Строка 6")
            println("Строка 7")
            println("Строка 8")
            println("Строка 9")
            println("Строка 10")
        }
        ```

        Второй абзац текста, который достаточно длинный и также может требовать разбивки на несколько чанков при небольшой величине лимита.
    """.trimIndent()

        val chunkSize = 150
        val result = splitMarkdown(markdownText, chunkSize)
        result.forEachIndexed { index, chunk ->
            println("Чанк ${index + 1} размером ${chunk.length}:")
            println(chunk)
            println("-------------------------")
        }
    }
}