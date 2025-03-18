package utils

//ChatGPT generated code. Must be rewrited and improved

fun splitMarkdown(text: String, chunkSize: Int): List<String> {
    // Первый этап: разбивка исходного markdown‑текста на логические блоки.
    val blocks = mutableListOf<String>()
    val currentBlock = StringBuilder()
    var inCodeBlock = false // флаг, отслеживающий, находимся ли мы внутри блока кода

    text.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (!inCodeBlock && currentBlock.isNotEmpty()) {
                blocks.add(currentBlock.toString().trimEnd())
                currentBlock.clear()
            }
            currentBlock.append(line).append("\n")
            inCodeBlock = !inCodeBlock
            if (!inCodeBlock) {
                blocks.add(currentBlock.toString().trimEnd())
                currentBlock.clear()
            }
        } else {
            if (inCodeBlock) {
                currentBlock.append(line).append("\n")
            } else {
                if (line.isBlank()) {
                    if (currentBlock.isNotEmpty()) {
                        blocks.add(currentBlock.toString().trimEnd())
                        currentBlock.clear()
                    }
                } else {
                    currentBlock.append(line).append("\n")
                }
            }
        }
    }
    if (currentBlock.isNotEmpty()) {
        blocks.add(currentBlock.toString().trimEnd())
    }

    // Второй этап: собираем блоки в чанки с учетом лимита chunkSize.
    val chunks = mutableListOf<String>()
    val currentChunk = StringBuilder()

    fun tryAppendBlock(blockText: String): Boolean {
        val candidate = if (currentChunk.isEmpty()) blockText else currentChunk.toString() + "\n" + blockText
        return if (candidate.length <= chunkSize) {
            if (currentChunk.isEmpty())
                currentChunk.append(blockText)
            else
                currentChunk.append("\n").append(blockText)
            true
        } else {
            false
        }
    }

    for (block in blocks) {
        if (block.length <= chunkSize) {
            if (!tryAppendBlock(block)) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trimEnd())
                    currentChunk.clear()
                }
                currentChunk.append(block)
            }
        } else {
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trimEnd())
                currentChunk.clear()
            }
            val splitParts = splitAndWrapBlock(block, chunkSize)
            chunks.addAll(splitParts)
        }
    }
    if (currentChunk.isNotEmpty()) {
        chunks.add(currentChunk.toString().trimEnd())
    }

    return chunks
}

private fun splitAndWrapBlock(block: String, chunkSize: Int): List<String> {
    val result = mutableListOf<String>()
    val isCodeBlock = block.trimStart().startsWith("```") && block.trimEnd().endsWith("```")
    if (isCodeBlock) {
        val lines = block.lines()
        if (lines.size < 2) {
            result.add(block)
            return result
        }
        val header = lines.first()        // например, "```kotlin"
        val footer = lines.last()           // "```"
        val content = lines.subList(1, lines.size - 1).joinToString("\n")
        // Учитываем, что при оборачивании в каждом чанке будут добавлены header и footer с переносами строк.
        val overhead = header.length + footer.length + 2
        val allowed = chunkSize - overhead
        if (allowed < 1) {
            result.add(block)
            return result
        }
        val parts = splitTextPreservingLines(content, allowed)
        for (part in parts) {
            val chunk = buildString {
                append(header).append("\n")
                append(part.trimEnd()).append("\n")
                append(footer)
            }
            result.add(chunk)
        }
    } else {
        val parts = splitTextPreservingLines(block, chunkSize)
        result.addAll(parts)
    }
    return result
}

private fun splitTextPreservingLines(text: String, maxLen: Int): List<String> {
    val lines = text.lines()
    val parts = mutableListOf<String>()
    val curr = StringBuilder()
    for (line in lines) {
        val candidate = if (curr.isEmpty()) line else curr.toString() + "\n" + line
        if (candidate.length <= maxLen) {
            if (curr.isEmpty())
                curr.append(line)
            else
                curr.append("\n").append(line)
        } else {
            if (curr.isNotEmpty()) {
                parts.add(curr.toString())
                curr.clear()
            }
            if (line.length > maxLen) {
                var start = 0
                while (start < line.length) {
                    val end = (start + maxLen).coerceAtMost(line.length)
                    parts.add(line.substring(start, end))
                    start = end
                }
            } else {
                curr.append(line)
            }
        }
    }
    if (curr.isNotEmpty()) parts.add(curr.toString())
    return parts
}