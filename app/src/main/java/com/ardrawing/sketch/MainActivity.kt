package com.ardrawing.sketch

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {

    private enum class Tab { HOME, GALLERY, STORE, SETTINGS }
    private enum class Flow { SPLASH, AUTH, RESTRICTED, ADMIN, MAIN, LANG, PRIVACY, ANIME_CHARS, CHAR_GALLERY, BROWSER, EDITOR, CAMERA, LIGHTBOX, CONGRATS }

    private var flow = Flow.SPLASH
    private var currentTab = Tab.HOME
    private lateinit var root: FrameLayout
    private lateinit var analysisExecutor: ExecutorService
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val tracker by lazy { PlaneTracker() }

    private lateinit var billingAds: BillingAdsManager
    private lateinit var cloud: FirebaseCloudManager
    private var sessionTimerRunnable: Runnable? = null
    private var sessionSeconds = 0
    private var usageWindowStartMs = 0L
    private var usageFlushRunnable: Runnable? = null
    private val prefs by lazy { getSharedPreferences("ar_draw_prefs", MODE_PRIVATE) }
    private val galleryDir by lazy { File(filesDir, "gallery").apply { mkdirs() } }

    private var originalBitmap: Bitmap? = null
    private var cutoutBitmap: Bitmap? = null
    private val editHistory = ArrayList<Bitmap>()
    private var bgRemoved = false
    private var isProcessingBg = false
    private var isColorPickMode = false
    private var pickedColor: Int? = null
    private var colorTolerance = 38
    private var pickedTouchX = -1f
    private var pickedTouchY = -1f
    private var opacityPercent = 80
    private var brightnessPercent = 80
    private var isLightboxLocked = false
    private var openLightboxAfterPick = false

    private var selectedSeries: AnimeCatalog.Series = AnimeCatalog.list[0]
    private var selectedCharacterName = ""
    private var selectedCharacterSeries = ""
    private var charGalleryReturnFlow = Flow.MAIN
    private var browserReturnFlow = Flow.MAIN
    private var browserQuery = "Anime black and white sketch drawing"
    private var activeWebView: WebView? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var torchOn = false
    private var controlsHidden = false
    private var overlayView: OverlayView? = null
    private var cameraHintView: TextView? = null
    private var cameraHintDot: View? = null
    private var pendingCameraUri: Uri? = null
    private var importToGalleryMode = false
    private var openCameraAfterPermission = false
    private var insetTop = 0
    private var insetBottom = 0
    private var applyInsetsCallback: (() -> Unit)? = null
    private var activePopupDismiss: (() -> Unit)? = null
    @Volatile
    private var isDownloadingOnlineImage = false

    private fun safeTop(): Int = maxOf(insetTop, dp(16))
    private fun safeBottom(): Int = maxOf(insetBottom, dp(10))

    private fun updateSystemBarsAppearance(lightBars: Boolean) {
        try {
            val ctrl = WindowInsetsControllerCompat(window, root)
            ctrl.isAppearanceLightStatusBars = lightBars
            ctrl.isAppearanceLightNavigationBars = lightBars
        } catch (_: Throwable) {}
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handlePickedUri(uri) else openLightboxAfterPick = false
    }
    private val pickAnyFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleImportFileUri(uri)
    }
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) handlePickedUri(uri)
    }
    private val cameraPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(this, L.t("camera_denied"), Toast.LENGTH_LONG).show()
        else if (openCameraAfterPermission) { openCameraAfterPermission = false; launchTakePhoto() }
        else showFlow(Flow.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try { OpenCVLoader.initLocal() } catch (_: Throwable) {}
        L.lang = prefs.getString("lang", "en") ?: "en"
        UiTheme.isDark = prefs.getBoolean("dark_mode", false)
        analysisExecutor = Executors.newSingleThreadExecutor()

        billingAds = BillingAdsManager(
            context = this,
            onCoinsOrAdsChanged = { runOnUiThread { if (flow == Flow.MAIN) renderMain() } },
            onMessage = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } },
            onAdWatched = { adType ->
                if (::cloud.isInitialized && cloud.currentUser() != null) {
                    lifecycleScope.launch { cloud.recordAdWatched(adType) }
                }
            },
            onPurchaseCompleted = { productId, title, amountCents ->
                if (::cloud.isInitialized && cloud.currentUser() != null) {
                    lifecycleScope.launch { cloud.recordPurchase(productId, title, amountCents) }
                }
            }
        )
        try { billingAds.init() } catch (_: Throwable) {}
        cloud = FirebaseCloudManager(this)

        root = FrameLayout(this).apply { setBackgroundColor(UiTheme.bg) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            insetTop = bars.top
            insetBottom = bars.bottom
            applyInsetsCallback?.invoke()
            insets
        }
        setContentView(root)
        updateSystemBarsAppearance(!UiTheme.isDark)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                activePopupDismiss?.let {
                    it.invoke()
                    return
                }
                when (flow) {
                    Flow.SPLASH -> {}
                    Flow.AUTH, Flow.RESTRICTED -> finish()
                    Flow.ADMIN -> { cloud.stopAdminUsersListener(); showFlow(Flow.MAIN) }
                    Flow.CAMERA -> { stopCamera(); showFlow(Flow.EDITOR) }
                    Flow.LIGHTBOX -> {
                        if (!isLightboxLocked) {
                            restoreWindowDefaults()
                            showFlow(if (originalBitmap != null) Flow.EDITOR else Flow.MAIN)
                        }
                    }
                    Flow.CHAR_GALLERY -> showFlow(charGalleryReturnFlow)
                    Flow.BROWSER -> {
                        val wv = activeWebView
                        if (wv != null && wv.canGoBack()) wv.goBack()
                        else { destroyWebView(); showFlow(browserReturnFlow) }
                    }
                    Flow.ANIME_CHARS, Flow.LANG, Flow.PRIVACY, Flow.EDITOR, Flow.CONGRATS -> showFlow(Flow.MAIN)
                    Flow.MAIN -> if (currentTab != Tab.HOME) { currentTab = Tab.HOME; renderMain() } else finish()
                }
            }
        })
        SketchSearchRepository.warmupHomePreviews(applicationContext, UiTabs.homeShuffleSeed)
        showSplash()
    }

    private fun showSplash() {
        flow = Flow.SPLASH
        applyInsetsCallback = null
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews()
        val wrap = FrameLayout(this).apply {
            background = UiTheme.grad(
                this@MainActivity,
                if (UiTheme.isDark) "#07090F" else "#F2EFFE",
                if (UiTheme.isDark) "#1A153E" else "#FCE7F3",
                0
            )
        }

        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), safeTop(), dp(24), dp(42))
        }
        val logoHero = FrameLayout(this)
        logoHero.addView(
            StudioIcons.SplashAuraView(this),
            FrameLayout.LayoutParams(dp(236), dp(236), Gravity.CENTER)
        )
        val logoCard = FrameLayout(this).apply {
            background = UiTheme.round(this@MainActivity, Color.WHITE, 34, Color.parseColor("#66FFFFFF"), 2)
            UiTheme.applyColoredShadow(this, Color.parseColor("#7C3AED"), 16)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(34).toFloat()) }
            }
            clipToOutline = true
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.app_logo)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = L.t("app")
                },
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            )
        }
        logoHero.addView(logoCard, FrameLayout.LayoutParams(dp(152), dp(152), Gravity.CENTER))
        UiTheme.attachGentleFloat(logoCard, 3.5f, 2600L)
        centerCol.addView(logoHero, LinearLayout.LayoutParams(dp(236), dp(236)).apply { bottomMargin = dp(4) })
        centerCol.addView(TextView(this).apply {
            text = L.t("app")
            textSize = 26f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        })
        centerCol.addView(TextView(this).apply {
            text = L.t("splash_sub")
            textSize = 13.5f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
        })
        centerCol.scaleX = 0.84f
        centerCol.scaleY = 0.84f
        centerCol.alpha = 0f
        centerCol.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(540)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.18f))
            .start()

        val bottomCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(38), 0, dp(38), safeBottom() + dp(24))
        }
        val pctTv = TextView(this).apply {
            text = "${L.t("loading")}... 1%"
            fontFeatureSettings = "tnum"
            textSize = 12.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.accentBright)
            setPadding(0, 0, 0, dp(8))
        }
        val bar = StudioIcons.StudioProgressView(this).apply { progress = 1 }
        bottomCol.addView(pctTv)
        bottomCol.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
        bottomCol.addView(TextView(this).apply {
            text = L.t("ad_notice")
            textSize = 11.5f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            setPadding(0, dp(10), 0, 0)
        })

        wrap.addView(centerCol, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        wrap.addView(bottomCol, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(wrap, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            bottomCol.setPadding(dp(38), 0, dp(38), safeBottom() + dp(24))
        }
        ViewCompat.requestApplyInsets(root)

        var p = 1
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed || flow != Flow.SPLASH) return
                p = (p + 4).coerceAtMost(100)
                bar.progress = p
                pctTv.text = "${L.t("loading")}... $p%"
                if (p < 100) handler.postDelayed(this, 45)
                else handler.postDelayed({
                    if (isFinishing || isDestroyed || flow != Flow.SPLASH) return@postDelayed
                    billingAds.showStartupAd(this@MainActivity) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) proceedAfterSplash()
                        }
                    }
                }, 140)
            }
        }, 45)
    }

    private fun proceedAfterSplash() {
        if (isFinishing || isDestroyed) return
        cloud.attemptAutoSignIn(
            activity = this,
            scope = lifecycleScope,
            onAuthSuccess = {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) onAuthenticatedReady()
                }
            },
            onUnauthenticated = {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) showFlow(Flow.AUTH)
                }
            }
        )
    }

    private fun shouldShowRestricted(): Boolean {
        if (cloud.isAdminUser) return false
        return cloud.currentUserRecord?.blocked == true || cloud.globalConfig.maintenanceMode
    }

    private fun onAuthenticatedReady() {
        lifecycleScope.launch {
            cloud.initializeAuthenticatedSession(
                languageCode = L.lang,
                onConfigUpdated = { cfg ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        billingAds.applyRemoteAdsEnabled(cfg.adsEnabled)
                        if (shouldShowRestricted()) {
                            if (flow != Flow.RESTRICTED) showFlow(Flow.RESTRICTED)
                            else renderRestrictedGate()
                        } else if (flow == Flow.RESTRICTED) {
                            showFlow(Flow.MAIN)
                        } else if (flow == Flow.MAIN) {
                            if (!cfg.storeEnabled && !cloud.isAdminUser && currentTab == Tab.STORE) {
                                currentTab = Tab.HOME
                            }
                            renderMain()
                        }
                    }
                },
                onSelfUserUpdated = { rec ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        billingAds.syncCloudRewards(rec.vipGranted, rec.bonusCoins)
                        if (shouldShowRestricted()) {
                            if (flow != Flow.RESTRICTED) showFlow(Flow.RESTRICTED)
                            else renderRestrictedGate()
                        } else if (flow == Flow.RESTRICTED) {
                            showFlow(Flow.MAIN)
                        }
                    }
                },
                onAdminStatusResolved = { _ ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && flow == Flow.MAIN) renderMain()
                    }
                }
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                startUsageTracking()
                if (shouldShowRestricted()) showFlow(Flow.RESTRICTED)
                else showFlow(Flow.MAIN)
            }
        }
    }

    private fun startUsageTracking() {
        if (!::cloud.isInitialized || cloud.currentUser() == null) return
        if (usageWindowStartMs == 0L) {
            usageWindowStartMs = SystemClock.elapsedRealtime()
        }
        usageFlushRunnable?.let { handler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                flushUsageSeconds(resetStart = true)
                handler.postDelayed(this, 60_000L)
            }
        }
        usageFlushRunnable = r
        handler.postDelayed(r, 60_000L)
    }

    private fun flushUsageSeconds(resetStart: Boolean) {
        if (usageWindowStartMs <= 0L) return
        val now = SystemClock.elapsedRealtime()
        val deltaSec = ((now - usageWindowStartMs) / 1000L).coerceAtLeast(0L)
        usageWindowStartMs = if (resetStart) now else 0L
        if (deltaSec >= 2L && ::cloud.isInitialized && cloud.currentUser() != null) {
            lifecycleScope.launch { cloud.recordUsageSeconds(deltaSec) }
        }
    }

    private fun stopUsageTracking() {
        usageFlushRunnable?.let { handler.removeCallbacks(it) }
        usageFlushRunnable = null
        flushUsageSeconds(resetStart = false)
    }

    private fun handleSignOut() {
        stopUsageTracking()
        cloud.signOut(lifecycleScope) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    billingAds.syncCloudRewards(false, 0L)
                    billingAds.applyRemoteAdsEnabled(true)
                    showFlow(Flow.AUTH)
                }
            }
        }
    }

    private fun stopSessionTimer() {
        sessionTimerRunnable?.let { handler.removeCallbacks(it) }
        sessionTimerRunnable = null
    }

    private fun startSessionTimer(targetTv: TextView) {
        stopSessionTimer()
        sessionSeconds = 0
        fun fmt(sec: Int): String = String.format("%02d:%02d", sec / 60, sec % 60)
        targetTv.text = fmt(0)
        val r = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed || (flow != Flow.CAMERA && flow != Flow.LIGHTBOX)) return
                sessionSeconds++
                targetTv.text = fmt(sessionSeconds)
                handler.postDelayed(this, 1000L)
            }
        }
        sessionTimerRunnable = r
        handler.postDelayed(r, 1000L)
    }

    private fun showFlow(target: Flow) {
        if (!cloud.isAdminUser && target != Flow.AUTH && target != Flow.RESTRICTED && target != Flow.SPLASH) {
            val cfg = cloud.globalConfig
            if (target == Flow.CAMERA && !cfg.arCameraEnabled) {
                Toast.makeText(this, L.t("feature_disabled_admin"), Toast.LENGTH_LONG).show()
                return
            }
            if (target == Flow.LIGHTBOX && !cfg.lightboxEnabled) {
                Toast.makeText(this, L.t("feature_disabled_admin"), Toast.LENGTH_LONG).show()
                return
            }
            if (target == Flow.BROWSER && !cfg.bingSearchEnabled) {
                Toast.makeText(this, L.t("feature_disabled_admin"), Toast.LENGTH_LONG).show()
                return
            }
        }
        applyInsetsCallback = null
        if (flow == Flow.CAMERA && target != Flow.CAMERA) stopCamera()
        if (flow == Flow.LIGHTBOX && target != Flow.LIGHTBOX) {
            isLightboxLocked = false
            restoreWindowDefaults()
        }
        if ((flow == Flow.CAMERA || flow == Flow.LIGHTBOX) && target != Flow.CAMERA && target != Flow.LIGHTBOX) {
            stopSessionTimer()
        }
        if (flow == Flow.ADMIN && target != Flow.ADMIN) cloud.stopAdminUsersListener()
        if (flow == Flow.BROWSER && target != Flow.BROWSER) destroyWebView()
        flow = target
        if (target != Flow.SPLASH && target != Flow.AUTH && cloud.currentUser() != null) {
            lifecycleScope.launch { cloud.updateActiveScreen(target.name, L.lang) }
        }
        when (target) {
            Flow.AUTH -> renderAuthGate()
            Flow.RESTRICTED -> renderRestrictedGate()
            Flow.ADMIN -> renderAdminPanel()
            Flow.MAIN -> renderMain()
            Flow.LANG -> renderLanguagePage()
            Flow.PRIVACY -> renderPrivacyPage()
            Flow.ANIME_CHARS -> renderAnimeCharsPage()
            Flow.CHAR_GALLERY -> renderCharacterGallery()
            Flow.BROWSER -> renderBrowser()
            Flow.EDITOR -> renderEditor()
            Flow.CAMERA -> renderCamera()
            Flow.LIGHTBOX -> renderLightbox()
            Flow.CONGRATS -> renderCongrats()
            Flow.SPLASH -> showSplash()
        }
    }

    private fun renderAuthGate() {
        applyInsetsCallback = null
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews()
        val view = AdminPanelUi.buildAuthGateView(
            act = this,
            safeTopPx = safeTop(),
            safeBottomPx = safeBottom(),
            cloud = cloud,
            scope = lifecycleScope,
            onSignedIn = { onAuthenticatedReady() }
        )
        root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            view.setPadding(dp(22), safeTop() + dp(20), dp(22), safeBottom() + dp(20))
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderRestrictedGate() {
        applyInsetsCallback = null
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews()
        val isBlocked = cloud.currentUserRecord?.blocked == true
        val title = if (isBlocked) L.t("blocked_title") else L.t("maintenance_title")
        val msg = if (isBlocked) {
            L.t("blocked_message")
        } else {
            cloud.globalConfig.maintenanceMessage.ifBlank { L.t("maintenance_title") }
        }
        val view = AdminPanelUi.buildRestrictedGateView(
            act = this,
            safeTopPx = safeTop(),
            safeBottomPx = safeBottom(),
            isBlocked = isBlocked,
            title = title,
            message = msg,
            email = cloud.currentUser()?.email ?: "",
            onSignOut = { handleSignOut() }
        )
        root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            view.setPadding(dp(24), safeTop() + dp(20), dp(24), safeBottom() + dp(20))
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderAdminPanel() {
        if (!cloud.isAdminUser) {
            showFlow(Flow.MAIN)
            return
        }
        applyInsetsCallback = null
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews()
        val view = AdminPanelUi.buildAdminPanel(
            act = this,
            safeTopPx = safeTop(),
            safeBottomPx = safeBottom(),
            cloud = cloud,
            scope = lifecycleScope,
            onBack = { showFlow(Flow.MAIN) }
        )
        root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            view.setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(10))
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun openDirectBingForCharacter(character: String, series: String, returnFlow: Flow) {
        val subject = SketchSearchRepository.cleanSearchQuery(character, series)
        browserQuery = listOf(subject, "black and white sketch drawing")
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        browserReturnFlow = returnFlow
        showFlow(Flow.BROWSER)
    }

    private fun restoreWindowDefaults() {
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = -1f }
        } catch (_: Throwable) {}
    }

    private fun renderMain() {
        applyInsetsCallback = null
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val mainCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Branded Studio Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiTheme.surface)
            setPadding(dp(16), safeTop() + dp(10), dp(16), dp(12))
            elevation = dp(5).toFloat()
        }
        val topLogoBox = FrameLayout(this).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(10).toFloat()) }
            }
            clipToOutline = true
            UiTheme.applyColoredShadow(this, Color.parseColor("#7C3AED"), 4)
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.app_logo)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = L.t("app")
                },
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            )
        }
        topBar.addView(
            topLogoBox,
            LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) }
        )
        val titleStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleStack.addView(TextView(this).apply {
            text = when (currentTab) {
                Tab.HOME -> L.t("app_short")
                Tab.GALLERY -> L.t("my_activity")
                Tab.STORE -> L.t("store_title")
                Tab.SETTINGS -> L.t("settings")
            }
            textSize = 17.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            maxLines = 1
        })
        if (currentTab == Tab.HOME) {
            titleStack.addView(TextView(this).apply {
                text = L.t("app_sub")
                textSize = 10.5f
                UiTheme.applyUiFont(this, bold = true)
                setTextColor(UiTheme.accentBright)
                maxLines = 1
            })
        }
        topBar.addView(titleStack, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

        if (cloud.isAdminUser) {
            val adminPill = StudioIcons.iconPill(
                context = this,
                iconType = StudioIcons.Type.SHIELD_LOCK,
                label = L.t("admin_badge"),
                textColor = Color.WHITE,
                iconColor = Color.WHITE,
                iconSizeDp = 12,
                textSizeSp = 10f
            ).apply {
                background = UiTheme.premiumGrad(this@MainActivity, "#312E81", "#7C3AED", 12)
                UiTheme.applyColoredShadow(this, Color.parseColor("#7C3AED"), 5)
                setPadding(dp(9), dp(4), dp(9), dp(4))
                UiTheme.bounce(this) { showFlow(Flow.ADMIN) }
            }
            topBar.addView(adminPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(30)).apply { marginEnd = dp(6) })
        }

        if (billingAds.adsRemoved) {
            val shieldPill = StudioIcons.iconPill(
                context = this,
                iconType = if (billingAds.permanentAdsRemoved) StudioIcons.Type.CROWN else StudioIcons.Type.SHIELD,
                label = if (billingAds.permanentAdsRemoved) "VIP" else "PRO",
                textColor = Color.WHITE,
                iconColor = Color.WHITE,
                iconSizeDp = 12,
                textSizeSp = 10.5f,
                filled = billingAds.permanentAdsRemoved
            ).apply {
                background = UiTheme.premiumGrad(this@MainActivity, "#10B981", "#059669", 12)
                UiTheme.applyColoredShadow(this, Color.parseColor("#10B981"), 5)
                setPadding(dp(9), dp(4), dp(9), dp(4))
                UiTheme.bounce(this) { currentTab = Tab.STORE; renderMain() }
            }
            topBar.addView(shieldPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(30)).apply { marginEnd = dp(6) })
        }

        val coinWalletPill = StudioIcons.iconPill(
            context = this,
            iconType = StudioIcons.Type.COIN,
            label = "${billingAds.coins}",
            textColor = UiTheme.textMain,
            iconColor = Color.WHITE,
            iconSizeDp = 17,
            textSizeSp = 13.5f,
            trailingIcon = StudioIcons.Type.PLUS
        ).apply {
            background = UiTheme.premiumSurface(this@MainActivity, UiTheme.accentSoft, 18, UiTheme.accentBright, 1)
            elevation = dp(3).toFloat()
            setPadding(dp(11), dp(6), dp(11), dp(6))
            UiTheme.bounce(this) { currentTab = Tab.STORE; renderMain() }
        }
        topBar.addView(coinWalletPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)))
        mainCol.addView(topBar)
        mainCol.addView(View(this).apply { setBackgroundColor(UiTheme.stroke) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))

        val cfg = cloud.globalConfig
        val liveAnnouncement = if (cfg.announcementEnabled) cfg.announcementTitle to cfg.announcementText else null
        val body = when (currentTab) {
            Tab.HOME -> UiTabs.buildHome(
                this,
                { showCuteSourcePopup() },
                { openCamCapture() },
                { importToGalleryMode = false; openLightboxAfterPick = false; pickGalleryLauncher.launch("image/*") },
                { currentTab = Tab.GALLERY; renderMain() },
                { importToGalleryMode = false; openLightboxAfterPick = true; pickGalleryLauncher.launch("image/*") },
                { openEditorWithBitmap(it) },
                { q -> browserQuery = q; browserReturnFlow = Flow.MAIN; showFlow(Flow.BROWSER) },
                { series -> selectedSeries = series; showFlow(Flow.ANIME_CHARS) },
                { ch, series -> openDirectBingForCharacter(ch, series, Flow.MAIN) },
                { renderMain() },
                announcement = liveAnnouncement
            )
            Tab.GALLERY -> UiTabs.buildGallery(this, galleryDir, prefs, { showCuteImportPopup() }, { exportZipFiles(it) }, { openEditorWithBitmap(it) }, { renderMain() })
            Tab.STORE -> UiTabs.buildStore(this, billingAds)
            Tab.SETTINGS -> UiTabs.buildSettings(
                act = this,
                prefs = prefs,
                onOpenLanguagePage = { showFlow(Flow.LANG) },
                onOpenPrivacyPage = { showFlow(Flow.PRIVACY) },
                onRefresh = { renderMain() },
                userEmail = cloud.currentUser()?.email,
                userName = cloud.currentUser()?.displayName,
                isAdmin = cloud.isAdminUser,
                isVip = billingAds.permanentAdsRemoved,
                onOpenAdminPanel = if (cloud.isAdminUser) ({ showFlow(Flow.ADMIN) }) else null,
                onSignOut = { handleSignOut() }
            )
        }
        UiTheme.animatePageIn(body, 12, 220L)
        mainCol.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        if (!billingAds.adsRemoved) {
            val adBox = FrameLayout(this).apply { setBackgroundColor(UiTheme.bg) }
            billingAds.attachBanner(adBox)
            mainCol.addView(adBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        // Floating Capsule Studio Dock with Crisp Vector Icons
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 26, UiTheme.strokeStrong, 1)
            setPadding(dp(8), dp(7), dp(8), dp(7))
            elevation = dp(14).toFloat()
        }
        val navTabs = buildList {
            add(Triple(Tab.HOME, StudioIcons.Type.HOME, L.t("home")))
            add(Triple(Tab.GALLERY, StudioIcons.Type.GALLERY, L.t("my_gallery")))
            if (cfg.storeEnabled || cloud.isAdminUser) {
                add(Triple(Tab.STORE, StudioIcons.Type.STORE, L.t("store")))
            }
            add(Triple(Tab.SETTINGS, StudioIcons.Type.SETTINGS, L.t("settings")))
        }
        navTabs.forEach { (tab, icon, label) ->
            val sel = currentTab == tab
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                background = if (sel) {
                    UiTheme.premiumGrad(this@MainActivity, "#7C3AED", "#4F46E5", 19)
                } else {
                    UiTheme.premiumSurface(this@MainActivity, Color.TRANSPARENT, 19, null)
                }
                if (sel) UiTheme.applyColoredShadow(this, Color.parseColor("#7C3AED"), 6)
                else elevation = 0f
                addView(
                    StudioIcons.imageView(
                        this@MainActivity,
                        icon,
                        20,
                        if (sel) Color.WHITE else UiTheme.textSub,
                        filled = sel,
                        secondaryTint = if (sel) Color.parseColor("#44FFFFFF") else null
                    ),
                    LinearLayout.LayoutParams(dp(20), dp(20)).apply { bottomMargin = dp(3) }
                )
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    UiTheme.applyUiFont(this, bold = sel)
                    setTextColor(if (sel) Color.WHITE else UiTheme.textSub)
                })
                UiTheme.bounce(this) { currentTab = tab; renderMain() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        val navLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(dp(12), dp(6), dp(12), safeBottom())
        }
        mainCol.addView(nav, navLp)
        root.addView(mainCol, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            topBar.setPadding(dp(16), safeTop() + dp(10), dp(16), dp(12))
            navLp.setMargins(dp(12), dp(6), dp(12), safeBottom())
            nav.layoutParams = navLp
        }
        ViewCompat.requestApplyInsets(root)
    }

    // ---------------- ANIME CHARACTERS PAGE (BUILT-IN SKETCHES + CHARACTER SEARCH) ----------------
    private fun renderAnimeCharsPage() {
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(8))
        }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        topRow.addView(
            UiTheme.studioIconBtn(
                act = this,
                icon = StudioIcons.Type.ARROW_LEFT,
                sizeDp = 44,
                iconSizeDp = 20,
                fillColor = UiTheme.surface,
                strokeColor = UiTheme.stroke,
                iconTint = UiTheme.textMain,
                contentDesc = L.t("back")
            ) { showFlow(Flow.MAIN) },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        )
        topRow.addView(TextView(this).apply {
            text = selectedSeries.name
            textSize = 20f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topRow.addView(TextView(this).apply {
            text = "${selectedSeries.characters.size}"
            fontFeatureSettings = "tnum"
            textSize = 12f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.accentBright)
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 10, UiTheme.stroke)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        main.addView(topRow)

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, UiTheme.accentBright)
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        searchWrap.addView(
            StudioIcons.imageView(this, StudioIcons.Type.SEARCH, 17, UiTheme.textSub),
            LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(8) }
        )
        val searchBox = EditText(this).apply {
            hint = L.t("filter_chars_hint").replace("{name}", selectedSeries.name)
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textMain)
            setHintTextColor(UiTheme.textSub)
            background = null
            setPadding(0, dp(9), 0, dp(9))
            setSingleLine(true)
        }
        searchWrap.addView(searchBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        main.addView(searchWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun fill(filter: String) {
            listCol.removeAllViews()
            val q = AnimeCatalog.normalizeQuery(filter).trim().lowercase()

            val chars = if (q.isEmpty()) selectedSeries.characters
            else selectedSeries.characters.filter { it.lowercase().startsWith(q) } +
                selectedSeries.characters.filter { !it.lowercase().startsWith(q) && it.lowercase().contains(q) }

            chars.forEachIndexed { cIdx, ch ->
                val (c1, c2) = UiTheme.seriesPalette(cIdx)
                val initials = ch.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                val bundled = Samples.byCharacter(ch, selectedSeries.name)
                val thumbBmp = bundled.firstOrNull()?.let { Samples.loadThumb(this, it, 160) }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.premiumSurface(
                        this@MainActivity,
                        UiTheme.surface,
                        16,
                        if (bundled.isNotEmpty()) UiTheme.accentBright else UiTheme.stroke
                    )
                    setPadding(dp(12), dp(10), dp(14), dp(10))
                    elevation = dp(3).toFloat()
                    if (thumbBmp != null) {
                        val thumbFrame = FrameLayout(this@MainActivity).apply {
                            background = UiTheme.round(this@MainActivity, Color.WHITE, 11, UiTheme.stroke)
                            setPadding(dp(3), dp(3), dp(3), dp(3))
                            addView(ImageView(this@MainActivity).apply {
                                setImageBitmap(thumbBmp)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                        }
                        addView(thumbFrame, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
                    } else {
                        addView(
                            UiTheme.monogramBadge(this@MainActivity, initials.ifEmpty { "A" }, c1, c2, sizeDp = 40, rDp = 11, textSizeSp = 12.5f),
                            LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
                        )
                    }
                    val nameCol = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                    nameCol.addView(TextView(this@MainActivity).apply {
                        text = ch
                        textSize = 14.5f
                        UiTheme.applyDisplayFont(this, bold = true)
                        setTextColor(UiTheme.textMain)
                    })
                    nameCol.addView(TextView(this@MainActivity).apply {
                        text = selectedSeries.name
                        textSize = 11f
                        UiTheme.applyUiFont(this, bold = false)
                        setTextColor(UiTheme.textSub)
                    })
                    addView(nameCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

                    addView(
                        StudioIcons.iconPill(
                            context = this@MainActivity,
                            iconType = StudioIcons.Type.GLOBE,
                            label = L.t("bing_pill"),
                            textColor = Color.WHITE,
                            iconColor = Color.WHITE,
                            iconSizeDp = 12,
                            textSizeSp = 11.5f,
                            trailingIcon = StudioIcons.Type.ARROW_RIGHT
                        ).apply {
                            background = UiTheme.premiumGrad(this@MainActivity, "#0EA5E9", "#2563EB", 10)
                            UiTheme.applyColoredShadow(this, Color.parseColor("#0EA5E9"), 4)
                            setPadding(dp(10), dp(6), dp(10), dp(6))
                        }
                    )
                    UiTheme.bounce(this) {
                        openDirectBingForCharacter(ch, selectedSeries.name, Flow.ANIME_CHARS)
                    }
                }
                listCol.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }

            if (chars.isEmpty() && filter.trim().isNotEmpty()) {
                val customChar = AnimeCatalog.normalizeQuery(filter.trim()).ifEmpty { filter.trim() }
                val customRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, UiTheme.accentBright)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    addView(
                        StudioIcons.imageView(this@MainActivity, StudioIcons.Type.GLOBE, 18, UiTheme.accentBright),
                        LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) }
                    )
                    addView(TextView(this@MainActivity).apply {
                        text = "$customChar • ${selectedSeries.name}"
                        textSize = 14f
                        UiTheme.applyDisplayFont(this, bold = true)
                        setTextColor(UiTheme.textMain)
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(
                        StudioIcons.iconPill(
                            context = this@MainActivity,
                            iconType = StudioIcons.Type.GLOBE,
                            label = L.t("bing_pill"),
                            textColor = Color.WHITE,
                            iconColor = Color.WHITE,
                            iconSizeDp = 12,
                            textSizeSp = 11.5f,
                            trailingIcon = StudioIcons.Type.ARROW_RIGHT
                        ).apply {
                            background = UiTheme.grad(this@MainActivity, "#0EA5E9", "#2563EB", 10)
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                        }
                    )
                    UiTheme.bounce(this) {
                        openDirectBingForCharacter(customChar, selectedSeries.name, Flow.ANIME_CHARS)
                    }
                }
                listCol.addView(customRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
        }
        fill("")
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { fill(s?.toString() ?: "") }
        })

        main.addView(ScrollView(this).apply { addView(listCol) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        UiTheme.animatePageIn(main, 12, 220L)
        root.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            main.setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(8))
        }
        ViewCompat.requestApplyInsets(root)
    }

    // ---------------- CHARACTER 5 B&W SKETCHES GALLERY + MORE PHOTOS ----------------
    private fun renderCharacterGallery() {
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(10))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        topRow.addView(
            UiTheme.studioIconBtn(
                act = this,
                icon = StudioIcons.Type.ARROW_LEFT,
                sizeDp = 44,
                iconSizeDp = 20,
                fillColor = UiTheme.surface,
                strokeColor = UiTheme.stroke,
                iconTint = UiTheme.textMain,
                contentDesc = L.t("back")
            ) { showFlow(charGalleryReturnFlow) },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        )

        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = selectedCharacterName.ifEmpty { "Anime Sketch" }
            textSize = 18.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            maxLines = 1
        })
        if (selectedCharacterSeries.isNotEmpty()) {
            titleCol.addView(TextView(this).apply {
                text = selectedCharacterSeries
                textSize = 11.5f
                UiTheme.applyUiFont(this, bold = true)
                setTextColor(UiTheme.accentBright)
                maxLines = 1
            })
        }
        topRow.addView(titleCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

        topRow.addView(TextView(this).apply {
            text = "5"
            fontFeatureSettings = "tnum"
            textSize = 12.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.accentBright)
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 10, UiTheme.stroke)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })

        val bodyCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cardW = (resources.displayMetrics.widthPixels - dp(44)) / 2

        fun openMorePhotosBrowser() {
            val subject = SketchSearchRepository.cleanSearchQuery(selectedCharacterName, selectedCharacterSeries)
            browserQuery = listOf(subject, "black and white sketch drawing")
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            browserReturnFlow = Flow.CHAR_GALLERY
            showFlow(Flow.BROWSER)
        }

        fun buildMorePhotosBar(): View = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = UiTheme.grad(this@MainActivity, "#0EA5E9", "#2563EB", 18)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(5).toFloat()
            addView(
                StudioIcons.imageView(this@MainActivity, StudioIcons.Type.GLOBE, 18, Color.WHITE),
                LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) }
            )
            addView(TextView(this@MainActivity).apply {
                text = L.t("more_photos")
                textSize = 15f
                UiTheme.applyDisplayFont(this, true)
                setTextColor(Color.WHITE)
            })
            addView(
                StudioIcons.imageView(this@MainActivity, StudioIcons.Type.ARROW_RIGHT, 16, Color.WHITE),
                LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(10) }
            )
            UiTheme.bounce(this) { openMorePhotosBrowser() }
        }

        fun buildSketchCard(item: SketchSearchRepository.SketchResult, index: Int, fullWidth: Boolean): View {
            val imgH = if (fullWidth) dp(215) else cardW + dp(16)
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTheme.round(this@MainActivity, UiTheme.surface, 18, UiTheme.stroke)
                setPadding(dp(8), dp(8), dp(8), dp(10))
                elevation = dp(4).toFloat()

                val imgBox = FrameLayout(this@MainActivity).apply {
                    background = UiTheme.round(this@MainActivity, Color.WHITE, 14, UiTheme.stroke)
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    val iv = ImageView(this@MainActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    addView(iv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                    val cachedBmp = SketchSearchRepository.getCachedThumbSync(this@MainActivity, item)
                    if (cachedBmp != null) {
                        iv.setImageBitmap(cachedBmp)
                    } else {
                        val spinner = ProgressBar(this@MainActivity).apply { isIndeterminate = true }
                        addView(spinner, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                        SketchSearchRepository.loadThumbBitmap(this@MainActivity, item) { bmp ->
                            spinner.visibility = View.GONE
                            if (bmp != null) {
                                iv.alpha = 0f
                                iv.setImageBitmap(bmp)
                                iv.animate().alpha(1f).setDuration(140).start()
                            }
                        }
                    }

                    addView(TextView(this@MainActivity).apply {
                        text = item.mood
                        textSize = 10f
                        UiTheme.applyUiFont(this, bold = true)
                        setTextColor(UiTheme.accentBright)
                        background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 8)
                        setPadding(dp(7), dp(2), dp(7), dp(2))
                    }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START))

                    addView(TextView(this@MainActivity).apply {
                        text = "#${index + 1}"
                        fontFeatureSettings = "tnum"
                        textSize = 10f
                        UiTheme.applyDisplayFont(this, true)
                        setTextColor(Color.WHITE)
                        background = UiTheme.round(this@MainActivity, Color.parseColor("#B0111827"), 8)
                        setPadding(dp(7), dp(2), dp(7), dp(2))
                    }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END))
                }
                addView(imgBox, LinearLayout.LayoutParams(MATCH_PARENT, imgH))

                val footRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(3), dp(8), dp(3), 0)
                }
                footRow.addView(TextView(this@MainActivity).apply {
                    text = if (item.localSample != null) item.title else "${item.title} • ${item.mood}"
                    textSize = 12.5f
                    UiTheme.applyDisplayFont(this, true)
                    setTextColor(UiTheme.textMain)
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })

                val tracePill = StudioIcons.iconPill(
                    context = this@MainActivity,
                    iconType = StudioIcons.Type.ARROW_RIGHT,
                    label = L.t("trace_now"),
                    textColor = Color.WHITE,
                    iconColor = Color.WHITE,
                    iconSizeDp = 12,
                    textSizeSp = 10.5f
                ).apply {
                    background = UiTheme.grad(this@MainActivity, "#8B5CF6", "#6D28D9", 10)
                    setPadding(dp(9), dp(4), dp(9), dp(4))
                }
                footRow.addView(tracePill)
                addView(footRow)

                var loadingClick = false
                UiTheme.bounce(this) {
                    if (loadingClick) return@bounce
                    val local = item.localSample
                    if (local != null) {
                        Samples.loadFull(this@MainActivity, local)?.let { openEditorWithBitmap(it) }
                    } else {
                        loadingClick = true
                        Toast.makeText(this@MainActivity, L.t("saving_image"), Toast.LENGTH_SHORT).show()
                        SketchSearchRepository.loadFullForEditor(this@MainActivity, item) { bmp ->
                            loadingClick = false
                            if (isFinishing || isDestroyed) return@loadFullForEditor
                            if (bmp != null) openEditorWithBitmap(bmp)
                            else Toast.makeText(this@MainActivity, L.t("no_sketches_found"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        fun loadGallery(forceRefresh: Boolean) {
            fun renderResults(results: List<SketchSearchRepository.SketchResult>) {
                bodyCol.removeAllViews()
                val topFive = results.take(5)
                if (topFive.isEmpty()) {
                    val emptyCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        background = UiTheme.round(this@MainActivity, UiTheme.surface, 18, UiTheme.stroke)
                        setPadding(dp(20), dp(28), dp(20), dp(24))
                        addView(TextView(this@MainActivity).apply {
                            text = L.t("no_sketches_found")
                            textSize = 13.5f
                            UiTheme.applyUiFont(this)
                            setTextColor(UiTheme.textSub)
                            gravity = Gravity.CENTER
                            setPadding(0, 0, 0, dp(14))
                        })
                        val retryBtn = StudioIcons.iconPill(
                            context = this@MainActivity,
                            iconType = StudioIcons.Type.RESTORE,
                            label = L.t("retry_search"),
                            textColor = Color.WHITE,
                            iconColor = Color.WHITE,
                            iconSizeDp = 14,
                            textSizeSp = 13f
                        ).apply {
                            background = UiTheme.grad(this@MainActivity, "#7C3AED", "#4F46E5", 12)
                            setPadding(dp(16), dp(9), dp(16), dp(9))
                            UiTheme.bounce(this) { loadGallery(forceRefresh = true) }
                        }
                        addView(retryBtn)
                    }
                    bodyCol.addView(emptyCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
                } else {
                    val pairCount = (topFive.size / 2) * 2
                    var idx = 0
                    while (idx < pairCount) {
                        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        row.addView(buildSketchCard(topFive[idx], idx, fullWidth = false), LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply {
                            marginEnd = dp(12)
                        })
                        row.addView(buildSketchCard(topFive[idx + 1], idx + 1, fullWidth = false), LinearLayout.LayoutParams(cardW, WRAP_CONTENT))
                        bodyCol.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
                        idx += 2
                    }
                    if (topFive.size % 2 == 1) {
                        val lastIdx = topFive.size - 1
                        bodyCol.addView(buildSketchCard(topFive[lastIdx], lastIdx, fullWidth = true), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                            bottomMargin = dp(14)
                        })
                    }
                }

                bodyCol.addView(buildMorePhotosBar(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = dp(2)
                    bottomMargin = dp(18)
                })
            }

            val targetChar = selectedCharacterName
            val targetSeries = selectedCharacterSeries
            if (!forceRefresh) {
                SketchSearchRepository.getCachedFiveSync(this, targetChar, targetSeries)?.let { cached ->
                    renderResults(cached)
                    return
                }
            }

            bodyCol.removeAllViews()
            val loadingCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = UiTheme.round(this@MainActivity, UiTheme.surface, 18, UiTheme.stroke)
                setPadding(dp(20), dp(34), dp(20), dp(34))
                addView(ProgressBar(this@MainActivity).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { bottomMargin = dp(12) })
                addView(TextView(this@MainActivity).apply {
                    text = L.t("searching_sketches")
                    textSize = 13.5f
                    UiTheme.applyUiFont(this, bold = true)
                    setTextColor(UiTheme.textSub)
                    gravity = Gravity.CENTER
                })
            }
            bodyCol.addView(loadingCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
            bodyCol.addView(buildMorePhotosBar(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

            SketchSearchRepository.getFiveSketches(targetChar, targetSeries, forceRefresh) { results ->
                if (flow != Flow.CHAR_GALLERY || isFinishing || isDestroyed) return@getFiveSketches
                if (selectedCharacterName != targetChar || selectedCharacterSeries != targetSeries) return@getFiveSketches
                renderResults(results)
            }
        }

        topRow.addView(
            UiTheme.studioIconBtn(
                act = this,
                icon = StudioIcons.Type.RESTORE,
                sizeDp = 42,
                iconSizeDp = 19,
                fillColor = UiTheme.surface,
                strokeColor = UiTheme.stroke,
                iconTint = UiTheme.accentBright,
                contentDesc = L.t("retry_search")
            ) { loadGallery(forceRefresh = true) },
            LinearLayout.LayoutParams(dp(42), dp(42))
        )
        main.addView(topRow)

        val badgeRow = StudioIcons.iconPill(
            context = this,
            iconType = StudioIcons.Type.SEARCH,
            label = L.t("bw_sketch_badge"),
            textColor = UiTheme.accentBright,
            iconColor = UiTheme.accentBright,
            iconSizeDp = 13,
            textSizeSp = 11.5f
        ).apply {
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 10, UiTheme.stroke)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        main.addView(badgeRow, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        main.addView(ScrollView(this).apply { addView(bodyCol) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            main.setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(10))
        }
        ViewCompat.requestApplyInsets(root)
        loadGallery(forceRefresh = false)
    }

    private fun buildBingImagesUrl(query: String): String {
        val q = query.trim().ifEmpty { "Anime black and white sketch drawing" }
        val isSketch = q.contains("black and white", ignoreCase = true) ||
            q.contains("sketch", ignoreCase = true) ||
            q.contains("drawing", ignoreCase = true) ||
            q.contains("line art", ignoreCase = true)
        val qft = if (isSketch) "&qft=${Uri.encode("+filterui:color2-bw+filterui:photo-linedrawing")}" else ""
        return "https://www.bing.com/images/search?q=${Uri.encode(q)}$qft"
    }

    private fun openInChrome(query: String) {
        val bingUrl = buildBingImagesUrl(query)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bingUrl)).apply {
                setPackage("com.android.chrome")
            })
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bingUrl)))
            } catch (_: Throwable) {}
        }
    }

    // ---------------- MICROSOFT BING IMAGES IN-APP BROWSER ----------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun renderBrowser() {
        updateSystemBarsAppearance(false)
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(this@MainActivity, "#0EA5E9", "#2563EB", 0)
            setPadding(dp(10), safeTop() + dp(10), dp(10), dp(10))
        }
        topBar.addView(
            UiTheme.studioIconBtn(
                act = this,
                icon = StudioIcons.Type.ARROW_LEFT,
                sizeDp = 42,
                iconSizeDp = 20,
                rDp = 12,
                fillColor = Color.parseColor("#2AFFFFFF"),
                strokeColor = null,
                iconTint = Color.WHITE,
                contentDesc = L.t("back")
            ) { destroyWebView(); showFlow(browserReturnFlow) },
            LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(8) }
        )
        val barInput = EditText(this).apply {
            setText(browserQuery)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#DBEAFE"))
            textSize = 13f
            background = UiTheme.round(this@MainActivity, Color.parseColor("#33FFFFFF"), 14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setSingleLine(true)
        }
        topBar.addView(barInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

        val wv = WebView(this)
        fun submitBrowserSearch() {
            val q = barInput.text.toString().trim()
            if (q.isNotEmpty()) {
                browserQuery = q
                wv.loadUrl(buildBingImagesUrl(q))
                try {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(barInput.windowToken, 0)
                } catch (_: Throwable) {}
                barInput.clearFocus()
            }
        }
        barInput.imeOptions = EditorInfo.IME_ACTION_SEARCH
        barInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submitBrowserSearch()
                true
            } else false
        }
        topBar.addView(
            UiTheme.studioIconBtn(
                act = this,
                icon = StudioIcons.Type.SEARCH,
                sizeDp = 42,
                iconSizeDp = 19,
                rDp = 12,
                fillColor = Color.parseColor("#2AFFFFFF"),
                strokeColor = null,
                iconTint = Color.WHITE,
                contentDesc = L.t("search_btn")
            ) { submitBrowserSearch() },
            LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(6) }
        )
        topBar.addView(
            StudioIcons.iconPill(
                context = this,
                iconType = StudioIcons.Type.GLOBE,
                label = L.t("open_chrome"),
                textColor = Color.WHITE,
                iconColor = Color.WHITE,
                iconSizeDp = 14,
                textSizeSp = 11.5f
            ).apply {
                background = UiTheme.round(this@MainActivity, Color.parseColor("#2AFFFFFF"), 12)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                UiTheme.bounce(this) {
                    val q = barInput.text.toString().trim().ifEmpty { browserQuery }
                    openInChrome(q)
                }
            },
            LinearLayout.LayoutParams(WRAP_CONTENT, dp(42))
        )
        col.addView(topBar)

        col.addView(TextView(this).apply {
            text = L.t("bing_banner")
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E40AF"))
            setPadding(0, dp(6), 0, dp(6))
        })

        val webProgress = StudioIcons.StudioProgressView(this).apply {
            progress = 15
            visibility = View.VISIBLE
        }
        col.addView(webProgress, LinearLayout.LayoutParams(MATCH_PARENT, dp(3)))

        wv.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onImageTapped(fullUrl: String, thumbUrl: String) {
                    saveOnlineImageAndDraw(fullUrl, thumbUrl)
                }
            }, "AndroidBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    webProgress.progress = newProgress
                    webProgress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    webProgress.visibility = View.GONE
                    val js = """
                        (function() {
                            if (window.__bingArtHook) return;
                            window.__bingArtHook = true;
                            document.addEventListener('click', function(e) {
                                var el = e.target;
                                var link = el && el.closest ? el.closest('a.iusc') : null;
                                var gLink = el && el.closest ? el.closest('a[href*="imgurl="]') : null;
                                var img = (el && el.tagName === 'IMG') ? el : (el && el.closest ? el.closest('a,div')?.querySelector('img') : null);
                                var fullUrl = '';
                                if (link) {
                                    try {
                                        var m = JSON.parse(link.getAttribute('m') || '{}');
                                        if (m.murl) fullUrl = m.murl;
                                    } catch(err) {}
                                } else if (gLink) {
                                    try {
                                        var u = new URL(gLink.href);
                                        fullUrl = u.searchParams.get('imgurl') || '';
                                    } catch(err) {}
                                }
                                var thumbUrl = (img && (img.getAttribute('data-src') || img.getAttribute('data-iurl') || img.currentSrc || img.src)) || '';
                                if ((fullUrl || thumbUrl) && (!img || (img.naturalWidth > 55 && img.naturalHeight > 55))) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    AndroidBridge.onImageTapped(fullUrl, thumbUrl);
                                }
                            }, true);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }
            loadUrl(buildBingImagesUrl(browserQuery))
        }
        activeWebView = wv
        col.addView(wv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            topBar.setPadding(dp(10), safeTop() + dp(10), dp(10), dp(10))
            col.setPadding(0, 0, 0, insetBottom)
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun decodeBytesBounded(bytes: ByteArray, maxDimPx: Int = 1440): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / (sample * 2) >= maxDimPx) sample *= 2
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val curMax = maxOf(raw.width, raw.height)
        return if (curMax > maxDimPx) {
            val s = maxDimPx.toFloat() / curMax
            Bitmap.createScaledBitmap(raw, (raw.width * s).toInt().coerceAtLeast(1), (raw.height * s).toInt().coerceAtLeast(1), true)
        } else raw
    }

    private fun decodeRemoteImage(src: String): Bitmap? {
        if (src.isEmpty()) return null
        return try {
            if (src.startsWith("data:image")) {
                val bytes = Base64.decode(src.substringAfter("base64,", ""), Base64.DEFAULT)
                decodeBytesBounded(bytes)
            } else if (src.startsWith("http")) {
                val conn = URL(src).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000; conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bytes = conn.inputStream.use { it.readBytes() }
                decodeBytesBounded(bytes)
            } else null
        } catch (_: Throwable) { null }
    }

    private fun saveOnlineImageAndDraw(fullUrl: String, thumbUrl: String) {
        if (isDownloadingOnlineImage) return
        isDownloadingOnlineImage = true
        runOnUiThread { Toast.makeText(this, L.t("saving_image"), Toast.LENGTH_SHORT).show() }
        ioExecutor.execute {
            try {
                val bmp = decodeRemoteImage(fullUrl) ?: decodeRemoteImage(thumbUrl)
                if (bmp != null) {
                    val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
                    val clean = if (ImageProcessor.isLikelyBwDrawing(argb)) {
                        ImageProcessor.toCleanBwSketch(argb)
                    } else {
                        argb
                    }
                    saveToGallery(clean, "Bing")
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && flow == Flow.BROWSER) {
                            destroyWebView()
                            openEditorWithBitmap(clean)
                        }
                    }
                } else {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) Toast.makeText(this, L.t("import_fail"), Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isDownloadingOnlineImage = false
            }
        }
    }

    private fun destroyWebView() {
        isDownloadingOnlineImage = false
        activeWebView?.destroy()
        activeWebView = null
    }

    private fun showCuteSourcePopup() {
        val backdrop = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#99040712")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 28, UiTheme.strokeStrong, 1)
            setPadding(dp(20), dp(22), dp(20), dp(20))
            elevation = dp(18).toFloat()
        }
        var dismissed = false
        fun dismiss(after: (() -> Unit)? = null) {
            if (dismissed) return
            dismissed = true
            activePopupDismiss = null
            UiTheme.animateModalOut(backdrop, card) {
                root.removeView(backdrop)
                after?.invoke()
            }
        }
        activePopupDismiss = { dismiss() }
        card.addView(TextView(this).apply {
            text = L.t("choose_source")
            textSize = 19f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun optBtn(icon: StudioIcons.Type, title: String, c1: String, c2: String, onTap: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiTheme.premiumGrad(this@MainActivity, c1, c2, 22)
            setPadding(dp(12), dp(20), dp(12), dp(20))
            UiTheme.applyColoredShadow(this, Color.parseColor(c1), 9)
            val well = FrameLayout(this@MainActivity).apply {
                background = UiTheme.round(this@MainActivity, Color.parseColor("#2EFFFFFF"), 18, Color.parseColor("#44FFFFFF"))
                addView(
                    StudioIcons.imageView(this@MainActivity, icon, 28, Color.WHITE),
                    FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                )
            }
            addView(well, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 14f
                UiTheme.applyDisplayFont(this, true)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })
            UiTheme.bounce(this) { dismiss(onTap) }
        }
        row.addView(optBtn(StudioIcons.Type.CAMERA, L.t("take_photo"), "#8B5CF6", "#6D28D9") { openCamCapture() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
        row.addView(optBtn(StudioIcons.Type.PHOTO, L.t("upload_gallery"), "#EC4899", "#DB2777") { importToGalleryMode = false; openLightboxAfterPick = false; pickGalleryLauncher.launch("image/*") }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        card.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
        card.addView(
            UiTheme.studioSoftAction(
                act = this,
                icon = StudioIcons.Type.CLOSE,
                label = L.t("cancel"),
                textColor = UiTheme.textSub,
                iconColor = UiTheme.textSub,
                rDp = 15
            ) { dismiss() },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(48))
        )
        backdrop.setOnClickListener { dismiss() }; card.setOnClickListener { }
        backdrop.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply { setMargins(dp(24), 0, dp(24), 0) })
        root.addView(backdrop, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        UiTheme.animateModalIn(backdrop, card)
    }

    private fun showCuteImportPopup() {
        val backdrop = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#99040712")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 28, UiTheme.strokeStrong, 1)
            setPadding(dp(20), dp(22), dp(20), dp(20))
            elevation = dp(18).toFloat()
        }
        var dismissed = false
        fun dismiss(after: (() -> Unit)? = null) {
            if (dismissed) return
            dismissed = true
            activePopupDismiss = null
            UiTheme.animateModalOut(backdrop, card) {
                root.removeView(backdrop)
                after?.invoke()
            }
        }
        activePopupDismiss = { dismiss() }
        card.addView(TextView(this).apply {
            text = L.t("import_title")
            textSize = 19f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        card.addView(
            UiTheme.studioGradientAction(this, StudioIcons.Type.FOLDER, L.t("choose_file"), "#3B82F6", "#2563EB", 16) {
                dismiss { pickAnyFileLauncher.launch("*/*") }
            },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { bottomMargin = dp(10) }
        )
        card.addView(
            UiTheme.studioGradientAction(this, StudioIcons.Type.LINK, L.t("paste_link"), "#8B5CF6", "#6D28D9", 16) {
                dismiss { showLinkInputDialog() }
            },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { bottomMargin = dp(10) }
        )
        card.addView(
            UiTheme.studioSoftAction(
                act = this,
                icon = StudioIcons.Type.CLOSE,
                label = L.t("cancel"),
                textColor = UiTheme.textSub,
                iconColor = UiTheme.textSub,
                rDp = 15
            ) { dismiss() },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(48))
        )
        backdrop.setOnClickListener { dismiss() }; card.setOnClickListener { }
        backdrop.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply { setMargins(dp(24), 0, dp(24), 0) })
        root.addView(backdrop, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        UiTheme.animateModalIn(backdrop, card)
    }

    private fun showLinkInputDialog() {
        val inp = EditText(this).apply { hint = L.t("enter_url") }
        AlertDialog.Builder(this).setTitle(L.t("paste_link")).setView(inp)
            .setPositiveButton(L.t("import_photo")) { _, _ ->
                val urlStr = inp.text.toString().trim()
                if (urlStr.startsWith("http")) downloadFromLink(urlStr)
                else Toast.makeText(this, L.t("import_fail"), Toast.LENGTH_SHORT).show()
            }.setNegativeButton(L.t("cancel"), null).show()
    }

    private fun downloadFromLink(urlStr: String) {
        ioExecutor.execute {
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 15000
                conn.inputStream.use { importStream(it, urlStr.lowercase().contains(".zip")) }
                runOnUiThread { Toast.makeText(this, L.t("import_ok"), Toast.LENGTH_SHORT).show(); if (flow == Flow.MAIN) renderMain() }
            } catch (_: Exception) { runOnUiThread { Toast.makeText(this, L.t("import_fail"), Toast.LENGTH_LONG).show() } }
        }
    }

    private fun handleImportFileUri(uri: Uri) {
        ioExecutor.execute {
            try {
                val isZip = (contentResolver.getType(uri)?.lowercase()?.contains("zip") == true) || uri.toString().lowercase().endsWith(".zip")
                contentResolver.openInputStream(uri)?.use { importStream(it, isZip) }
                runOnUiThread { Toast.makeText(this, L.t("import_ok"), Toast.LENGTH_SHORT).show(); if (flow == Flow.MAIN) renderMain() }
            } catch (_: Exception) { runOnUiThread { Toast.makeText(this, L.t("import_fail"), Toast.LENGTH_LONG).show() } }
        }
    }

    private fun importStream(stream: InputStream, isZip: Boolean) {
        val bytes = stream.readBytes()
        if (isZip || (bytes.size > 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())) {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) { BitmapFactory.decodeStream(zis)?.let { saveToGallery(it, "ZIP") } }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
        } else {
            val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException()
            saveToGallery(b, "Imported")
        }
    }

    private fun exportZipFiles(files: List<File>) {
        ioExecutor.execute {
            try {
                val zipName = "FreeArt_${System.currentTimeMillis()}.zip"
                val cacheZip = File(cacheDir, zipName)
                val baos = ByteArrayOutputStream()
                ZipOutputStream(baos).use { zos ->
                    files.forEach { f -> zos.putNextEntry(ZipEntry(f.name)); FileInputStream(f).use { it.copyTo(zos) }; zos.closeEntry() }
                }
                val zipBytes = baos.toByteArray()
                FileOutputStream(cacheZip).use { it.write(zipBytes) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, zipName); put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)?.let { contentResolver.openOutputStream(it)?.use { os -> os.write(zipBytes) } }
                }
                val shareUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheZip)
                runOnUiThread {
                    UiTabs.selectedFiles.clear(); renderMain()
                    Toast.makeText(this, L.t("export_ok"), Toast.LENGTH_LONG).show()
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"; putExtra(Intent.EXTRA_STREAM, shareUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, L.t("export_zip")))
                }
            } catch (_: Exception) { runOnUiThread { Toast.makeText(this, L.t("import_fail"), Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun renderLanguagePage() {
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(8))
        }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        topRow.addView(
            UiTheme.studioIconBtn(
                act = this,
                icon = StudioIcons.Type.ARROW_LEFT,
                sizeDp = 44,
                iconSizeDp = 20,
                fillColor = UiTheme.surface,
                strokeColor = UiTheme.stroke,
                iconTint = UiTheme.textMain,
                contentDesc = L.t("back")
            ) { showFlow(Flow.MAIN) },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        )
        topRow.addView(TextView(this).apply {
            text = L.t("language")
            textSize = 20f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topRow.addView(TextView(this).apply {
            text = "${L.languages.size}"
            fontFeatureSettings = "tnum"
            textSize = 12f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.accentBright)
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 12, UiTheme.stroke)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        main.addView(topRow)

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, UiTheme.stroke)
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        searchWrap.addView(
            StudioIcons.imageView(this, StudioIcons.Type.SEARCH, 17, UiTheme.textSub),
            LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(8) }
        )
        val searchBox = EditText(this).apply {
            hint = "${L.t("language")}..."
            setTextColor(UiTheme.textMain)
            setHintTextColor(UiTheme.textSub)
            textSize = 14f
            background = null
            setPadding(0, dp(9), 0, dp(9))
            setSingleLine(true)
        }
        searchWrap.addView(searchBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        main.addView(searchWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16)) }
        fun fillLanguages(filter: String) {
            listCol.removeAllViews()
            val q = filter.trim().lowercase()
            val matches = if (q.isEmpty()) L.languages
            else L.languages.filter {
                it.label.lowercase().contains(q) || it.sub.lowercase().contains(q) || it.code.lowercase().contains(q) || it.badge.lowercase().contains(q)
            }
            matches.forEachIndexed { idx, info ->
                val sel = L.lang == info.code
                val (c1, c2) = UiTheme.seriesPalette(idx)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, if (sel) UiTheme.accentBright else UiTheme.stroke, if (sel) 2 else 1)
                    setPadding(dp(14), dp(12), dp(16), dp(12))
                    elevation = dp(2).toFloat()
                    addView(
                        UiTheme.monogramBadge(this@MainActivity, info.badge, c1, c2, sizeDp = 42, rDp = 12, textSizeSp = 13f),
                        LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) }
                    )
                    val tc = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                    tc.addView(TextView(this@MainActivity).apply {
                        text = info.label
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(UiTheme.textMain)
                    })
                    tc.addView(TextView(this@MainActivity).apply {
                        text = info.sub
                        textSize = 12f
                        setTextColor(UiTheme.textSub)
                    })
                    addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    if (sel) {
                        val checkCircle = FrameLayout(this@MainActivity).apply {
                            background = UiTheme.grad(this@MainActivity, "#8B5CF6", "#6D28D9", 14)
                            addView(
                                StudioIcons.imageView(this@MainActivity, StudioIcons.Type.CHECK, 15, Color.WHITE),
                                FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER)
                            )
                        }
                        addView(checkCircle, LinearLayout.LayoutParams(dp(28), dp(28)))
                    }
                    UiTheme.bounce(this) {
                        L.lang = info.code
                        prefs.edit().putString("lang", info.code).apply()
                        showFlow(Flow.MAIN)
                    }
                }
                listCol.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
            }
        }
        fillLanguages("")
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { fillLanguages(s?.toString() ?: "") }
        })

        main.addView(ScrollView(this).apply { addView(listCol) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            main.setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(8))
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderPrivacyPage() {
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews()
        root.setBackgroundColor(UiTheme.bg)
        val pv = PrivacyPolicyContent.buildView(this) { showFlow(Flow.MAIN) }
        pv.setPadding(0, safeTop(), 0, safeBottom())
        root.addView(pv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = { pv.setPadding(0, safeTop(), 0, safeBottom()) }
        ViewCompat.requestApplyInsets(root)
    }

    private fun openCamCapture() {
        importToGalleryMode = false
        openLightboxAfterPick = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchTakePhoto()
        else { openCameraAfterPermission = true; cameraPermLauncher.launch(Manifest.permission.CAMERA) }
    }

    private fun openEditorWithBitmap(bmp: Bitmap) {
        originalBitmap = bmp
        cutoutBitmap = null
        editHistory.clear()
        bgRemoved = false
        isColorPickMode = false
        pickedColor = null
        pickedTouchX = -1f
        pickedTouchY = -1f
        brightnessPercent = 80
        opacityPercent = 80
        showFlow(Flow.EDITOR)
    }

    private fun openLightboxDirectly(bmp: Bitmap) {
        originalBitmap = bmp
        cutoutBitmap = null
        editHistory.clear()
        bgRemoved = false
        isColorPickMode = false
        pickedColor = null
        pickedTouchX = -1f
        pickedTouchY = -1f
        opacityPercent = 90
        brightnessPercent = 140
        showFlow(Flow.LIGHTBOX)
    }

    private fun createCheckerDrawable(): BitmapDrawable {
        val tile = dp(10).coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(tile * 2, tile * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val light = Paint().apply { color = Color.parseColor(if (UiTheme.isDark) "#1E293B" else "#F1F5F9") }
        val dark = Paint().apply { color = Color.parseColor(if (UiTheme.isDark) "#334155" else "#CBD5E1") }
        c.drawRect(0f, 0f, (tile * 2).toFloat(), (tile * 2).toFloat(), light)
        c.drawRect(0f, 0f, tile.toFloat(), tile.toFloat(), dark)
        c.drawRect(tile.toFloat(), tile.toFloat(), (tile * 2).toFloat(), (tile * 2).toFloat(), dark)
        return BitmapDrawable(resources, bmp).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderEditor() {
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(16))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        topRow.addView(
            UiTheme.studioIconBtn(
                act = this,
                icon = StudioIcons.Type.ARROW_LEFT,
                sizeDp = 44,
                iconSizeDp = 20,
                fillColor = UiTheme.surface,
                strokeColor = UiTheme.stroke,
                iconTint = UiTheme.textMain,
                contentDesc = L.t("back")
            ) { showFlow(Flow.MAIN) },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        )
        topRow.addView(TextView(this).apply {
            text = L.t("drawing_create")
            textSize = 19f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val sizeBadgeTv = TextView(this).apply {
            fontFeatureSettings = "tnum"
            textSize = 11f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.accentBright)
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 12, UiTheme.stroke)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        topRow.addView(sizeBadgeTv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        val saveGalleryBtn = UiTheme.studioIconBtn(
            act = this,
            icon = StudioIcons.Type.IMPORT,
            sizeDp = 40,
            iconSizeDp = 18,
            rDp = 13,
            fillColor = UiTheme.surface,
            strokeColor = UiTheme.stroke,
            iconTint = UiTheme.accentBright,
            contentDesc = L.t("save")
        ) {
            val active = if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap
            if (active != null) {
                saveToGallery(active, L.t("art_prefix"))
                Toast.makeText(this, L.t("import_ok"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, L.t("need_image"), Toast.LENGTH_SHORT).show()
            }
        }
        topRow.addView(saveGalleryBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        col.addView(topRow)

        val previewFrame = FrameLayout(this).apply {
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 22, UiTheme.stroke)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(4).toFloat()
        }
        val checkerBox = FrameLayout(this).apply {
            background = createCheckerDrawable()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat()) }
            }
            clipToOutline = true
        }
        val previewIv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val pickerRing = View(this).apply {
            background = UiTheme.round(this@MainActivity, Color.TRANSPARENT, 14, Color.parseColor("#EF4444"), 2)
            visibility = View.GONE
        }
        val processingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = UiTheme.round(this@MainActivity, Color.parseColor("#CC0F172A"), 18, Color.parseColor("#33FFFFFF"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            visibility = View.GONE
            addView(ProgressBar(this@MainActivity).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
            addView(TextView(this@MainActivity).apply {
                text = L.t("removing_bg")
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
        }
        checkerBox.addView(previewIv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        checkerBox.addView(pickerRing, FrameLayout.LayoutParams(dp(24), dp(24)))
        checkerBox.addView(processingOverlay, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        previewFrame.addView(checkerBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        col.addView(previewFrame, LinearLayout.LayoutParams(MATCH_PARENT, dp(264)).apply { bottomMargin = dp(14) })

        fun activeEditBitmap(): Bitmap? = if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap

        fun makeToolBox(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        fun setToolContent(box: LinearLayout, icon: StudioIcons.Type, label: String, fgColor: Int) {
            box.removeAllViews()
            box.addView(
                StudioIcons.imageView(this, icon, 17, fgColor),
                LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(7) }
            )
            box.addView(TextView(this).apply {
                text = label
                textSize = 13f
                UiTheme.applyUiFont(this, bold = true)
                setTextColor(fgColor)
                maxLines = 1
            })
        }

        val autoBgBtn = makeToolBox()
        val keepLinesBtn = makeToolBox()
        val bwSketchBtn = makeToolBox()
        val cropBtn = makeToolBox()
        val pickColorBtn = makeToolBox()
        val rotateBtn = makeToolBox()
        val undoBtn = makeToolBox()

        val pickPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 18, UiTheme.accentBright)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(4).toFloat()
        }
        val pickInfoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val colorSwatch = FrameLayout(this)
        val pickHintTv = TextView(this).apply {
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
        }
        pickInfoRow.addView(colorSwatch, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
        pickInfoRow.addView(pickHintTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        pickPanel.addView(pickInfoRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val tolLabel = TextView(this).apply {
            text = "${L.t("tolerance")}: $colorTolerance%"
            fontFeatureSettings = "tnum"
            setTextColor(UiTheme.textSub); textSize = 12f
        }
        pickPanel.addView(tolLabel)
        pickPanel.addView(UiTheme.styleProSeekBar(this, SeekBar(this).apply {
            max = 90; progress = colorTolerance
            setOnSeekBarChangeListener(simpleSeek { p ->
                colorTolerance = p.coerceAtLeast(8)
                tolLabel.text = "${L.t("tolerance")}: $colorTolerance%"
            })
        }, "#EC4899", "#F43F5E"), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val pickActionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val removePickedBtn = makeToolBox().apply {
            background = UiTheme.premiumGrad(this@MainActivity, "#EF4444", "#DC2626", 14)
            UiTheme.applyColoredShadow(this, Color.parseColor("#EF4444"), 6)
            setToolContent(this, StudioIcons.Type.ERASER, L.t("remove_picked_color"), Color.WHITE)
        }
        val donePickBtn = UiTheme.studioSoftAction(
            act = this,
            icon = StudioIcons.Type.CHECK,
            label = L.t("pick_done"),
            rDp = 14
        ) { }
        pickActionRow.addView(removePickedBtn, LinearLayout.LayoutParams(0, dp(44), 2f).apply { marginEnd = dp(8) })
        pickActionRow.addView(donePickBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        pickPanel.addView(pickActionRow)

        fun sync() {
            val active = activeEditBitmap()
            previewIv.setImageBitmap(active)
            previewIv.alpha = opacityPercent / 100f
            processingOverlay.visibility = if (isProcessingBg) View.VISIBLE else View.GONE
            if (active != null) {
                sizeBadgeTv.text = "${active.width} × ${active.height} px"
                sizeBadgeTv.visibility = View.VISIBLE
            } else {
                sizeBadgeTv.visibility = View.GONE
            }

            setToolContent(autoBgBtn, StudioIcons.Type.MAGIC_BG, if (isProcessingBg) L.t("removing_bg") else L.t("remove_bg"), Color.WHITE)
            autoBgBtn.background = UiTheme.premiumGrad(this, "#3B82F6", "#2563EB", 15)
            UiTheme.applyColoredShadow(autoBgBtn, Color.parseColor("#3B82F6"), 6)

            setToolContent(keepLinesBtn, StudioIcons.Type.INK_PEN, if (isProcessingBg) L.t("removing_bg") else L.t("keep_lines"), Color.WHITE)
            keepLinesBtn.background = UiTheme.premiumGrad(this, "#8B5CF6", "#6D28D9", 15)
            UiTheme.applyColoredShadow(keepLinesBtn, Color.parseColor("#8B5CF6"), 6)

            setToolContent(bwSketchBtn, StudioIcons.Type.SPARK, if (isProcessingBg) L.t("removing_bg") else L.t("bw_sketch_btn"), Color.WHITE)
            bwSketchBtn.background = UiTheme.premiumGrad(this, "#10B981", "#059669", 15)
            UiTheme.applyColoredShadow(bwSketchBtn, Color.parseColor("#10B981"), 6)

            setToolContent(cropBtn, StudioIcons.Type.CROP, L.t("crop_resize"), Color.WHITE)
            cropBtn.background = UiTheme.premiumGrad(this, "#0EA5E9", "#0284C7", 15)
            UiTheme.applyColoredShadow(cropBtn, Color.parseColor("#0EA5E9"), 6)

            if (isColorPickMode) {
                setToolContent(pickColorBtn, StudioIcons.Type.EYEDROPPER, L.t("pick_color_btn"), Color.WHITE)
                pickColorBtn.background = UiTheme.premiumGrad(this, "#EC4899", "#DB2777", 15)
                UiTheme.applyColoredShadow(pickColorBtn, Color.parseColor("#EC4899"), 6)
            } else {
                setToolContent(pickColorBtn, StudioIcons.Type.EYEDROPPER, L.t("pick_color_btn"), UiTheme.textMain)
                pickColorBtn.background = UiTheme.premiumSurface(this, UiTheme.surface, 15, UiTheme.stroke)
                pickColorBtn.elevation = dp(2).toFloat()
            }

            setToolContent(rotateBtn, StudioIcons.Type.RESTORE, L.t("rotate_90"), UiTheme.textMain)
            rotateBtn.background = UiTheme.premiumSurface(this, UiTheme.surface, 15, UiTheme.stroke)
            rotateBtn.elevation = dp(2).toFloat()

            val canRestore = bgRemoved && cutoutBitmap != null
            setToolContent(
                undoBtn,
                if (editHistory.isNotEmpty()) StudioIcons.Type.UNDO else StudioIcons.Type.RESTORE,
                if (editHistory.isNotEmpty()) L.t("undo_step") else L.t("restore_orig"),
                UiTheme.textMain
            )
            undoBtn.isEnabled = canRestore && !isProcessingBg
            undoBtn.alpha = if (undoBtn.isEnabled) 1f else 0.45f
            undoBtn.background = UiTheme.premiumSurface(this, UiTheme.surfaceAlt, 15, UiTheme.stroke)

            pickPanel.visibility = if (isColorPickMode) View.VISIBLE else View.GONE
            colorSwatch.removeAllViews()
            val pc = pickedColor
            if (pc != null) {
                colorSwatch.background = UiTheme.round(this, pc, 17, UiTheme.textMain, 2)
                pickHintTv.text = L.t("pick_color_ready")
                removePickedBtn.isEnabled = !isProcessingBg
                removePickedBtn.alpha = if (isProcessingBg) 0.5f else 1f
            } else {
                colorSwatch.background = UiTheme.round(this, UiTheme.surfaceAlt, 17, UiTheme.stroke)
                colorSwatch.addView(
                    StudioIcons.imageView(this, StudioIcons.Type.EYEDROPPER, 16, UiTheme.accentBright),
                    FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
                )
                pickHintTv.text = L.t("pick_color_hint")
                removePickedBtn.isEnabled = false
                removePickedBtn.alpha = 0.5f
            }

            if (isColorPickMode && pickedTouchX >= 0f && pickedTouchY >= 0f) {
                pickerRing.visibility = View.VISIBLE
                pickerRing.translationX = pickedTouchX - dp(12)
                pickerRing.translationY = pickedTouchY - dp(12)
            } else {
                pickerRing.visibility = View.GONE
            }
        }

        fun applyProcessed(newBmp: Bitmap) {
            val prev = activeEditBitmap()
            if (prev != null && bgRemoved) {
                editHistory.add(prev)
                if (editHistory.size > 8) editHistory.removeAt(0)
            }
            cutoutBitmap = newBmp
            bgRemoved = true
            isProcessingBg = false
            sync()
        }

        previewIv.setOnTouchListener { v, ev ->
            if (!isColorPickMode) return@setOnTouchListener false
            v.parent?.requestDisallowInterceptTouchEvent(true)
            if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_MOVE) {
                val bmp = activeEditBitmap() ?: return@setOnTouchListener true
                val inv = Matrix()
                if (previewIv.imageMatrix.invert(inv)) {
                    val pts = floatArrayOf(ev.x - previewIv.paddingLeft, ev.y - previewIv.paddingTop)
                    inv.mapPoints(pts)
                    val bx = pts[0].toInt()
                    val by = pts[1].toInt()
                    if (bx in 0 until bmp.width && by in 0 until bmp.height) {
                        var sumR = 0; var sumG = 0; var sumB = 0; var cnt = 0
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                val sx = (bx + dx).coerceIn(0, bmp.width - 1)
                                val sy = (by + dy).coerceIn(0, bmp.height - 1)
                                val c = bmp.getPixel(sx, sy)
                                if (Color.alpha(c) > 40) {
                                    sumR += Color.red(c); sumG += Color.green(c); sumB += Color.blue(c); cnt++
                                }
                            }
                        }
                        if (cnt > 0) {
                            pickedColor = Color.rgb(sumR / cnt, sumG / cnt, sumB / cnt)
                            pickedTouchX = ev.x.coerceIn(dp(12).toFloat(), (v.width - dp(12)).toFloat())
                            pickedTouchY = ev.y.coerceIn(dp(12).toFloat(), (v.height - dp(12)).toFloat())
                            sync()
                        }
                    }
                }
            }
            true
        }

        UiTheme.bounce(autoBgBtn) {
            if (isProcessingBg) return@bounce
            val cur = activeEditBitmap() ?: return@bounce
            isProcessingBg = true; isColorPickMode = false; sync()
            ImageProcessor.removeBackgroundAsync(cur) { res ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || flow != Flow.EDITOR) return@runOnUiThread
                    applyProcessed(res)
                }
            }
        }

        UiTheme.bounce(keepLinesBtn) {
            if (isProcessingBg) return@bounce
            val cur = activeEditBitmap() ?: return@bounce
            isProcessingBg = true; isColorPickMode = false; sync()
            ImageProcessor.extractInkLinesAsync(cur, sensitivity = 58) { res ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || flow != Flow.EDITOR) return@runOnUiThread
                    applyProcessed(res)
                }
            }
        }

        UiTheme.bounce(bwSketchBtn) {
            if (isProcessingBg) return@bounce
            val cur = activeEditBitmap() ?: return@bounce
            isProcessingBg = true; isColorPickMode = false; sync()
            ioExecutor.execute {
                val res = try { ImageProcessor.toCleanBwSketch(cur) } catch (_: Throwable) { cur }
                runOnUiThread {
                    if (isFinishing || isDestroyed || flow != Flow.EDITOR) return@runOnUiThread
                    applyProcessed(res)
                }
            }
        }

        UiTheme.bounce(cropBtn) {
            if (isProcessingBg) return@bounce
            val cur = activeEditBitmap() ?: return@bounce
            isColorPickMode = false
            CropOverlayDialog.show(this, cur) { cropped ->
                applyProcessed(cropped)
            }
        }

        UiTheme.bounce(pickColorBtn) {
            if (isProcessingBg) return@bounce
            isColorPickMode = !isColorPickMode
            sync()
        }

        UiTheme.bounce(rotateBtn) {
            if (isProcessingBg) return@bounce
            val cur = activeEditBitmap() ?: return@bounce
            isColorPickMode = false
            val m = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(cur, 0, 0, cur.width, cur.height, m, true)
            applyProcessed(rotated)
        }

        UiTheme.bounce(donePickBtn) {
            isColorPickMode = false
            sync()
        }

        UiTheme.bounce(removePickedBtn) {
            if (isProcessingBg) return@bounce
            val cur = activeEditBitmap() ?: return@bounce
            val target = pickedColor ?: return@bounce
            isProcessingBg = true; sync()
            ImageProcessor.removePickedColorAsync(cur, target, colorTolerance) { res ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || flow != Flow.EDITOR) return@runOnUiThread
                    pickedColor = null
                    pickedTouchX = -1f
                    pickedTouchY = -1f
                    applyProcessed(res)
                }
            }
        }

        UiTheme.bounce(undoBtn) {
            if (isProcessingBg) return@bounce
            if (editHistory.isNotEmpty()) {
                cutoutBitmap = editHistory.removeAt(editHistory.size - 1)
                bgRemoved = true
            } else {
                cutoutBitmap = null
                bgRemoved = false
            }
            pickedColor = null
            pickedTouchX = -1f
            pickedTouchY = -1f
            sync()
        }

        sync()

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(autoBgBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        row1.addView(keepLinesBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        col.addView(row1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(bwSketchBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        row2.addView(cropBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        col.addView(row2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(pickColorBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        row3.addView(rotateBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        col.addView(row3, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        col.addView(undoBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)).apply { bottomMargin = dp(10) })
        col.addView(pickPanel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val opCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 18, UiTheme.stroke)
            setPadding(dp(14), dp(12), dp(14), dp(10))
            elevation = dp(2).toFloat()
        }
        val opHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        opHead.addView(
            StudioIcons.imageView(this, StudioIcons.Type.DROPLET, 16, UiTheme.accentBright),
            LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(7) }
        )
        opHead.addView(TextView(this).apply {
            text = L.t("opacity")
            setTextColor(UiTheme.textMain)
            textSize = 14f
            UiTheme.applyDisplayFont(this, true)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val opPctBadge = TextView(this).apply {
            text = "$opacityPercent%"
            fontFeatureSettings = "tnum"
            textSize = 12f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.accentBright)
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 10)
            setPadding(dp(9), dp(3), dp(9), dp(3))
        }
        opHead.addView(opPctBadge)
        opCard.addView(opHead, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
        opCard.addView(UiTheme.styleProSeekBar(this, SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                opacityPercent = p.coerceAtLeast(10)
                opPctBadge.text = "$opacityPercent%"
                previewIv.alpha = opacityPercent / 100f
            })
        }, "#7C3AED", "#EC4899"))
        col.addView(opCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        col.addView(TextView(this).apply {
            text = L.t("choose_draw_system")
            textSize = 14f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            setPadding(dp(2), 0, 0, dp(8))
        })

        fun drawModeCard(
            icon: StudioIcons.Type,
            title: String,
            sub: String,
            c1: String,
            c2: String,
            onTap: () -> Unit
        ): FrameLayout {
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiTheme.premiumGrad(this@MainActivity, c1, c2, 19)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                val well = FrameLayout(this@MainActivity).apply {
                    background = UiTheme.round(this@MainActivity, Color.parseColor("#2EFFFFFF"), 14, Color.parseColor("#44FFFFFF"))
                    addView(
                        StudioIcons.imageView(this@MainActivity, icon, 22, Color.WHITE),
                        FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
                    )
                }
                addView(well, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
                val tc = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                tc.addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15.5f
                    UiTheme.applyDisplayFont(this, true)
                    setTextColor(Color.WHITE)
                })
                tc.addView(TextView(this@MainActivity).apply {
                    text = sub
                    textSize = 11.5f
                    UiTheme.applyUiFont(this)
                    setTextColor(Color.parseColor("#E2E8F0"))
                })
                addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
                addView(
                    StudioIcons.imageView(this@MainActivity, StudioIcons.Type.ARROW_RIGHT, 18, Color.WHITE),
                    LinearLayout.LayoutParams(dp(18), dp(18))
                )
                UiTheme.bounce(this, onTap)
            }
            return UiTheme.wrapShimmerCta(this, inner, 19).apply {
                UiTheme.applyColoredShadow(this, Color.parseColor(c1), 9)
            }
        }

        val cameraModeBtn = drawModeCard(
            icon = StudioIcons.Type.CAMERA,
            title = L.t("mode_camera"),
            sub = L.t("mode_camera_sub"),
            c1 = "#10B981",
            c2 = "#059669"
        ) {
            if (originalBitmap == null) Toast.makeText(this, L.t("need_image"), Toast.LENGTH_SHORT).show()
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                brightnessPercent = brightnessPercent.coerceIn(10, 100)
                showFlow(Flow.CAMERA)
            } else {
                openCameraAfterPermission = false
                cameraPermLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        col.addView(cameraModeBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(76)).apply { bottomMargin = dp(10) })

        val lightboxModeBtn = drawModeCard(
            icon = StudioIcons.Type.SUN,
            title = L.t("mode_lightbox"),
            sub = L.t("mode_lightbox_sub"),
            c1 = "#0EA5E9",
            c2 = "#2563EB"
        ) {
            if (originalBitmap == null) {
                importToGalleryMode = false
                openLightboxAfterPick = true
                pickGalleryLauncher.launch("image/*")
            } else {
                if (brightnessPercent < 100) brightnessPercent = 140
                showFlow(Flow.LIGHTBOX)
            }
        }
        col.addView(lightboxModeBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(76)).apply { bottomMargin = dp(14) })

        UiTheme.animatePageIn(col, 14, 240L)
        root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            col.setPadding(dp(16), safeTop() + dp(10), dp(16), safeBottom() + dp(16))
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderCamera() {
        updateSystemBarsAppearance(false)
        root.removeAllViews(); torchOn = false; controlsHidden = false
        var activeBmp = (if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap) ?: return
        val previewView = PreviewView(this)
        val overlay = OverlayView(this).apply { setBitmap(activeBmp, true); setOpacity(opacityPercent) }
        overlayView = overlay

        // Translucent Dark Glassmorphic Control Capsule
        val glassFill = Color.parseColor("#CC0F172A")
        val glassStroke = Color.parseColor("#33FFFFFF")
        val topPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, glassFill, 26, glassStroke)
            elevation = dp(8).toFloat()
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun pillIconBtn(
            icon: StudioIcons.Type,
            tint: Int = Color.WHITE,
            filled: Boolean = false,
            desc: String = "",
            onTap: (FrameLayout, ImageView) -> Unit
        ): FrameLayout {
            val wrap = FrameLayout(this).apply {
                contentDescription = desc
                isClickable = true
                isFocusable = true
            }
            val iv = StudioIcons.imageView(this, icon, 20, tint, filled)
            wrap.addView(iv, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            UiTheme.bounce(wrap) { onTap(wrap, iv) }
            return wrap
        }

        // Bottom HUD Card (Quick AR Controls + Opacity + Brightness)
        val bottomCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, Color.parseColor("#DD0F172A"), 24, glassStroke)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val gridPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, Color.parseColor("#1AFFFFFF"), 12, Color.parseColor("#28FFFFFF"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val gridIconIv = StudioIcons.imageView(this, StudioIcons.Type.GRID, 15, Color.parseColor("#CBD5E1"))
        val gridLabelTv = TextView(this).apply {
            text = overlay.gridModeLabel()
            textSize = 12f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(Color.WHITE)
        }
        gridPill.addView(gridIconIv, LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(6) })
        gridPill.addView(gridLabelTv)
        fun syncGridPill() {
            val on = overlay.gridEnabled
            gridLabelTv.text = overlay.gridModeLabel()
            gridIconIv.setImageDrawable(StudioIcons.icon(this, StudioIcons.Type.GRID, 15, if (on) Color.parseColor("#38BDF8") else Color.parseColor("#CBD5E1")))
            gridPill.background = if (on) {
                UiTheme.round(this, Color.parseColor("#3338BDF8"), 12, Color.parseColor("#38BDF8"))
            } else {
                UiTheme.round(this, Color.parseColor("#1AFFFFFF"), 12, Color.parseColor("#28FFFFFF"))
            }
        }
        UiTheme.bounce(gridPill) {
            overlay.cycleGridMode()
            syncGridPill()
        }

        val resetPosPill = StudioIcons.iconPill(
            context = this,
            iconType = StudioIcons.Type.RESET_POS,
            label = L.t("reset_crop"),
            textColor = Color.WHITE,
            iconColor = Color.parseColor("#CBD5E1"),
            iconSizeDp = 15,
            textSizeSp = 12f
        ).apply {
            background = UiTheme.round(this@MainActivity, Color.parseColor("#1AFFFFFF"), 12, Color.parseColor("#28FFFFFF"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        UiTheme.bounce(resetPosPill) {
            if (!overlay.isLocked) overlay.resetPosition()
        }
        val camTimerTv = TextView(this).apply {
            text = "00:00"
            fontFeatureSettings = "tnum"
            textSize = 11.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(Color.parseColor("#A7F3D0"))
            background = UiTheme.round(this@MainActivity, Color.parseColor("#2210B981"), 11, Color.parseColor("#4410B981"))
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        quickRow.addView(gridPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(34)).apply { marginEnd = dp(8) })
        quickRow.addView(resetPosPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(34)))
        quickRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        quickRow.addView(camTimerTv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        bottomCard.addView(quickRow)
        startSessionTimer(camTimerTv)

        topPill.addView(
            pillIconBtn(StudioIcons.Type.CLOSE, Color.WHITE, desc = L.t("back")) { _, _ ->
                stopCamera(); showFlow(Flow.EDITOR)
            },
            LinearLayout.LayoutParams(dp(42), dp(42))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.FLIP_H, Color.WHITE, desc = "Flip") { _, _ ->
                val m = Matrix().apply { postScale(-1f, 1f) }
                activeBmp = Bitmap.createBitmap(activeBmp, 0, 0, activeBmp.width, activeBmp.height, m, true)
                overlay.setBitmap(activeBmp, resetPos = false)
            },
            LinearLayout.LayoutParams(dp(42), dp(42))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.FLASH, Color.WHITE, desc = "Flash") { wrap, iv ->
                torchOn = !torchOn
                boundCamera?.cameraControl?.enableTorch(torchOn)
                iv.setImageDrawable(
                    StudioIcons.icon(
                        this,
                        StudioIcons.Type.FLASH,
                        20,
                        if (torchOn) Color.parseColor("#FBBF24") else Color.WHITE,
                        filled = torchOn
                    )
                )
                wrap.background = if (torchOn) UiTheme.round(this, Color.parseColor("#33FBBF24"), 14) else null
            },
            LinearLayout.LayoutParams(dp(42), dp(42))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.LOCK, Color.WHITE, desc = "Lock") { wrap, iv ->
                if (!overlay.isLocked) {
                    tracker.startLock(); overlay.lock()
                    iv.setImageDrawable(StudioIcons.icon(this, StudioIcons.Type.UNLOCK, 20, Color.parseColor("#34D399"), filled = true))
                    wrap.background = UiTheme.round(this, Color.parseColor("#3310B981"), 14)
                    cameraHintView?.text = L.t("hint_locked")
                    cameraHintDot?.background = UiTheme.round(this, Color.parseColor("#10B981"), 5)
                    resetPosPill.isEnabled = false
                    resetPosPill.alpha = 0.4f
                } else {
                    tracker.stopLock(); overlay.unlock()
                    iv.setImageDrawable(StudioIcons.icon(this, StudioIcons.Type.LOCK, 20, Color.WHITE))
                    wrap.background = null
                    cameraHintView?.text = L.t("hint_place")
                    cameraHintDot?.background = UiTheme.round(this, Color.parseColor("#FBBF24"), 5)
                    resetPosPill.isEnabled = true
                    resetPosPill.alpha = 1f
                }
            },
            LinearLayout.LayoutParams(dp(42), dp(42))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.CHECK, Color.parseColor("#34D399"), desc = L.t("save")) { _, _ ->
                saveToGallery(activeBmp, L.t("art_prefix")); stopCamera()
                billingAds.showRewardedOnComplete(this@MainActivity) { runOnUiThread { showFlow(Flow.CONGRATS) } }
            },
            LinearLayout.LayoutParams(dp(42), dp(42))
        )

        val opRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        opRow.addView(StudioIcons.imageView(this, StudioIcons.Type.DROPLET, 16, Color.parseColor("#A78BFA")), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
        val opTv = TextView(this).apply {
            text = "${L.t("opacity")}  $opacityPercent%"
            fontFeatureSettings = "tnum"
            setTextColor(Color.WHITE)
            textSize = 13f
            UiTheme.applyDisplayFont(this, true)
        }
        opRow.addView(opTv)
        bottomCard.addView(opRow)
        bottomCard.addView(UiTheme.styleProSeekBar(this, SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                opacityPercent = p.coerceAtLeast(10)
                opTv.text = "${L.t("opacity")}  $opacityPercent%"
                overlay.setOpacity(opacityPercent)
            })
        }, "#8B5CF6", "#EC4899", darkSurface = true))

        val brRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        brRow.addView(StudioIcons.imageView(this, StudioIcons.Type.SUN, 16, Color.parseColor("#FBBF24")), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
        val brTv = TextView(this).apply {
            text = "${L.t("brightness")}  $brightnessPercent%"
            fontFeatureSettings = "tnum"
            setTextColor(Color.WHITE)
            textSize = 13f
            UiTheme.applyDisplayFont(this, true)
        }
        brRow.addView(brTv)
        bottomCard.addView(brRow)
        bottomCard.addView(UiTheme.styleProSeekBar(this, SeekBar(this).apply {
            max = 100; progress = brightnessPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                brightnessPercent = p.coerceAtLeast(10)
                brTv.text = "${L.t("brightness")}  $brightnessPercent%"
                window.attributes = window.attributes.apply { screenBrightness = brightnessPercent / 100f }
            })
        }, "#F59E0B", "#FDE047", darkSurface = true))

        val hideBtn = FrameLayout(this).apply {
            background = UiTheme.premiumSurface(this@MainActivity, glassFill, 24, glassStroke)
            elevation = dp(8).toFloat()
            minimumWidth = dp(64)
            setPadding(dp(14), 0, dp(14), 0)
            contentDescription = L.t("hide_btn")
        }
        val hideTv = TextView(this).apply {
            text = L.t("hide_btn")
            textSize = 13f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        hideBtn.addView(hideTv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))

        val hintPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, Color.parseColor("#B30F172A"), 14, glassStroke)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val hintDot = View(this).apply {
            background = UiTheme.round(this@MainActivity, Color.parseColor("#FBBF24"), 5)
        }
        val hintTv = TextView(this).apply {
            text = L.t("hint_place")
            setTextColor(Color.WHITE)
            textSize = 12f
            UiTheme.applyUiFont(this, true)
        }
        hintPill.addView(hintDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
        hintPill.addView(hintTv)
        cameraHintView = hintTv
        cameraHintDot = hintDot

        fun setControlsVisible(show: Boolean) {
            controlsHidden = !show
            val vis = if (show) View.VISIBLE else View.GONE
            topPill.visibility = vis; bottomCard.visibility = vis; hintPill.visibility = vis
            overlay.setGuidesVisible(show)
            hideTv.text = if (show) L.t("hide_btn") else L.t("show_btn")
            hideBtn.animate().alpha(if (show) 1f else 0.58f).setDuration(180).start()
        }
        UiTheme.bounce(hideBtn) { setControlsVisible(controlsHidden) }

        val tapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (controlsHidden) { setControlsVisible(true); return true }
                return false
            }
        })
        overlay.setOnTouchListener { _, ev -> tapDetector.onTouchEvent(ev); false }
        previewView.setOnTouchListener { _, ev -> tapDetector.onTouchEvent(ev); true }

        val topBar = FrameLayout(this).apply { setPadding(dp(14), safeTop() + dp(8), dp(14), 0) }
        topBar.addView(topPill, FrameLayout.LayoutParams(WRAP_CONTENT, dp(50), Gravity.START))
        topBar.addView(hideBtn, FrameLayout.LayoutParams(WRAP_CONTENT, dp(48), Gravity.END or Gravity.CENTER_VERTICAL))

        val hintLp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = safeTop() + dp(68)
        }
        val bottomLp = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(14), 0, dp(14), safeBottom() + dp(10))
        }

        root.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        root.addView(hintPill, hintLp)
        root.addView(bottomCard, bottomLp)
        UiTheme.animatePageIn(topBar, -14, 240L)
        UiTheme.animatePageIn(hintPill, -10, 280L)
        UiTheme.animatePageIn(bottomCard, 22, 260L)
        applyInsetsCallback = {
            topBar.setPadding(dp(14), safeTop() + dp(8), dp(14), 0)
            hintLp.topMargin = safeTop() + dp(68)
            hintPill.layoutParams = hintLp
            bottomLp.setMargins(dp(14), 0, dp(14), safeBottom() + dp(10))
            bottomCard.layoutParams = bottomLp
        }
        ViewCompat.requestApplyInsets(root)
        startCamera(previewView)
    }

    // ---------------- SYSTEM 2: SCREEN / DISPLAY LIGHTBOX TRACING ----------------
    @SuppressLint("ClickableViewAccessibility")
    private fun renderLightbox() {
        updateSystemBarsAppearance(true)
        root.removeAllViews()
        isLightboxLocked = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var activeBmp = (if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap) ?: return
        brightnessPercent = brightnessPercent.coerceIn(10, 200)

        val whiteCanvas = View(this).apply { setBackgroundColor(Color.WHITE) }
        val overlay = OverlayView(this).apply {
            setBitmap(activeBmp, true)
            setOpacity(opacityPercent)
        }
        overlayView = overlay

        val glassFill = Color.parseColor("#E60F172A")
        val glassStroke = Color.parseColor("#33FFFFFF")
        val topPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, glassFill, 26, glassStroke)
            elevation = dp(8).toFloat()
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun pillIconBtn(
            icon: StudioIcons.Type,
            tint: Int = Color.WHITE,
            filled: Boolean = false,
            desc: String = "",
            onTap: (FrameLayout, ImageView) -> Unit
        ): FrameLayout {
            val wrap = FrameLayout(this).apply {
                contentDescription = desc
                isClickable = true
                isFocusable = true
            }
            val iv = StudioIcons.imageView(this, icon, 20, tint, filled)
            wrap.addView(iv, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            UiTheme.bounce(wrap) { onTap(wrap, iv) }
            return wrap
        }

        // Top-Left Capsule (4 actions) + Separate Lock Button (Button #5) above touch blocker
        topPill.addView(
            pillIconBtn(StudioIcons.Type.CLOSE, Color.WHITE, desc = L.t("back")) { _, _ ->
                restoreWindowDefaults()
                showFlow(if (originalBitmap != null) Flow.EDITOR else Flow.MAIN)
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.FLIP_H, Color.WHITE, desc = "Flip") { _, _ ->
                if (isLightboxLocked) return@pillIconBtn
                val m = Matrix().apply { postScale(-1f, 1f) }
                activeBmp = Bitmap.createBitmap(activeBmp, 0, 0, activeBmp.width, activeBmp.height, m, true)
                overlay.setBitmap(activeBmp, resetPos = false)
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.GRID, Color.WHITE, desc = "Grid") { wrap, iv ->
                if (isLightboxLocked) return@pillIconBtn
                overlay.cycleGridMode()
                val on = overlay.gridEnabled
                iv.setImageDrawable(
                    StudioIcons.icon(
                        this,
                        StudioIcons.Type.GRID,
                        20,
                        if (on) Color.parseColor("#38BDF8") else Color.WHITE
                    )
                )
                wrap.background = if (on) UiTheme.round(this, Color.parseColor("#3338BDF8"), 14) else null
                Toast.makeText(this, overlay.gridModeLabel(), Toast.LENGTH_SHORT).show()
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.CHECK, Color.parseColor("#34D399"), desc = L.t("save")) { _, _ ->
                if (isLightboxLocked) return@pillIconBtn
                saveToGallery(activeBmp, L.t("art_prefix"))
                restoreWindowDefaults()
                billingAds.showRewardedOnComplete(this@MainActivity) { runOnUiThread { showFlow(Flow.CONGRATS) } }
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )

        val hintPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, Color.parseColor("#D90F172A"), 14, glassStroke)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val hintDot = View(this).apply {
            background = UiTheme.round(this@MainActivity, Color.parseColor("#FBBF24"), 5)
        }
        val hintTv = TextView(this).apply {
            text = L.t("lightbox_hint")
            setTextColor(Color.WHITE)
            textSize = 11.5f
            UiTheme.applyUiFont(this, true)
        }
        hintPill.addView(hintDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
        hintPill.addView(hintTv)

        val bottomCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, Color.parseColor("#E60F172A"), 24, glassStroke)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }

        val opRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        opRow.addView(StudioIcons.imageView(this, StudioIcons.Type.DROPLET, 16, Color.parseColor("#A78BFA")), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
        val opTv = TextView(this).apply {
            text = "${L.t("opacity")}  $opacityPercent%"
            fontFeatureSettings = "tnum"
            setTextColor(Color.WHITE)
            textSize = 13f
            UiTheme.applyDisplayFont(this, true)
        }
        opRow.addView(opTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val resetPosChip = StudioIcons.iconPill(
            context = this,
            iconType = StudioIcons.Type.RESET_POS,
            label = L.t("reset_crop"),
            textColor = Color.WHITE,
            iconColor = Color.parseColor("#CBD5E1"),
            iconSizeDp = 13,
            textSizeSp = 11.5f
        ).apply {
            background = UiTheme.round(this@MainActivity, Color.parseColor("#24FFFFFF"), 10, Color.parseColor("#33FFFFFF"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            UiTheme.bounce(this) {
                if (!isLightboxLocked) overlay.resetPosition()
            }
        }
        val lbTimerTv = TextView(this).apply {
            text = "00:00"
            fontFeatureSettings = "tnum"
            textSize = 11f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(Color.parseColor("#A7F3D0"))
            background = UiTheme.round(this@MainActivity, Color.parseColor("#2210B981"), 10, Color.parseColor("#4410B981"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        opRow.addView(lbTimerTv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
        opRow.addView(resetPosChip)
        bottomCard.addView(opRow)
        startSessionTimer(lbTimerTv)
        bottomCard.addView(UiTheme.styleProSeekBar(this, SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                if (isLightboxLocked) return@simpleSeek
                opacityPercent = p.coerceAtLeast(10)
                opTv.text = "${L.t("opacity")}  $opacityPercent%"
                overlay.setOpacity(opacityPercent)
            })
        }, "#8B5CF6", "#EC4899", darkSurface = true))

        val brRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        brRow.addView(StudioIcons.imageView(this, StudioIcons.Type.SUN, 16, Color.parseColor("#FBBF24")), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
        val brTv = TextView(this).apply {
            fontFeatureSettings = "tnum"
            setTextColor(Color.WHITE)
            textSize = 13f
            UiTheme.applyDisplayFont(this, true)
        }
        val boostBadge = TextView(this).apply {
            text = L.t("brightness_overdrive")
            textSize = 10.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(Color.parseColor("#1E1B4B"))
            background = UiTheme.premiumGrad(this@MainActivity, "#FDE047", "#F59E0B", 9)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        brRow.addView(brTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        brRow.addView(boostBadge)
        bottomCard.addView(brRow)

        fun applyLightboxBrightness(p: Int) {
            brightnessPercent = p.coerceIn(10, 200)
            brTv.text = "${L.t("brightness")}  $brightnessPercent%"
            if (brightnessPercent <= 100) {
                val hw = (brightnessPercent / 100f).coerceIn(0.08f, 1.0f)
                window.attributes = window.attributes.apply { screenBrightness = hw }
                val shade = (115 + ((brightnessPercent - 10) * 140) / 90).coerceIn(115, 255)
                whiteCanvas.setBackgroundColor(Color.rgb(shade, shade, shade))
                overlay.setContrastBoost(0f)
                boostBadge.visibility = View.GONE
            } else {
                // 101..200% = Full Hardware Max + Pure White Luminance + 2x Line Contrast Overdrive
                window.attributes = window.attributes.apply { screenBrightness = 1.0f }
                whiteCanvas.setBackgroundColor(Color.WHITE)
                val boost = ((brightnessPercent - 100) / 100f).coerceIn(0.01f, 1.0f)
                overlay.setContrastBoost(boost)
                boostBadge.visibility = View.VISIBLE
            }
        }
        applyLightboxBrightness(brightnessPercent)

        bottomCard.addView(UiTheme.styleProSeekBar(this, SeekBar(this).apply {
            max = 200; progress = brightnessPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                if (isLightboxLocked) return@simpleSeek
                applyLightboxBrightness(p)
            })
        }, "#F59E0B", "#FDE047", darkSurface = true))

        // Full-Screen Touch Blocker (enabled ONLY when locked — blocks canvas, sliders & all buttons except Lock button)
        val touchBlocker = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            setOnTouchListener { _, _ -> true }
        }

        // Hide Button (Text-only "Hide" / "Show" — no eye icon; hides all controls so only the sketch shows)
        var lightboxControlsHidden = false
        val hideBtn = FrameLayout(this).apply {
            background = UiTheme.premiumSurface(this@MainActivity, glassFill, 24, glassStroke)
            elevation = dp(10).toFloat()
            minimumWidth = dp(62)
            setPadding(dp(14), 0, dp(14), 0)
            contentDescription = L.t("hide_btn")
            isClickable = true
            isFocusable = true
        }
        val hideTv = TextView(this).apply {
            text = L.t("hide_btn")
            textSize = 13f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        hideBtn.addView(hideTv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))

        // Button #5: Lock / Unlock positioned ABOVE touchBlocker in Z-order so ONLY it responds while locked
        val lockBtn = FrameLayout(this).apply {
            background = UiTheme.round(this@MainActivity, glassFill, 24, glassStroke)
            elevation = dp(16).toFloat()
            contentDescription = "Lock"
            isClickable = true
            isFocusable = true
        }
        val lockIv = StudioIcons.imageView(this, StudioIcons.Type.LOCK, 22, Color.WHITE)
        lockBtn.addView(lockIv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))

        fun setLightboxControlsVisible(show: Boolean) {
            lightboxControlsHidden = !show
            val vis = if (show) View.VISIBLE else View.GONE
            topPill.visibility = vis
            hintPill.visibility = vis
            bottomCard.visibility = vis
            lockBtn.visibility = vis
            overlay.setGuidesVisible(show)
            hideTv.text = if (show) L.t("hide_btn") else L.t("show_btn")
            hideBtn.animate()
                .translationX(if (show) 0f else dp(56).toFloat())
                .alpha(if (show) 1f else 0.52f)
                .setDuration(190)
                .start()
        }

        UiTheme.bounce(hideBtn) {
            if (isLightboxLocked) return@bounce
            setLightboxControlsVisible(lightboxControlsHidden)
        }

        val lightboxTapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isLightboxLocked && lightboxControlsHidden) {
                    setLightboxControlsVisible(true)
                    return true
                }
                return false
            }
        })
        overlay.setOnTouchListener { _, ev -> lightboxTapDetector.onTouchEvent(ev); false }
        whiteCanvas.setOnTouchListener { _, ev -> lightboxTapDetector.onTouchEvent(ev); true }

        UiTheme.bounce(lockBtn) {
            isLightboxLocked = !isLightboxLocked
            if (isLightboxLocked) {
                overlay.lock()
                touchBlocker.visibility = View.VISIBLE
                lockIv.setImageDrawable(StudioIcons.icon(this, StudioIcons.Type.UNLOCK, 22, Color.WHITE, filled = true))
                lockBtn.background = UiTheme.premiumGrad(this, "#10B981", "#059669", 24, Color.parseColor("#88FFFFFF"))
                UiTheme.applyColoredShadow(lockBtn, Color.parseColor("#10B981"), 16)
                hintTv.text = L.t("lightbox_locked")
                hintDot.background = UiTheme.round(this, Color.parseColor("#10B981"), 5)
                topPill.animate().alpha(0.18f).setDuration(180).start()
                bottomCard.animate().alpha(0.16f).setDuration(180).start()
                hideBtn.animate().alpha(0.16f).setDuration(180).start()
            } else {
                overlay.unlock()
                touchBlocker.visibility = View.GONE
                lockIv.setImageDrawable(StudioIcons.icon(this, StudioIcons.Type.LOCK, 22, Color.WHITE))
                lockBtn.background = UiTheme.round(this, glassFill, 24, glassStroke)
                lockBtn.elevation = dp(16).toFloat()
                hintTv.text = L.t("lightbox_hint")
                hintDot.background = UiTheme.round(this, Color.parseColor("#FBBF24"), 5)
                topPill.animate().alpha(1f).setDuration(180).start()
                bottomCard.animate().alpha(1f).setDuration(180).start()
                hideBtn.animate().alpha(if (lightboxControlsHidden) 0.52f else 1f).setDuration(180).start()
            }
        }

        val topPillLp = FrameLayout.LayoutParams(WRAP_CONTENT, dp(50), Gravity.TOP or Gravity.START).apply {
            setMargins(dp(12), safeTop() + dp(8), 0, 0)
        }
        val hintLp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = safeTop() + dp(68)
        }
        val bottomLp = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(14), 0, dp(14), safeBottom() + dp(10))
        }
        val hideBtnLp = FrameLayout.LayoutParams(WRAP_CONTENT, dp(48), Gravity.TOP or Gravity.END).apply {
            setMargins(0, safeTop() + dp(9), dp(70), 0)
        }
        val lockBtnLp = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            setMargins(0, safeTop() + dp(9), dp(14), 0)
        }

        root.addView(whiteCanvas, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(topPill, topPillLp)
        root.addView(hintPill, hintLp)
        root.addView(bottomCard, bottomLp)
        root.addView(hideBtn, hideBtnLp)
        root.addView(touchBlocker, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(lockBtn, lockBtnLp)
        UiTheme.animatePageIn(topPill, -14, 240L)
        UiTheme.animatePageIn(hideBtn, -14, 240L)
        UiTheme.animatePageIn(lockBtn, -14, 240L)
        UiTheme.animatePageIn(hintPill, -10, 280L)
        UiTheme.animatePageIn(bottomCard, 22, 260L)

        applyInsetsCallback = {
            topPillLp.setMargins(dp(12), safeTop() + dp(8), 0, 0)
            topPill.layoutParams = topPillLp
            hintLp.topMargin = safeTop() + dp(68)
            hintPill.layoutParams = hintLp
            bottomLp.setMargins(dp(14), 0, dp(14), safeBottom() + dp(10))
            bottomCard.layoutParams = bottomLp
            hideBtnLp.setMargins(0, safeTop() + dp(9), dp(70), 0)
            hideBtn.layoutParams = hideBtnLp
            lockBtnLp.setMargins(0, safeTop() + dp(9), dp(14), 0)
            lockBtn.layoutParams = lockBtnLp
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderCongrats() {
        updateSystemBarsAppearance(!UiTheme.isDark)
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), safeTop() + dp(16), dp(24), safeBottom() + dp(16))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 28, UiTheme.strokeStrong)
            setPadding(dp(24), dp(28), dp(24), dp(26))
            elevation = dp(12).toFloat()
        }
        card.addView(
            StudioIcons.CelebrationArtView(this),
            LinearLayout.LayoutParams(dp(136), dp(136)).apply { bottomMargin = dp(12) }
        )
        card.addView(TextView(this).apply {
            text = L.t("congrats")
            textSize = 26f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
        })
        card.addView(TextView(this).apply {
            text = L.t("good_luck")
            textSize = 14.5f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        })
        val homeCta = UiTheme.wrapShimmerCta(
            this,
            UiTheme.studioGradientAction(this, StudioIcons.Type.HOME, L.t("go_home"), "#7C3AED", "#4F46E5", 17) {
                showFlow(Flow.MAIN)
            },
            17
        ).apply {
            UiTheme.applyColoredShadow(this, Color.parseColor("#7C3AED"), 9)
        }
        card.addView(homeCta, LinearLayout.LayoutParams(MATCH_PARENT, dp(54)))
        col.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        UiTheme.animatePageIn(card, 20, 320L)
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        applyInsetsCallback = {
            col.setPadding(dp(24), safeTop() + dp(16), dp(24), safeBottom() + dp(16))
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun startCamera(previewView: PreviewView) {
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        previewView.post {
            if (flow != Flow.CAMERA || isFinishing || isDestroyed) return@post
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                try {
                    if (flow != Flow.CAMERA || isFinishing || isDestroyed) return@addListener
                    val provider = future.get(); cameraProvider = provider
                    val resSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .build()
                    val preview = Preview.Builder()
                        .setResolutionSelector(resSelector)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        try {
                            val out = tracker.process(image); val ov = overlayView
                            if (ov != null && ov.isLocked) runOnUiThread {
                                ov.updateTracking(out)
                                cameraHintView?.text = if (out.good) L.t("hint_locked") else L.t("hint_lost")
                                cameraHintDot?.background = UiTheme.round(
                                    this,
                                    Color.parseColor(if (out.good) "#10B981" else "#F43F5E"),
                                    5
                                )
                            }
                        } finally { image.close() }
                    }
                    provider.unbindAll()
                    val vp = previewView.viewPort
                    boundCamera = if (vp != null) {
                        val group = UseCaseGroup.Builder()
                            .setViewPort(vp)
                            .addUseCase(preview)
                            .addUseCase(analysis)
                            .build()
                        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
                    } else {
                        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                } catch (_: Exception) { Toast.makeText(this, L.t("camera_error"), Toast.LENGTH_LONG).show() }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun stopCamera() {
        try { boundCamera?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
        restoreWindowDefaults()
        boundCamera = null; torchOn = false; tracker.stopLock(); cameraProvider?.unbindAll()
        overlayView = null; cameraHintView = null; cameraHintDot = null
    }

    private fun launchTakePhoto() {
        val file = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri; takePhotoLauncher.launch(uri)
    }

    private fun handlePickedUri(uri: Uri) {
        try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxS = maxOf(info.size.width, info.size.height)
                if (maxS > 1080) { val s = 1080f / maxS; d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1)) }
            }
            val clean = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (importToGalleryMode) {
                openLightboxAfterPick = false
                saveToGallery(clean, "Imported")
                renderMain()
            } else if (openLightboxAfterPick) {
                openLightboxAfterPick = false
                openLightboxDirectly(clean)
            } else {
                openEditorWithBitmap(clean)
            }
        } catch (_: Exception) {
            openLightboxAfterPick = false
            Toast.makeText(this, L.t("image_failed"), Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToGallery(bmp: Bitmap?, prefix: String) {
        if (bmp == null) return
        try {
            val maxDim = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            val outBmp = if (maxDim > 1080) {
                val s = 1080f / maxDim
                Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
            } else {
                bmp
            }
            val name = "art_${System.currentTimeMillis()}.png"
            FileOutputStream(File(galleryDir, name)).use { outBmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            prefs.edit().putString("gname_$name", "$prefix #${galleryDir.listFiles()?.size ?: 1}").apply()
            if (::cloud.isInitialized && cloud.currentUser() != null) {
                lifecycleScope.launch { cloud.recordDrawingSaved("$prefix ($name)") }
            }
        } catch (_: Exception) {}
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun dp(v: Int): Int = UiTheme.dp(this, v)

    override fun onResume() {
        super.onResume()
        if (::cloud.isInitialized && cloud.currentUser() != null) {
            cloud.markPresenceAsync(lifecycleScope, true)
            startUsageTracking()
        }
    }

    override fun onPause() {
        stopUsageTracking()
        if (::cloud.isInitialized && cloud.currentUser() != null) {
            cloud.markPresenceAsync(lifecycleScope, false)
        }
        super.onPause()
    }

    override fun onDestroy() {
        stopUsageTracking()
        stopSessionTimer()
        handler.removeCallbacksAndMessages(null)
        activePopupDismiss = null
        if (::cloud.isInitialized) cloud.stopAllListeners()
        super.onDestroy(); stopCamera(); destroyWebView(); billingAds.destroy()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
        ioExecutor.shutdown()
    }
}
