package com.example.insta1.ui.ad

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(private val context: Context) {

    companion object {
        // Official Google Public Test Interstitial Ad Unit ID (Always safe for development & testing)
        // Production: Replace with real AdMob Interstitial Ad Unit ID from AdMob Console
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    init {
        try {
            AdMobConfig.initialize(context)
            loadAd()
        } catch (_: Throwable) {}
    }

    fun loadAd() {
        try {
            if (interstitialAd != null || isLoading) return
            isLoading = true

            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoading = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isLoading = false
                    }
                }
            )
        } catch (_: Throwable) {
            isLoading = false
        }
    }

    fun showAdIfAvailable(
        activity: Activity,
        isPro: Boolean,
        onAdDismissed: () -> Unit = {}
    ) {
        try {
            // Zero ads for PRO users
            if (isPro) {
                onAdDismissed()
                return
            }

            val ad = interstitialAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        loadAd() // Pre-load next ad for future splits
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        interstitialAd = null
                        loadAd()
                        onAdDismissed()
                    }
                }
                ad.show(activity)
            } else {
                // If ad is not cached yet, do not block the user
                loadAd()
                onAdDismissed()
            }
        } catch (_: Throwable) {
            onAdDismissed()
        }
    }
}
