package com.example.notes.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtils {

    fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir("images")
        return File.createTempFile(
            "NOTE_${timeStamp}",
            ".jpg",
            storageDir
        )
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun deleteImageFile(uri: String?) {
        uri?.let {
            try {
                val file = File(Uri.parse(uri).path ?: return)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
    }
}
