package dev.limebeck.command

data class Command(
    val name: String,
    val arguments: List<String>,
)

suspend fun parseCommand(message: String): Command =
    when {
        message.equals("end", ignoreCase = true) -> Command("end", listOf())
        message.equals("help", ignoreCase = true) -> Command("help", listOf())
        message.startsWith("$") -> {
            // TODO: Improve parsing
            val commandName = message.split(' ').first()
            Command(commandName.removePrefix("$"), listOf(message.removePrefix(commandName)))
        }

        else -> Command("completion", listOf())
    }
