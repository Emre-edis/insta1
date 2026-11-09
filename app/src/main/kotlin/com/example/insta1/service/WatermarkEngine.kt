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
        val emblemRaw = BitmapFactory.decodeResource(context.resources, R.drawable.watermark_emblem)

        val emblemW = 200
        val emblemH = if (emblemRaw != null && emblemRaw.width > 0) {
            (emblemW.toFloat() * (emblemRaw.height.toFloat() / emblemRaw.width.toFloat())).toInt()
        } else {
            175
        }

        val spacing = 8
        val textHeight = 40

        val paintStory = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, Color.argb(190, 0, 0, 0))
        }

        val paintCut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255) // Cyan
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, Color.argb(190, 0, 0, 0))
        }

        val storyWidth = paintStory.measureText("Story")
        val cutWidth = paintCut.measureText("Cut")
        val totalTextWidth = storyWidth + cutWidth

        val totalW = maxOf(emblemW, (totalTextWidth + 16).toInt())
        val totalH = emblemH + spacing + textHeight

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Emblem squircle centered horizontally
        if (emblemRaw != null) {
            val emblemScaled = Bitmap.createScaledBitmap(emblemRaw, emblemW, emblemH, true)
            val emblemX = (totalW - emblemW) / 2f
            canvas.drawBitmap(emblemScaled, emblemX, 0f, null)
        }

        // 2. Draw "Story" (White) + "Cut" (Cyan) directly below the emblem
        val textStartX = (totalW - totalTextWidth) / 2f
        val textY = emblemH + spacing + 30f

        canvas.drawText("Story", textStartX, textY, paintStory)
        canvas.drawText("Cut", textStartX + storyWidth, textY, paintCut)

        return bitmap
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

        // Scale relative to narrower video dimension so it is proportional on all aspect ratios
        val targetWidthPx = minOf(safeVidW, safeVidH) * 0.17f
        val scaleFactor = targetWidthPx / wmWidth.toFloat()
        val scaleX = (wmWidth * scaleFactor / safeVidW.toFloat()) * 2.0f
        val scaleY = (wmHeight * scaleFactor / safeVidH.toFloat()) * 2.0f

        // Position on bottom-right corner of video
        val overlaySettings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(0.88f, -0.80f)
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
