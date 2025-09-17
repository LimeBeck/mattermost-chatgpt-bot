package dev.limebeck.mattermost

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface MattermostClient {
    suspend fun receiveDirectMessages(): Flow<DirectMessage>
    suspend fun receiveNewChatStarted(): Flow<NewChatStartedEvent>
    suspend fun sendMessage(channelId: ChannelId, message: String)
    suspend fun isMemberOfTeam(userId: UserId, teamId: TeamId): Boolean
}

data class DirectMessage(
    val channelId: ChannelId,
    val userId: UserId,
    val text: String,
    val userName: String,
    val attachments: List<Attachment> = emptyList(),
)

data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val data: ByteArray,
)

data class NewChatStartedEvent(
    val userId: UserId,
    val channelId: ChannelId,
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
