package com.examsystem.app.ads

import android.app.Activity
import android.content.Context
import com.examsystem.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Loads and shows rewarded ads for unlocking student scores after an exam.
 * Uses [BuildConfig.ADMOB_REWARDED_UNIT_ID] — replace with your real AdMob unit for production.
 */
object RewardedAdManager {

    private var cachedAd: RewardedAd? = null
    private var isLoading = false

    fun isReady(): Boolean = cachedAd != null

    fun clear() {
        cachedAd = null
        isLoading = false
    }

    fun preload(
        context: Context,
        onReady: () -> Unit = {},
        onFailed: (String) -> Unit = {}
    ) {
        if (cachedAd != null) {
            onReady()
            return
        }
        if (isLoading) return
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    cachedAd = ad
                    onReady()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    cachedAd = null
                    onFailed(formatLoadError(error))
                }
            }
        )
    }

    fun show(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onFailed: (String) -> Unit,
        onDismissed: () -> Unit = {}
    ) {
        val ad = cachedAd
        if (ad == null) {
            onFailed("Ad not ready yet. Check internet and try again.")
            return
        }
        cachedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity.applicationContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload(activity.applicationContext)
                onFailed("Could not show ad (${error.code}): ${error.message}")
            }
        }
        ad.show(activity) {
            onRewardEarned()
        }
    }

    private fun formatLoadError(error: LoadAdError): String {
        val hint = when (error.code) {
            0 -> "Internal error — restart the app and try again."
            1 -> "Invalid ad request — check AdMob app ID in AndroidManifest."
            2 -> "Network error — connect to Wi‑Fi or mobile data."
            3 -> "No ad available right now — try again in a minute."
            else -> error.message
        }
        return "Ad failed to load (code ${error.code}): $hint"
    }
}
