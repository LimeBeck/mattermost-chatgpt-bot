package dev.limebeck.command

import dev.limebeck.context.RequestContext

interface CommandProcessor {
    val name: String
    suspend fun processCommand(
        ctx: RequestContext,
        arguments: List<String>,
    )
}