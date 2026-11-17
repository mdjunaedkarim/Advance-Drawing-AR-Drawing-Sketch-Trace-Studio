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
import android.webkit.JavascriptInterface
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
    private enum class Flow { SPLASH, MAIN, LANG, PRIVACY, ANIME_CHARS, CHAR_GALLERY, BROWSER, EDITOR, CAMERA, CONGRATS }

    private var flow = Flow.SPLASH
    private var currentTab = Tab.HOME
    private lateinit var root: FrameLayout
    private lateinit var analysisExecutor: ExecutorService
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val tracker by lazy { PlaneTracker() }

    private lateinit var billingAds: BillingAdsManager
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
        if (uri != null) handlePickedUri(uri)
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
            onMessage = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }
        )
        try { billingAds.init() } catch (_: Throwable) {}

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
                when (flow) {
                    Flow.SPLASH -> {}
                    Flow.CAMERA -> { stopCamera(); showFlow(Flow.EDITOR) }
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
        val logoAnim = StudioIcons.BrandSplashView(this)
        centerCol.addView(logoAnim, LinearLayout.LayoutParams(dp(196), dp(196)))
        centerCol.addView(TextView(this).apply {
            text = L.t("app")
            textSize = 25f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        })
        centerCol.addView(TextView(this).apply {
            text = L.t("splash_sub")
            textSize = 13.5f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
        })
        centerCol.scaleX = 0.85f
        centerCol.scaleY = 0.85f
        centerCol.alpha = 0f
        centerCol.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(460).start()

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
                p = (p + 4).coerceAtMost(100)
                bar.progress = p
                pctTv.text = "${L.t("loading")}... $p%"
                if (p < 100) handler.postDelayed(this, 45)
                else handler.postDelayed({
                    billingAds.showStartupAd(this@MainActivity) {
                        runOnUiThread { showFlow(Flow.MAIN) }
                    }
                }, 140)
            }
        }, 45)
    }

    private fun showFlow(target: Flow) {
        applyInsetsCallback = null
        if (flow == Flow.CAMERA && target != Flow.CAMERA) stopCamera()
        if (flow == Flow.BROWSER && target != Flow.BROWSER) destroyWebView()
        flow = target
        when (target) {
            Flow.MAIN -> renderMain()
            Flow.LANG -> renderLanguagePage()
            Flow.PRIVACY -> renderPrivacyPage()
            Flow.ANIME_CHARS -> renderAnimeCharsPage()
            Flow.CHAR_GALLERY -> renderCharacterGallery()
            Flow.BROWSER -> renderBrowser()
            Flow.EDITOR -> renderEditor()
            Flow.CAMERA -> renderCamera()
            Flow.CONGRATS -> renderCongrats()
            Flow.SPLASH -> showSplash()
        }
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
            elevation = dp(4).toFloat()
        }
        topBar.addView(
            ImageView(this).apply {
                setImageDrawable(StudioIcons.brandLogoDrawable(this@MainActivity, 32))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = L.t("app")
            },
            LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
        )
        topBar.addView(TextView(this).apply {
            text = when (currentTab) {
                Tab.HOME -> L.t("app")
                Tab.GALLERY -> L.t("my_activity")
                Tab.STORE -> L.t("store_title")
                Tab.SETTINGS -> L.t("settings")
            }
            textSize = 18.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

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
                background = UiTheme.grad(this@MainActivity, "#10B981", "#059669", 12)
                setPadding(dp(8), dp(4), dp(8), dp(4))
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
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 18, UiTheme.accentBright)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            UiTheme.bounce(this) { currentTab = Tab.STORE; renderMain() }
        }
        topBar.addView(coinWalletPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)))
        mainCol.addView(topBar)

        val body = when (currentTab) {
            Tab.HOME -> UiTabs.buildHome(
                this,
                { showCuteSourcePopup() },
                { openCamCapture() },
                { importToGalleryMode = false; pickGalleryLauncher.launch("image/*") },
                { currentTab = Tab.GALLERY; renderMain() },
                { openEditorWithBitmap(it) },
                { q -> browserQuery = q; browserReturnFlow = Flow.MAIN; showFlow(Flow.BROWSER) },
                { series -> selectedSeries = series; showFlow(Flow.ANIME_CHARS) },
                { ch, series ->
                    selectedCharacterName = ch
                    selectedCharacterSeries = series
                    charGalleryReturnFlow = Flow.MAIN
                    showFlow(Flow.CHAR_GALLERY)
                },
                { renderMain() }
            )
            Tab.GALLERY -> UiTabs.buildGallery(this, galleryDir, prefs, { showCuteImportPopup() }, { exportZipFiles(it) }, { openEditorWithBitmap(it) }, { renderMain() })
            Tab.STORE -> UiTabs.buildStore(this, billingAds)
            Tab.SETTINGS -> UiTabs.buildSettings(this, prefs, { showFlow(Flow.LANG) }, { showFlow(Flow.PRIVACY) }) { renderMain() }
        }
        mainCol.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        if (!billingAds.adsRemoved) {
            val adBox = FrameLayout(this).apply { setBackgroundColor(UiTheme.bg) }
            billingAds.attachBanner(adBox)
            mainCol.addView(adBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        // Floating Capsule Studio Dock with Crisp Vector Icons
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 24, UiTheme.stroke, 1)
            setPadding(dp(8), dp(7), dp(8), dp(7))
            elevation = dp(12).toFloat()
        }
        listOf(
            Triple(Tab.HOME, StudioIcons.Type.HOME, L.t("home")),
            Triple(Tab.GALLERY, StudioIcons.Type.GALLERY, L.t("my_gallery")),
            Triple(Tab.STORE, StudioIcons.Type.STORE, L.t("store")),
            Triple(Tab.SETTINGS, StudioIcons.Type.SETTINGS, L.t("settings"))
        ).forEach { (tab, icon, label) ->
            val sel = currentTab == tab
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                background = if (sel) UiTheme.grad(this@MainActivity, "#7C3AED", "#4F46E5", 18) else null
                elevation = if (sel) dp(4).toFloat() else 0f
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
                    background = UiTheme.round(
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
                    if (bundled.isNotEmpty()) {
                        nameCol.addView(TextView(this@MainActivity).apply {
                            text = "${bundled.size} ${L.t("sketches_ready").replace("{n}", bundled.size.toString()).substringAfter(" ")}"
                            textSize = 11f
                            UiTheme.applyUiFont(this, bold = true)
                            setTextColor(UiTheme.accentBright)
                        })
                    }
                    addView(nameCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

                    if (bundled.isNotEmpty()) {
                        addView(
                            StudioIcons.iconPill(
                                context = this@MainActivity,
                                iconType = StudioIcons.Type.ARROW_RIGHT,
                                label = "${bundled.size}",
                                textColor = Color.WHITE,
                                iconColor = Color.WHITE,
                                iconSizeDp = 12,
                                textSizeSp = 12f
                            ).apply {
                                background = UiTheme.grad(this@MainActivity, "#7C3AED", "#4F46E5", 10)
                                setPadding(dp(11), dp(6), dp(11), dp(6))
                            }
                        )
                    } else {
                        addView(
                            StudioIcons.iconPill(
                                context = this@MainActivity,
                                iconType = StudioIcons.Type.SEARCH,
                                label = L.t("search_btn").replace("›", "").trim(),
                                textColor = UiTheme.accentBright,
                                iconColor = UiTheme.accentBright,
                                iconSizeDp = 13,
                                textSizeSp = 12f
                            ).apply {
                                background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 10)
                                setPadding(dp(9), dp(5), dp(9), dp(5))
                            }
                        )
                    }
                    UiTheme.bounce(this) {
                        selectedCharacterName = ch
                        selectedCharacterSeries = selectedSeries.name
                        charGalleryReturnFlow = Flow.ANIME_CHARS
                        showFlow(Flow.CHAR_GALLERY)
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
                        StudioIcons.imageView(this@MainActivity, StudioIcons.Type.SEARCH, 18, UiTheme.accentBright),
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
                            iconType = StudioIcons.Type.ARROW_RIGHT,
                            label = "5",
                            textColor = Color.WHITE,
                            iconColor = Color.WHITE,
                            iconSizeDp = 12,
                            textSizeSp = 11f
                        ).apply {
                            background = UiTheme.grad(this@MainActivity, "#7C3AED", "#4F46E5", 10)
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                        }
                    )
                    UiTheme.bounce(this) {
                        selectedCharacterName = customChar
                        selectedCharacterSeries = selectedSeries.name
                        charGalleryReturnFlow = Flow.ANIME_CHARS
                        showFlow(Flow.CHAR_GALLERY)
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
                        iv.setImageDrawable(StudioIcons.icon(this@MainActivity, StudioIcons.Type.ANIME_STAR, 32, Color.parseColor("#E5E7EB")))
                        val miniSpin = ProgressBar(this@MainActivity).apply { isIndeterminate = true }
                        addView(miniSpin, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.BOTTOM or Gravity.END).apply {
                            setMargins(0, 0, dp(4), dp(4))
                        })
                        SketchSearchRepository.loadThumbBitmap(this@MainActivity, item) { bmp ->
                            miniSpin.visibility = View.GONE
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
            q.contains("drawing", ignoreCase = true)
        val tbs = if (isSketch) "&tbs=ic:gray" else ""
        return "https://www.google.com/search?tbm=isch&q=${Uri.encode(q)}$tbs"
    }

    private fun openInChrome(query: String) {
        val clean = query.trim().ifEmpty { "Anime black and white sketch drawing" }
        val googleUrl = "https://www.google.com/search?tbm=isch&q=${Uri.encode(clean)}&tbs=ic:gray"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(googleUrl)).apply {
                setPackage("com.android.chrome")
            })
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(googleUrl)))
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
            ) {
                val q = barInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    browserQuery = q
                    wv.loadUrl(buildBingImagesUrl(q))
                }
            },
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

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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

    private fun decodeRemoteImage(src: String): Bitmap? {
        if (src.isEmpty()) return null
        return try {
            if (src.startsWith("data:image")) {
                val bytes = Base64.decode(src.substringAfter("base64,", ""), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else if (src.startsWith("http")) {
                val conn = URL(src).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000; conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (_: Exception) { null }
    }

    private fun saveOnlineImageAndDraw(fullUrl: String, thumbUrl: String) {
        runOnUiThread { Toast.makeText(this, L.t("saving_image"), Toast.LENGTH_SHORT).show() }
        ioExecutor.execute {
            val bmp = decodeRemoteImage(fullUrl) ?: decodeRemoteImage(thumbUrl)
            if (bmp != null) {
                val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
                val clean = if (
                    browserQuery.contains("sketch", ignoreCase = true) ||
                    browserQuery.contains("black and white", ignoreCase = true) ||
                    browserQuery.contains("drawing", ignoreCase = true)
                ) {
                    ImageProcessor.toCleanBwSketch(argb)
                } else {
                    argb
                }
                saveToGallery(clean, "Google")
                runOnUiThread { destroyWebView(); openEditorWithBitmap(clean) }
            } else {
                runOnUiThread { Toast.makeText(this, L.t("import_fail"), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun destroyWebView() { activeWebView?.destroy(); activeWebView = null }

    private fun showCuteSourcePopup() {
        val backdrop = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#88000000")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 26, UiTheme.stroke)
            setPadding(dp(20), dp(22), dp(20), dp(20))
            elevation = dp(12).toFloat()
        }
        card.addView(TextView(this).apply {
            text = L.t("choose_source")
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun optBtn(icon: StudioIcons.Type, title: String, c1: String, c2: String, onTap: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiTheme.grad(this@MainActivity, c1, c2, 22, Color.parseColor("#33FFFFFF"))
            setPadding(dp(12), dp(20), dp(12), dp(20))
            elevation = dp(5).toFloat()
            val well = FrameLayout(this@MainActivity).apply {
                background = UiTheme.round(this@MainActivity, Color.parseColor("#28FFFFFF"), 18)
                addView(
                    StudioIcons.imageView(this@MainActivity, icon, 28, Color.WHITE),
                    FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                )
            }
            addView(well, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })
            UiTheme.bounce(this) { root.removeView(backdrop); onTap() }
        }
        row.addView(optBtn(StudioIcons.Type.CAMERA, L.t("take_photo"), "#8B5CF6", "#6D28D9") { openCamCapture() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
        row.addView(optBtn(StudioIcons.Type.PHOTO, L.t("upload_gallery"), "#EC4899", "#DB2777") { importToGalleryMode = false; pickGalleryLauncher.launch("image/*") }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        card.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
        card.addView(
            UiTheme.studioSoftAction(
                act = this,
                icon = StudioIcons.Type.CLOSE,
                label = L.t("cancel"),
                textColor = UiTheme.textSub,
                iconColor = UiTheme.textSub,
                rDp = 15
            ) { root.removeView(backdrop) },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(48))
        )
        backdrop.setOnClickListener { root.removeView(backdrop) }; card.setOnClickListener { }
        backdrop.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply { setMargins(dp(24), 0, dp(24), 0) })
        root.addView(backdrop, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun showCuteImportPopup() {
        val backdrop = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#88000000")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 26, UiTheme.stroke)
            setPadding(dp(20), dp(22), dp(20), dp(20))
            elevation = dp(12).toFloat()
        }
        card.addView(TextView(this).apply {
            text = L.t("import_title")
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        card.addView(
            UiTheme.studioGradientAction(this, StudioIcons.Type.FOLDER, L.t("choose_file"), "#3B82F6", "#2563EB", 16) {
                root.removeView(backdrop); pickAnyFileLauncher.launch("*/*")
            },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { bottomMargin = dp(10) }
        )
        card.addView(
            UiTheme.studioGradientAction(this, StudioIcons.Type.LINK, L.t("paste_link"), "#8B5CF6", "#6D28D9", 16) {
                root.removeView(backdrop); showLinkInputDialog()
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
            ) { root.removeView(backdrop) },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(48))
        )
        backdrop.setOnClickListener { root.removeView(backdrop) }; card.setOnClickListener { }
        backdrop.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply { setMargins(dp(24), 0, dp(24), 0) })
        root.addView(backdrop, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
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
        val cropBtn = makeToolBox()
        val pickColorBtn = makeToolBox()
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
        pickPanel.addView(SeekBar(this).apply {
            max = 90; progress = colorTolerance
            setOnSeekBarChangeListener(simpleSeek { p ->
                colorTolerance = p.coerceAtLeast(8)
                tolLabel.text = "${L.t("tolerance")}: $colorTolerance%"
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val pickActionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val removePickedBtn = makeToolBox().apply {
            background = UiTheme.grad(this@MainActivity, "#EF4444", "#DC2626", 14)
            elevation = dp(3).toFloat()
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
            autoBgBtn.background = UiTheme.grad(this, "#3B82F6", "#2563EB", 15, Color.parseColor("#33FFFFFF"))
            autoBgBtn.elevation = dp(4).toFloat()

            setToolContent(keepLinesBtn, StudioIcons.Type.INK_PEN, if (isProcessingBg) L.t("removing_bg") else L.t("keep_lines"), Color.WHITE)
            keepLinesBtn.background = UiTheme.grad(this, "#8B5CF6", "#6D28D9", 15, Color.parseColor("#33FFFFFF"))
            keepLinesBtn.elevation = dp(4).toFloat()

            setToolContent(cropBtn, StudioIcons.Type.CROP, L.t("crop_resize"), Color.WHITE)
            cropBtn.background = UiTheme.grad(this, "#0EA5E9", "#0284C7", 15, Color.parseColor("#33FFFFFF"))
            cropBtn.elevation = dp(4).toFloat()

            if (isColorPickMode) {
                setToolContent(pickColorBtn, StudioIcons.Type.EYEDROPPER, L.t("pick_color_btn"), Color.WHITE)
                pickColorBtn.background = UiTheme.grad(this, "#EC4899", "#DB2777", 15, Color.parseColor("#33FFFFFF"))
                pickColorBtn.elevation = dp(4).toFloat()
            } else {
                setToolContent(pickColorBtn, StudioIcons.Type.EYEDROPPER, L.t("pick_color_btn"), UiTheme.textMain)
                pickColorBtn.background = UiTheme.round(this, UiTheme.surface, 15, UiTheme.stroke)
                pickColorBtn.elevation = dp(2).toFloat()
            }

            val canRestore = bgRemoved && cutoutBitmap != null
            setToolContent(
                undoBtn,
                if (editHistory.isNotEmpty()) StudioIcons.Type.UNDO else StudioIcons.Type.RESTORE,
                if (editHistory.isNotEmpty()) L.t("undo_step") else L.t("restore_orig"),
                UiTheme.textMain
            )
            undoBtn.isEnabled = canRestore && !isProcessingBg
            undoBtn.alpha = if (undoBtn.isEnabled) 1f else 0.45f
            undoBtn.background = UiTheme.round(this, UiTheme.surfaceAlt, 15, UiTheme.stroke)

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
            if (prev != null && bgRemoved) editHistory.add(prev)
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
            ImageProcessor.removeBackgroundAsync(cur) { res -> runOnUiThread { applyProcessed(res) } }
        }

        UiTheme.bounce(keepLinesBtn) {
            if (isProcessingBg) return@bounce
            val cur = activeEditBitmap() ?: return@bounce
            isProcessingBg = true; isColorPickMode = false; sync()
            ImageProcessor.extractInkLinesAsync(cur, sensitivity = 58) { res -> runOnUiThread { applyProcessed(res) } }
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
        row2.addView(cropBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        row2.addView(pickColorBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        col.addView(row2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

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
        opCard.addView(SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                opacityPercent = p.coerceAtLeast(10)
                opPctBadge.text = "$opacityPercent%"
                previewIv.alpha = opacityPercent / 100f
            })
        })
        col.addView(opCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        val startArBtn = UiTheme.studioGradientAction(
            act = this,
            icon = StudioIcons.Type.CAMERA,
            label = L.t("next"),
            hex1 = "#10B981",
            hex2 = "#059669",
            rDp = 18,
            trailingIcon = StudioIcons.Type.ARROW_RIGHT,
            iconSizeDp = 19,
            textSizeSp = 15f
        ) {
            if (originalBitmap == null) Toast.makeText(this, L.t("need_image"), Toast.LENGTH_SHORT).show()
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showFlow(Flow.CAMERA)
            else { openCameraAfterPermission = false; cameraPermLauncher.launch(Manifest.permission.CAMERA) }
        }
        col.addView(startArBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(54)).apply { bottomMargin = dp(12) })
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
        val gridPill = StudioIcons.iconPill(
            context = this,
            iconType = StudioIcons.Type.GRID,
            label = "3×3",
            textColor = Color.WHITE,
            iconColor = Color.parseColor("#CBD5E1"),
            iconSizeDp = 15,
            textSizeSp = 12f
        ).apply {
            background = UiTheme.round(this@MainActivity, Color.parseColor("#1AFFFFFF"), 12, Color.parseColor("#28FFFFFF"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        fun syncGridPill() {
            val on = overlay.gridEnabled
            gridPill.background = if (on) {
                UiTheme.round(this, Color.parseColor("#3338BDF8"), 12, Color.parseColor("#38BDF8"))
            } else {
                UiTheme.round(this, Color.parseColor("#1AFFFFFF"), 12, Color.parseColor("#28FFFFFF"))
            }
        }
        UiTheme.bounce(gridPill) {
            overlay.setGridEnabled(!overlay.gridEnabled)
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
        quickRow.addView(gridPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(34)).apply { marginEnd = dp(8) })
        quickRow.addView(resetPosPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(34)))
        bottomCard.addView(quickRow)

        topPill.addView(
            pillIconBtn(StudioIcons.Type.CLOSE, Color.WHITE, desc = L.t("back")) { _, _ ->
                stopCamera(); showFlow(Flow.EDITOR)
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.FLIP_H, Color.WHITE, desc = "Flip") { _, _ ->
                val m = Matrix().apply { postScale(-1f, 1f) }
                activeBmp = Bitmap.createBitmap(activeBmp, 0, 0, activeBmp.width, activeBmp.height, m, true)
                overlay.setBitmap(activeBmp, resetPos = false)
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
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
            LinearLayout.LayoutParams(dp(44), dp(44))
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
            LinearLayout.LayoutParams(dp(44), dp(44))
        )
        topPill.addView(
            pillIconBtn(StudioIcons.Type.CHECK, Color.parseColor("#34D399"), desc = L.t("save")) { _, _ ->
                saveToGallery(activeBmp, L.t("art_prefix")); stopCamera()
                billingAds.showRewardedOnComplete(this@MainActivity) { runOnUiThread { showFlow(Flow.CONGRATS) } }
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
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
        bottomCard.addView(SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                opacityPercent = p.coerceAtLeast(10)
                opTv.text = "${L.t("opacity")}  $opacityPercent%"
                overlay.setOpacity(opacityPercent)
            })
        })

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
        bottomCard.addView(SeekBar(this).apply {
            max = 100; progress = brightnessPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                brightnessPercent = p.coerceAtLeast(10)
                brTv.text = "${L.t("brightness")}  $brightnessPercent%"
                window.attributes = window.attributes.apply { screenBrightness = brightnessPercent / 100f }
            })
        })

        val hideBtn = FrameLayout(this).apply {
            background = UiTheme.round(this@MainActivity, glassFill, 24, glassStroke)
            elevation = dp(8).toFloat()
        }
        val hideIv = StudioIcons.imageView(this, StudioIcons.Type.EYE_HIDE, 20, Color.WHITE)
        hideBtn.addView(hideIv, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))

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
            hideIv.setImageDrawable(
                StudioIcons.icon(
                    this,
                    if (show) StudioIcons.Type.EYE_HIDE else StudioIcons.Type.EXPAND,
                    20,
                    Color.WHITE
                )
            )
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
        topBar.addView(topPill, FrameLayout.LayoutParams(WRAP_CONTENT, dp(52), Gravity.START))
        topBar.addView(hideBtn, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))

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
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 28, UiTheme.stroke)
            setPadding(dp(24), dp(28), dp(24), dp(26))
            elevation = dp(10).toFloat()
        }
        card.addView(
            StudioIcons.CelebrationArtView(this),
            LinearLayout.LayoutParams(dp(136), dp(136)).apply { bottomMargin = dp(12) }
        )
        card.addView(TextView(this).apply {
            text = L.t("congrats")
            textSize = 25f
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
        card.addView(
            UiTheme.studioGradientAction(this, StudioIcons.Type.HOME, L.t("go_home"), "#7C3AED", "#4F46E5", 16) {
                showFlow(Flow.MAIN)
            },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(52))
        )
        col.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
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
        window.attributes = window.attributes.apply { screenBrightness = -1f }
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
            if (importToGalleryMode) { saveToGallery(clean, "Imported"); renderMain() } else openEditorWithBitmap(clean)
        } catch (_: Exception) { Toast.makeText(this, L.t("image_failed"), Toast.LENGTH_LONG).show() }
    }

    private fun saveToGallery(bmp: Bitmap?, prefix: String) {
        if (bmp == null) return
        try {
            val s = 512f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            val thumb = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
            val name = "art_${System.currentTimeMillis()}.png"
            FileOutputStream(File(galleryDir, name)).use { thumb.compress(Bitmap.CompressFormat.PNG, 92, it) }
            prefs.edit().putString("gname_$name", "$prefix #${galleryDir.listFiles()?.size ?: 1}").apply()
        } catch (_: Exception) {}
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun dp(v: Int): Int = UiTheme.dp(this, v)

    override fun onDestroy() {
        super.onDestroy(); stopCamera(); destroyWebView(); billingAds.destroy()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
    }
}
