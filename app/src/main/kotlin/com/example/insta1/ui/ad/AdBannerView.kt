package com.example.insta1.ui.ad

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object AdMobConfig {
    // Official Google Test Banner Ad Unit ID (Always safe for testing)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    private var initialized = false

    fun initialize(context: Context) {
        if (!initialized) {
            try {
                MobileAds.initialize(context) {}
                initialized = true
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun AdBannerView(
    isPro: Boolean,
    modifier: Modifier = Modifier
) {
    if (isPro) {
        // Zero ads for PRO users!
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF141722))
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                try {
                    AdMobConfig.initialize(context)
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdMobConfig.TEST_BANNER_AD_UNIT_ID
                        loadAd(AdRequest.Builder().build())
                    }
                } catch (_: Throwable) {
                    android.view.View(context)
                }
            }
        )
    }
}
