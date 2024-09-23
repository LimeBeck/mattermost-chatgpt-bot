package dev.limebeck.mattermost

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface MattermostClient {
    suspend fun receiveDirectMessages(): Flow<DirectMessage>
    suspend fun sendMessage(channelId: ChannelId, message: String)
    suspend fun isMemberOfTeam(userId: UserId, teamId: TeamId): Boolean
}

data class DirectMessage(
    val channelId: ChannelId,
    val userId: UserId,
    val text: String,
)

@Serializable
@JvmInline
value class ChannelId(val value: String)

@Serializable
@JvmInline
value class UserId(val value: String)

@Serializable
@JvmInline
value class TeamId(val value: String)
