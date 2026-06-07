package com.example.notes.util

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.notes.data.NoteEntity
import com.example.notes.work.ReminderWorker
import java.util.concurrent.TimeUnit

object ReminderManager {

    fun scheduleReminder(context: Context, note: NoteEntity, reminderTime: Long) {
        val currentTime = System.currentTimeMillis()
        val delay = reminderTime - currentTime

        if (delay <= 0) {
            return
        }

        val inputData = workDataOf(
            ReminderWorker.KEY_NOTE_ID to note.id,
            ReminderWorker.KEY_NOTE_TITLE to note.title,
            ReminderWorker.KEY_NOTE_CONTENT to note.content
        )

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancelReminder(context: Context, noteId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag("note_$noteId")
    }
}
