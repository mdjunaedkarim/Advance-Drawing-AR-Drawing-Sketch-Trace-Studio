package com.ardrawing.sketch

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try { Firebase.auth.currentUser } catch (_: Throwable) { null }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class UserRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val deviceModel: String = "",
    val appVersion: String = "1.1.0",
    val language: String = "en",
    val activeScreen: String = "HOME",
    val online: Boolean = false,
    val blocked: Boolean = false,
    val vipGranted: Boolean = false,
    val bonusCoins: Long = 0L,
    val drawingsCount: Long = 0L,
    val sessionsCount: Long = 0L,
    val adsWatchedCount: Long = 0L,
    val purchasesCount: Long = 0L,
    val totalSpentCents: Long = 0L,
    val totalUsageSeconds: Long = 0L,
    val adminNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserActivityLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val detail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "Advance Drawing Studio is currently being upgraded. Please check back shortly.",
    val announcementEnabled: Boolean = false,
    val announcementTitle: String = "",
    val announcementText: String = "",
    val adsEnabled: Boolean = true,
    val arCameraEnabled: Boolean = true,
    val lightboxEnabled: Boolean = true,
    val bingSearchEnabled: Boolean = true,
    val storeEnabled: Boolean = true,
    val minSupportedVersion: String = "1.0.0",
    val welcomeBonusCoins: Long = 0L,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirebaseCloudManager(
    private val appContext: Context,
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        context.applicationContext,
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        val ALLOWED_ADMIN_EMAILS = setOf(
            "md.junaed.karim@gmail.com",
            "mdjunaidkarim85@gmail.com",
            "mdjunaidkarim26@gmail.com",
            "junaedkarim20@gmail.com"
        )
        const val PRIMARY_ADMIN_EMAIL = "md.junaed.karim@gmail.com"
        const val APP_VERSION_NAME = "1.1.0"
        private const val TAG = "CloudStudio"
    }

    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager: CredentialManager = CredentialManager.create(appContext)

    var globalConfig: AppConfig = AppConfig()
        private set
    var currentUserRecord: UserRecord? = null
        private set
    var isAdminUser: Boolean = false
        private set

    private var configListener: ListenerRegistration? = null
    private var selfUserListener: ListenerRegistration? = null
    private var allUsersListener: ListenerRegistration? = null
    private var activeListenersUid: String? = null

    fun currentUser(): FirebaseUser? = auth.currentUser

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    fun isPrimaryAdmin(user: FirebaseUser? = auth.currentUser): Boolean {
        if (user == null) return false
        val email = user.email?.trim()?.lowercase(java.util.Locale.US) ?: return false
        return user.isEmailVerified && email in ALLOWED_ADMIN_EMAILS
    }

    private fun deviceLabel(): String {
        val m = (Build.MANUFACTURER ?: "").trim()
        val model = (Build.MODEL ?: "").trim()
        val combined = if (model.startsWith(m, ignoreCase = true)) model else "$m $model".trim()
        return combined.ifEmpty { "Android Device" }.take(80)
    }

    // 1. Silent Auto-Sign-In on Startup (single credential option per request)
    fun attemptAutoSignIn(
        activity: Activity,
        scope: CoroutineScope,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        val existing = auth.currentUser
        if (existing != null) {
            onAuthSuccess(existing)
            return
        }
        val clientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    val signedIn = result.user
                    if (signedIn != null) onAuthSuccess(signedIn) else onUnauthenticated()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    // 2. Interactive Google Sign-In (single GetSignInWithGoogleOption per request)
    fun onGoogleSignInClicked(
        activity: Activity,
        scope: CoroutineScope,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    val signedIn = result.user
                    if (signedIn != null) onAuthSuccess(signedIn)
                    else onAuthError("Sign-in succeeded without user profile")
                } else {
                    onAuthError("Unexpected credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    // 3. Safe Sign-Out inside NonCancellable (detaches listeners before signOut)
    fun signOut(scope: CoroutineScope, onComplete: () -> Unit) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    markOfflineInternal()
                } catch (_: Exception) {}
                stopAllListeners()
                currentUserRecord = null
                isAdminUser = false
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                    onComplete()
                }
            }
        }
    }

    fun stopAllListeners() {
        configListener?.remove(); configListener = null
        selfUserListener?.remove(); selfUserListener = null
        allUsersListener?.remove(); allUsersListener = null
        activeListenersUid = null
    }

    fun stopAdminUsersListener() {
        allUsersListener?.remove()
        allUsersListener = null
    }

    suspend fun initializeAuthenticatedSession(
        languageCode: String,
        onConfigUpdated: (AppConfig) -> Unit,
        onSelfUserUpdated: (UserRecord) -> Unit,
        onAdminStatusResolved: (Boolean) -> Unit
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        if (uid.isBlank()) return Result.failure(IllegalStateException("Blank UID"))

        // Resolve admin role (primary admin email_verified OR /admins/{uid})
        val resolvedAdmin = resolveAdminRole(user)
        isAdminUser = resolvedAdmin
        onAdminStatusResolved(resolvedAdmin)

        // Register or refresh user document
        val regResult = upsertUserSession(user, languageCode)

        // Start real-time listeners (strictly gated behind authenticated UID)
        startRealtimeListeners(uid, onConfigUpdated, onSelfUserUpdated)

        // If admin and /app_config/global does not exist yet, initialize default config cleanly
        if (resolvedAdmin) {
            ensureGlobalConfigExists(user.email ?: PRIMARY_ADMIN_EMAIL)
        }

        // Log session start
        logActivity("SESSION_START", "Opened app (${deviceLabel()})")
        return regResult
    }

    private suspend fun resolveAdminRole(user: FirebaseUser): Boolean {
        // Strictly restrict Admin Panel access ONLY to the 4 allowed verified Gmail accounts
        return isPrimaryAdmin(user)
    }

    private fun startRealtimeListeners(
        uid: String,
        onConfigUpdated: (AppConfig) -> Unit,
        onSelfUserUpdated: (UserRecord) -> Unit
    ) {
        if (activeListenersUid == uid && configListener != null && selfUserListener != null) return
        stopAllListeners()
        activeListenersUid = uid

        val configPath = "app_config/global"
        configListener = db.collection("app_config").document("global")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(err, OperationType.GET, configPath)
                    }
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val cfg = snap.toObject(AppConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (cfg != null) {
                        globalConfig = cfg
                        onConfigUpdated(cfg)
                    }
                }
            }

        val userPath = "users/$uid"
        selfUserListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(err, OperationType.GET, userPath)
                    }
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val rec = snap.toObject(UserRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (rec != null) {
                        currentUserRecord = rec
                        onSelfUserUpdated(rec)
                    }
                }
            }
    }

    private suspend fun upsertUserSession(user: FirebaseUser, languageCode: String): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val safeName = (user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: user.email?.substringBefore("@")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Studio Artist").take(80)
            val safeEmail = (user.email ?: "").trim().take(120)
            val safeLang = languageCode.trim().ifEmpty { "en" }.take(16)
            val device = deviceLabel()

            if (!snap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "deviceModel" to device,
                    "appVersion" to APP_VERSION_NAME,
                    "language" to safeLang,
                    "activeScreen" to "HOME",
                    "online" to true,
                    "blocked" to false,
                    "vipGranted" to false,
                    "bonusCoins" to 0L,
                    "drawingsCount" to 0L,
                    "sessionsCount" to 1L,
                    "adsWatchedCount" to 0L,
                    "purchasesCount" to 0L,
                    "totalSpentCents" to 0L,
                    "totalUsageSeconds" to 0L,
                    "adminNote" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "deviceModel" to device,
                    "appVersion" to APP_VERSION_NAME,
                    "language" to safeLang,
                    "activeScreen" to "HOME",
                    "online" to true,
                    "sessionsCount" to FieldValue.increment(1L),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!snap.contains("adsWatchedCount")) updatePayload["adsWatchedCount"] = 0L
                if (!snap.contains("purchasesCount")) updatePayload["purchasesCount"] = 0L
                if (!snap.contains("totalSpentCents")) updatePayload["totalSpentCents"] = 0L
                if (!snap.contains("totalUsageSeconds")) updatePayload["totalUsageSeconds"] = 0L
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun ensureGlobalConfigExists(adminEmail: String) {
        val path = "app_config/global"
        try {
            val ref = db.collection("app_config").document("global")
            val snap = ref.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "maintenanceMode" to false,
                    "maintenanceMessage" to "Advance Drawing Studio is upgrading. Please check back shortly.",
                    "announcementEnabled" to false,
                    "announcementTitle" to "",
                    "announcementText" to "",
                    "adsEnabled" to true,
                    "arCameraEnabled" to true,
                    "lightboxEnabled" to true,
                    "bingSearchEnabled" to true,
                    "storeEnabled" to true,
                    "minSupportedVersion" to "1.0.0",
                    "welcomeBonusCoins" to 0L,
                    "updatedBy" to adminEmail.take(120),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun updateActiveScreen(screen: String, languageCode: String = L.lang): Result<Unit> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "activeScreen" to screen.take(32),
                    "language" to languageCode.take(16),
                    "online" to true,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordDrawingSaved(detail: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "drawingsCount" to FieldValue.increment(1L),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logActivity("SAVE_DRAWING", detail.take(140))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordAdWatched(adType: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "adsWatchedCount" to FieldValue.increment(1L),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logActivity("AD_WATCHED", adType.take(140))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordPurchase(productId: String, title: String, amountCents: Long): Result<Unit> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) return Result.success(Unit)
        val safeCents = amountCents.coerceIn(0L, 10_000_000L)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "purchasesCount" to FieldValue.increment(1L),
                    "totalSpentCents" to FieldValue.increment(safeCents),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val usd = String.format(java.util.Locale.US, "$%.2f", safeCents / 100.0)
            logActivity("PURCHASE", "$title ($productId) • $usd".take(140))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordUsageSeconds(deltaSeconds: Long): Result<Unit> {
        val uid = auth.currentUser?.uid
        val safeSec = deltaSeconds.coerceIn(0L, 86_400L)
        if (uid.isNullOrBlank() || safeSec <= 0L) return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "totalUsageSeconds" to FieldValue.increment(safeSec),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun logActivity(action: String, detail: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) return Result.success(Unit)
        val cleanAction = action.trim().ifEmpty { "EVENT" }.take(40)
        val cleanDetail = detail.trim().take(140)
        val path = "users/$uid/activity_logs"
        return try {
            val ref = db.collection("users").document(uid).collection("activity_logs").document()
            ref.set(
                mapOf(
                    "action" to cleanAction,
                    "detail" to cleanDetail,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun markOfflineInternal() {
        val uid = auth.currentUser?.uid ?: return
        if (uid.isBlank()) return
        val path = "users/$uid"
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "online" to false,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    fun markPresenceAsync(scope: CoroutineScope, isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (uid.isBlank()) return
        scope.launch {
            val path = "users/$uid"
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "online" to isOnline,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, path)
            }
        }
    }

    // ---------------- ADMIN PANEL OPERATIONS ----------------

    fun observeAllUsersForAdmin(
        onUsers: (List<UserRecord>) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || !isAdminUser) {
            onError("Admin authorization required.")
            return
        }
        stopAdminUsersListener()
        val path = "users"
        allUsersListener = db.collection("users")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(err, OperationType.LIST, path)
                    }
                    onError(err.localizedMessage ?: "Failed to load users")
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(UserRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.sortedWith(
                        compareByDescending<UserRecord> { it.online }
                            .thenByDescending { it.lastActiveAt ?: it.updatedAt ?: Timestamp(0, 0) }
                    ) ?: emptyList()
                onUsers(list)
            }
    }

    suspend fun fetchUserActivityLogs(targetUid: String): Result<List<UserActivityLog>> {
        val path = "users/$targetUid/activity_logs"
        return try {
            requireUserId()
            require(targetUid.isNotBlank()) { "Target UID is blank" }
            val snap = db.collection("users").document(targetUid)
                .collection("activity_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()
            val logs = snap.toObjects(UserActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(logs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUser(
        targetUid: String,
        blocked: Boolean,
        vipGranted: Boolean,
        bonusCoins: Long,
        adminNote: String
    ): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            check(isAdminUser) { "Admin privilege required." }
            require(targetUid.isNotBlank()) { "Target UID must not be blank." }
            val safeCoins = bonusCoins.coerceIn(0L, 1_000_000L)
            val safeNote = adminNote.trim().take(200)
            db.collection("users").document(targetUid).update(
                mapOf(
                    "blocked" to blocked,
                    "vipGranted" to vipGranted,
                    "bonusCoins" to safeCoins,
                    "adminNote" to safeNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSaveGlobalConfig(config: AppConfig): Result<Unit> {
        val path = "app_config/global"
        return try {
            requireUserId()
            check(isAdminUser) { "Admin privilege required." }
            val adminEmail = (auth.currentUser?.email ?: PRIMARY_ADMIN_EMAIL).take(120)
            val ref = db.collection("app_config").document("global")
            val exists = ref.get().await().exists()

            val common = mutableMapOf<String, Any>(
                "maintenanceMode" to config.maintenanceMode,
                "maintenanceMessage" to config.maintenanceMessage.trim().take(300),
                "announcementEnabled" to config.announcementEnabled,
                "announcementTitle" to config.announcementTitle.trim().take(80),
                "announcementText" to config.announcementText.trim().take(240),
                "adsEnabled" to config.adsEnabled,
                "arCameraEnabled" to config.arCameraEnabled,
                "lightboxEnabled" to config.lightboxEnabled,
                "bingSearchEnabled" to config.bingSearchEnabled,
                "storeEnabled" to config.storeEnabled,
                "minSupportedVersion" to config.minSupportedVersion.trim().ifEmpty { "1.0.0" }.take(20),
                "welcomeBonusCoins" to config.welcomeBonusCoins.coerceIn(0L, 5000L),
                "updatedBy" to adminEmail,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (exists) {
                ref.update(common).await()
            } else {
                common["createdAt"] = FieldValue.serverTimestamp()
                ref.set(common).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
