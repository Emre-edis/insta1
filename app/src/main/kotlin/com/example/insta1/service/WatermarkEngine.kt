package com.example.insta1.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    fun createWatermarkBitmap(context: Context): Bitmap {
        // High-definition pre-rendered master emblem (730x910) matching Image 2 reference with 100% fidelity
        return BitmapFactory.decodeResource(context.resources, R.drawable.watermark_emblem)
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

        val watermarkBitmap = createWatermarkBitmap(context)

        val wmWidth = watermarkBitmap.width
        val wmHeight = watermarkBitmap.height
        val safeVidW = if (videoWidth > 0) videoWidth else 1080
        val safeVidH = if (videoHeight > 0) videoHeight else 1920

        // Sizing matching user's reference Image 2 (~18% of narrower video dimension, min 110px for zero pixelation)
        val targetWidthPx = maxOf(110f, minOf(safeVidW, safeVidH) * 0.18f)
        val scaleX = targetWidthPx / safeVidW.toFloat()
        val scaleY = (targetWidthPx * (wmHeight.toFloat() / wmWidth.toFloat())) / safeVidH.toFloat()

        // Position neatly in the bottom-right corner with 3.5% margin from edge
        val overlaySettings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(0.88f, -0.82f)
            .setOverlayFrameAnchor(1.0f, -1.0f)
            .setScale(scaleX, scaleY)
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
