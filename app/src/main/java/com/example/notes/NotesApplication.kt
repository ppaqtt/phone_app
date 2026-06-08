package com.example.notes

import android.app.Application
import com.example.notes.data.AppDatabase
import com.example.notes.repository.NotesRepository

/**
 * Application class that owns the dependency graph. Kept intentionally simple —
 * a production app would use Hilt or Koin. Here we expose singletons through
 * lazy properties so the rest of the app can grab them without DI plumbing.
 */
class NotesApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: NotesRepository by lazy {
        NotesRepository(
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            noteImageDao = database.noteImageDao()
        )
    }
}
