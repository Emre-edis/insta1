package com.example.insta1.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object GallerySaver {

    fun saveVideoToGallery(
        context: Context,
        tempVideoFile: File,
        displayName: String
    ): Uri? {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Insta1")
                put(MediaStore.Video.Media.IS_PENDING, 1)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            }

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, contentValues) ?: return null

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(tempVideoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                // Scan file to ensure instant visibility in Gallery
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(uri.toString()),
                    arrayOf("video/mp4"),
                    null
                )
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                e.printStackTrace()
                return null
            }
        } else {
            // Legacy storage for API < 29
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Insta1"
            )
            if (!moviesDir.exists()) moviesDir.mkdirs()

            val targetFile = File(moviesDir, displayName)
            try {
                FileInputStream(tempVideoFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                return Uri.fromFile(targetFile)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}
