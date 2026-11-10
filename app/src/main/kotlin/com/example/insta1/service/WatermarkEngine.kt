package com.example.insta1.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
import com.example.insta1.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
object WatermarkEngine {

    fun createFullFrameWatermark(
        context: Context,
        frameWidth: Int,
        frameHeight: Int
    ): Bitmap {
        val emblemRaw = BitmapFactory.decodeResource(context.resources, R.drawable.watermark_emblem)
        val safeW = if (frameWidth > 0) frameWidth else 1080
        val safeH = if (frameHeight > 0) frameHeight else 1920

        val frameBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)

        if (emblemRaw != null && emblemRaw.width > 0) {
            // Elegant, razor-sharp corner watermark (one notch smaller: ~12% of minDim, min 72px):
            val minDim = minOf(safeW, safeH)
            val targetW = maxOf(72, (minDim * 0.12f).toInt())
            val aspect = emblemRaw.height.toFloat() / emblemRaw.width.toFloat()
            val targetH = maxOf(1, (targetW.toFloat() * aspect).toInt())

            val marginX = maxOf(10, (safeW * 0.032f).toInt())
            val marginY = maxOf(10, (safeH * 0.032f).toInt())

            val left = safeW - targetW - marginX
            val top = safeH - targetH - marginY

            // Multi-step progressive downscaling preserves 100% crisp vector edges and text at smaller sizes
            var scaledEmblem: Bitmap = emblemRaw
            while (scaledEmblem.width / 2 >= targetW && scaledEmblem.height / 2 >= targetH) {
                val next = Bitmap.createScaledBitmap(
                    scaledEmblem,
                    scaledEmblem.width / 2,
                    scaledEmblem.height / 2,
                    true
                )
                if (scaledEmblem !== emblemRaw) {
                    scaledEmblem.recycle()
                }
                scaledEmblem = next
            }
            if (scaledEmblem.width != targetW || scaledEmblem.height != targetH) {
                val finalScaled = Bitmap.createScaledBitmap(scaledEmblem, targetW, targetH, true)
                if (scaledEmblem !== emblemRaw) {
                    scaledEmblem.recycle()
                }
                scaledEmblem = finalScaled
            }

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(scaledEmblem, left.toFloat(), top.toFloat(), paint)
            if (scaledEmblem !== emblemRaw) {
                scaledEmblem.recycle()
            }
        }

        return frameBitmap
    }

    suspend fun splitWithWatermark(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        videoWidth: Int = 1080,
        videoHeight: Int = 1920,
        outputFile: File
    ): Boolean = withContext(Dispatchers.Main) {
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val deferred = CompletableDeferred<Boolean>()

        // Pre-rendered full-frame canvas guarantees exact 1:1 pixel placement with ZERO scaling bugs
        val frameBitmap = createFullFrameWatermark(context, videoWidth, videoHeight)
        val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(frameBitmap)
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
