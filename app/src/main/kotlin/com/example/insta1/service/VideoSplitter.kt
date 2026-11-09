package com.example.insta1.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.insta1.model.SplitProgress
import com.example.insta1.model.VideoSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil

object VideoSplitter {

    suspend fun splitVideo(
        context: Context,
        sourceUri: Uri,
        originalFileName: String,
        segmentDurationMs: Long = 60_000L, // 1 minute default
        isPro: Boolean = false,
        onProgress: (SplitProgress) -> Unit
    ): List<VideoSegment> = withContext(Dispatchers.IO) {
        val segments = mutableListOf<VideoSegment>()

        // 1. Get total duration
        var totalDurationUs: Long = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, sourceUri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durStr?.toLongOrNull() ?: 0L
            totalDurationUs = durationMs * 1000L
        } catch (e: Exception) {
            onProgress(SplitProgress(error = "Could not read video metadata: ${e.localizedMessage}"))
            return@withContext emptyList()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        if (totalDurationUs <= 0L) {
            onProgress(SplitProgress(error = "Invalid video duration."))
            return@withContext emptyList()
        }

        val segmentDurationUs = segmentDurationMs * 1000L
        val totalParts = maxOf(1, ceil(totalDurationUs.toDouble() / segmentDurationUs).toInt())
        val rawName = originalFileName.substringBeforeLast(".").ifBlank { "video" }
        val cleanName = rawName.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "video" }

        onProgress(
            SplitProgress(
                currentPart = 0,
                totalParts = totalParts,
                percentage = 0f,
                statusText = "Starting split into $totalParts parts..."
            )
        )

        val bufferSize = 2 * 1024 * 1024 // 2MB sample buffer
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        for (partIndex in 0 until totalParts) {
            val startUs = partIndex * segmentDurationUs
            val endUs = minOf((partIndex + 1) * segmentDurationUs, totalDurationUs)
            val partNumber = partIndex + 1

            onProgress(
                SplitProgress(
                    currentPart = partNumber,
                    totalParts = totalParts,
                    percentage = (partIndex.toFloat() / totalParts.toFloat()),
                    statusText = "Splitting part $partNumber of $totalParts..."
                )
            )

            val tempFile = File(context.cacheDir, "insta1_part_${partNumber}_${System.currentTimeMillis()}.mp4")
            var muxer: MediaMuxer? = null
            var extractor: MediaExtractor? = null

            try {
                var watermarkApplied = false
                if (!isPro) {
                    try {
                        onProgress(
                            SplitProgress(
                                currentPart = partNumber,
                                totalParts = totalParts,
                                percentage = (partIndex.toFloat() / totalParts.toFloat()),
                                statusText = "Applying StoryCut watermark to part $partNumber of $totalParts..."
                            )
                        )
                        watermarkApplied = WatermarkEngine.splitWithWatermark(
                            context = context,
                            sourceUri = sourceUri,
                            startMs = startUs / 1000L,
                            endMs = endUs / 1000L,
                            outputFile = tempFile
                        )
                    } catch (_: Exception) {
                        watermarkApplied = false
                    }
                }

                if (!watermarkApplied) {
                    val ext = MediaExtractor()
                    extractor = ext

                    // Open source video descriptor
                    val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
                        ?: throw IllegalStateException("Could not open video source file.")
                    ext.setDataSource(pfd.fileDescriptor)
                    pfd.close()

                    val trackCount = ext.trackCount
                    val mx = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    muxer = mx
                    val trackIndexMap = HashMap<Int, Int>()

                    var videoTrackIndex = -1
                    for (i in 0 until trackCount) {
                        val format = ext.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                            val muxerTrackIndex = mx.addTrack(format)
                            trackIndexMap[i] = muxerTrackIndex
                            if (mime.startsWith("video/")) {
                                videoTrackIndex = i
                                // Keep video rotation if available
                                if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                                    val rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                                    mx.setOrientationHint(rotation)
                                }
                            }
                        }
                    }

                    if (trackIndexMap.isEmpty()) {
                        throw IllegalStateException("No valid video or audio tracks found.")
                    }

                    mx.start()

                    // Process tracks
                    val trackPtsOffsets = HashMap<Int, Long>()

                    for (extractorTrackIndex in trackIndexMap.keys) {
                        ext.unselectTrack(extractorTrackIndex)
                    }

                    for (extractorTrackIndex in trackIndexMap.keys) {
                        val muxerTrackIndex = trackIndexMap[extractorTrackIndex] ?: continue
                        ext.selectTrack(extractorTrackIndex)
                        ext.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                        var firstSampleTimeUs = -1L

                        while (true) {
                            buffer.clear()
                            val sampleSize = ext.readSampleData(buffer, 0)
                            if (sampleSize < 0) break

                            val sampleTimeUs = ext.sampleTime
                            if (sampleTimeUs >= endUs) break

                            if (sampleTimeUs >= startUs) {
                                if (firstSampleTimeUs == -1L) {
                                    firstSampleTimeUs = sampleTimeUs
                                    trackPtsOffsets[extractorTrackIndex] = sampleTimeUs
                                }

                                val offset = trackPtsOffsets[extractorTrackIndex] ?: 0L
                                val adjustedPts = maxOf(0L, sampleTimeUs - offset)

                                bufferInfo.offset = 0
                                bufferInfo.size = sampleSize
                                bufferInfo.presentationTimeUs = adjustedPts
                                bufferInfo.flags = ext.sampleFlags

                                mx.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                            }

                            if (!ext.advance()) break
                        }

                        ext.unselectTrack(extractorTrackIndex)
                    }

                    mx.stop()
                    mx.release()
                    muxer = null
                }

                // Save to Gallery
                val formattedPart = String.format("%02d", partNumber)
                val formattedTotal = String.format("%02d", totalParts)
                val prefix = if (isPro) "StoryCut" else "StoryCut_Watermarked"
                val displayName = "${prefix}_${cleanName}_part_${formattedPart}_of_${formattedTotal}.mp4"

                val watermarkNote = if (!isPro) " [StoryCut Watermark]" else " [PRO No Watermark]"
                onProgress(
                    SplitProgress(
                        currentPart = partNumber,
                        totalParts = totalParts,
                        percentage = ((partIndex + 0.9f) / totalParts.toFloat()),
                        statusText = "Saving part $partNumber of $totalParts to Gallery$watermarkNote..."
                    )
                )

                val galleryUri = GallerySaver.saveVideoToGallery(context, tempFile, displayName)

                segments.add(
                    VideoSegment(
                        partIndex = partNumber,
                        totalParts = totalParts,
                        startTimeMs = startUs / 1000L,
                        endTimeMs = endUs / 1000L,
                        durationMs = (endUs - startUs) / 1000L,
                        outputUri = galleryUri,
                        fileName = displayName,
                        isSavedToGallery = galleryUri != null
                    )
                )

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { extractor?.release() } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
                if (tempFile.exists()) tempFile.delete()
            }
        }

        onProgress(
            SplitProgress(
                currentPart = totalParts,
                totalParts = totalParts,
                percentage = 1.0f,
                statusText = "Completed! All videos saved sequentially to Gallery.",
                isCompleted = true
            )
        )

        segments
    }
}
