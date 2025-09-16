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
            {"id":"jw3bskeneprhmmiy3sb5peb71y","create_at":1758043177329,"update_at":1758043177329,"edit_at":0,"delete_at":0,"is_pinned":false,"user_id":"onxfpcxmbibyub83fuxho8s3wy","channel_id":"dxxbwg13zjb3bk5mdogcmubd8h","root_id":"","original_id":"","message":"Прочитай текст на скриншоте","type":"","props":{"disable_group_highlight":true},"hashtags":"","file_ids":["uf1m7qyzctftdg8se4iko9446c"],"pending_post_id":"onxfpcxmbibyub83fuxho8s3wy:1758043178282","remote_id":"","reply_count":0,"last_reply_at":0,"participants":null,"metadata":{"files":[{"id":"uf1m7qyzctftdg8se4iko9446c","user_id":"onxfpcxmbibyub83fuxho8s3wy","post_id":"jw3bskeneprhmmiy3sb5peb71y","channel_id":"dxxbwg13zjb3bk5mdogcmubd8h","create_at":1758043165418,"update_at":1758043165418,"delete_at":0,"name":"image.png","extension":"png","size":130564,"mime_type":"image/png","width":1184,"height":314,"has_preview_image":true,"mini_preview":"/9j/2wCEAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRQBAwQEBQQFCQUFCRQNCw0UFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFP/AABEIABAAEAMBIgACEQEDEQH/xAGiAAABBQEBAQEBAQAAAAAAAAAAAQIDBAUGBwgJCgsQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVмЗ2hpanN0дXZ3eHl6g4SFhoeIiYqSk5SVlpeYmЗqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2dri4+Tl5ufo6ery8/T19vf4+fr/2gAMAwEAAhEDEQA/APzbks4NQiSWO2WzRhkJAGOPbc2SaoXmnpbsdsycDO2Q4c/QYqVIFms49siRvtzxgEnngkv/AEqjNC8QUuynPTEgb+RoA//Z","remote_id":"","archived":false}]}}
            """.trimIndent()

        val post =
            json.decodeFromString<MattermostClientImpl.Post>(postJson)

        assertEquals(listOf("uf1m7qyzctftdg8se4iko9446c"), post.fileIds)
        val metadata = post.metadata?.files?.single()
        assertEquals("uf1m7qyzctftdg8se4iko9446c", metadata?.id)
        assertEquals("image/png", metadata?.mimeType)
    }
}
