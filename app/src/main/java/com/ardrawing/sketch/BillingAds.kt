package com.ardrawing.sketch

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

data class StorePack(
    val productId: String,
    val coins: Int,
    val priceLabel: String,
    val isRemoveAds: Boolean = false
)

class BillingAdsManager(
    private val context: Context,
    private val onCoinsOrAdsChanged: () -> Unit,
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        // Play Store-এ দেওয়ার আগে এগুলো আপনার আসল AdMob Unit ID দিয়ে বদলাবেন
        const val INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712"
        const val BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"

        val PACKS = listOf(
            StorePack("coins_10", 10, "$0.10"),
            StorePack("coins_50", 50, "$0.50"),
            StorePack("coins_100", 100, "$1.01"),
            StorePack("coins_200", 200, "$2.01"),
            StorePack("coins_500", 500, "$5.00"),
            StorePack("coins_1000", 1000, "$10.00"),
            StorePack("remove_ads", 0, "$2.99", isRemoveAds = true)
        )
    }

    private val prefs = context.getSharedPreferences("ar_draw_prefs", Context.MODE_PRIVATE)
    var coins: Int
        get() = prefs.getInt("coins", 0)
        private set(v) { prefs.edit().putInt("coins", v).apply() }

    var adsRemoved: Boolean
        get() = prefs.getBoolean("ads_removed", false)
        private set(v) { prefs.edit().putBoolean("ads_removed", v).apply() }

    private var interstitialAd: InterstitialAd? = null
    private var billingClient: BillingClient? = null
    private val productMap = HashMap<String, ProductDetails>()

    fun init() {
        try {
            MobileAds.initialize(context) {}
            loadInterstitial()
        } catch (_: Exception) {}
        connectBilling()
    }

    fun loadInterstitial() {
        if (adsRemoved) return
        try {
            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                    override fun onAdFailedToLoad(err: LoadAdError) { interstitialAd = null }
                }
            )
        } catch (_: Exception) {}
    }

    fun showInterstitial(activity: Activity, onFinished: () -> Unit) {
        if (adsRemoved) {
            onFinished()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial()
            onFinished()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial()
                onFinished()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                interstitialAd = null
                onFinished()
            }
        }
        ad.show(activity)
    }

    fun attachBanner(container: ViewGroup) {
        container.removeAllViews()
        if (adsRemoved) return
        try {
            val adView = AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_ID
            }
            container.addView(adView)
            adView.loadAd(AdRequest.Builder().build())
        } catch (_: Exception) {}
    }

    private fun connectBilling() {
        try {
            val client = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()
            billingClient = client
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(res: BillingResult) {
                    if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryProducts()
                        restorePurchases()
                    }
                }
                override fun onBillingServiceDisconnected() {}
            })
        } catch (_: Exception) {}
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
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { res, list ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                list.forEach { handlePurchase(it) }
            }
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
            )
            .build()
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
            adsRemoved = true
            if (!purchase.isAcknowledged) {
                val ack = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.acknowledgePurchase(ack) {}
            }
            onCoinsOrAdsChanged()
        } else {
            val consume = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.consumeAsync(consume) { br, _ ->
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    coins += pack.coins
                    onCoinsOrAdsChanged()
                }
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
    }
}
