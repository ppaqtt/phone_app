package com.example.notes.data.dto

import com.example.notes.data.NoteEntity

data class NoteDto(
    val id: Long? = null,
    val title: String,
    val content: String,
    val categoryId: Long? = null,
    val tags: String? = null,
    val isPinned: Boolean = false,
    val color: Int? = null,
    val coverImageUri: String? = null,
    val reminderTime: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

fun NoteDto.toEntity(): NoteEntity {
    return NoteEntity(
        id = id ?: 0,
        title = title,
        content = content,
        categoryId = categoryId,
        tags = tags ?: "",
        isPinned = isPinned,
        color = color ?: 0xFFFFFFFF.toInt(),
        coverImageUri = coverImageUri,
        reminderTime = reminderTime,
        createdAt = createdAt ?: System.currentTimeMillis(),
        updatedAt = updatedAt ?: System.currentTimeMillis()
    )
}

fun NoteEntity.toDto(): NoteDto {
    return NoteDto(
        id = if (id == 0L) null else id,
        title = title,
        content = content,
        categoryId = categoryId,
        tags = tags,
        isPinned = isPinned,
        color = color,
        coverImageUri = coverImageUri,
        reminderTime = reminderTime,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
