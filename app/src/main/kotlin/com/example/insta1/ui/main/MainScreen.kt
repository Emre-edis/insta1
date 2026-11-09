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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.example.insta1.ui.ad.InterstitialAdManager
import com.example.insta1.util.VideoUtils
import kotlinx.coroutines.launch
import kotlin.math.ceil

// Neon Cyan & Sapphire Blue Aesthetics
val NeonCyan = Color(0xFF00E5FF)          // Electric Cyan (#00E5FF)
val NeonSapphire = Color(0xFF0070F3)      // Vivid Sapphire Blue (#0070F3)
val NeonSkyBlue = Color(0xFF38BDF8)       // Sky Blue (#38BDF8)
val AppDarkBg = Color(0xFF050811)         // Midnight Obsidian Black (#050811)
val AppCardBg = Color(0xFF080F1D)         // Sleek Dark Slate Card (#080F1D)
val AppCardBorder = Color(0xFF132238)     // Subtle Card Border
val AppGreen = Color(0xFF10B981)          // Success Emerald Green

val NeonGlowGradient = Brush.horizontalGradient(
    listOf(NeonCyan, NeonSapphire)
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val billingManager = remember { BillingManager(context, scope) }
    val isPro by billingManager.isProUnlocked.collectAsState()
    val productDetails by billingManager.productDetails.collectAsState()
    val priceLabel = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$2.99"

    val interstitialAdManager = remember { InterstitialAdManager(context) }

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
        containerColor = AppDarkBg,
        bottomBar = {
            AdBannerView(isPro = isPro)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header & Brand
            item {
                HeaderSection()
            }

            // 1.5 PRO Banner (Lifetime One-Time Purchase)
            item {
                StoryCutProCard(
                    isPro = isPro,
                    priceLabel = priceLabel,
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

                // 4. Action Button ("CUT & SAVE")
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
                                    if (results.isNotEmpty() && !isPro && activity != null) {
                                        interstitialAdManager.showAdIfAvailable(activity, isPro = false)
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isProcessing) Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                                    else NeonGlowGradient
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
                                    fontWeight = FontWeight.ExtraBold,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Squircle App Icon with Subtle Neon Cyan Border
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppCardBg)
                .border(1.2.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.example.insta1.R.drawable.app_logo),
                contentDescription = "StoryCut Logo",
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "StoryCut",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Smart 1-Minute Video Splitter for Stories & Reels",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun StoryCutProCard(
    isPro: Boolean,
    priceLabel: String = "$2.99",
    onUpgradeClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onTestToggle: () -> Unit
) {
    // Ambient Neon Cyan Glow Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isPro) AppGreen.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    radius = 450f
                ),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPro) Color(0xFF061714) else AppCardBg
            ),
            border = BorderStroke(
                1.8.dp,
                if (isPro) Brush.horizontalGradient(listOf(AppGreen, Color(0xFF059669)))
                else Brush.horizontalGradient(listOf(NeonCyan, NeonSapphire))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.clickable { onTestToggle() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = if (isPro) "👑" else "✨", fontSize = 20.sp)
                        Text(
                            text = if (isPro) "StoryCut Lifetime PRO Active" else "StoryCut Lifetime PRO",
                            color = if (isPro) AppGreen else NeonSkyBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isPro) AppGreen.copy(alpha = 0.2f)
                                else Color(0xFF003852)
                            )
                            .border(
                                1.dp,
                                if (isPro) AppGreen.copy(alpha = 0.5f)
                                else NeonCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPro) "NO WATERMARK • NO ADS" else "NO MONTHLY FEES",
                            color = if (isPro) AppGreen else NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                if (isPro) {
                    Text(
                        text = "Lifetime Access Active: Split videos cleanly with zero watermark, maximum lossless remuxing speed, and no ads forever!",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            text = "No Monthly Fees! Pay Once ($priceLabel), Own Forever:",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        ProFeatureItem("Remove 'StoryCut' watermark")
                        ProFeatureItem("Ultra-fast lossless remuxing")
                        ProFeatureItem("Eliminate all ads")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pay Once Button with vibrant cyan-blue gradient
                        Button(
                            onClick = onUpgradeClick,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(NeonSapphire, NeonCyan)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "PAY ONCE ($priceLabel)",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Restore Purchases Button
                        Button(
                            onClick = onRestoreClick,
                            modifier = Modifier
                                .weight(0.9f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D1929)),
                            border = BorderStroke(1.2.dp, Color(0xFF1E3A5F))
                        ) {
                            Text(
                                text = "Restore",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFeatureItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "✓",
            color = NeonCyan,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 13.sp
        )
    }
}

@Composable
fun VideoPickerCard(
    selectedVideo: VideoInfo?,
    onPickClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = if (selectedVideo == null) 20.dp else 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Glowing Background Aura
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.16f), Color.Transparent),
                        radius = 450f
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickClick() },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AppCardBg),
                border = BorderStroke(
                    1.8.dp,
                    Brush.horizontalGradient(
                        listOf(
                            NeonCyan.copy(alpha = 0.9f),
                            NeonSapphire.copy(alpha = 0.65f)
                        )
                    )
                )
            ) {
                if (selectedVideo == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 38.dp, start = 20.dp, end = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Halo around Slate/Clapperboard
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF003848))
                                .border(1.2.dp, NeonCyan.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🎬", fontSize = 30.sp)
                        }

                        Text(
                            text = "Select Video from Gallery",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Pick a video of any duration; the app will split it into sequential 1-minute clips automatically.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (selectedVideo.thumbnail != null) {
                            Image(
                                bitmap = selectedVideo.thumbnail.asImageBitmap(),
                                contentDescription = "Video Thumbnail",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0F1E33)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "▶", color = NeonCyan, fontSize = 26.sp)
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
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap to choose a different video ↻",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Floating '+' squircle button overlapping the bottom border (exact match to screenshot!)
        if (selectedVideo == null) {
            Box(
                modifier = Modifier
                    .offset(y = 20.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AppCardBg)
                    .border(1.8.dp, NeonCyan, RoundedCornerShape(13.dp))
                    .clickable { onPickClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = NeonCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-1).dp)
                )
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
        colors = CardDefaults.cardColors(containerColor = AppCardBg),
        border = BorderStroke(1.2.dp, NeonCyan.copy(alpha = 0.35f))
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
                        .background(Color(0xFF003852))
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$totalParts Segments Total",
                        color = NeonCyan,
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
                            selectedContainerColor = NeonSapphire,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF0F1E33),
                            labelColor = Color(0xFF94A3B8)
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardBg),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(NeonCyan, NeonSapphire)))
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
                    color = NeonCyan,
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
                color = NeonCyan,
                trackColor = Color(0xFF0F1E33)
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF062016)),
        border = BorderStroke(1.4.dp, AppGreen)
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
                    color = AppGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isPro) {
                        "$totalParts clean clips added to 'Movies/StoryCut' without watermark."
                    } else {
                        "$totalParts clips added to 'Movies/StoryCut' (StoryCut watermark applied. Upgrade to PRO to remove!)."
                    },
                    color = Color.White.copy(alpha = 0.85f),
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardBg),
        border = BorderStroke(1.2.dp, NeonCyan.copy(alpha = 0.25f))
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
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), CircleShape),
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
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenInGallery,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "Open", fontSize = 11.sp, color = Color.White)
                }

                Button(
                    onClick = onShare,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .background(NeonGlowGradient, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Share", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
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
