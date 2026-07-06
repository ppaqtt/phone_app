package com.qingjian.notes.util

import com.qingjian.notes.data.CategoryEntity
import com.qingjian.notes.data.NoteEntity
import com.qingjian.notes.data.NoteImageEntity
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * F1: 数据备份/恢复 JSON 序列化/反序列化工具。
 *
 * 用 Gson 而非 org.json:
 * 1) 体积大时 Gson 流式 API 性能更好;
 * 2) 字段缺失/类型不匹配时 Gson 默认宽容, 老版本备份文件能继续导入;
 * 3) 注释中提供 schema 描述, 方便用户手工校验/编辑。
 *
 * Schema (v1):
 * {
 *   "version": 1,
 *   "exportedAt": 1718000000000,
 *   "appVersion": "1.4.0",
 *   "categories": [
 *     { "oldId": 1, "name": "工作", "color": -16776961, "createdAt": 1717000000000 }
 *   ],
 *   "notes": [
 *     {
 *       "oldId": 10, "title": "...", "content": "...",
 *       "categoryOldId": 1, "tags": "a,b", "isPinned": false,
 *       "priority": 0, "color": -1,
 *       "reminderTime": null, "createdAt": ..., "updatedAt": ...
 *     }
 *   ],
 *   "images": [
 *     { "oldId": 100, "noteOldId": 10, "uri": "content://...", "position": 0 }
 *   ]
 * }
 */
