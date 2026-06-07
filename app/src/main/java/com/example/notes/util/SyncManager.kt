package com.example.notes.util

import android.content.Context
import com.example.notes.data.NoteEntity
import com.example.notes.data.dto.toDto
import com.example.notes.data.dto.toEntity
import com.example.notes.network.NotesApi
import com.example.notes.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncManager(
    private val notesApi: NotesApi,
    private val repository: NotesRepository
) {

    suspend fun syncNotes() = withContext(Dispatchers.IO) {
        try {
            val remoteNotes = notesApi.getAllNotes().body() ?: emptyList()
            val localNotes = repository.getAllNotesForSync()

            val remoteIds = remoteNotes.mapNotNull { it.id }.toSet()
            val localIds = localNotes.map { it.id }.toSet()

            val newFromRemote = remoteNotes.filter { it.id !in localIds }
            val newFromLocal = localNotes.filter { it.id !in remoteIds }

            newFromRemote.forEach { dto ->
                repository.saveNote(dto.toEntity())
            }

            newFromLocal.forEach { entity ->
                notesApi.createNote(entity.toDto())
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        try {
            if (note.id == 0L) {
                notesApi.createNote(note.toDto())
            } else {
                notesApi.updateNote(note.id, note.toDto())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteNoteFromServer(noteId: Long) = withContext(Dispatchers.IO) {
        try {
            notesApi.deleteNote(noteId)
            true
        } catch (e: Exception) {
            false
        }
    }
}
