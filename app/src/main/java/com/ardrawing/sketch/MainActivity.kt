package com.ardrawing.sketch

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.Button
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Tab { HOME, GALLERY, STORE, SETTINGS }
    private enum class Flow { SPLASH, MAIN, EDITOR, CAMERA, CONGRATS }

    private var flow = Flow.SPLASH
    private var currentTab = Tab.HOME
    private lateinit var root: FrameLayout
    private lateinit var analysisExecutor: ExecutorService
    private val handler = Handler(Looper.getMainLooper())
    private val tracker by lazy { PlaneTracker() }

    private lateinit var billingAds: BillingAdsManager
    private val prefs by lazy { getSharedPreferences("ar_draw_prefs", MODE_PRIVATE) }
    private val galleryDir by lazy { File(filesDir, "gallery").apply { mkdirs() } }

    private var originalBitmap: Bitmap? = null
    private var cutoutBitmap: Bitmap? = null
    private var bgRemoved = false
    private var isProcessingBg = false
    private var opacityPercent = 80
    private var brightnessPercent = 80

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var torchOn = false
    private var controlsHidden = false
    private var overlayView: OverlayView? = null
    private var cameraHintView: TextView? = null
    private var pendingCameraUri: Uri? = null
    private var importToGalleryMode = false
    private var openCameraAfterPermission = false

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handlePickedUri(uri)
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
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (flow) {
                    Flow.SPLASH -> {}
                    Flow.CAMERA -> { stopCamera(); showFlow(Flow.EDITOR) }
                    Flow.EDITOR, Flow.CONGRATS -> showFlow(Flow.MAIN)
                    Flow.MAIN -> if (currentTab != Tab.HOME) { currentTab = Tab.HOME; renderMain() } else finish()
                }
            }
        })
        showSplash()
    }

    private fun loadSafeLogo(maxPx: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, R.drawable.app_logo, o)
        var s = 1
        while (o.outWidth / s > maxPx || o.outHeight / s > maxPx) s *= 2
        BitmapFactory.decodeResource(resources, R.drawable.app_logo, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Throwable) { null }

    private fun showSplash() {
        flow = Flow.SPLASH
        root.removeAllViews()
        val wrap = FrameLayout(this).apply {
            background = UiTheme.grad(this@MainActivity, if (UiTheme.isDark) "#0B0F19" else "#F3E8FF", if (UiTheme.isDark) "#1E1B4B" else "#FFFFFF", 0)
        }
        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        val logoCard = FrameLayout(this).apply {
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 28)
            elevation = dp(10).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val logoIv = ImageView(this).apply {
            loadSafeLogo(320)?.let { setImageBitmap(it) } ?: setImageResource(R.drawable.app_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(24).toFloat()) }
            }
            clipToOutline = true
        }
        logoCard.addView(logoIv, FrameLayout.LayoutParams(dp(120), dp(120)))
        centerCol.addView(logoCard)
        centerCol.addView(TextView(this).apply {
            text = "Free Art Center"; textSize = 24f; setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain); setPadding(0, dp(16), 0, 0)
        })

        val bottomCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(36), 0, dp(36), dp(36))
        }
        val pctTv = TextView(this).apply { text = "${L.t("loading")}... 1%"; textSize = 13f; setTextColor(UiTheme.textSub); setPadding(0, 0, 0, dp(8)) }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 1 }
        val adsNote = TextView(this).apply { text = "This action may contain Ads"; textSize = 12f; setTextColor(UiTheme.textSub); setPadding(0, dp(10), 0, 0) }
        bottomCol.addView(pctTv)
        bottomCol.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
        bottomCol.addView(adsNote)

        wrap.addView(centerCol, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        wrap.addView(bottomCol, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(wrap, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        var p = 1
        handler.postDelayed(object : Runnable {
            override fun run() {
                p = (p + 3).coerceAtMost(100)
                bar.progress = p; pctTv.text = "${L.t("loading")}... $p%"
                if (p < 100) handler.postDelayed(this, 55) else handler.postDelayed({ showFlow(Flow.MAIN) }, 200)
            }
        }, 60)
    }

    private fun showFlow(target: Flow) {
        if (flow == Flow.CAMERA && target != Flow.CAMERA) stopCamera()
        flow = target
        when (target) {
            Flow.MAIN -> renderMain()
            Flow.EDITOR -> renderEditor()
            Flow.CAMERA -> renderCamera()
            Flow.CONGRATS -> renderCongrats()
            Flow.SPLASH -> showSplash()
        }
    }

    private fun renderMain() {
        root.removeAllViews()
        root.setBackgroundColor(UiTheme.bg)
        val mainCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiTheme.surface); setPadding(dp(16), dp(34), dp(16), dp(12)); elevation = dp(2).toFloat()
        }
        topBar.addView(TextView(this).apply {
            text = when (currentTab) {
                Tab.HOME -> "Free Art Center"; Tab.GALLERY -> L.t("my_activity"); Tab.STORE -> L.t("store_title"); Tab.SETTINGS -> L.t("settings")
            }
            textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(if (currentTab == Tab.HOME) UiTheme.accent else UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBar.addView(Button(this).apply {
            text = "🪙 ${billingAds.coins}"; setTextColor(UiTheme.accent); textSize = 14f; isAllCaps = false; setTypeface(null, Typeface.BOLD)
            background = UiTheme.round(this@MainActivity, UiTheme.surfaceAlt, 20, UiTheme.stroke); setPadding(dp(14), 0, dp(14), 0)
            UiTheme.bounce(this) { currentTab = Tab.STORE; renderMain() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)))
        mainCol.addView(topBar)

        val body = when (currentTab) {
            Tab.HOME -> UiTabs.buildHome(this, { showSourceChooser() }, { openCamCapture() }, { importToGalleryMode = false; pickGalleryLauncher.launch("image/*") }, { currentTab = Tab.GALLERY; renderMain() }, { openEditorWithBitmap(it) })
            Tab.GALLERY -> UiTabs.buildGallery(this, galleryDir, prefs, { importToGalleryMode = true; pickGalleryLauncher.launch("image/*") }, { openEditorWithBitmap(it) }, { renderMain() })
            Tab.STORE -> UiTabs.buildStore(this, billingAds)
            Tab.SETTINGS -> UiTabs.buildSettings(this, prefs) { renderMain() }
        }
        mainCol.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        if (!billingAds.adsRemoved) {
            val adBox = FrameLayout(this).apply { setBackgroundColor(UiTheme.bg) }
            billingAds.attachBanner(adBox)
            mainCol.addView(adBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(UiTheme.surface); setPadding(dp(8), dp(8), dp(8), dp(14)); elevation = dp(8).toFloat()
        }
        listOf(
            Triple(Tab.HOME, "🏠", L.t("home")), Triple(Tab.GALLERY, "📁", L.t("my_gallery")),
            Triple(Tab.STORE, "🛒", L.t("store")), Triple(Tab.SETTINGS, "⚙️", L.t("settings"))
        ).forEach { (tab, icon, label) ->
            val sel = currentTab == tab
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(2), dp(6), dp(2), dp(6))
                background = if (sel) UiTheme.round(this@MainActivity, UiTheme.surfaceAlt, 14) else null
                addView(TextView(this@MainActivity).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER })
                addView(TextView(this@MainActivity).apply {
                    text = label; textSize = 11f; gravity = Gravity.CENTER; maxLines = 1
                    setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (sel) UiTheme.accent else UiTheme.textSub)
                })
                UiTheme.bounce(this) { currentTab = tab; renderMain() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        mainCol.addView(nav)
        root.addView(mainCol, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun openCamCapture() {
        importToGalleryMode = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchTakePhoto()
        else { openCameraAfterPermission = true; cameraPermLauncher.launch(Manifest.permission.CAMERA) }
    }

    private fun showSourceChooser() {
        AlertDialog.Builder(this).setTitle(L.t("choose_source"))
            .setItems(arrayOf("📷  ${L.t("take_photo")}", "🖼️  ${L.t("upload_gallery")}")) { _, w ->
                if (w == 0) openCamCapture() else { importToGalleryMode = false; pickGalleryLauncher.launch("image/*") }
            }.setNegativeButton(L.t("cancel"), null).show()
    }

    private fun openEditorWithBitmap(bmp: Bitmap) {
        originalBitmap = bmp; cutoutBitmap = null; bgRemoved = false; brightnessPercent = 80; opacityPercent = 80
        showFlow(Flow.EDITOR)
    }

    private fun renderEditor() {
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(36), dp(18), dp(24)) }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(14)) }
        topRow.addView(Button(this).apply {
            text = "←"; setTextColor(UiTheme.textMain); background = UiTheme.round(this@MainActivity, UiTheme.surface, 14, UiTheme.stroke)
            UiTheme.bounce(this) { showFlow(Flow.MAIN) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        topRow.addView(TextView(this).apply { text = L.t("drawing_create"); textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain) })
        col.addView(topRow)

        val previewIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER; background = UiTheme.round(this@MainActivity, UiTheme.surface, 20, UiTheme.stroke)
            setPadding(dp(10), dp(10), dp(10), dp(10)); elevation = dp(3).toFloat()
        }
        col.addView(previewIv, LinearLayout.LayoutParams(MATCH_PARENT, dp(270)).apply { bottomMargin = dp(16) })

        val bgBtn = Button(this).apply { setTextColor(Color.WHITE); isAllCaps = false; textSize = 15f; setTypeface(null, Typeface.BOLD); elevation = dp(3).toFloat() }
        fun sync() {
            previewIv.setImageBitmap(if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap)
            previewIv.alpha = opacityPercent / 100f
            bgBtn.text = when { isProcessingBg -> L.t("removing_bg"); bgRemoved -> "↩️  ${L.t("restore_orig")}"; else -> "✂️  ${L.t("remove_bg")}" }
            bgBtn.background = UiTheme.grad(this, if (bgRemoved) "#F59E0B" else "#3B82F6", if (bgRemoved) "#D97706" else "#2563EB", 16)
        }
        sync()
        UiTheme.bounce(bgBtn) {
            if (isProcessingBg) return@bounce
            val orig = originalBitmap ?: return@bounce
            if (bgRemoved) { bgRemoved = false; sync() }
            else if (cutoutBitmap != null) { bgRemoved = true; sync() }
            else {
                isProcessingBg = true; sync()
                ImageProcessor.removeBackgroundAsync(orig) { res -> runOnUiThread { isProcessingBg = false; cutoutBitmap = res; bgRemoved = true; sync() } }
            }
        }
        col.addView(bgBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { bottomMargin = dp(14) })
        col.addView(TextView(this).apply { text = "${L.t("opacity")}  $opacityPercent%"; setTextColor(UiTheme.textMain); textSize = 14f })
        col.addView(SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p -> opacityPercent = p.coerceAtLeast(10); previewIv.alpha = opacityPercent / 100f })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(18) })
        col.addView(UiTabs.cuteBtn(this, "${L.t("next")}  →", "#10B981", "#059669", 54) {
            if (originalBitmap == null) Toast.makeText(this, L.t("need_image"), Toast.LENGTH_SHORT).show()
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showFlow(Flow.CAMERA)
            else { openCameraAfterPermission = false; cameraPermLauncher.launch(Manifest.permission.CAMERA) }
        })
        root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun renderCamera() {
        root.removeAllViews(); torchOn = false; controlsHidden = false
        var activeBmp = (if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap) ?: return
        val previewView = PreviewView(this)
        val overlay = OverlayView(this).apply { setBitmap(activeBmp, true); setOpacity(opacityPercent) }
        overlayView = overlay

        // Top-Left Floating Pill (Close, Flip, Flash, Lock, Done)
        val topPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 26); elevation = dp(6).toFloat()
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun pillIcon(txt: String, onTap: (TextView) -> Unit): TextView = TextView(this).apply {
            text = txt; textSize = 18f; gravity = Gravity.CENTER; setTextColor(UiTheme.accent)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            UiTheme.bounce(this) { onTap(this) }
        }
        topPill.addView(pillIcon("✕") { stopCamera(); showFlow(Flow.EDITOR) })
        topPill.addView(pillIcon("⇄") {
            val m = Matrix().apply { postScale(-1f, 1f) }
            activeBmp = Bitmap.createBitmap(activeBmp, 0, 0, activeBmp.width, activeBmp.height, m, true)
            overlay.setBitmap(activeBmp, resetPos = false)
        })
        topPill.addView(pillIcon("⚡") { tv ->
            torchOn = !torchOn; boundCamera?.cameraControl?.enableTorch(torchOn)
            tv.setTextColor(if (torchOn) Color.parseColor("#F59E0B") else UiTheme.accent)
        })
        topPill.addView(pillIcon("🔒") { tv ->
            if (!overlay.isLocked) {
                tracker.startLock(); overlay.lock(); tv.text = "🔓"
                tv.setTextColor(Color.parseColor("#10B981")); cameraHintView?.text = L.t("hint_locked")
            } else {
                tracker.stopLock(); overlay.unlock(); tv.text = "🔒"
                tv.setTextColor(UiTheme.accent); cameraHintView?.text = L.t("hint_place")
            }
        })
        topPill.addView(pillIcon("✓") {
            saveToGallery(activeBmp, "Art"); stopCamera()
            billingAds.showInterstitial(this@MainActivity) { runOnUiThread { showFlow(Flow.CONGRATS) } }
        })

        // Bottom Floating Transparency & Brightness Card
        val bottomCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = UiTheme.round(this@MainActivity, UiTheme.surface, 22)
            elevation = dp(8).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        val opTv = TextView(this).apply { text = "💧  ${L.t("opacity")}  $opacityPercent%"; setTextColor(UiTheme.textMain); textSize = 13f; setTypeface(null, Typeface.BOLD) }
        val brTv = TextView(this).apply { text = "☀️  ${L.t("brightness")}  $brightnessPercent%"; setTextColor(UiTheme.textMain); textSize = 13f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, 0) }
        bottomCard.addView(opTv)
        bottomCard.addView(SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p -> opacityPercent = p.coerceAtLeast(10); opTv.text = "💧  ${L.t("opacity")}  $opacityPercent%"; overlay.setOpacity(opacityPercent) })
        })
        bottomCard.addView(brTv)
        bottomCard.addView(SeekBar(this).apply {
            max = 100; progress = brightnessPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                brightnessPercent = p.coerceAtLeast(10); brTv.text = "☀️  ${L.t("brightness")}  $brightnessPercent%"
                window.attributes = window.attributes.apply { screenBrightness = brightnessPercent / 100f }
            })
        })

        // Top-Right Hide/Show All UI Circle Button
        val hideBtn = TextView(this).apply {
            text = "⛶"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(UiTheme.accent)
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 24); elevation = dp(6).toFloat()
        }
        val hintTv = TextView(this).apply {
            text = L.t("hint_place"); setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            background = UiTheme.round(this@MainActivity, Color.parseColor("#88000000"), 12); setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        cameraHintView = hintTv

        fun setControlsVisible(show: Boolean) {
            controlsHidden = !show
            val vis = if (show) View.VISIBLE else View.GONE
            topPill.visibility = vis; bottomCard.visibility = vis; hintTv.visibility = vis
            hideBtn.text = if (show) "⛶" else "✕"
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

        val topBar = FrameLayout(this).apply { setPadding(dp(14), dp(36), dp(14), 0) }
        topBar.addView(topPill, FrameLayout.LayoutParams(WRAP_CONTENT, dp(48), Gravity.START))
        topBar.addView(hideBtn, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END))

        root.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        root.addView(hintTv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(92) })
        root.addView(bottomCard, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(dp(16), 0, dp(16), dp(22)) })
        startCamera(previewView)
    }

    private fun renderCongrats() {
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)) }
        col.addView(TextView(this).apply { text = L.t("congrats"); textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain); gravity = Gravity.CENTER })
        col.addView(TextView(this).apply { text = L.t("good_luck"); textSize = 16f; setTextColor(UiTheme.textSub); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(28)) })
        col.addView(UiTabs.cuteBtn(this, L.t("go_home"), "#8B5CF6", "#6D28D9", 54) { showFlow(Flow.MAIN) })
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun startCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get(); cameraProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val out = tracker.process(image); val ov = overlayView
                        if (ov != null && ov.isLocked) runOnUiThread { ov.updateTracking(out); cameraHintView?.text = if (out.good) L.t("hint_locked") else L.t("hint_lost") }
                    } finally { image.close() }
                }
                provider.unbindAll()
                boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { Toast.makeText(this, L.t("camera_error"), Toast.LENGTH_LONG).show() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try { boundCamera?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
        window.attributes = window.attributes.apply { screenBrightness = -1f }
        boundCamera = null; torchOn = false; tracker.stopLock(); cameraProvider?.unbindAll(); overlayView = null; cameraHintView = null
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

    override fun onDestroy() { super.onDestroy(); stopCamera(); billingAds.destroy(); if (::analysisExecutor.isInitialized) analysisExecutor.shutdown() }
}