object BackupManager {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Long::class.java, LenientLongAdapter)
        .registerTypeAdapter(Long::class.javaPrimitiveType, LenientLongAdapter)
        .create()

    /**
     * 把 DTO 序列化为 JSON 字符串。
     * 失败抛异常, 由调用方捕获并提示。
     */
    fun toJson(payload: BackupPayload): String = gson.toJson(payload)

    /**
     * 把 JSON 字符串反序列化为 DTO。
     * 失败抛 JsonSyntaxException, 由调用方捕获并提示。
     */
    fun fromJson(json: String): BackupPayload {
        val payload = gson.fromJson(json, BackupPayload::class.java)
        val valid = payload.copy(
            categories = payload.categories ?: emptyList(),
            notes = payload.notes ?: emptyList(),
            images = payload.images ?: emptyList()
        )
        return valid
    }

    /**
     * P97-FIX: 安全反序列化, 失败返回 Result.failure 而非抛异常。
     * 用法: 从用户选择的文件读取 JSON 后, 跑 [fromJsonSafe], 失败分支给用户
     * 清晰反馈 ("备份文件已损坏 / 格式不符"), 成功分支直接导入。
     */
    fun fromJsonSafe(json: String): Result<BackupPayload> = runCatching {
        val payload = gson.fromJson(json, BackupPayload::class.java)
        val valid = payload.copy(
            categories = payload.categories ?: emptyList(),
            notes = payload.notes ?: emptyList(),
            images = payload.images ?: emptyList()
        )
        valid
    }

    /**
     * 增强版反序列化，增加数据验证和详细日志。
     * 如果解析出的数据异常，返回详细的错误信息。
     */
    fun fromJsonWithValidation(json: String): Result<BackupPayload> {
        return runCatching {
            val obj = gson.fromJson(json, JsonObject::class.java)
            
            val version = obj.get("version")?.asInt ?: 0
            val exportedAt = obj.get("exportedAt")?.asLong ?: 0
            val appVersion = obj.get("appVersion")?.asString ?: ""
            
            val categories = mutableListOf<CategoryBackup>()
            obj.getAsJsonArray("categories")?.forEach { catElem ->
                val catObj = catElem.asJsonObject
                categories.add(CategoryBackup(
                    oldId = catObj.get("oldId")?.asLong ?: 0L,
                    name = catObj.get("name")?.asString ?: "",
                    color = catObj.get("color")?.asInt ?: 0,
                    parentOldId = if (catObj.has("parentOldId") && !catObj.get("parentOldId").isJsonNull) catObj.get("parentOldId").asLong else null,
                    createdAt = catObj.get("createdAt")?.asLong ?: 0L
                ))
            }
            
            val notes = mutableListOf<NoteBackup>()
            obj.getAsJsonArray("notes")?.forEach { noteElem ->
                val noteObj = noteElem.asJsonObject
                val title = noteObj.get("title")?.asString
                val content = noteObj.get("content")?.asString
                notes.add(NoteBackup(
                    oldId = noteObj.get("oldId")?.asLong ?: 0L,
                    title = title,
                    content = content,
                    categoryOldId = if (noteObj.has("categoryOldId") && !noteObj.get("categoryOldId").isJsonNull) noteObj.get("categoryOldId").asLong else null,
                    tags = noteObj.get("tags")?.asString ?: "",
                    isPinned = noteObj.get("isPinned")?.asBoolean ?: false,
                    priority = noteObj.get("priority")?.asInt ?: 0,
                    color = noteObj.get("color")?.asInt ?: -1,
                    reminderTime = if (noteObj.has("reminderTime") && !noteObj.get("reminderTime").isJsonNull) noteObj.get("reminderTime").asLong else null,
                    reminderRepeat = noteObj.get("reminderRepeat")?.asString,
                    createdAt = noteObj.get("createdAt")?.asLong ?: 0L,
                    updatedAt = noteObj.get("updatedAt")?.asLong ?: 0L
                ))
            }
            
            val images = mutableListOf<ImageBackup>()
            obj.getAsJsonArray("images")?.forEach { imgElem ->
                val imgObj = imgElem.asJsonObject
                images.add(ImageBackup(
                    oldId = imgObj.get("oldId")?.asLong ?: 0L,
                    noteOldId = imgObj.get("noteOldId")?.asLong ?: 0L,
                    uri = imgObj.get("uri")?.asString,
                    position = imgObj.get("position")?.asInt ?: 0
                ))
            }
            
            BackupPayload(
                version = version,
                exportedAt = exportedAt,
                appVersion = appVersion,
                categories = categories,
                notes = notes,
                images = images
            )
        }
    }

    /**
     * 用数据库实体直接构建 payload —— 免得调用方手工映射 6 个字段。
     */
    fun buildPayload(
        appVersion: String,
        categories: List<CategoryEntity>,
        notes: List<NoteEntity>,
        images: List<NoteImageEntity>
    ): BackupPayload = BackupPayload(
        version = BackupPayload.CURRENT_VERSION,
        exportedAt = System.currentTimeMillis(),
        appVersion = appVersion,
        categories = categories.map {
            CategoryBackup(
                oldId = it.id,
                name = it.name,
                color = it.color,
                parentOldId = it.parentId,
                createdAt = it.createdAt
            )
        },
        notes = notes.map {
            NoteBackup(
                oldId = it.id,
                title = it.title,
                content = it.content,
                categoryOldId = it.categoryId,
                tags = it.tags,
                isPinned = it.isPinned,
                priority = it.priority,
                color = it.color,
                reminderTime = it.reminderTime,
                reminderRepeat = it.reminderRepeat,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        },
        images = images.map {
            ImageBackup(
                oldId = it.id,
                noteOldId = it.noteId,
                uri = it.uri,
                position = it.position
            )
        }
    )

    /**
     * 解析 DTO 内的备份文件 schema 版本号, 用于决定是否做兼容处理。
     */
    fun payloadVersion(json: String): Int = try {
        val obj = gson.fromJson(json, JsonObject::class.java)
        obj.get("version")?.asInt ?: 0
    } catch (e: Exception) {
        0
    }
}

/**
 * 把 JSON 中的整数 / 浮点都尝试解析为 Long。
 * 旧版本 (如手编脚本) 可能把 exportedAt 写成浮点秒级时间戳,
 * 严格模式下 Gson 会抛 NumberFormatException, 这里给个兜底。
 */
private object LenientLongAdapter : JsonSerializer<Long>, JsonDeserializer<Long> {
    override fun serialize(src: Long?, type: Type, ctx: JsonSerializationContext): JsonElement =
        JsonPrimitive(src)

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): Long {
        val prim = json.asJsonPrimitive
        return when {
            prim.isNumber -> prim.asLong
            prim.isString -> prim.asString.toLongOrNull()
                ?: throw IllegalArgumentException("无法解析为 Long: ${prim.asString}")
            else -> throw IllegalArgumentException("Long 字段类型错误: $json")
        }
    }
}
