package com.ardrawing.sketch

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AdminPanelUi {

    private enum class AdminSection { USERS, CONTROLS }
    private enum class UserFilter { ALL, ONLINE, VIP, BLOCKED }

    private fun formatRelativeTime(ts: Timestamp?): String {
        if (ts == null) return "—"
        val diffSec = ((System.currentTimeMillis() - ts.toDate().time) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 -> "Just now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            else -> SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(ts.toDate())
        }
    }

    private fun formatExactTime(ts: Timestamp?): String {
        val d = ts?.toDate() ?: Date()
        return SimpleDateFormat("dd MMM • HH:mm:ss", Locale.US).format(d)
    }

    // ---------------- GOOGLE SIGN-IN GATE SCREEN ----------------
    fun buildAuthGateView(
        act: Activity,
        safeTopPx: Int,
        safeBottomPx: Int,
        cloud: FirebaseCloudManager,
        scope: CoroutineScope,
        onSignedIn: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val wrap = FrameLayout(act).apply {
            background = UiTheme.grad(
                act,
                if (UiTheme.isDark) "#07090F" else "#EEF2FF",
                if (UiTheme.isDark) "#1D1645" else "#FCE7F3",
                0
            )
            setPadding(dp(22), safeTopPx + dp(20), dp(22), safeBottomPx + dp(20))
        }

        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiTheme.round(act, UiTheme.surface, 30, UiTheme.strokeStrong, 1)
            setPadding(dp(24), dp(28), dp(24), dp(26))
            elevation = dp(14).toFloat()
        }

        val logoCard = FrameLayout(act).apply {
            background = UiTheme.round(act, Color.WHITE, 28, UiTheme.stroke, 2)
            UiTheme.applyColoredShadow(this, Color.parseColor("#7C3AED"), 12)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(28).toFloat())
                }
            }
            clipToOutline = true
            addView(
                ImageView(act).apply {
                    setImageResource(R.drawable.app_logo)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = L.t("app")
                },
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            )
        }
        card.addView(logoCard, LinearLayout.LayoutParams(dp(106), dp(106)).apply { bottomMargin = dp(16) })

        card.addView(TextView(act).apply {
            text = L.t("app")
            textSize = 20f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
        })

        card.addView(TextView(act).apply {
            text = L.t("auth_subtitle")
            textSize = 13f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(18))
        })

        val featureRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(
            StudioIcons.Type.CAMERA to L.t("mode_camera"),
            StudioIcons.Type.SUN to L.t("screen_trace_short"),
            StudioIcons.Type.SHIELD to L.t("cloud_sync_pill")
        ).forEach { (ic, lbl) ->
            val pill = StudioIcons.iconPill(
                context = act,
                iconType = ic,
                label = lbl,
                textColor = UiTheme.accentBright,
                iconColor = UiTheme.accentBright,
                iconSizeDp = 12,
                textSizeSp = 10.5f
            ).apply {
                background = UiTheme.round(act, UiTheme.accentSoft, 10, UiTheme.stroke)
                setPadding(dp(8), dp(5), dp(8), dp(5))
            }
            featureRow.addView(pill, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        card.addView(featureRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(20) })

        val errorTv = TextView(act).apply {
            textSize = 12.5f
            UiTheme.applyUiFont(this, bold = true)
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(10))
        }
        card.addView(errorTv)

        val btnContent = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = UiTheme.premiumGrad(act, "#7C3AED", "#4F46E5", 18)
            UiTheme.applyColoredShadow(this, Color.parseColor("#7C3AED"), 10)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            contentDescription = L.t("sign_in_google")
        }
        val spinner = ProgressBar(act).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        val btnIcon = StudioIcons.imageView(act, StudioIcons.Type.SHIELD, 18, Color.WHITE)
        val btnLabel = TextView(act).apply {
            text = L.t("sign_in_google")
            textSize = 15.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(Color.WHITE)
        }
        btnContent.addView(spinner, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
        btnContent.addView(btnIcon, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) })
        btnContent.addView(btnLabel)

        var isLoading = false
        fun setLoading(loading: Boolean) {
            isLoading = loading
            btnContent.isEnabled = !loading
            btnContent.alpha = if (loading) 0.75f else 1f
            spinner.visibility = if (loading) View.VISIBLE else View.GONE
            btnIcon.visibility = if (loading) View.GONE else View.VISIBLE
            btnLabel.text = if (loading) L.t("signing_in") else L.t("sign_in_google")
        }

        UiTheme.bounce(btnContent) {
            if (isLoading) return@bounce
            errorTv.visibility = View.GONE
            setLoading(true)
            cloud.onGoogleSignInClicked(
                activity = act,
                scope = scope,
                onAuthSuccess = {
                    act.runOnUiThread {
                        setLoading(false)
                        onSignedIn()
                    }
                },
                onAuthError = { msg ->
                    act.runOnUiThread {
                        setLoading(false)
                        errorTv.text = msg
                        errorTv.visibility = View.VISIBLE
                    }
                },
                onAuthCancelled = {
                    act.runOnUiThread {
                        setLoading(false)
                    }
                }
            )
        }

        val shimmerBtn = UiTheme.wrapShimmerCta(act, btnContent, 18)
        card.addView(shimmerBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(54)))

        card.addView(TextView(act).apply {
            text = L.t("auth_footer_note")
            textSize = 11.5f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(14), dp(4), 0)
        })

        wrap.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        UiTheme.animatePageIn(card, 18, 280L)
        return wrap
    }

    // ---------------- MAINTENANCE OR BLOCKED GATE ----------------
    fun buildRestrictedGateView(
        act: Activity,
        safeTopPx: Int,
        safeBottomPx: Int,
        isBlocked: Boolean,
        title: String,
        message: String,
        email: String,
        onSignOut: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val wrap = FrameLayout(act).apply {
            setBackgroundColor(UiTheme.bg)
            setPadding(dp(24), safeTopPx + dp(20), dp(24), safeBottomPx + dp(20))
        }
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiTheme.round(act, UiTheme.surface, 28, UiTheme.strokeStrong, 1)
            setPadding(dp(24), dp(28), dp(24), dp(24))
            elevation = dp(10).toFloat()
        }
        val badgeWell = FrameLayout(act).apply {
            background = UiTheme.premiumGrad(
                act,
                if (isBlocked) "#EF4444" else "#F59E0B",
                if (isBlocked) "#DC2626" else "#D97706",
                24
            )
            UiTheme.applyColoredShadow(this, Color.parseColor(if (isBlocked) "#EF4444" else "#F59E0B"), 8)
            addView(
                StudioIcons.imageView(act, StudioIcons.Type.SHIELD_LOCK, 32, Color.WHITE),
                FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
            )
        }
        card.addView(badgeWell, LinearLayout.LayoutParams(dp(68), dp(68)).apply { bottomMargin = dp(16) })

        card.addView(TextView(act).apply {
            text = title
            textSize = 21f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
        })
        card.addView(TextView(act).apply {
            text = message
            textSize = 14f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(12))
        })
        if (email.isNotBlank()) {
            card.addView(TextView(act).apply {
                text = email
                textSize = 12f
                UiTheme.applyUiFont(this, bold = true)
                setTextColor(UiTheme.accentBright)
                background = UiTheme.round(act, UiTheme.accentSoft, 10)
                setPadding(dp(12), dp(5), dp(12), dp(5))
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(18) })
        }
        val switchBtn = UiTheme.studioSoftAction(
            act = act,
            icon = StudioIcons.Type.CLOSE,
            label = L.t("sign_out"),
            rDp = 16
        ) { onSignOut() }
        card.addView(switchBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        wrap.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        return wrap
    }

    // ---------------- FULL ADMIN CONTROL PANEL ----------------
    fun buildAdminPanel(
        act: Activity,
        safeTopPx: Int,
        safeBottomPx: Int,
        cloud: FirebaseCloudManager,
        scope: CoroutineScope,
        onBack: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val main = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.bg)
            setPadding(dp(16), safeTopPx + dp(10), dp(16), safeBottomPx + dp(10))
        }

        // Top Bar
        val topRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        topRow.addView(
            UiTheme.studioIconBtn(
                act = act,
                icon = StudioIcons.Type.ARROW_LEFT,
                sizeDp = 44,
                iconSizeDp = 20,
                fillColor = UiTheme.surface,
                strokeColor = UiTheme.stroke,
                iconTint = UiTheme.textMain,
                contentDesc = L.t("back")
            ) {
                cloud.stopAdminUsersListener()
                onBack()
            },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        )
        val titleStack = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        titleStack.addView(TextView(act).apply {
            text = L.t("admin_panel")
            textSize = 19.5f
            UiTheme.applyDisplayFont(this, true)
            setTextColor(UiTheme.textMain)
        })
        titleStack.addView(TextView(act).apply {
            text = cloud.currentUser()?.email ?: FirebaseCloudManager.PRIMARY_ADMIN_EMAIL
            textSize = 11f
            UiTheme.applyUiFont(this, true)
            setTextColor(UiTheme.accentBright)
            maxLines = 1
        })
        topRow.addView(titleStack, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val liveBadge = StudioIcons.iconPill(
            context = act,
            iconType = StudioIcons.Type.SPARK,
            label = "LIVE",
            textColor = Color.WHITE,
            iconColor = Color.WHITE,
            iconSizeDp = 12,
            textSizeSp = 11f
        ).apply {
            background = UiTheme.premiumGrad(act, "#10B981", "#059669", 12)
            UiTheme.applyColoredShadow(this, Color.parseColor("#10B981"), 5)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        topRow.addView(liveBadge)
        main.addView(topRow)

        // Live KPI Dashboard Strip (2x2 Grid)
        val kpiTotalVal = TextView(act)
        val kpiOnlineVal = TextView(act)
        val kpiDrawVal = TextView(act)
        val kpiVipVal = TextView(act)

        fun kpiCard(valueTv: TextView, label: String, c1: String, c2: String): LinearLayout {
            return LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTheme.premiumGrad(act, c1, c2, 18)
                UiTheme.applyColoredShadow(this, Color.parseColor(c1), 6)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                valueTv.apply {
                    text = "0"
                    fontFeatureSettings = "tnum"
                    textSize = 22f
                    UiTheme.applyDisplayFont(this, true)
                    setTextColor(Color.WHITE)
                }
                addView(valueTv)
                addView(TextView(act).apply {
                    text = label
                    textSize = 11.5f
                    UiTheme.applyUiFont(this, true)
                    setTextColor(Color.parseColor("#E0FFFFFF"))
                    maxLines = 1
                })
            }
        }

        val kpiRow1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        kpiRow1.addView(
            kpiCard(kpiTotalVal, L.t("admin_total_users"), "#7C3AED", "#4F46E5"),
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        kpiRow1.addView(
            kpiCard(kpiOnlineVal, L.t("admin_online_now"), "#10B981", "#059669"),
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        )
        main.addView(kpiRow1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val kpiRow2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        kpiRow2.addView(
            kpiCard(kpiDrawVal, L.t("admin_total_drawings"), "#0EA5E9", "#2563EB"),
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        kpiRow2.addView(
            kpiCard(kpiVipVal, L.t("admin_vip_blocked"), "#EC4899", "#DB2777"),
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        )
        main.addView(kpiRow2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        // Segmented Switcher: Users Monitor vs Remote App Control
        var activeSection = AdminSection.USERS
        val segWrap = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.round(act, UiTheme.surfaceAlt, 16, UiTheme.stroke)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val usersTabBtn = TextView(act).apply {
            text = L.t("admin_tab_users")
            textSize = 13.5f
            gravity = Gravity.CENTER
            UiTheme.applyDisplayFont(this, true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val ctrlTabBtn = TextView(act).apply {
            text = L.t("admin_tab_controls")
            textSize = 13.5f
            gravity = Gravity.CENTER
            UiTheme.applyDisplayFont(this, true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        segWrap.addView(usersTabBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        segWrap.addView(ctrlTabBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        main.addView(segWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val bodyHost = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(act).apply { addView(bodyHost) }
        main.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        var latestUsers: List<UserRecord> = emptyList()
        var usersLoadError: String? = null
        var userSearchQuery = ""
        var userFilter = UserFilter.ALL

        fun syncSegmentStyle() {
            val uActive = activeSection == AdminSection.USERS
            usersTabBtn.background = if (uActive) UiTheme.premiumGrad(act, "#7C3AED", "#4F46E5", 13) else null
            usersTabBtn.setTextColor(if (uActive) Color.WHITE else UiTheme.textSub)
            ctrlTabBtn.background = if (!uActive) UiTheme.premiumGrad(act, "#7C3AED", "#4F46E5", 13) else null
            ctrlTabBtn.setTextColor(if (!uActive) Color.WHITE else UiTheme.textSub)
        }

        fun showUserLogsDialog(user: UserRecord) {
            val dialog = Dialog(act)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCanceledOnTouchOutside(true)

            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 24, UiTheme.strokeStrong)
                setPadding(dp(18), dp(18), dp(18), dp(16))
                elevation = dp(14).toFloat()
            }
            val headRow = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(10))
            }
            val hCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            hCol.addView(TextView(act).apply {
                text = user.displayName.ifBlank { "Studio User" }
                textSize = 17f
                UiTheme.applyDisplayFont(this, true)
                setTextColor(UiTheme.textMain)
            })
            hCol.addView(TextView(act).apply {
                text = "${user.email} • ${user.deviceModel}"
                textSize = 11.5f
                UiTheme.applyUiFont(this)
                setTextColor(UiTheme.textSub)
            })
            headRow.addView(hCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            headRow.addView(
                UiTheme.studioIconBtn(act, StudioIcons.Type.CLOSE, 36, 16, 11, UiTheme.surfaceAlt, UiTheme.stroke, UiTheme.textMain, contentDesc = L.t("close")) {
                    dialog.dismiss()
                },
                LinearLayout.LayoutParams(dp(36), dp(36))
            )
            card.addView(headRow)

            val logListCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            logListCol.addView(ProgressBar(act).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(20); bottomMargin = dp(20)
            })
            val logScroll = ScrollView(act).apply { addView(logListCol) }
            card.addView(logScroll, LinearLayout.LayoutParams(MATCH_PARENT, dp(320)))

            scope.launch {
                val res = cloud.fetchUserActivityLogs(user.userId.ifBlank { user.id })
                act.runOnUiThread {
                    logListCol.removeAllViews()
                    res.onSuccess { logs ->
                        if (logs.isEmpty()) {
                            logListCol.addView(TextView(act).apply {
                                text = L.t("admin_no_logs")
                                textSize = 13f
                                UiTheme.applyUiFont(this)
                                setTextColor(UiTheme.textSub)
                                gravity = Gravity.CENTER
                                setPadding(0, dp(24), 0, dp(24))
                            })
                        } else {
                            logs.forEach { lg ->
                                val row = LinearLayout(act).apply {
                                    orientation = LinearLayout.VERTICAL
                                    background = UiTheme.round(act, UiTheme.surfaceAlt, 14, UiTheme.stroke)
                                    setPadding(dp(12), dp(9), dp(12), dp(9))
                                    val top = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                                    top.addView(TextView(act).apply {
                                        text = lg.action
                                        textSize = 11.5f
                                        UiTheme.applyDisplayFont(this, true)
                                        setTextColor(UiTheme.accentBright)
                                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                                    top.addView(TextView(act).apply {
                                        text = formatExactTime(lg.createdAt)
                                        fontFeatureSettings = "tnum"
                                        textSize = 10.5f
                                        UiTheme.applyUiFont(this)
                                        setTextColor(UiTheme.textSub)
                                    })
                                    addView(top)
                                    if (lg.detail.isNotBlank()) {
                                        addView(TextView(act).apply {
                                            text = lg.detail
                                            textSize = 12.5f
                                            UiTheme.applyUiFont(this)
                                            setTextColor(UiTheme.textMain)
                                            setPadding(0, dp(3), 0, 0)
                                        })
                                    }
                                }
                                logListCol.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(7) })
                            }
                        }
                    }.onFailure { err ->
                        logListCol.addView(TextView(act).apply {
                            text = err.localizedMessage ?: "Failed to load activity logs"
                            textSize = 13f
                            setTextColor(Color.parseColor("#EF4444"))
                            setPadding(dp(8), dp(16), dp(8), dp(16))
                        })
                    }
                }
            }

            val widthPx = (act.resources.displayMetrics.widthPixels - dp(36)).coerceAtMost(dp(400))
            val holder = FrameLayout(act)
            holder.addView(card, FrameLayout.LayoutParams(widthPx, WRAP_CONTENT, Gravity.CENTER))
            dialog.setContentView(holder)
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.55f)
                setLayout(WRAP_CONTENT, WRAP_CONTENT)
            }
            dialog.show()
            card.scaleX = 0.88f; card.scaleY = 0.88f; card.alpha = 0f
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).setInterpolator(OvershootInterpolator(1.2f)).start()
        }

        val usersListContainer = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }

        fun rebuildUsersListOnly() {
            usersListContainer.removeAllViews()
            val errMsg = usersLoadError
            if (errMsg != null) {
                usersListContainer.addView(TextView(act).apply {
                    text = errMsg
                    textSize = 13f
                    UiTheme.applyUiFont(this, true)
                    setTextColor(Color.parseColor("#EF4444"))
                    background = UiTheme.round(act, UiTheme.surface, 16, Color.parseColor("#EF4444"))
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                })
                return
            }

            val q = userSearchQuery.trim().lowercase()
            val filtered = latestUsers.filter { u ->
                val matchesFilter = when (userFilter) {
                    UserFilter.ALL -> true
                    UserFilter.ONLINE -> u.online
                    UserFilter.VIP -> u.vipGranted
                    UserFilter.BLOCKED -> u.blocked
                }
                val matchesQuery = q.isEmpty() ||
                    u.displayName.lowercase().contains(q) ||
                    u.email.lowercase().contains(q) ||
                    u.deviceModel.lowercase().contains(q) ||
                    u.activeScreen.lowercase().contains(q)
                matchesFilter && matchesQuery
            }

            if (filtered.isEmpty()) {
                usersListContainer.addView(TextView(act).apply {
                    text = L.t("admin_no_users")
                    textSize = 13.5f
                    UiTheme.applyUiFont(this)
                    setTextColor(UiTheme.textSub)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(28), dp(16), dp(28))
                })
                return
            }

            val selfUid = cloud.currentUser()?.uid ?: ""
            filtered.forEachIndexed { idx, u ->
                val uid = u.userId.ifBlank { u.id }
                val isSelf = uid == selfUid
                val (c1, c2) = UiTheme.seriesPalette(idx)
                val initials = u.displayName.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "U" }

                val card = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    background = UiTheme.premiumSurface(
                        act,
                        UiTheme.surface,
                        20,
                        when {
                            u.blocked -> Color.parseColor("#EF4444")
                            u.online -> Color.parseColor("#10B981")
                            else -> UiTheme.stroke
                        },
                        if (u.blocked || u.online) 2 else 1
                    )
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                    elevation = dp(4).toFloat()
                }

                // Header Row
                val header = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val avatarWrap = FrameLayout(act)
                avatarWrap.addView(
                    UiTheme.monogramBadge(act, initials, c1, c2, 42, 13, 13f),
                    FrameLayout.LayoutParams(dp(42), dp(42))
                )
                val statusDot = View(act).apply {
                    background = UiTheme.round(
                        act,
                        Color.parseColor(if (u.online) "#10B981" else "#94A3B8"),
                        6,
                        UiTheme.surface,
                        2
                    )
                }
                avatarWrap.addView(statusDot, FrameLayout.LayoutParams(dp(13), dp(13), Gravity.BOTTOM or Gravity.END))
                header.addView(avatarWrap, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })

                val infoCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                val nameRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                nameRow.addView(TextView(act).apply {
                    text = u.displayName.ifBlank { "Studio Artist" }
                    textSize = 15f
                    UiTheme.applyDisplayFont(this, true)
                    setTextColor(UiTheme.textMain)
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

                if (isSelf) {
                    nameRow.addView(TextView(act).apply {
                        text = "ADMIN"
                        textSize = 9.5f
                        UiTheme.applyDisplayFont(this, true)
                        setTextColor(Color.WHITE)
                        background = UiTheme.premiumGrad(act, "#7C3AED", "#4F46E5", 7)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4) })
                }
                if (u.vipGranted) {
                    nameRow.addView(TextView(act).apply {
                        text = "VIP"
                        textSize = 9.5f
                        UiTheme.applyDisplayFont(this, true)
                        setTextColor(Color.parseColor("#1E1B4B"))
                        background = UiTheme.premiumGrad(act, "#FDE047", "#F59E0B", 7)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4) })
                }
                if (u.blocked) {
                    nameRow.addView(TextView(act).apply {
                        text = "BLOCKED"
                        textSize = 9.5f
                        UiTheme.applyDisplayFont(this, true)
                        setTextColor(Color.WHITE)
                        background = UiTheme.premiumGrad(act, "#EF4444", "#DC2626", 7)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4) })
                }
                infoCol.addView(nameRow)

                infoCol.addView(TextView(act).apply {
                    text = u.email.ifBlank { uid }
                    textSize = 11.5f
                    UiTheme.applyUiFont(this)
                    setTextColor(UiTheme.textSub)
                    maxLines = 1
                })
                infoCol.addView(TextView(act).apply {
                    text = "${u.deviceModel} • v${u.appVersion} • ${u.language.uppercase(Locale.US)}"
                    textSize = 11f
                    UiTheme.applyUiFont(this, true)
                    setTextColor(UiTheme.accentBright)
                    maxLines = 1
                })
                header.addView(infoCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                card.addView(header)

                // Telemetry Strip
                val statsRow = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(act, UiTheme.surfaceAlt, 12)
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                }
                val statusText = if (u.online) "Online (${u.activeScreen})" else formatRelativeTime(u.lastActiveAt ?: u.updatedAt)
                statsRow.addView(TextView(act).apply {
                    text = "$statusText  •  ${u.drawingsCount} art  •  ${u.sessionsCount} visits  •  +${u.bonusCoins} coins"
                    fontFeatureSettings = "tnum"
                    textSize = 11f
                    UiTheme.applyUiFont(this, true)
                    setTextColor(UiTheme.textMain)
                })
                card.addView(statsRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = dp(9); bottomMargin = dp(10)
                })

                // Action Row
                val actions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                fun miniBtn(label: String, bgHex1: String, bgHex2: String, fgColor: Int = Color.WHITE, enabled: Boolean = true, onClick: () -> Unit): TextView {
                    return TextView(act).apply {
                        text = label
                        textSize = 11f
                        UiTheme.applyDisplayFont(this, true)
                        setTextColor(fgColor)
                        gravity = Gravity.CENTER
                        background = UiTheme.premiumGrad(act, bgHex1, bgHex2, 11)
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        isEnabled = enabled
                        alpha = if (enabled) 1f else 0.4f
                        UiTheme.bounce(this) { if (enabled) onClick() }
                    }
                }

                actions.addView(
                    miniBtn(L.t("admin_logs"), "#0EA5E9", "#2563EB") { showUserLogsDialog(u) },
                    LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
                )
                actions.addView(
                    miniBtn("+100", "#F59E0B", "#D97706") {
                        scope.launch {
                            val r = cloud.adminUpdateUser(uid, u.blocked, u.vipGranted, u.bonusCoins + 100L, u.adminNote)
                            act.runOnUiThread {
                                Toast.makeText(
                                    act,
                                    if (r.isSuccess) "+100 Coins sent to ${u.displayName}" else (r.exceptionOrNull()?.localizedMessage ?: "Update failed"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
                )
                actions.addView(
                    miniBtn(if (u.vipGranted) "Un-VIP" else "VIP", "#8B5CF6", "#6D28D9") {
                        scope.launch {
                            val r = cloud.adminUpdateUser(uid, u.blocked, !u.vipGranted, u.bonusCoins, u.adminNote)
                            act.runOnUiThread {
                                Toast.makeText(
                                    act,
                                    if (r.isSuccess) (if (!u.vipGranted) "VIP granted" else "VIP removed") else (r.exceptionOrNull()?.localizedMessage ?: "Update failed"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
                )
                actions.addView(
                    miniBtn(
                        if (u.blocked) L.t("admin_unblock") else L.t("admin_block"),
                        if (u.blocked) "#10B981" else "#EF4444",
                        if (u.blocked) "#059669" else "#DC2626",
                        enabled = !isSelf
                    ) {
                        scope.launch {
                            val r = cloud.adminUpdateUser(uid, !u.blocked, u.vipGranted, u.bonusCoins, u.adminNote)
                            act.runOnUiThread {
                                Toast.makeText(
                                    act,
                                    if (r.isSuccess) (if (!u.blocked) "User blocked" else "User unblocked") else (r.exceptionOrNull()?.localizedMessage ?: "Update failed"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(36), 1.1f)
                )
                card.addView(actions)

                usersListContainer.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
            }
        }

        fun renderSection() {
            syncSegmentStyle()
            bodyHost.removeAllViews()

            if (activeSection == AdminSection.USERS) {
                // Search Bar
                val searchWrap = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                }
                searchWrap.addView(
                    StudioIcons.imageView(act, StudioIcons.Type.SEARCH, 16, UiTheme.textSub),
                    LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) }
                )
                val searchEt = EditText(act).apply {
                    hint = L.t("admin_search_users")
                    setText(userSearchQuery)
                    textSize = 13.5f
                    UiTheme.applyUiFont(this)
                    setTextColor(UiTheme.textMain)
                    setHintTextColor(UiTheme.textSub)
                    background = null
                    setSingleLine(true)
                    setPadding(0, dp(8), 0, dp(8))
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            userSearchQuery = s?.toString() ?: ""
                            rebuildUsersListOnly()
                        }
                    })
                }
                searchWrap.addView(searchEt, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                bodyHost.addView(searchWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

                // Filter Chips
                val chipRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                val chips = listOf(
                    UserFilter.ALL to L.t("admin_filter_all"),
                    UserFilter.ONLINE to L.t("admin_online_now"),
                    UserFilter.VIP to "VIP",
                    UserFilter.BLOCKED to L.t("admin_blocked")
                )
                val chipViews = ArrayList<Pair<UserFilter, TextView>>()
                fun syncFilterChips() {
                    chipViews.forEach { (f, tv) ->
                        val active = f == userFilter
                        tv.background = if (active) UiTheme.premiumGrad(act, "#7C3AED", "#4F46E5", 12) else UiTheme.round(act, UiTheme.surface, 12, UiTheme.stroke)
                        tv.setTextColor(if (active) Color.WHITE else UiTheme.textMain)
                    }
                }
                chips.forEach { (f, label) ->
                    val tv = TextView(act).apply {
                        text = label
                        textSize = 12f
                        UiTheme.applyUiFont(this, true)
                        setPadding(dp(12), dp(7), dp(12), dp(7))
                        UiTheme.bounce(this) {
                            userFilter = f
                            syncFilterChips()
                            rebuildUsersListOnly()
                        }
                    }
                    chipViews.add(f to tv)
                    chipRow.addView(tv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
                }
                syncFilterChips()
                bodyHost.addView(
                    HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(chipRow) },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
                )

                (usersListContainer.parent as? LinearLayout)?.removeView(usersListContainer)
                bodyHost.addView(usersListContainer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                rebuildUsersListOnly()
            } else {
                // REMOTE APP CONTROLS SECTION
                val cfg = cloud.globalConfig
                var maintenanceMode = cfg.maintenanceMode
                var announcementEnabled = cfg.announcementEnabled
                var adsEnabled = cfg.adsEnabled
                var arCameraEnabled = cfg.arCameraEnabled
                var lightboxEnabled = cfg.lightboxEnabled
                var bingSearchEnabled = cfg.bingSearchEnabled
                var storeEnabled = cfg.storeEnabled

                fun switchCard(title: String, sub: String, initial: Boolean, onToggle: (Boolean) -> Unit): LinearLayout {
                    return LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiTheme.round(act, UiTheme.surface, 18, UiTheme.stroke)
                        setPadding(dp(14), dp(11), dp(14), dp(11))
                        elevation = dp(2).toFloat()
                        val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                        tc.addView(TextView(act).apply {
                            text = title
                            textSize = 14f
                            UiTheme.applyDisplayFont(this, true)
                            setTextColor(UiTheme.textMain)
                        })
                        tc.addView(TextView(act).apply {
                            text = sub
                            textSize = 11.5f
                            UiTheme.applyUiFont(this)
                            setTextColor(UiTheme.textSub)
                        })
                        addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
                        addView(Switch(act).apply {
                            isChecked = initial
                            setOnCheckedChangeListener { _, checked -> onToggle(checked) }
                        })
                    }
                }

                fun styledInput(hintText: String, initialVal: String, numeric: Boolean = false): EditText {
                    return EditText(act).apply {
                        hint = hintText
                        setText(initialVal)
                        textSize = 13.5f
                        UiTheme.applyUiFont(this)
                        setTextColor(UiTheme.textMain)
                        setHintTextColor(UiTheme.textSub)
                        background = UiTheme.round(act, UiTheme.surface, 14, UiTheme.stroke)
                        setPadding(dp(14), dp(11), dp(14), dp(11))
                        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
                    }
                }

                // 1. Maintenance Mode
                bodyHost.addView(
                    switchCard(L.t("admin_maintenance"), L.t("admin_maintenance_sub"), maintenanceMode) { maintenanceMode = it },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                )
                val maintMsgEt = styledInput(L.t("admin_maint_msg_hint"), cfg.maintenanceMessage)
                bodyHost.addView(maintMsgEt, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

                // 2. Live Home Announcement
                bodyHost.addView(
                    switchCard(L.t("admin_announce"), L.t("admin_announce_sub"), announcementEnabled) { announcementEnabled = it },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                )
                val annTitleEt = styledInput(L.t("admin_announce_title_hint"), cfg.announcementTitle)
                val annTextEt = styledInput(L.t("admin_announce_body_hint"), cfg.announcementText)
                bodyHost.addView(annTitleEt, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
                bodyHost.addView(annTextEt, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

                // 3. Feature Master Switches
                bodyHost.addView(
                    UiTheme.sectionHeader(act, StudioIcons.Type.SETTINGS, L.t("admin_feature_flags"), c1 = "#0EA5E9", c2 = "#2563EB")
                )
                bodyHost.addView(
                    switchCard(L.t("admin_flag_ads"), L.t("admin_flag_ads_sub"), adsEnabled) { adsEnabled = it },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                )
                bodyHost.addView(
                    switchCard(L.t("mode_camera"), L.t("mode_camera_sub"), arCameraEnabled) { arCameraEnabled = it },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                )
                bodyHost.addView(
                    switchCard(L.t("mode_lightbox"), L.t("mode_lightbox_sub"), lightboxEnabled) { lightboxEnabled = it },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                )
                bodyHost.addView(
                    switchCard(L.t("admin_flag_bing"), L.t("admin_flag_bing_sub"), bingSearchEnabled) { bingSearchEnabled = it },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                )
                bodyHost.addView(
                    switchCard(L.t("store_title"), L.t("admin_flag_store_sub"), storeEnabled) { storeEnabled = it },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
                )

                val minVerEt = styledInput("Min Version (e.g. 1.0.0)", cfg.minSupportedVersion)
                val welcomeCoinsEt = styledInput("Welcome Bonus Coins (0 - 5000)", cfg.welcomeBonusCoins.toString(), numeric = true)
                bodyHost.addView(minVerEt, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
                bodyHost.addView(welcomeCoinsEt, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

                var saving = false
                val saveBtn = UiTheme.studioGradientAction(
                    act = act,
                    icon = StudioIcons.Type.CHECK,
                    label = L.t("admin_push_live"),
                    hex1 = "#10B981",
                    hex2 = "#059669",
                    rDp = 18
                ) {
                    if (saving) return@studioGradientAction
                    saving = true
                    val welcomeCoins = welcomeCoinsEt.text.toString().trim().toLongOrNull()?.coerceIn(0L, 5000L) ?: 0L
                    val nextConfig = AppConfig(
                        maintenanceMode = maintenanceMode,
                        maintenanceMessage = maintMsgEt.text.toString().trim().ifEmpty { "Studio upgrade in progress." },
                        announcementEnabled = announcementEnabled,
                        announcementTitle = annTitleEt.text.toString().trim(),
                        announcementText = annTextEt.text.toString().trim(),
                        adsEnabled = adsEnabled,
                        arCameraEnabled = arCameraEnabled,
                        lightboxEnabled = lightboxEnabled,
                        bingSearchEnabled = bingSearchEnabled,
                        storeEnabled = storeEnabled,
                        minSupportedVersion = minVerEt.text.toString().trim().ifEmpty { "1.0.0" },
                        welcomeBonusCoins = welcomeCoins
                    )
                    scope.launch {
                        val res = cloud.adminSaveGlobalConfig(nextConfig)
                        act.runOnUiThread {
                            saving = false
                            if (res.isSuccess) {
                                Toast.makeText(act, L.t("admin_saved_ok"), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(act, res.exceptionOrNull()?.localizedMessage ?: "Failed to save config", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                bodyHost.addView(
                    UiTheme.wrapShimmerCta(act, saveBtn, 18).apply {
                        UiTheme.applyColoredShadow(this, Color.parseColor("#10B981"), 8)
                    },
                    LinearLayout.LayoutParams(MATCH_PARENT, dp(54)).apply { bottomMargin = dp(18) }
                )
            }
        }

        UiTheme.bounce(usersTabBtn) {
            if (activeSection != AdminSection.USERS) {
                activeSection = AdminSection.USERS
                renderSection()
            }
        }
        UiTheme.bounce(ctrlTabBtn) {
            if (activeSection != AdminSection.CONTROLS) {
                activeSection = AdminSection.CONTROLS
                renderSection()
            }
        }

        // Attach real-time Firestore listener for all users
        cloud.observeAllUsersForAdmin(
            onUsers = { users ->
                act.runOnUiThread {
                    usersLoadError = null
                    latestUsers = users
                    kpiTotalVal.text = users.size.toString()
                    kpiOnlineVal.text = users.count { it.online }.toString()
                    kpiDrawVal.text = users.sumOf { it.drawingsCount }.toString()
                    kpiVipVal.text = "${users.count { it.vipGranted }} / ${users.count { it.blocked }}"
                    if (activeSection == AdminSection.USERS) rebuildUsersListOnly()
                }
            },
            onError = { msg ->
                act.runOnUiThread {
                    usersLoadError = msg
                    if (activeSection == AdminSection.USERS) rebuildUsersListOnly()
                }
            }
        )

        renderSection()
        UiTheme.animatePageIn(main, 12, 220L)
        return main
    }
}
