package dev.limebeck.context

import dev.limebeck.mattermost.ChannelId
import dev.limebeck.mattermost.DirectMessage
import dev.limebeck.mattermost.UserId

data class RequestContext(
    val channelId: ChannelId,
    val userId: UserId,
    val userName: String,
    val userContext: UserContext,
    val requestUuid: String,
    val message: DirectMessage
)