package com.example.insta1.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.insta1.model.SplitProgress
import com.example.insta1.model.VideoInfo
import com.example.insta1.model.VideoSegment
import com.example.insta1.service.BillingManager
import com.example.insta1.service.VideoSplitter
import com.example.insta1.ui.ad.AdBannerView
import com.example.insta1.util.VideoUtils
import kotlinx.coroutines.launch
import kotlin.math.ceil

val InstaPurple = Color(0xFF00E5FF)       // Electric Cyan / Light Blue
val InstaPink = Color(0xFF0070F3)         // Vivid Sapphire Blue
val InstaOrange = Color(0xFF38BDF8)       // Sky Blue
val InstaDark = Color(0xFF05070B)         // Pitch Solid Black Background
val InstaCardDark = Color(0xFF0B101C)     // Deep Midnight Card
val InstaCardBorder = Color(0xFF17243B)   // Neon Blue Border
val InstaGreen = Color(0xFF10B981)

val InstaGradient = Brush.horizontalGradient(
    listOf(InstaPurple, InstaPink, Color(0xFF003882))
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val billingManager = remember { BillingManager(context, scope) }
    val isPro by billingManager.isProUnlocked.collectAsState()

    var selectedVideo by remember { mutableStateOf<VideoInfo?>(null) }
    var segmentDurationMs by remember { mutableLongStateOf(60_000L) } // 60s default
    var isProcessing by remember { mutableStateOf(false) }
    var progressState by remember { mutableStateOf(SplitProgress()) }
    val completedSegments = remember { mutableStateListOf<VideoSegment>() }

    // Video Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val info = VideoUtils.extractVideoInfo(context, it)
            selectedVideo = info
            completedSegments.clear()
            progressState = SplitProgress()
        }
    }

    Scaffold(
        containerColor = InstaDark,
        bottomBar = {
            AdBannerView(isPro = isPro)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header & Brand
            item {
                HeaderSection()
            }

            // 1.5 PRO Banner
            item {
                StoryCutProCard(
                    isPro = isPro,
                    onUpgradeClick = {
                        activity?.let { billingManager.launchPurchaseFlow(it) }
                    },
                    onRestoreClick = {
                        billingManager.queryPurchases()
                    },
                    onTestToggle = {
                        billingManager.toggleTestPro()
                    }
                )
            }

            // 2. Video Picker Section
            item {
                VideoPickerCard(
                    selectedVideo = selectedVideo,
                    onPickClick = { videoPickerLauncher.launch("video/*") }
                )
            }

            // 3. Segment Configuration & Calculated Parts
            if (selectedVideo != null) {
                item {
                    val video = selectedVideo!!
                    val totalParts = maxOf(1, ceil(video.durationMs.toDouble() / segmentDurationMs).toInt())

                    ConfigurationCard(
                        video = video,
                        totalParts = totalParts,
                        currentDurationMs = segmentDurationMs,
                        onDurationSelect = { segmentDurationMs = it }
                    )
                }

                // 4. Action Button
                item {
                    Button(
                        onClick = {
                            if (!isProcessing && selectedVideo != null) {
                                isProcessing = true
                                completedSegments.clear()
                                scope.launch {
                                    val results = VideoSplitter.splitVideo(
                                        context = context,
                                        sourceUri = selectedVideo!!.uri,
                                        originalFileName = selectedVideo!!.name,
                                        segmentDurationMs = segmentDurationMs,
                                        isPro = isPro,
                                        onProgress = { progress ->
                                            progressState = progress
                                        }
                                    )
                                    completedSegments.addAll(results)
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isProcessing) Brush.horizontalGradient(listOf(Color.Gray, Color.DarkGray))
                                    else InstaGradient
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isProcessing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "SPLITTING VIDEO...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "CUT & SAVE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }

            // 5. Progress Card
            if (isProcessing || progressState.percentage > 0f) {
                item {
                    ProgressCard(progress = progressState)
                }
            }

            // 6. Completed Segments List
            if (completedSegments.isNotEmpty()) {
                item {
                    SuccessHeader(totalParts = completedSegments.size, isPro = isPro)
                }

                items(completedSegments) { segment ->
                    SegmentItemCard(
                        segment = segment,
                        onOpenInGallery = {
                            segment.outputUri?.let { uri ->
                                openVideoInGallery(context, uri)
                            }
                        },
                        onShare = {
                            segment.outputUri?.let { uri ->
                                shareVideo(context, uri)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = com.example.insta1.R.drawable.app_logo),
                contentDescription = "StoryCut Logo",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Column {
                Text(
                    text = "StoryCut",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Smart 1-Minute Video Splitter for Stories & Reels",
                    fontSize = 12.sp,
                    color = Color(0xFFA0A5B5)
                )
            }
        }
    }
}

@Composable
fun StoryCutProCard(
    isPro: Boolean,
    onUpgradeClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onTestToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) Color(0xFF0C1E16) else Color(0xFF091120)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isPro) Brush.horizontalGradient(listOf(InstaGreen, Color(0xFF059669)))
            else Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0070F3), Color(0xFF003882)))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = if (isPro) "👑" else "✨", fontSize = 20.sp)
                    Text(
                        text = if (isPro) "StoryCut PRO Active" else "StoryCut PRO (50¢)",
                        color = if (isPro) InstaGreen else Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                if (isPro) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(InstaGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "NO WATERMARK • NO ADS",
                            color = InstaGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = if (isPro) {
                    "Lifetime Access Active: Split videos cleanly with zero watermark, maximum remuxing speed, and no banner ads!"
                } else {
                    "Upgrade once for \$0.50: Remove the 'StoryCut' watermark from videos, unlock ultra-fast lossless remuxing, and remove all ads."
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            if (!isPro) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onUpgradeClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0070F3)
                        )
                    ) {
                        Text(
                            text = "BUY PRO ($0.50)",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onRestoreClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Restore", fontSize = 12.sp, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onTestToggle,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Dev Test", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPickerCard(
    selectedVideo: VideoInfo?,
    onPickClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InstaCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(InstaCardBorder, InstaPurple.copy(alpha = 0.5f))))
    ) {
        if (selectedVideo == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(InstaPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎬", fontSize = 28.sp)
                }
                Text(
                    text = "Select Video from Gallery",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Pick a video of any duration; the app will split it into sequential 1-minute clips automatically.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedVideo.thumbnail != null) {
                    Image(
                        bitmap = selectedVideo.thumbnail.asImageBitmap(),
                        contentDescription = "Video Thumbnail",
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "▶", color = Color.White, fontSize = 24.sp)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedVideo.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Duration: ${VideoUtils.formatDuration(selectedVideo.durationMs)}  •  Size: ${VideoUtils.formatFileSize(selectedVideo.sizeBytes)}",
                        color = Color(0xFFA0A5B5),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap to choose a different video ↻",
                        color = InstaOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigurationCard(
    video: VideoInfo,
    totalParts: Int,
    currentDurationMs: Long,
    onDurationSelect: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InstaCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(InstaCardBorder, InstaCardBorder)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Segment Length:",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(InstaPurple.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$totalParts Segments Total",
                        color = Color(0xFFE0C3FC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val durations = listOf(
                    60_000L to "1 Min (Default)",
                    30_000L to "30 Seconds",
                    15_000L to "15 Seconds"
                )

                durations.forEach { (ms, label) ->
                    val isSelected = currentDurationMs == ms
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDurationSelect(ms) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InstaPink,
                            selectedLabelColor = Color.White,
                            containerColor = InstaDark,
                            labelColor = Color.Gray
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressCard(progress: SplitProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = InstaCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(InstaPurple, InstaPink)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = progress.statusText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "%${(progress.percentage * 100).toInt()}",
                    color = InstaOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            LinearProgressIndicator(
                progress = { progress.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = InstaPink,
                trackColor = InstaDark
            )

            if (progress.error != null) {
                Text(
                    text = "Error: ${progress.error}",
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SuccessHeader(totalParts: Int, isPro: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = InstaGreen.copy(alpha = 0.15f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(InstaGreen, InstaGreen)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "✅", fontSize = 22.sp)
            Column {
                Text(
                    text = "All Segments Saved to Gallery!",
                    color = InstaGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isPro) {
                        "$totalParts clean clips added to 'Movies/StoryCut' without watermark."
                    } else {
                        "$totalParts clips added to 'Movies/StoryCut' (StoryCut watermark applied. Upgrade to PRO to remove!)."
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SegmentItemCard(
    segment: VideoSegment,
    onOpenInGallery: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = InstaCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(InstaCardBorder, InstaCardBorder)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(InstaPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${segment.partIndex}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Column {
                    Text(
                        text = "Part ${String.format("%02d", segment.partIndex)} of ${String.format("%02d", segment.totalParts)}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${VideoUtils.formatDuration(segment.startTimeMs)} - ${VideoUtils.formatDuration(segment.endTimeMs)} (${VideoUtils.formatDuration(segment.durationMs)})",
                        color = Color(0xFFA0A5B5),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onOpenInGallery,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "Open", fontSize = 11.sp, color = Color.White)
                }

                Button(
                    onClick = onShare,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InstaPurple),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "Share", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

fun openVideoInGallery(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

fun shareVideo(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Video"))
    } catch (_: Exception) {}
}
