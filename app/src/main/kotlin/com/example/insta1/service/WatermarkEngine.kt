package com.example.insta1.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
object WatermarkEngine {

    fun createWatermarkBitmap(): Bitmap {
        val width = 360
        val height = 90
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Semi-transparent dark pill background
        val bgPaint = Paint().apply {
            color = Color.argb(175, 8, 12, 22)
            isAntiAlias = true
        }
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)

        // Neon Cyan border
        val borderPaint = Paint().apply {
            color = Color.argb(200, 0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)

        // Electric cyan indicator dot
        val dotPaint = Paint().apply {
            color = Color.argb(255, 0, 229, 255)
            isAntiAlias = true
        }
        canvas.drawCircle(42f, height / 2f, 13f, dotPaint)

        // White typography
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("StoryCut", 75f, height / 2f + 12f, textPaint)

        return bitmap
    }

    suspend fun splitWithWatermark(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFile: File
    ): Boolean = withContext(Dispatchers.Main) {
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val deferred = CompletableDeferred<Boolean>()

        val watermarkBitmap = createWatermarkBitmap()

        // Position on bottom-left corner of video
        val overlaySettings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(-0.90f, -0.90f)
            .setOverlayFrameAnchor(-1f, -1f)
            .setScale(0.35f, 0.35f)
            .build()

        val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap, overlaySettings)
        val overlayEffect = OverlayEffect(listOf(bitmapOverlay))

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val effects = Effects(
            emptyList(),
            listOf(overlayEffect)
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    deferred.complete(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    deferred.completeExceptionally(exportException)
                }
            })
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)
        deferred.await()
    }
}
