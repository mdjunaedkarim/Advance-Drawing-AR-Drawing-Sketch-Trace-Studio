package com.ardrawing.sketch

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object UiTabs {

    fun dp(act: Activity, v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    fun roundedBg(act: Activity, hex: String, rDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(act, rDp).toFloat()
    }

    fun actionBtn(act: Activity, label: String, hex: String, hDp: Int, onClick: () -> Unit) = Button(act).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
        isAllCaps = false
        background = roundedBg(act, hex, 12)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(act, hDp)).apply {
            bottomMargin = dp(act, 10)
        }
        setOnClickListener { onClick() }
    }

    fun buildHome(act: Activity, onCreateClick: () -> Unit, onSampleClick: (Bitmap) -> Unit): View {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 18), dp(act, 18), dp(act, 18), dp(act, 20))
        }
        col.addView(TextView(act).apply {
            text = L.t("home_sub")
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(act, 16))
        })
        col.addView(actionBtn(act, "✏️  ${L.t("drawing_create")}", "#4F46E5", 58, onCreateClick))
        col.addView(TextView(act).apply {
            text = L.t("samples")
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(act, 14), 0, dp(act, 10))
        })

        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until Samples.COUNT) {
            val card = FrameLayout(act).apply {
                background = roundedBg(act, "#FFFFFF", 14)
                setPadding(dp(act, 6), dp(act, 6), dp(act, 6), dp(act, 6))
                addView(ImageView(act).apply {
                    setImageBitmap(Samples.render(i, 240))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(dp(act, 100), dp(act, 100)))
                setOnClickListener { onSampleClick(Samples.render(i, 960)) }
            }
            row.addView(card, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginEnd = dp(act, 10)
            })
        }
        col.addView(HorizontalScrollView(act).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        return col
    }

    fun buildGallery(
        act: Activity,
        galleryDir: File,
        prefs: SharedPreferences,
        onImport: () -> Unit,
        onDraw: (Bitmap) -> Unit,
        onRefresh: () -> Unit
    ): View {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 14), dp(act, 14), dp(act, 14), dp(act, 20))
        }
        col.addView(actionBtn(act, "➕  ${L.t("import_photo")}", "#2563EB", 48, onImport))

        val files = (galleryDir.listFiles()?.toList() ?: emptyList())
            .filter { it.name.endsWith(".png") }
            .sortedByDescending { it.lastModified() }

        if (files.isEmpty()) {
            col.addView(TextView(act).apply {
                text = L.t("empty_gallery")
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(act, 48), 0, 0)
            })
            return col
        }

        val cardSize = (act.resources.displayMetrics.widthPixels - dp(act, 44)) / 2
        var row: LinearLayout? = null
        files.forEachIndexed { idx, file ->
            if (idx % 2 == 0) {
                row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = dp(act, 12)
                })
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            val savedName = prefs.getString("gname_${file.name}", "Drawing #${files.size - idx}") ?: "Drawing"
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg(act, "#161E31", 14)
                setPadding(dp(act, 8), dp(act, 8), dp(act, 8), dp(act, 8))

                addView(ImageView(act).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.parseColor("#0B0F19"))
                    setOnClickListener { if (bmp != null) onDraw(bmp) }
                }, LinearLayout.LayoutParams(MATCH_PARENT, cardSize - dp(act, 16)))

                addView(TextView(act).apply {
                    text = savedName
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    maxLines = 1
                    setPadding(0, dp(act, 6), 0, dp(act, 4))
                })

                val br = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                fun miniBtn(t: String, onTap: () -> Unit) = TextView(act).apply {
                    text = t
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#CBD5E1"))
                    background = roundedBg(act, "#1E293B", 8)
                    setPadding(0, dp(act, 5), 0, dp(act, 5))
                    setOnClickListener { onTap() }
                }

                br.addView(miniBtn("✏️") {
                    val inp = EditText(act).apply { setText(savedName); hint = L.t("rename_hint") }
                    AlertDialog.Builder(act).setTitle(L.t("rename")).setView(inp)
                        .setPositiveButton(L.t("save")) { _, _ ->
                            val nn = inp.text.toString().trim()
                            if (nn.isNotEmpty()) {
                                prefs.edit().putString("gname_${file.name}", nn).apply()
                                onRefresh()
                            }
                        }.setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(act, 4) })

                br.addView(miniBtn("🔗") {
                    try {
                        val u = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
                        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, u)
                            putExtra(Intent.EXTRA_TEXT, "$savedName — ${L.t("share_text")}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, L.t("share")))
                    } catch (_: Exception) {}
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(act, 4) })

                br.addView(miniBtn("🗑️") {
                    AlertDialog.Builder(act).setMessage(L.t("delete_confirm"))
                        .setPositiveButton(L.t("delete")) { _, _ ->
                            file.delete()
                            prefs.edit().remove("gname_${file.name}").apply()
                            onRefresh()
                        }.setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(br)
            }
            row?.addView(card, LinearLayout.LayoutParams(cardSize, WRAP_CONTENT).apply {
                if (idx % 2 == 0) marginEnd = dp(act, 12)
            })
        }
        return col
    }

    fun buildStore(act: Activity, billingAds: BillingAdsManager): View {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 20))
        }
        BillingAdsManager.PACKS.forEach { pack ->
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBg(act, if (pack.isRemoveAds) "#312E81" else "#161E31", 14)
                setPadding(dp(act, 14), dp(act, 14), dp(act, 14), dp(act, 14))
            }
            val info = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(act).apply {
                text = if (pack.isRemoveAds) "🚫  ${L.t("remove_ads")}" else "🪙  ${pack.coins} ${L.t("coins_label")}"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            if (pack.isRemoveAds) {
                info.addView(TextView(act).apply {
                    text = L.t("remove_ads_desc")
                    textSize = 12f
                    setTextColor(Color.parseColor("#CBD5E1"))
                })
            }
            val done = pack.isRemoveAds && billingAds.adsRemoved
            card.addView(info, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            card.addView(Button(act).apply {
                text = if (done) L.t("ads_removed") else pack.priceLabel
                setTextColor(Color.WHITE)
                isAllCaps = false
                background = roundedBg(act, if (done) "#10B981" else "#4F46E5", 10)
                setOnClickListener { if (!done) billingAds.purchase(act, pack) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(act, 42)))
            col.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(act, 10)
            })
        }
        return col
    }

    fun buildSettings(act: Activity, prefs: SharedPreferences, onRefresh: () -> Unit): View {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 24))
        }
        val cur = L.languages.find { it.code == L.lang }?.label ?: "English"
        col.addView(actionBtn(act, "🌐  ${L.t("language")}: $cur", "#1E293B", 50) {
            AlertDialog.Builder(act).setTitle(L.t("language"))
                .setItems(L.languages.map { it.label }.toTypedArray()) { _, i ->
                    L.lang = L.languages[i].code
                    prefs.edit().putString("lang", L.lang).apply()
                    onRefresh()
                }.show()
        })
        col.addView(actionBtn(act, "🔒  ${L.t("privacy_policy")}", "#1E293B", 50) {
            AlertDialog.Builder(act).setTitle(L.t("privacy_policy"))
                .setMessage(L.t("privacy_text")).setPositiveButton("OK", null).show()
        })
        col.addView(actionBtn(act, "ℹ️  ${L.t("about")}", "#1E293B", 50) { openPlayStore(act) })
        col.addView(actionBtn(act, "📤  ${L.t("share_friend")}", "#2563EB", 50) {
            val url = "https://play.google.com/store/apps/details?id=${act.packageName}"
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("App Link", url))
            Toast.makeText(act, L.t("link_copied"), Toast.LENGTH_SHORT).show()
            act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, L.t("share_text"))
            }, L.t("share_friend")))
        })
        col.addView(actionBtn(act, "⭐  ${L.t("rate_us")}", "#1E293B", 50) { openPlayStore(act) })
        col.addView(actionBtn(act, "✉️  ${L.t("feedback")}", "#1E293B", 50) {
            try {
                act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "AR Drawing Feedback")
                })
            } catch (_: Exception) {}
        })
        return col
    }

    private fun openPlayStore(act: Activity) {
        try {
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${act.packageName}")))
        } catch (_: Exception) {
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${act.packageName}")))
        }
    }
}
