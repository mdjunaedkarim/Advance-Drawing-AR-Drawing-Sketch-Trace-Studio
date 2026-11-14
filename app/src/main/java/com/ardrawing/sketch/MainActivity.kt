package com.ardrawing.sketch

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
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
import android.widget.Button
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    private enum class Flow { SPLASH, MAIN, LANG, PRIVACY, ANIME_CHARS, BROWSER, EDITOR, CAMERA, CONGRATS }

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
    private var browserQuery = "Anime easy line drawing"
    private var activeWebView: WebView? = null

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
                    Flow.BROWSER -> {
                        val wv = activeWebView
                        if (wv != null && wv.canGoBack()) wv.goBack()
                        else { destroyWebView(); showFlow(Flow.MAIN) }
                    }
                    Flow.ANIME_CHARS, Flow.LANG, Flow.PRIVACY, Flow.EDITOR, Flow.CONGRATS -> showFlow(Flow.MAIN)
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
            background = UiTheme.grad(this@MainActivity, if (UiTheme.isDark) "#0F0A1E" else "#EDE9FE", if (UiTheme.isDark) "#1E1B4B" else "#FDF2F8", 0)
        }
        wrap.addView(View(this).apply { background = UiTheme.round(this@MainActivity, Color.parseColor("#338B5CF6"), 110) },
            FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER))

        val centerCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val logoCard = FrameLayout(this).apply {
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 32)
            elevation = dp(14).toFloat(); setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val logoIv = ImageView(this).apply {
            loadSafeLogo(320)?.let { setImageBitmap(it) } ?: setImageResource(R.drawable.app_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(28).toFloat()) }
            }
            clipToOutline = true
        }
        logoCard.addView(logoIv, FrameLayout.LayoutParams(dp(128), dp(128)))
        centerCol.addView(logoCard)
        centerCol.addView(TextView(this).apply { text = L.t("app"); textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain); setPadding(0, dp(18), 0, dp(4)) })
        centerCol.addView(TextView(this).apply { text = L.t("splash_sub"); textSize = 13f; setTextColor(UiTheme.textSub) })
        centerCol.scaleX = 0.8f; centerCol.scaleY = 0.8f; centerCol.alpha = 0f
        centerCol.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(480).start()

        val bottomCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(38), 0, dp(38), dp(38)) }
        val pctTv = TextView(this).apply { text = "${L.t("loading")}... 1%"; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.accent); setPadding(0, 0, 0, dp(8)) }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 1 }
        bottomCol.addView(pctTv); bottomCol.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
        bottomCol.addView(TextView(this).apply { text = L.t("ad_notice"); textSize = 12f; setTextColor(UiTheme.textSub); setPadding(0, dp(10), 0, 0) })

        wrap.addView(centerCol, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        wrap.addView(bottomCol, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(wrap, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        var p = 1
        handler.postDelayed(object : Runnable {
            override fun run() {
                p = (p + 3).coerceAtMost(100); bar.progress = p; pctTv.text = "${L.t("loading")}... $p%"
                if (p < 100) handler.postDelayed(this, 52)
                else handler.postDelayed({
                    billingAds.showStartupAd(this@MainActivity) {
                        runOnUiThread { showFlow(Flow.MAIN) }
                    }
                }, 200)
            }
        }, 60)
    }

    private fun showFlow(target: Flow) {
        if (flow == Flow.CAMERA && target != Flow.CAMERA) stopCamera()
        if (flow == Flow.BROWSER && target != Flow.BROWSER) destroyWebView()
        flow = target
        when (target) {
            Flow.MAIN -> renderMain()
            Flow.LANG -> renderLanguagePage()
            Flow.PRIVACY -> renderPrivacyPage()
            Flow.ANIME_CHARS -> renderAnimeCharsPage()
            Flow.BROWSER -> renderBrowser()
            Flow.EDITOR -> renderEditor()
            Flow.CAMERA -> renderCamera()
            Flow.CONGRATS -> renderCongrats()
            Flow.SPLASH -> showSplash()
        }
    }

    private fun renderMain() {
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val mainCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiTheme.surface); setPadding(dp(16), dp(34), dp(16), dp(12)); elevation = dp(3).toFloat()
        }
        topBar.addView(TextView(this).apply {
            text = when (currentTab) {
                Tab.HOME -> L.t("app"); Tab.GALLERY -> L.t("my_activity"); Tab.STORE -> L.t("store_title"); Tab.SETTINGS -> L.t("settings")
            }
            textSize = 19f; setTypeface(null, Typeface.BOLD); setTextColor(if (currentTab == Tab.HOME) UiTheme.accent else UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBar.addView(Button(this).apply {
            text = "🪙 ${billingAds.coins}"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(this@MainActivity, this, 18, UiTheme.accentSoft, UiTheme.accent, UiTheme.accent)
            UiTheme.bounce(this) { currentTab = Tab.STORE; renderMain() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(38)))
        mainCol.addView(topBar)

        val body = when (currentTab) {
            Tab.HOME -> UiTabs.buildHome(
                this,
                { showCuteSourcePopup() },
                { openCamCapture() },
                { importToGalleryMode = false; pickGalleryLauncher.launch("image/*") },
                { currentTab = Tab.GALLERY; renderMain() },
                { openEditorWithBitmap(it) },
                { q -> browserQuery = q; showFlow(Flow.BROWSER) },
                { series -> selectedSeries = series; showFlow(Flow.ANIME_CHARS) },
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

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 0, UiTheme.stroke, 1)
            setPadding(dp(10), dp(8), dp(10), dp(14))
            elevation = dp(10).toFloat()
        }
        listOf(
            Triple(Tab.HOME, "🏠", L.t("home")), Triple(Tab.GALLERY, "📁", L.t("my_gallery")),
            Triple(Tab.STORE, "🛒", L.t("store")), Triple(Tab.SETTINGS, "⚙️", L.t("settings"))
        ).forEach { (tab, icon, label) ->
            val sel = currentTab == tab
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(7), dp(4), dp(7))
                background = if (sel) UiTheme.grad(this@MainActivity, "#8B5CF6", "#6D28D9", 16) else null
                elevation = if (sel) dp(3).toFloat() else 0f
                addView(TextView(this@MainActivity).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER })
                addView(TextView(this@MainActivity).apply {
                    text = label; textSize = 11f; gravity = Gravity.CENTER; maxLines = 1
                    setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (sel) Color.WHITE else UiTheme.textSub)
                })
                UiTheme.bounce(this) { currentTab = tab; renderMain() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        mainCol.addView(nav)
        root.addView(mainCol, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    // ---------------- ANIME CHARACTERS PAGE (20-25 CHARACTERS + LIVE FILTER) ----------------
    private fun renderAnimeCharsPage() {
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(36), dp(16), dp(16)) }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        topRow.addView(Button(this).apply {
            text = "←"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(this@MainActivity, this, 14, UiTheme.surface, UiTheme.stroke, UiTheme.textMain)
            UiTheme.bounce(this) { showFlow(Flow.MAIN) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        topRow.addView(TextView(this).apply {
            text = "${selectedSeries.name} (${selectedSeries.characters.size})"; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
        })
        main.addView(topRow)

        val searchBox = EditText(this).apply {
            hint = L.t("filter_chars_hint").replace("{name}", selectedSeries.name)
            setTextColor(UiTheme.textMain); setHintTextColor(UiTheme.textSub)
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, UiTheme.accent)
            setPadding(dp(14), dp(12), dp(14), dp(12)); setSingleLine(true)
        }
        main.addView(searchBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fill(filter: String) {
            listCol.removeAllViews()
            val q = filter.trim().lowercase()
            val chars = if (q.isEmpty()) selectedSeries.characters
            else selectedSeries.characters.filter { it.lowercase().startsWith(q) } +
                selectedSeries.characters.filter { !it.lowercase().startsWith(q) && it.lowercase().contains(q) }

            chars.forEach { ch ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, UiTheme.stroke)
                    setPadding(dp(14), dp(14), dp(14), dp(14)); elevation = dp(2).toFloat()
                    addView(TextView(this@MainActivity).apply { text = "🎨  $ch"; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain) },
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(TextView(this@MainActivity).apply { text = L.t("search_btn"); textSize = 13f; setTextColor(UiTheme.accent) })
                    UiTheme.bounce(this) {
                        browserQuery = "$ch ${selectedSeries.name} easy line drawing"
                        showFlow(Flow.BROWSER)
                    }
                }
                listCol.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
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
    }

    // ---------------- MICROSOFT BING IMAGES IN-APP BROWSER ----------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun renderBrowser() {
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(this@MainActivity, "#0EA5E9", "#2563EB", 0)
            setPadding(dp(10), dp(34), dp(10), dp(10))
        }
        topBar.addView(TextView(this).apply {
            text = "←"; textSize = 20f; setTextColor(Color.WHITE); setPadding(dp(8), dp(4), dp(10), dp(4))
            UiTheme.bounce(this) { destroyWebView(); showFlow(Flow.MAIN) }
        })
        val barInput = EditText(this).apply {
            setText(browserQuery); setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#DBEAFE"))
            textSize = 13f; background = UiTheme.round(this@MainActivity, Color.parseColor("#33FFFFFF"), 14)
            setPadding(dp(10), dp(6), dp(10), dp(6)); setSingleLine(true)
        }
        topBar.addView(barInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })

        val wv = WebView(this)
        topBar.addView(TextView(this).apply {
            text = "🔍"; textSize = 17f; setTextColor(Color.WHITE); setPadding(dp(8), dp(6), dp(8), dp(6))
            UiTheme.bounce(this) {
                val q = barInput.text.toString().trim()
                if (q.isNotEmpty()) { browserQuery = q; wv.loadUrl("https://www.bing.com/images/search?q=${Uri.encode(q)}") }
            }
        })
        col.addView(topBar)

        col.addView(TextView(this).apply {
            text = L.t("bing_banner"); textSize = 12f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#1E40AF")); setPadding(0, dp(5), 0, dp(5))
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
                                var img = (el && el.tagName === 'IMG') ? el : (el && el.closest ? el.closest('a,div')?.querySelector('img') : null);
                                var fullUrl = '';
                                if (link) {
                                    try {
                                        var m = JSON.parse(link.getAttribute('m') || '{}');
                                        if (m.murl) fullUrl = m.murl;
                                    } catch(err) {}
                                }
                                var thumbUrl = (img && img.src) ? img.src : '';
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
            loadUrl("https://www.bing.com/images/search?q=${Uri.encode(browserQuery)}")
        }
        activeWebView = wv
        col.addView(wv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
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
                val clean = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
                saveToGallery(clean, "Bing")
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
            orientation = LinearLayout.VERTICAL; background = UiTheme.round(this@MainActivity, UiTheme.surface, 26)
            setPadding(dp(20), dp(20), dp(20), dp(20)); elevation = dp(12).toFloat()
        }
        card.addView(TextView(this).apply {
            text = L.t("choose_source"); textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun optBtn(emoji: String, title: String, c1: String, c2: String, onTap: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = UiTheme.grad(this@MainActivity, c1, c2, 22); setPadding(dp(12), dp(22), dp(12), dp(22)); elevation = dp(4).toFloat()
            addView(TextView(this@MainActivity).apply { text = emoji; textSize = 34f; gravity = Gravity.CENTER })
            addView(TextView(this@MainActivity).apply { text = title; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
            UiTheme.bounce(this) { root.removeView(backdrop); onTap() }
        }
        row.addView(optBtn("📷", L.t("take_photo"), "#8B5CF6", "#6D28D9") { openCamCapture() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
        row.addView(optBtn("🖼️", L.t("upload_gallery"), "#EC4899", "#DB2777") { importToGalleryMode = false; pickGalleryLauncher.launch("image/*") }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        card.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
        card.addView(Button(this).apply {
            text = L.t("cancel")
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(this@MainActivity, this, 15, UiTheme.surfaceAlt, UiTheme.stroke, UiTheme.textSub)
            UiTheme.bounce(this) { root.removeView(backdrop) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))
        backdrop.setOnClickListener { root.removeView(backdrop) }; card.setOnClickListener { }
        backdrop.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply { setMargins(dp(24), 0, dp(24), 0) })
        root.addView(backdrop, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun showCuteImportPopup() {
        val backdrop = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#88000000")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = UiTheme.round(this@MainActivity, UiTheme.surface, 26, UiTheme.stroke)
            setPadding(dp(20), dp(20), dp(20), dp(20)); elevation = dp(12).toFloat()
        }
        card.addView(TextView(this).apply {
            text = L.t("import_title"); textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16))
        })
        card.addView(UiTabs.cuteBtn(this, "📁  ${L.t("choose_file")}", "#3B82F6", "#2563EB", 52) { root.removeView(backdrop); pickAnyFileLauncher.launch("*/*") })
        card.addView(UiTabs.cuteBtn(this, "🔗  ${L.t("paste_link")}", "#8B5CF6", "#6D28D9", 52) { root.removeView(backdrop); showLinkInputDialog() })
        card.addView(Button(this).apply {
            text = L.t("cancel")
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(this@MainActivity, this, 15, UiTheme.surfaceAlt, UiTheme.stroke, UiTheme.textSub)
            UiTheme.bounce(this) { root.removeView(backdrop) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))
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
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(36), dp(16), dp(16)) }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        topRow.addView(Button(this).apply {
            text = "←"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(this@MainActivity, this, 14, UiTheme.surface, UiTheme.stroke, UiTheme.textMain)
            UiTheme.bounce(this) { showFlow(Flow.MAIN) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        topRow.addView(TextView(this).apply {
            text = L.t("language")
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topRow.addView(TextView(this).apply {
            text = "${L.languages.size}"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent)
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 12, UiTheme.stroke)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        main.addView(topRow)

        val searchBox = EditText(this).apply {
            hint = "🔎  ${L.t("language")}..."
            setTextColor(UiTheme.textMain)
            setHintTextColor(UiTheme.textSub)
            textSize = 14f
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, UiTheme.stroke)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setSingleLine(true)
        }
        main.addView(searchBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16)) }
        fun fillLanguages(filter: String) {
            listCol.removeAllViews()
            val q = filter.trim().lowercase()
            val matches = if (q.isEmpty()) L.languages
            else L.languages.filter {
                it.label.lowercase().contains(q) || it.sub.lowercase().contains(q) || it.code.lowercase().contains(q)
            }
            matches.forEach { info ->
                val sel = L.lang == info.code
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(this@MainActivity, UiTheme.surface, 16, if (sel) UiTheme.accent else UiTheme.stroke, if (sel) 2 else 1)
                    setPadding(dp(14), dp(13), dp(16), dp(13)); elevation = dp(2).toFloat()
                    addView(TextView(this@MainActivity).apply {
                        text = info.flag
                        textSize = 22f
                        gravity = Gravity.CENTER
                        background = UiTheme.round(this@MainActivity, UiTheme.surfaceAlt, 12)
                    }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
                    val tc = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                    tc.addView(TextView(this@MainActivity).apply {
                        text = info.label; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
                    })
                    tc.addView(TextView(this@MainActivity).apply {
                        text = info.sub; textSize = 12f; setTextColor(UiTheme.textSub)
                    })
                    addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    if (sel) addView(TextView(this@MainActivity).apply {
                        text = "✓"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.accent)
                    })
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
    }

    private fun renderPrivacyPage() {
        root.removeAllViews()
        root.setBackgroundColor(UiTheme.bg)
        root.addView(
            PrivacyPolicyContent.buildView(this) { showFlow(Flow.MAIN) },
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
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
        root.removeAllViews(); root.setBackgroundColor(UiTheme.bg)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(34), dp(16), dp(24)) }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        topRow.addView(Button(this).apply {
            text = "←"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(this@MainActivity, this, 14, UiTheme.surface, UiTheme.stroke, UiTheme.textMain)
            UiTheme.bounce(this) { showFlow(Flow.MAIN) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        topRow.addView(TextView(this).apply {
            text = L.t("drawing_create")
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val sizeBadgeTv = TextView(this).apply {
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent)
            background = UiTheme.round(this@MainActivity, UiTheme.accentSoft, 12, UiTheme.stroke)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        topRow.addView(sizeBadgeTv)
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
        val previewIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val pickerRing = View(this).apply {
            background = UiTheme.round(this@MainActivity, Color.TRANSPARENT, 14, Color.parseColor("#EF4444"), 2)
            visibility = View.GONE
        }
        checkerBox.addView(previewIv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        checkerBox.addView(pickerRing, FrameLayout.LayoutParams(dp(24), dp(24)))
        previewFrame.addView(checkerBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        col.addView(previewFrame, LinearLayout.LayoutParams(MATCH_PARENT, dp(264)).apply { bottomMargin = dp(14) })

        fun activeEditBitmap(): Bitmap? = if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap

        val autoBgBtn = Button(this).apply { textSize = 13f; setTypeface(null, Typeface.BOLD) }
        val keepLinesBtn = Button(this).apply { textSize = 13f; setTypeface(null, Typeface.BOLD) }
        val cropBtn = Button(this).apply { textSize = 13f; setTypeface(null, Typeface.BOLD) }
        val pickColorBtn = Button(this).apply { textSize = 13f; setTypeface(null, Typeface.BOLD) }
        val undoBtn = Button(this).apply { textSize = 13f; setTypeface(null, Typeface.BOLD) }

        val pickPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 18, UiTheme.accent)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(4).toFloat()
        }
        val pickInfoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val colorSwatch = TextView(this).apply {
            text = "🎯"; textSize = 14f; gravity = Gravity.CENTER
            background = UiTheme.round(this@MainActivity, UiTheme.surfaceAlt, 16, UiTheme.stroke)
        }
        val pickHintTv = TextView(this).apply {
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
        }
        pickInfoRow.addView(colorSwatch, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
        pickInfoRow.addView(pickHintTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        pickPanel.addView(pickInfoRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val tolLabel = TextView(this).apply {
            text = "${L.t("tolerance")}: $colorTolerance%"
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
        val removePickedBtn = Button(this).apply {
            text = "🧹  ${L.t("remove_picked_color")}"
            textSize = 13f; setTypeface(null, Typeface.BOLD)
            UiTheme.styleGradientBtn(this@MainActivity, this, "#EF4444", "#DC2626", 14)
        }
        val donePickBtn = Button(this).apply {
            text = L.t("pick_done")
            textSize = 13f; setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(this@MainActivity, this, 14, UiTheme.surfaceAlt, UiTheme.stroke, UiTheme.textMain)
        }
        pickActionRow.addView(removePickedBtn, LinearLayout.LayoutParams(0, dp(44), 2f).apply { marginEnd = dp(8) })
        pickActionRow.addView(donePickBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        pickPanel.addView(pickActionRow)

        fun sync() {
            val active = activeEditBitmap()
            previewIv.setImageBitmap(active)
            previewIv.alpha = opacityPercent / 100f
            if (active != null) {
                sizeBadgeTv.text = "${active.width} × ${active.height} px"
                sizeBadgeTv.visibility = View.VISIBLE
            } else {
                sizeBadgeTv.visibility = View.GONE
            }

            autoBgBtn.text = if (isProcessingBg) L.t("removing_bg") else "✂️  ${L.t("remove_bg")}"
            UiTheme.styleGradientBtn(this, autoBgBtn, "#3B82F6", "#2563EB", 15)

            keepLinesBtn.text = if (isProcessingBg) L.t("removing_bg") else "🖋️  ${L.t("keep_lines")}"
            UiTheme.styleGradientBtn(this, keepLinesBtn, "#8B5CF6", "#6D28D9", 15)

            cropBtn.text = "📐  ${L.t("crop_resize")}"
            UiTheme.styleGradientBtn(this, cropBtn, "#0EA5E9", "#0284C7", 15)

            pickColorBtn.text = "🎯  ${L.t("pick_color_btn")}"
            if (isColorPickMode) {
                UiTheme.styleGradientBtn(this, pickColorBtn, "#EC4899", "#DB2777", 15)
            } else {
                UiTheme.styleSoftBtn(this, pickColorBtn, 15, UiTheme.surface, UiTheme.stroke, UiTheme.textMain)
            }

            val canRestore = bgRemoved && cutoutBitmap != null
            undoBtn.text = if (editHistory.isNotEmpty()) "↩️  ${L.t("undo_step")}" else "🔄  ${L.t("restore_orig")}"
            undoBtn.isEnabled = canRestore && !isProcessingBg
            undoBtn.alpha = if (undoBtn.isEnabled) 1f else 0.45f
            UiTheme.styleSoftBtn(this, undoBtn, 15, UiTheme.surfaceAlt, UiTheme.stroke, UiTheme.textMain)

            pickPanel.visibility = if (isColorPickMode) View.VISIBLE else View.GONE
            val pc = pickedColor
            if (pc != null) {
                colorSwatch.text = ""
                colorSwatch.background = UiTheme.round(this, pc, 16, UiTheme.textMain)
                pickHintTv.text = L.t("pick_color_ready")
                removePickedBtn.isEnabled = !isProcessingBg
                removePickedBtn.alpha = if (isProcessingBg) 0.5f else 1f
            } else {
                colorSwatch.text = "🎯"
                colorSwatch.background = UiTheme.round(this, UiTheme.surfaceAlt, 16, UiTheme.stroke)
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

        col.addView(undoBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)).apply { bottomMargin = dp(10) })
        col.addView(pickPanel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val opCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 18, UiTheme.stroke)
            setPadding(dp(14), dp(12), dp(14), dp(10))
            elevation = dp(2).toFloat()
        }
        val opHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        opHead.addView(TextView(this).apply {
            text = "💧  ${L.t("opacity")}"
            setTextColor(UiTheme.textMain); textSize = 14f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val opPctBadge = TextView(this).apply {
            text = "$opacityPercent%"
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.accent)
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

        col.addView(UiTabs.cuteBtn(this, "📷  ${L.t("next")}  →", "#10B981", "#059669", 54) {
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

        val topPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 26); elevation = dp(6).toFloat()
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun pillIcon(txt: String, onTap: (TextView) -> Unit) = TextView(this).apply {
            text = txt; textSize = 18f; gravity = Gravity.CENTER; setTextColor(UiTheme.accent)
            setPadding(dp(10), dp(6), dp(10), dp(6)); UiTheme.bounce(this) { onTap(this) }
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
            saveToGallery(activeBmp, L.t("art_prefix")); stopCamera()
            billingAds.showRewardedOnComplete(this@MainActivity) { runOnUiThread { showFlow(Flow.CONGRATS) } }
        })

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
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiTheme.round(this@MainActivity, UiTheme.surface, 28, UiTheme.stroke)
            setPadding(dp(24), dp(32), dp(24), dp(26))
            elevation = dp(10).toFloat()
        }
        card.addView(TextView(this).apply {
            text = "🏆"
            textSize = 42f
            gravity = Gravity.CENTER
            background = UiTheme.grad(this@MainActivity, "#FDE047", "#F59E0B", 42)
        }, LinearLayout.LayoutParams(dp(84), dp(84)).apply { bottomMargin = dp(18) })
        card.addView(TextView(this).apply {
            text = L.t("congrats"); textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain); gravity = Gravity.CENTER
        })
        card.addView(TextView(this).apply {
            text = L.t("good_luck"); textSize = 15f; setTextColor(UiTheme.textSub); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(24))
        })
        card.addView(UiTabs.cuteBtn(this, "🏠  ${L.t("go_home")}", "#8B5CF6", "#6D28D9", 54) { showFlow(Flow.MAIN) })
        col.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
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

    override fun onDestroy() {
        super.onDestroy(); stopCamera(); destroyWebView(); billingAds.destroy()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
    }
}
