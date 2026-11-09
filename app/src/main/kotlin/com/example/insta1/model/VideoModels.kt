package com.example.insta1.model

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnail: Bitmap? = null
)

data class VideoSegment(
    val partIndex: Int,
    val totalParts: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val outputUri: Uri? = null,
    val outputFile: File? = null,
    val fileName: String = "",
    val isSavedToGallery: Boolean = false
)

data class SplitProgress(
    val currentPart: Int = 0,
    val totalParts: Int = 0,
    val percentage: Float = 0f,
    val statusText: String = "",
    val isCompleted: Boolean = false,
    val error: String? = null
)
