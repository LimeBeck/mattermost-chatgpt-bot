package mattermost

import dev.limebeck.mattermost.MattermostClientImpl
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PostParsingTest {
    @Test
    fun `parse post with file ids`() {
        val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                encodeDefaults = true
            }
        val postJson =
            """
            {"id":"post-id-123456","create_at":1758043177329,"update_at":1758043177329,"edit_at":0,"delete_at":0,"is_pinned":false,"user_id":"user-id-123456","channel_id":"channel-id-123456","root_id":"","original_id":"","message":"Прочитай текст на скриншоте","type":"","props":{"disable_group_highlight":true},"hashtags":"","file_ids":["file-id-123456"],"pending_post_id":"user-id-123456:1758043178282","remote_id":"","reply_count":0,"last_reply_at":0,"participants":null,"metadata":{"files":[{"id":"file-id-123456","user_id":"user-id-123456","post_id":"post-id-123456","channel_id":"channel-id-123456","create_at":1758043165418,"update_at":1758043165418,"delete_at":0,"name":"image.png","extension":"png","size":130564,"mime_type":"image/png","width":1184,"height":314,"has_preview_image":true,"mini_preview":"/9j/2wCEAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRQBAwQEBQQFCQUFCRQNCw0UFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFP/AABEIABAAEAMBIgACEQEDEQH/xAGiAAABBQEBAQEBAQAAAAAAAAAAAQIDBAUGBwgJCgsQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVмЗ2hpanN0дXZ3eHl6g4SFhoeIiYqSk5SVlpeYmЗqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2dri4+Tl5ufo6ery8/T19vf4+fr/2gAMAwEAAhEDEQA/APzbks4NQiSWO2WzRhkJAGOPbc2SaoXmnpbsdsycDO2Q4c/QYqVIFms49siRvtzxgEnngkv/AEqjNC8QUuynPTEgb+RoA//Z","remote_id":"","archived":false}]}}
            """.trimIndent()

        val post =
            json.decodeFromString<MattermostClientImpl.Post>(postJson)

        assertEquals(listOf("file-id-123456"), post.fileIds)
        val metadata = post.metadata?.files?.single()
        assertEquals("file-id-123456", metadata?.id)
        assertEquals("image/png", metadata?.mimeType)
    }
}
