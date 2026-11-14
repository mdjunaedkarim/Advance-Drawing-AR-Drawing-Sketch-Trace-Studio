package com.ardrawing.sketch

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

data class StorePack(
    val productId: String,
    val title: String,
    val coins: Int,
    val priceLabel: String,
    val isRemoveAds: Boolean = false
)

data class AdFreePass(
    val id: String,
    val coinsCost: Int,
    val durationMinutes: Int,
    val labelKey: String,
    val badgeKey: String? = null
)

class BillingAdsManager(
    private val context: Context,
    private val onCoinsOrAdsChanged: () -> Unit,
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val APP_OPEN_AD_ID = "ca-app-pub-3832772126171491/3986972658"
        const val REWARDED_INTERSTITIAL_AD_ID = "ca-app-pub-3832772126171491/4202595074"
        const val BANNER_AD_ID = "ca-app-pub-3832772126171491/2963720935"

        val PACKS = listOf(
            StorePack("coins_10", "Pocket Change", 10, "$0.10"),
            StorePack("coins_50", "Coin Pouch", 50, "$0.51"),
            StorePack("coins_100", "Starter Pack", 100, "$1.01"),
            StorePack("coins_200", "Bronze Vault", 200, "$2.01"),
            StorePack("coins_300", "Artist Bundle", 300, "$3.01"),
            StorePack("coins_400", "Sketch Chest", 400, "$4.01"),
            StorePack("coins_500", "Silver Stash", 500, "$5.01"),
            StorePack("coins_600", "Studio Supply", 600, "$6.01"),
            StorePack("coins_700", "Canvas Reserve", 700, "$7.01"),
            StorePack("coins_800", "Brush Cache", 800, "$8.01"),
            StorePack("coins_900", "Palette Fortress", 900, "$9.01"),
            StorePack("coins_1000", "Gold Reserve", 1000, "$10.01"),
            StorePack("coins_1500", "Pro Vault", 1500, "$15.01"),
            StorePack("coins_2000", "Master Treasury", 2000, "$20.01"),
            StorePack("coins_2500", "Royal Safe", 2500, "$25.01"),
            StorePack("coins_3000", "Diamond Box", 3000, "$30.01"),
            StorePack("coins_4000", "Titan Coffer", 4000, "$40.01"),
            StorePack("coins_5000", "Gallery Vault", 5000, "$50.20"),
            StorePack("coins_7500", "Imperial Chest", 7500, "$74.99"),
            StorePack("coins_10000", "Ultimate Horde", 10000, "$99.99"),
            StorePack("remove_ads", "Permanent Ad-Free", 0, "$499.99", isRemoveAds = true)
        )

        val AD_FREE_PASSES = listOf(
            AdFreePass("adfree_30m", 50, 30, "pass_30m", "pass_badge_starter"),
            AdFreePass("adfree_1h", 100, 60, "pass_1h", "pass_badge_popular"),
            AdFreePass("adfree_2h", 200, 120, "pass_2h"),
            AdFreePass("adfree_4h", 400, 240, "pass_4h"),
            AdFreePass("adfree_8h", 800, 480, "pass_8h"),
            AdFreePass("adfree_12h", 1200, 720, "pass_12h"),
            AdFreePass("adfree_24h", 2000, 1440, "pass_24h", "pass_badge_best")
        )
    }

    private val prefs = context.getSharedPreferences("ar_draw_prefs", Context.MODE_PRIVATE)
    var coins: Int
        get() = prefs.getInt("coins", 0)
        private set(v) { prefs.edit().putInt("coins", v).apply() }

    var permanentAdsRemoved: Boolean
        get() = prefs.getBoolean("ads_removed", false)
        private set(v) { prefs.edit().putBoolean("ads_removed", v).apply() }

    var adFreeUntilMillis: Long
        get() = prefs.getLong("ad_free_until", 0L)
        private set(v) { prefs.edit().putLong("ad_free_until", v).apply() }

    val adsRemoved: Boolean
        get() = permanentAdsRemoved || System.currentTimeMillis() < adFreeUntilMillis

    fun remainingAdFreeMillis(): Long = (adFreeUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    fun redeemAdFreeWithCoins(pass: AdFreePass): Boolean {
        if (permanentAdsRemoved) {
            onMessage(L.t("permanent_already_active"))
            return false
        }
        if (coins < pass.coinsCost) {
            onMessage(L.t("not_enough_coins").replace("{n}", pass.coinsCost.toString()))
            return false
        }
        coins -= pass.coinsCost
        val now = System.currentTimeMillis()
        val base = if (adFreeUntilMillis > now) adFreeUntilMillis else now
        adFreeUntilMillis = base + pass.durationMinutes * 60_000L
        onCoinsOrAdsChanged()
        onMessage(L.t("adfree_activated").replace("{t}", L.t(pass.labelKey)))
        return true
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpen = false
    private var pendingStartupShown = false
    private var pendingStartupCallback: (() -> Unit)? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var cachedBannerView: AdView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var billingClient: BillingClient? = null
    private val productMap = HashMap<String, ProductDetails>()

    fun init() {
        try {
            MobileAds.initialize(context) {
                loadAppOpen()
                loadRewarded()
            }
        } catch (_: Throwable) {}
        connectBilling()
    }

    fun loadAppOpen() {
        if (adsRemoved || isLoadingAppOpen || appOpenAd != null) return
        isLoadingAppOpen = true
        try {
            AppOpenAd.load(
                context,
                APP_OPEN_AD_ID,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isLoadingAppOpen = false
                        appOpenAd = ad
                        Log.d("AdMob", "AppOpen loaded")
                        pendingStartupCallback?.invoke()
                    }
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        isLoadingAppOpen = false
                        appOpenAd = null
                        Log.w("AdMob", "AppOpen failed: ${err.code} ${err.message}")
                        pendingStartupCallback?.invoke()
                    }
                }
            )
        } catch (_: Throwable) {
            isLoadingAppOpen = false
        }
    }

    fun showStartupAd(activity: Activity, onFinished: () -> Unit) {
        if (adsRemoved || pendingStartupShown) { onFinished(); return }
        val openAd = appOpenAd
        if (openAd != null) {
            pendingStartupShown = true
            pendingStartupCallback = null
            openAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null; loadAppOpen(); onFinished()
                }
                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    appOpenAd = null; loadAppOpen(); onFinished()
                }
            }
            openAd.show(activity)
            return
        }

        if (isLoadingAppOpen) {
            var completed = false
            val timeoutRunnable = Runnable {
                if (!completed) {
                    completed = true
                    pendingStartupCallback = null
                    onFinished()
                }
            }
            pendingStartupCallback = {
                if (!completed) {
                    completed = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    pendingStartupCallback = null
                    val loaded = appOpenAd
                    if (loaded != null && !activity.isFinishing && !activity.isDestroyed) {
                        pendingStartupShown = true
                        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                appOpenAd = null; loadAppOpen(); onFinished()
                            }
                            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                                appOpenAd = null; loadAppOpen(); onFinished()
                            }
                        }
                        loaded.show(activity)
                    } else {
                        onFinished()
                    }
                }
            }
            mainHandler.postDelayed(timeoutRunnable, 2500)
            return
        }

        loadAppOpen()
        onFinished()
    }

    fun loadRewarded() {
        if (adsRemoved || rewardedInterstitialAd != null) return
        try {
            RewardedInterstitialAd.load(
                context,
                REWARDED_INTERSTITIAL_AD_ID,
                AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        rewardedInterstitialAd = ad
                        Log.d("AdMob", "RewardedInterstitial loaded")
                    }
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        rewardedInterstitialAd = null
                        Log.w("AdMob", "RewardedInterstitial failed: ${err.code} ${err.message}")
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    fun showRewardedOnComplete(activity: Activity, onFinished: () -> Unit) {
        if (adsRemoved) { onFinished(); return }
        val ad = rewardedInterstitialAd
        if (ad == null) {
            loadRewarded()
            onFinished()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedInterstitialAd = null; loadRewarded(); onFinished()
            }
            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                rewardedInterstitialAd = null; loadRewarded(); onFinished()
            }
        }
        ad.show(activity) {
            coins += 5
            onCoinsOrAdsChanged()
            onMessage(L.t("reward_coins_earned"))
        }
    }

    fun attachBanner(container: ViewGroup) {
        container.removeAllViews()
        if (adsRemoved) return
        try {
            val adView = cachedBannerView ?: AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_ID
                adListener = object : com.google.android.gms.ads.AdListener() {
                    override fun onAdLoaded() { Log.d("AdMob", "Banner loaded") }
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        Log.w("AdMob", "Banner failed: ${err.code} ${err.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }.also { cachedBannerView = it }

            (adView.parent as? ViewGroup)?.removeView(adView)
            container.addView(adView, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        } catch (_: Throwable) {}
    }

    private fun connectBilling() {
        try {
            val client = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build()
            billingClient = client
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(res: BillingResult) {
                    if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryProducts(); restorePurchases()
                    }
                }
                override fun onBillingServiceDisconnected() {}
            })
        } catch (_: Throwable) {}
    }

    private fun queryProducts() {
        val client = billingClient ?: return
        val products = PACKS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { _, list ->
            productMap.clear()
            list.forEach { productMap[it.productId] = it }
        }
    }

    private fun restorePurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { res, list ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) list.forEach { handlePurchase(it) }
        }
    }

    fun purchase(activity: Activity, pack: StorePack) {
        val client = billingClient
        val pd = productMap[pack.productId]
        if (client == null || !client.isReady || pd == null) {
            onMessage(L.t("play_pending"))
            return
        }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build())
            ).build()
        client.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(res: BillingResult, purchases: MutableList<Purchase>?) {
        if (res.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val client = billingClient ?: return
        val prodId = purchase.products.firstOrNull() ?: return
        val pack = PACKS.find { it.productId == prodId } ?: return

        if (pack.isRemoveAds) {
            permanentAdsRemoved = true
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) {}
            }
            onCoinsOrAdsChanged()
        } else {
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { br, _ ->
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    coins += pack.coins
                    onCoinsOrAdsChanged()
                }
            }
        }
    }

    fun destroy() { billingClient?.endConnection() }
}
