package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.R
import com.example.model.ActivityLog
import com.example.model.CashTransaction
import com.example.model.GoalStatus
import com.example.model.Household
import com.example.model.HouseholdMember
import com.example.model.InviteCode
import com.example.model.InviteCodePreview
import com.example.model.MemberRole
import com.example.model.MonthlyBudget
import com.example.model.SavingsGoal
import com.example.model.SavingsTransaction
import com.example.model.SyncStatus
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Date
import java.util.UUID

class CoupleCashRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storage: FirebaseStorage? = null,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        db = createConfiguredFirestore(context.applicationContext),
        auth = Firebase.auth,
        storage = runCatching { FirebaseStorage.getInstance() }.getOrNull(),
        appContext = context.applicationContext
    )

    companion object {
        private const val TAG = "CoupleCashRepo"
        private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val INVITE_DURATION_MS = 24L * 60L * 60L * 1000L
        const val RESET_MARKER = "RESET_KESELURUHAN"

        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val databaseId = context.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            try {
                firestore.firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                }
            } catch (_: IllegalStateException) {
                // Already configured after first access
            }
            return firestore
        }
    }

    private fun requireUser(): FirebaseUser {
        return auth.currentUser
            ?: throw IllegalStateException("Pengguna wajib masuk dengan akun Google terlebih dahulu.")
    }

    private fun requireUserId(): String = requireUser().uid

    private suspend fun <T> withRetry(maxAttempts: Int = 3, block: suspend () -> T): T {
        var attempt = 0
        var delayMs = 350L
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                attempt++
                val retryable = e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                if (!retryable || attempt >= maxAttempts) throw e
                delay(delayMs)
                delayMs *= 2
            }
        }
    }

    // --- User Profile & Preferences ---

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val user = requireUser()
            val docRef = db.collection("users").document(user.uid)
            val snap = withRetry { docRef.get().await() }
            if (!snap.exists()) {
                val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Pengguna").take(80)
                val email = (user.email?.takeIf { it.isNotBlank() } ?: "${user.uid}@google.user").take(160)
                val payload = mapOf(
                    "uid" to user.uid,
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to user.photoUrl?.toString()?.take(600),
                    "partnerRoleLabel" to "Suami",
                    "notifPartnerTx" to true,
                    "notifIncome" to true,
                    "notifExpense" to true,
                    "notifSavings" to true,
                    "notifBudget" to true,
                    "notifGoal" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                withRetry { docRef.set(payload).await() }
                val fresh = docRef.get().await()
                Result.success(
                    fresh.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: UserProfile(uid = user.uid, displayName = displayName, email = email)
                )
            } else {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(uid = user.uid)
                Result.success(existing)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        if (uid.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserPartnerLabel(label: String, activeHouseholdId: String?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val userId = requireUserId()
            require(label in listOf("Suami", "Istri", "Pasangan")) { "Label peran tidak valid" }
            val batch = db.batch()
            batch.update(
                db.collection("users").document(userId),
                mapOf("partnerRoleLabel" to label, "updatedAt" to FieldValue.serverTimestamp())
            )
            if (!activeHouseholdId.isNullOrBlank()) {
                batch.update(
                    db.collection("households").document(activeHouseholdId)
                        .collection("members").document(userId),
                    mapOf("partnerLabel" to label, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNotificationPreferences(
        notifPartnerTx: Boolean,
        notifIncome: Boolean,
        notifExpense: Boolean,
        notifSavings: Boolean,
        notifBudget: Boolean,
        notifGoal: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val userId = requireUserId()
            db.collection("users").document(userId).update(
                mapOf(
                    "notifPartnerTx" to notifPartnerTx,
                    "notifIncome" to notifIncome,
                    "notifExpense" to notifExpense,
                    "notifSavings" to notifSavings,
                    "notifBudget" to notifBudget,
                    "notifGoal" to notifGoal,
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

    suspend fun syncFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isNotBlank()) {
                db.collection("users").document(uid).update(
                    mapOf(
                        "fcmToken" to token.take(512),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM token sync skipped: ${e.message}")
        }
    }

    // --- Households & Invite Codes ---

    fun observeHouseholds(uid: String): Flow<List<Household>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households"
        emitAll(
            db.collection("households")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap -> snap.toObjects(Household::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeHouseholdMembers(householdId: String, uid: String): Flow<List<HouseholdMember>> = flow {
        if (householdId.isBlank() || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households/$householdId/members"
        emitAll(
            db.collection("households").document(householdId).collection("members")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap -> snap.toObjects(HouseholdMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createHousehold(name: String, partnerLabel: String): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val cleanName = name.trim().take(80)
        require(cleanName.isNotEmpty()) { "Nama buku kas wajib diisi." }
        val householdId = "hh_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "households/$householdId"
        return try {
            val user = requireUser()
            val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Pemilik").take(80)
            val roleLabel = if (partnerLabel in listOf("Suami", "Istri", "Pasangan")) partnerLabel else "Suami"
            val memberUids = listOf(user.uid)

            val batch = db.batch()
            val hhRef = db.collection("households").document(householdId)
            batch.set(
                hhRef,
                mapOf(
                    "name" to cleanName,
                    "ownerId" to user.uid,
                    "ownerName" to displayName,
                    "memberUids" to memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                hhRef.collection("members").document(user.uid),
                mapOf(
                    "uid" to user.uid,
                    "householdId" to householdId,
                    "memberUids" to memberUids,
                    "displayName" to displayName,
                    "photoUrl" to user.photoUrl?.toString()?.take(600),
                    "role" to MemberRole.OWNER.name,
                    "partnerLabel" to roleLabel,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
            )
            batch.set(
                hhRef.collection("activityLogs").document(),
                mapOf(
                    "householdId" to householdId,
                    "memberUids" to memberUids,
                    "actorUid" to user.uid,
                    "actorName" to displayName,
                    "actorRoleLabel" to roleLabel,
                    "activityType" to "HOUSEHOLD_CREATED",
                    "detail" to "$displayName membuat Buku Kas \"$cleanName\"",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(user.uid),
                mapOf(
                    "activeHouseholdId" to householdId,
                    "partnerRoleLabel" to roleLabel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            withRetry { batch.commit().await() }
            Result.success(householdId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun generateInviteCode(household: Household): Result<InviteCode> {
        val path = "inviteCodes"
        return try {
            val user = requireUser()
            check(household.ownerId == user.uid) { "Hanya pemilik buku kas yang dapat membuat kode undangan." }
            check(household.memberUids.size < 2) { "Buku kas sudah terhubung dengan pasangan (maksimal 2 anggota)." }

            val random = SecureRandom()
            val code = buildString {
                repeat(8) {
                    append(INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)])
                }
            }
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + INVITE_DURATION_MS))
            val batch = db.batch()

            // Revoke previous active invite code if present
            val oldCode = household.activeInviteCode
            if (!oldCode.isNullOrBlank()) {
                val oldSnap = db.collection("inviteCodes").document(oldCode).get().await()
                if (oldSnap.exists() && oldSnap.getBoolean("active") == true) {
                    batch.update(
                        oldSnap.reference,
                        mapOf("active" to false, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }

            val codeRef = db.collection("inviteCodes").document(code)
            val payload = mapOf(
                "code" to code,
                "householdId" to household.id,
                "householdName" to household.name,
                "ownerId" to user.uid,
                "ownerName" to household.ownerName,
                "active" to true,
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(codeRef, payload)
            batch.update(
                db.collection("households").document(household.id),
                mapOf("activeInviteCode" to code, "updatedAt" to FieldValue.serverTimestamp())
            )
            withRetry { batch.commit().await() }

            Result.success(
                InviteCode(
                    id = code,
                    code = code,
                    householdId = household.id,
                    householdName = household.name,
                    ownerId = user.uid,
                    ownerName = household.ownerName,
                    active = true,
                    expiresAt = expiresAt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeInviteCode(householdId: String, code: String): Result<Unit> {
        val path = "inviteCodes/$code"
        return try {
            val uid = requireUserId()
            val codeRef = db.collection("inviteCodes").document(code)
            val snap = codeRef.get().await()
            if (snap.exists() && snap.getString("ownerId") == uid) {
                val batch = db.batch()
                batch.update(codeRef, mapOf("active" to false, "updatedAt" to FieldValue.serverTimestamp()))
                batch.update(
                    db.collection("households").document(householdId),
                    mapOf("activeInviteCode" to null, "updatedAt" to FieldValue.serverTimestamp())
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun previewInviteCode(rawCode: String): Result<InviteCodePreview> {
        val code = rawCode.trim().uppercase()
        val path = "inviteCodes/$code"
        return try {
            val uid = requireUserId()
            require(code.length in 6..16 && code.all { it.isLetterOrDigit() }) {
                "Kode undangan harus 6-16 karakter huruf/angka."
            }
            val snap = db.collection("inviteCodes").document(code).get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Kode undangan tidak ditemukan."))
            }
            val invite = snap.toObject(InviteCode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Data kode undangan tidak valid."))

            if (!invite.active) {
                return Result.failure(IllegalStateException("Kode undangan sudah digunakan atau dibatalkan."))
            }
            val expMillis = invite.expiresAt?.toDate()?.time ?: 0L
            if (expMillis <= System.currentTimeMillis()) {
                return Result.failure(IllegalStateException("Kode undangan sudah kedaluwarsa."))
            }
            if (invite.ownerId == uid) {
                return Result.failure(IllegalStateException("Anda adalah pemilik buku kas ini."))
            }
            Result.success(
                InviteCodePreview(
                    code = invite.code,
                    householdId = invite.householdId,
                    householdName = invite.householdName,
                    ownerName = invite.ownerName,
                    expiresAtMillis = expMillis
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun joinHouseholdByCode(rawCode: String, partnerLabel: String): Result<String> {
        val code = rawCode.trim().uppercase()
        val path = "inviteCodes/$code"
        return try {
            val user = requireUser()
            val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Pasangan").take(80)
            val roleLabel = if (partnerLabel in listOf("Suami", "Istri", "Pasangan")) partnerLabel else "Istri"

            val inviteSnap = db.collection("inviteCodes").document(code).get().await()
            val invite = inviteSnap.toObject(InviteCode::class.java)
                ?: return Result.failure(IllegalArgumentException("Kode undangan tidak ditemukan."))

            if (!invite.active || (invite.expiresAt?.toDate()?.time ?: 0L) <= System.currentTimeMillis()) {
                return Result.failure(IllegalStateException("Kode undangan tidak aktif atau kedaluwarsa."))
            }

            val householdId = invite.householdId
            val hhRef = db.collection("households").document(householdId)
            val codeRef = db.collection("inviteCodes").document(code)

            db.runTransaction { tx ->
                val hhSnap = tx.get(hhRef)
                val codeSnapTx = tx.get(codeRef)
                if (!hhSnap.exists() || !codeSnapTx.exists()) {
                    throw IllegalStateException("Buku kas atau kode undangan tidak tersedia.")
                }
                val currentMembers = (hhSnap.get("memberUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
                if (user.uid in currentMembers) {
                    throw IllegalStateException("Anda sudah tergabung di buku kas ini.")
                }
                if (currentMembers.size >= 2) {
                    throw IllegalStateException("Buku kas ini sudah memiliki 2 anggota aktif.")
                }
                val updatedMembers = currentMembers + user.uid

                tx.update(
                    hhRef,
                    mapOf("memberUids" to updatedMembers, "updatedAt" to FieldValue.serverTimestamp())
                )
                tx.update(
                    codeRef,
                    mapOf(
                        "active" to false,
                        "usedByUid" to user.uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    hhRef.collection("members").document(user.uid),
                    mapOf(
                        "uid" to user.uid,
                        "householdId" to householdId,
                        "memberUids" to updatedMembers,
                        "displayName" to displayName,
                        "photoUrl" to user.photoUrl?.toString()?.take(600),
                        "role" to MemberRole.PARTNER.name,
                        "partnerLabel" to roleLabel,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null } as Map<String, Any>
                )
                tx.set(
                    hhRef.collection("activityLogs").document(),
                    mapOf(
                        "householdId" to householdId,
                        "memberUids" to updatedMembers,
                        "actorUid" to user.uid,
                        "actorName" to displayName,
                        "actorRoleLabel" to roleLabel,
                        "activityType" to "MEMBER_JOINED",
                        "detail" to "$displayName bergabung ke Buku Kas Bersama",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    db.collection("users").document(user.uid),
                    mapOf(
                        "activeHouseholdId" to householdId,
                        "partnerRoleLabel" to roleLabel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(householdId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Keeps denormalized memberUids synchronized on subcollections when partner joins/leaves.
     */
    suspend fun syncSubcollectionMemberUids(household: Household) {
        val uid = auth.currentUser?.uid ?: return
        if (household.ownerId != uid) return
        val expected = household.memberUids
        val subs = listOf("members", "transactions", "budgets", "savingsGoals", "savingsTransactions", "activityLogs")
        for (sub in subs) {
            try {
                val snap = db.collection("households").document(household.id)
                    .collection(sub)
                    .whereArrayContains("memberUids", uid)
                    .get()
                    .await()
                val docsToUpdate = snap.documents.filter { doc ->
                    val current = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    current != expected
                }
                if (docsToUpdate.isNotEmpty()) {
                    val batch = db.batch()
                    docsToUpdate.take(400).forEach { doc ->
                        val updates = mutableMapOf<String, Any>("memberUids" to expected)
                        if (sub != "activityLogs") {
                            updates["updatedAt"] = FieldValue.serverTimestamp()
                        }
                        batch.update(doc.reference, updates)
                    }
                    batch.commit().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Non-fatal memberUids sync issue in $sub: ${e.message}")
            }
        }
    }

    suspend fun removeOrLeavePartner(household: Household, targetUid: String): Result<Unit> {
        val path = "households/${household.id}"
        return try {
            val actor = requireUser()
            check(household.memberUids.size == 2) { "Tidak ada anggota pasangan untuk dihapus." }
            check(targetUid != household.ownerId) { "Pemilik utama tidak dapat dihapus saat pasangan masih aktif." }
            check(actor.uid == household.ownerId || actor.uid == targetUid) { "Akses ditolak." }

            val updatedMembers = listOf(household.ownerId)
            val hhRef = db.collection("households").document(household.id)
            val batch = db.batch()
            batch.update(
                hhRef,
                mapOf("memberUids" to updatedMembers, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.delete(hhRef.collection("members").document(targetUid))
            batch.set(
                hhRef.collection("activityLogs").document(),
                mapOf(
                    "householdId" to household.id,
                    "memberUids" to updatedMembers,
                    "actorUid" to actor.uid,
                    "actorName" to (actor.displayName ?: "Anggota").take(80),
                    "actorRoleLabel" to if (actor.uid == household.ownerId) "Pemilik" else "Pasangan",
                    "activityType" to "MEMBER_LEFT",
                    "detail" to if (actor.uid == targetUid) "${actor.displayName ?: "Pasangan"} keluar dari Buku Kas"
                    else "Pemilik menghapus anggota pasangan dari Buku Kas",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (actor.uid == targetUid) {
                batch.update(
                    db.collection("users").document(actor.uid),
                    mapOf("activeHouseholdId" to null, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            withRetry { batch.commit().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Real-Time Sync Status ---

    fun observeSyncStatus(householdId: String, uid: String): Flow<SyncStatus> {
        val networkFlow = callbackFlow {
            val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                trySend(true)
                awaitClose {}
                return@callbackFlow
            }
            fun isOnline(): Boolean {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            trySend(isOnline())
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { trySend(true) }
                override fun onLost(network: Network) { trySend(isOnline()) }
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }

        val metadataFlow = flow {
            if (householdId.isBlank() || uid.isBlank()) {
                emit(Pair(false, false))
                return@flow
            }
            emitAll(
                db.collection("households").document(householdId).collection("transactions")
                    .whereArrayContains("memberUids", uid)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap -> Pair(snap.metadata.hasPendingWrites(), snap.metadata.isFromCache) }
                    .catch { emit(Pair(false, true)) }
            )
        }

        return combine(networkFlow, metadataFlow) { online, (hasPendingWrites, isFromCache) ->
            when {
                !online -> SyncStatus.OFFLINE
                hasPendingWrites || isFromCache -> SyncStatus.SYNCING
                else -> SyncStatus.SYNCED
            }
        }
    }

    // --- Transactions ---

    fun observeTransactions(householdId: String, uid: String): Flow<List<CashTransaction>> = flow {
        if (householdId.isBlank() || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households/$householdId/transactions"
        emitAll(
            db.collection("households").document(householdId).collection("transactions")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CashTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.transactionDateMillis }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun uploadReceiptPhoto(householdId: String, txId: String, uri: Uri): String? {
        val st = storage
        if (st != null) {
            try {
                val uploadedUrl = withTimeoutOrNull(6_000L) {
                    val mimeType = appContext?.contentResolver?.getType(uri)
                        ?.takeIf { it.startsWith("image/") }
                        ?: "image/jpeg"
                    val metadata = storageMetadata {
                        contentType = mimeType
                    }
                    val ref = st.reference.child("households/$householdId/receipts/$txId.jpg")
                    ref.putFile(uri, metadata).await()
                    ref.downloadUrl.await().toString()
                }
                if (!uploadedUrl.isNullOrBlank()) {
                    return uploadedUrl
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Storage unavailable, using compressed Firestore receipt fallback: ${e.message}")
            }
        }
        return encodeCompressedReceiptDataUri(uri)
    }

    private suspend fun encodeCompressedReceiptDataUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext null
        try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val maxDim = 800
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDim * 2 || bounds.outHeight / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            val scaled = if (decoded.width > maxDim || decoded.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val baos = ByteArrayOutputStream()
            var encodedDataUri: String? = null
            for (quality in intArrayOf(70, 55, 40, 28)) {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val bytes = baos.toByteArray()
                val candidate = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (candidate.length <= 245_000) {
                    encodedDataUri = candidate
                    break
                }
            }
            scaled.recycle()
            encodedDataUri
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compress receipt image fallback: ${e.message}")
            null
        }
    }

    suspend fun addTransaction(
        household: Household,
        type: TransactionType,
        amount: Long,
        category: String,
        source: String?,
        paymentMethod: String?,
        note: String?,
        receiptUri: Uri?,
        roleLabel: String,
        transactionDateMillis: Long,
        clientMutationId: String
    ): Result<Unit> {
        val path = "households/${household.id}/transactions"
        return try {
            val user = requireUser()
            require(amount > 0L) { "Nominal transaksi harus lebih dari Rp0." }
            require(category.isNotBlank()) { "Kategori wajib dipilih." }

            // Idempotent duplicate check using clientMutationId
            val dupCheck = db.collection("households").document(household.id)
                .collection("transactions")
                .whereArrayContains("memberUids", user.uid)
                .whereEqualTo("clientMutationId", clientMutationId)
                .get()
                .await()
            if (!dupCheck.isEmpty) {
                return Result.success(Unit)
            }

            val txRef = db.collection("households").document(household.id)
                .collection("transactions").document()
            val receiptUrl = receiptUri?.let { uploadReceiptPhoto(household.id, txRef.id, it) }

            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val cleanRole = roleLabel.trim().ifBlank { "Suami" }.take(30)
            val batch = db.batch()
            val payload = mapOf(
                "householdId" to household.id,
                "memberUids" to household.memberUids,
                "type" to type.name,
                "amount" to amount,
                "category" to category.take(60),
                "source" to source?.trim()?.takeIf { it.isNotEmpty() }?.take(100),
                "paymentMethod" to paymentMethod?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
                "note" to note?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "receiptUrl" to receiptUrl?.take(250_000),
                "recordedByUid" to user.uid,
                "recordedByName" to actorName,
                "recordedByRoleLabel" to cleanRole,
                "transactionDateMillis" to transactionDateMillis,
                "clientMutationId" to clientMutationId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            batch.set(txRef, payload)

            val activityType = if (type == TransactionType.INCOME) "INCOME_ADDED" else "EXPENSE_ADDED"
            val verb = if (type == TransactionType.INCOME) "menambahkan pemasukan" else "mencatat pengeluaran"
            batch.set(
                db.collection("households").document(household.id).collection("activityLogs").document(),
                mapOf(
                    "householdId" to household.id,
                    "memberUids" to household.memberUids,
                    "actorUid" to user.uid,
                    "actorName" to actorName,
                    "actorRoleLabel" to cleanRole,
                    "activityType" to activityType,
                    "detail" to "$actorName ($cleanRole) $verb $category sebesar ${com.example.util.CurrencyFormatter.formatRupiah(amount)}".take(250),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            withRetry { batch.commit().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        household: Household,
        existingTx: CashTransaction,
        amount: Long,
        category: String,
        source: String?,
        paymentMethod: String?,
        note: String?,
        receiptUri: Uri? = null,
        roleLabel: String,
        transactionDateMillis: Long
    ): Result<Unit> {
        val path = "households/${household.id}/transactions/${existingTx.id}"
        return try {
            val user = requireUser()
            require(amount > 0L) { "Nominal transaksi harus lebih dari Rp0." }
            require(category.isNotBlank()) { "Kategori wajib dipilih." }
            require(!existingTx.deleted) { "Transaksi yang sudah dihapus tidak dapat diedit." }

            val uploadedReceipt = receiptUri?.let { uploadReceiptPhoto(household.id, existingTx.id, it) }

            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val cleanRole = roleLabel.trim().ifBlank { "Suami" }.take(30)
            val oldAmountFmt = com.example.util.CurrencyFormatter.formatRupiah(existingTx.amount)
            val newAmountFmt = com.example.util.CurrencyFormatter.formatRupiah(amount)
            val summaryText = if (existingTx.amount != amount) {
                "Nominal $oldAmountFmt -> $newAmountFmt oleh $actorName ($cleanRole)"
            } else {
                "Diperbarui ($category) oleh $actorName ($cleanRole)"
            }.take(300)

            val txRef = db.collection("households").document(household.id)
                .collection("transactions").document(existingTx.id)
            val logRef = db.collection("households").document(household.id)
                .collection("activityLogs").document()

            db.runTransaction { tx ->
                val snap = tx.get(txRef)
                if (!snap.exists()) throw IllegalStateException("Transaksi tidak ditemukan.")
                if (snap.getBoolean("deleted") == true) {
                    throw IllegalStateException("Transaksi sudah dihapus sebelumnya.")
                }
                val currentEdits = snap.getLong("editCount") ?: 0L
                val updates = mutableMapOf<String, Any?>(
                    "amount" to amount,
                    "category" to category.take(60),
                    "source" to source?.trim()?.takeIf { it.isNotEmpty() }?.take(100),
                    "paymentMethod" to paymentMethod?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
                    "note" to note?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                    "transactionDateMillis" to transactionDateMillis,
                    "editCount" to (currentEdits + 1L),
                    "lastEditedByUid" to user.uid,
                    "lastEditedByName" to actorName,
                    "lastEditedByRoleLabel" to cleanRole,
                    "lastEditSummary" to summaryText,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!uploadedReceipt.isNullOrBlank()) {
                    updates["receiptUrl"] = uploadedReceipt.take(250_000)
                }
                tx.update(txRef, updates)

                val detailMsg = if (existingTx.amount != amount) {
                    "$actorName ($cleanRole) mengedit ${existingTx.category}: $oldAmountFmt menjadi $newAmountFmt"
                } else {
                    "$actorName ($cleanRole) memperbarui detail transaksi $category ($newAmountFmt)"
                }.take(250)

                tx.set(
                    logRef,
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "actorUid" to user.uid,
                        "actorName" to actorName,
                        "actorRoleLabel" to cleanRole,
                        "activityType" to "TRANSACTION_EDITED",
                        "detail" to detailMsg,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(
        household: Household,
        transactionId: String,
        roleLabel: String
    ): Result<Unit> {
        val path = "households/${household.id}/transactions/$transactionId"
        return try {
            val user = requireUser()
            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val cleanRole = roleLabel.trim().ifBlank { "Suami" }.take(30)
            val txRef = db.collection("households").document(household.id)
                .collection("transactions").document(transactionId)
            val logRef = db.collection("households").document(household.id)
                .collection("activityLogs").document()

            db.runTransaction { tx ->
                val snap = tx.get(txRef)
                if (!snap.exists()) throw IllegalStateException("Transaksi tidak ditemukan.")
                if (snap.getBoolean("deleted") == true) return@runTransaction
                val cat = snap.getString("category") ?: "Transaksi"
                val amt = snap.getLong("amount") ?: 0L

                tx.update(
                    txRef,
                    mapOf(
                        "deleted" to true,
                        "deletedAtMillis" to System.currentTimeMillis(),
                        "deletedByUid" to user.uid,
                        "deletedByName" to actorName,
                        "deletedByRoleLabel" to cleanRole,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    logRef,
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "actorUid" to user.uid,
                        "actorName" to actorName,
                        "actorRoleLabel" to cleanRole,
                        "activityType" to "TRANSACTION_DELETED",
                        "detail" to "$actorName ($cleanRole) menghapus $cat sebesar ${com.example.util.CurrencyFormatter.formatRupiah(amt)}".take(250),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreTransaction(
        household: Household,
        transactionId: String,
        roleLabel: String
    ): Result<Unit> {
        val path = "households/${household.id}/transactions/$transactionId"
        return try {
            val user = requireUser()
            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val cleanRole = roleLabel.trim().ifBlank { "Suami" }.take(30)
            val txRef = db.collection("households").document(household.id)
                .collection("transactions").document(transactionId)
            val logRef = db.collection("households").document(household.id)
                .collection("activityLogs").document()

            db.runTransaction { tx ->
                val snap = tx.get(txRef)
                if (!snap.exists()) throw IllegalStateException("Transaksi tidak ditemukan.")
                if (snap.getBoolean("deleted") != true) {
                    throw IllegalStateException("Transaksi sudah dalam status aktif.")
                }
                if (snap.getString("lastEditSummary") == RESET_MARKER) {
                    throw IllegalStateException("Transaksi dari sebelum reset data tidak dapat dipulihkan.")
                }
                val typeStr = snap.getString("type") ?: ""
                require(typeStr == TransactionType.INCOME.name || typeStr == TransactionType.EXPENSE.name) {
                    "Hanya transaksi pemasukan atau pengeluaran yang dapat dipulihkan."
                }
                val currentEdits = snap.getLong("editCount") ?: 0L
                val cat = snap.getString("category") ?: "Transaksi"
                val amt = snap.getLong("amount") ?: 0L

                tx.update(
                    txRef,
                    mapOf(
                        "deleted" to false,
                        "deletedAtMillis" to null,
                        "deletedByUid" to null,
                        "deletedByName" to null,
                        "deletedByRoleLabel" to null,
                        "editCount" to (currentEdits + 1L),
                        "lastEditedByUid" to user.uid,
                        "lastEditedByName" to actorName,
                        "lastEditedByRoleLabel" to cleanRole,
                        "lastEditSummary" to "Dipulihkan oleh $actorName ($cleanRole)".take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    logRef,
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "actorUid" to user.uid,
                        "actorName" to actorName,
                        "actorRoleLabel" to cleanRole,
                        "activityType" to "TRANSACTION_RESTORED",
                        "detail" to "$actorName ($cleanRole) memulihkan $cat sebesar ${com.example.util.CurrencyFormatter.formatRupiah(amt)}".take(250),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resetAllHouseholdData(
        household: Household,
        roleLabel: String
    ): Result<Unit> {
        val path = "households/${household.id}"
        return try {
            val user = requireUser()
            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val cleanRole = roleLabel.trim().ifBlank { "Suami" }.take(30)
            val hhRef = db.collection("households").document(household.id)
            val nowMillis = System.currentTimeMillis()

            val txSnap = hhRef.collection("transactions")
                .whereArrayContains("memberUids", user.uid)
                .get()
                .await()
            val budgetSnap = hhRef.collection("budgets")
                .whereArrayContains("memberUids", user.uid)
                .get()
                .await()
            val savingsTxSnap = hhRef.collection("savingsTransactions")
                .whereArrayContains("memberUids", user.uid)
                .get()
                .await()
            val goalsSnap = hhRef.collection("savingsGoals")
                .whereArrayContains("memberUids", user.uid)
                .get()
                .await()

            val nonZeroGoals = goalsSnap.documents.filter { (it.getLong("currentAmount") ?: 0L) != 0L }
            if (nonZeroGoals.isNotEmpty()) {
                for (chunk in nonZeroGoals.chunked(400)) {
                    val batch = db.batch()
                    chunk.forEach { doc ->
                        batch.update(
                            doc.reference,
                            mapOf(
                                "currentAmount" to 0L,
                                "status" to GoalStatus.ACTIVE.name,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    withRetry { batch.commit().await() }
                }
            }

            val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()
            txSnap.documents.forEach { doc ->
                val isAlreadyReset = doc.getBoolean("deleted") == true && doc.getString("lastEditSummary") == RESET_MARKER
                if (!isAlreadyReset) {
                    ops.add { batch ->
                        batch.update(
                            doc.reference,
                            mapOf(
                                "deleted" to true,
                                "deletedAtMillis" to nowMillis,
                                "deletedByUid" to user.uid,
                                "deletedByName" to actorName,
                                "deletedByRoleLabel" to cleanRole,
                                "lastEditSummary" to RESET_MARKER,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }
            budgetSnap.documents.forEach { doc -> ops.add { batch -> batch.delete(doc.reference) } }
            savingsTxSnap.documents.forEach { doc -> ops.add { batch -> batch.delete(doc.reference) } }
            goalsSnap.documents.forEach { doc -> ops.add { batch -> batch.delete(doc.reference) } }

            ops.add { batch ->
                batch.set(
                    hhRef.collection("activityLogs").document(),
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "actorUid" to user.uid,
                        "actorName" to actorName,
                        "actorRoleLabel" to cleanRole,
                        "activityType" to "SYSTEM_RESET",
                        "detail" to "$actorName ($cleanRole) mereset seluruh data keuangan buku kas".take(250),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            for (chunk in ops.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { applyOp -> applyOp(batch) }
                withRetry { batch.commit().await() }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Budgets ---

    fun observeBudgets(householdId: String, uid: String): Flow<List<MonthlyBudget>> = flow {
        if (householdId.isBlank() || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households/$householdId/budgets"
        emitAll(
            db.collection("households").document(householdId).collection("budgets")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap -> snap.toObjects(MonthlyBudget::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertBudget(
        household: Household,
        monthKey: String,
        category: String,
        limitAmount: Long,
        alertThresholdPercent: Long,
        roleLabel: String
    ): Result<Unit> {
        val docId = "${monthKey}_${category.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
        val path = "households/${household.id}/budgets/$docId"
        return try {
            val user = requireUser()
            require(limitAmount > 0L) { "Nominal anggaran harus lebih dari Rp0." }
            require(alertThresholdPercent in 10L..100L) { "Batas peringatan harus 10%-100%." }
            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val docRef = db.collection("households").document(household.id).collection("budgets").document(docId)
            val existing = docRef.get().await()

            val batch = db.batch()
            if (existing.exists()) {
                batch.update(
                    docRef,
                    mapOf(
                        "limitAmount" to limitAmount,
                        "alertThresholdPercent" to alertThresholdPercent,
                        "updatedByUid" to user.uid,
                        "updatedByName" to actorName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    docRef,
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "monthKey" to monthKey,
                        "category" to category.take(60),
                        "limitAmount" to limitAmount,
                        "alertThresholdPercent" to alertThresholdPercent,
                        "updatedByUid" to user.uid,
                        "updatedByName" to actorName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.set(
                db.collection("households").document(household.id).collection("activityLogs").document(),
                mapOf(
                    "householdId" to household.id,
                    "memberUids" to household.memberUids,
                    "actorUid" to user.uid,
                    "actorName" to actorName,
                    "actorRoleLabel" to roleLabel.ifBlank { "Suami" },
                    "activityType" to "BUDGET_UPDATED",
                    "detail" to "$actorName mengatur anggaran $category bulan $monthKey".take(250),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            withRetry { batch.commit().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBudget(householdId: String, budgetId: String): Result<Unit> {
        val path = "households/$householdId/budgets/$budgetId"
        return try {
            requireUserId()
            db.collection("households").document(householdId)
                .collection("budgets").document(budgetId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Savings Goals & Savings Transactions ---

    fun observeSavingsGoals(householdId: String, uid: String): Flow<List<SavingsGoal>> = flow {
        if (householdId.isBlank() || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households/$householdId/savingsGoals"
        emitAll(
            db.collection("households").document(householdId).collection("savingsGoals")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap -> snap.toObjects(SavingsGoal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSavingsTransactions(householdId: String, uid: String): Flow<List<SavingsTransaction>> = flow {
        if (householdId.isBlank() || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households/$householdId/savingsTransactions"
        emitAll(
            db.collection("households").document(householdId).collection("savingsTransactions")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SavingsTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.transactionDateMillis }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSavingsGoal(
        household: Household,
        name: String,
        category: String,
        targetAmount: Long,
        initialBalance: Long = 0L,
        startDateMillis: Long,
        deadlineMillis: Long,
        roleLabel: String
    ): Result<Unit> {
        val path = "households/${household.id}/savingsGoals"
        return try {
            val user = requireUser()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Nama target tabungan wajib diisi." }
            require(targetAmount > 0L) { "Nominal target harus lebih dari Rp0." }
            require(initialBalance >= 0L) { "Saldo awal tidak boleh negatif." }
            require(deadlineMillis >= startDateMillis) { "Tenggat waktu harus setelah tanggal mulai." }

            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val batch = db.batch()
            val goalRef = db.collection("households").document(household.id).collection("savingsGoals").document()
            val cleanCategory = category.take(60)
            batch.set(
                goalRef,
                mapOf(
                    "householdId" to household.id,
                    "memberUids" to household.memberUids,
                    "name" to cleanName,
                    "category" to cleanCategory,
                    "targetAmount" to targetAmount,
                    "currentAmount" to 0L,
                    "startDateMillis" to startDateMillis,
                    "deadlineMillis" to deadlineMillis,
                    "status" to GoalStatus.ACTIVE.name,
                    "createdByUid" to user.uid,
                    "createdByName" to actorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("households").document(household.id).collection("activityLogs").document(),
                mapOf(
                    "householdId" to household.id,
                    "memberUids" to household.memberUids,
                    "actorUid" to user.uid,
                    "actorName" to actorName,
                    "actorRoleLabel" to roleLabel.ifBlank { "Suami" },
                    "activityType" to "GOAL_CREATED",
                    "detail" to "$actorName membuat target tabungan \"$cleanName\"".take(250),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            withRetry { batch.commit().await() }

            if (initialBalance > 0L) {
                val createdGoal = SavingsGoal(
                    id = goalRef.id,
                    householdId = household.id,
                    memberUids = household.memberUids,
                    name = cleanName,
                    category = cleanCategory,
                    targetAmount = targetAmount,
                    currentAmount = 0L,
                    startDateMillis = startDateMillis,
                    deadlineMillis = deadlineMillis,
                    status = GoalStatus.ACTIVE.name,
                    createdByUid = user.uid,
                    createdByName = actorName
                )
                return recordSavingsMovement(
                    household = household,
                    goal = createdGoal,
                    type = TransactionType.SAVINGS_DEPOSIT,
                    amount = initialBalance,
                    note = "Nominal yang sudah terkumpul sebelumnya",
                    roleLabel = roleLabel,
                    dateMillis = startDateMillis,
                    isOpeningBalance = true
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSavingsGoal(
        householdId: String,
        goal: SavingsGoal,
        newName: String,
        newCategory: String,
        newTargetAmount: Long,
        newDeadlineMillis: Long
    ): Result<Unit> {
        val path = "households/$householdId/savingsGoals/${goal.id}"
        return try {
            requireUserId()
            require(newName.trim().isNotEmpty()) { "Nama target wajib diisi." }
            require(newTargetAmount > 0L) { "Nominal target harus lebih dari Rp0." }
            val newStatus = if (goal.currentAmount >= newTargetAmount) GoalStatus.COMPLETED.name else GoalStatus.ACTIVE.name
            db.collection("households").document(householdId)
                .collection("savingsGoals").document(goal.id)
                .update(
                    mapOf(
                        "name" to newName.trim().take(80),
                        "category" to newCategory.take(60),
                        "targetAmount" to newTargetAmount,
                        "deadlineMillis" to newDeadlineMillis.coerceAtLeast(goal.startDateMillis),
                        "status" to newStatus,
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

    suspend fun deleteSavingsGoal(householdId: String, goalId: String): Result<Unit> {
        val path = "households/$householdId/savingsGoals/$goalId"
        return try {
            requireUserId()
            db.collection("households").document(householdId)
                .collection("savingsGoals").document(goalId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically records a SAVINGS_DEPOSIT or SAVINGS_WITHDRAWAL:
     * 1. Updates SavingsGoal.currentAmount & status.
     * 2. Creates SavingsTransaction record.
     * 3. Creates CashTransaction record with type SAVINGS_DEPOSIT / SAVINGS_WITHDRAWAL
     *    so Cash Balance updates accurately WITHOUT affecting monthly EXPENSE budgets.
     * 4. Writes ActivityLog.
     */
    suspend fun recordSavingsMovement(
        household: Household,
        goal: SavingsGoal,
        type: TransactionType,
        amount: Long,
        note: String?,
        roleLabel: String,
        dateMillis: Long,
        isOpeningBalance: Boolean = false
    ): Result<Unit> {
        require(type == TransactionType.SAVINGS_DEPOSIT || type == TransactionType.SAVINGS_WITHDRAWAL)
        val path = "households/${household.id}/savingsGoals/${goal.id}"
        return try {
            val user = requireUser()
            require(amount > 0L) { "Nominal harus lebih dari Rp0." }
            val actorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Anggota").take(80)
            val cleanRole = roleLabel.ifBlank { "Suami" }.take(30)
            val hhRef = db.collection("households").document(household.id)
            val goalRef = hhRef.collection("savingsGoals").document(goal.id)
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
            val effectiveNote = if (isOpeningBalance) {
                ("[Saldo Awal] " + (cleanNote ?: "Tabungan yang sudah terkumpul sebelumnya")).take(300)
            } else {
                cleanNote?.take(300)
            }

            db.runTransaction { tx ->
                val snap = tx.get(goalRef)
                if (!snap.exists()) throw IllegalStateException("Target tabungan tidak ditemukan.")
                val current = snap.getLong("currentAmount") ?: 0L
                val target = snap.getLong("targetAmount") ?: goal.targetAmount
                val newAmount = if (type == TransactionType.SAVINGS_DEPOSIT) {
                    current + amount
                } else {
                    if (amount > current) throw IllegalArgumentException("Saldo tabungan tidak mencukupi untuk penarikan.")
                    current - amount
                }
                val newStatus = if (newAmount >= target) GoalStatus.COMPLETED.name else GoalStatus.ACTIVE.name

                tx.update(
                    goalRef,
                    mapOf(
                        "currentAmount" to newAmount,
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    hhRef.collection("savingsTransactions").document(),
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "goalId" to goal.id,
                        "goalName" to goal.name.take(80),
                        "type" to type.name,
                        "amount" to amount,
                        "note" to effectiveNote,
                        "recordedByUid" to user.uid,
                        "recordedByName" to actorName,
                        "recordedByRoleLabel" to cleanRole,
                        "transactionDateMillis" to dateMillis,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null } as Map<String, Any>
                )

                tx.set(
                    hhRef.collection("transactions").document(),
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "type" to type.name,
                        "amount" to amount,
                        "category" to goal.category.take(60),
                        "note" to (effectiveNote ?: goal.name).take(300),
                        "recordedByUid" to user.uid,
                        "recordedByName" to actorName,
                        "recordedByRoleLabel" to cleanRole,
                        "savingsGoalId" to goal.id,
                        "savingsGoalName" to goal.name.take(80),
                        "transactionDateMillis" to dateMillis,
                        "clientMutationId" to UUID.randomUUID().toString(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                // Double-entry opening credit so existing savings don't make Cash Balance negative
                if (isOpeningBalance && type == TransactionType.SAVINGS_DEPOSIT) {
                    tx.set(
                        hhRef.collection("transactions").document(),
                        mapOf(
                            "householdId" to household.id,
                            "memberUids" to household.memberUids,
                            "type" to TransactionType.INCOME.name,
                            "amount" to amount,
                            "category" to com.example.model.CoupleCashCategories.OPENING_SAVINGS_CATEGORY,
                            "source" to "Tabungan Sebelumnya",
                            "note" to "Saldo awal ${goal.name} sebelum menggunakan CoupleCash".take(300),
                            "recordedByUid" to user.uid,
                            "recordedByName" to actorName,
                            "recordedByRoleLabel" to cleanRole,
                            "savingsGoalId" to goal.id,
                            "savingsGoalName" to goal.name.take(80),
                            "transactionDateMillis" to dateMillis,
                            "clientMutationId" to UUID.randomUUID().toString(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val formattedAmt = com.example.util.CurrencyFormatter.formatRupiah(amount)
                val actionVerb = when {
                    isOpeningBalance -> "mencatat saldo awal tabungan"
                    type == TransactionType.SAVINGS_DEPOSIT -> "menyetor ke"
                    else -> "menarik dari"
                }
                tx.set(
                    hhRef.collection("activityLogs").document(),
                    mapOf(
                        "householdId" to household.id,
                        "memberUids" to household.memberUids,
                        "actorUid" to user.uid,
                        "actorName" to actorName,
                        "actorRoleLabel" to cleanRole,
                        "activityType" to if (isOpeningBalance) "SAVINGS_OPENING_BALANCE" else type.name,
                        "detail" to "$actorName ($cleanRole) $actionVerb ${goal.name} sebesar $formattedAmt".take(250),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Activity Logs ---

    fun observeActivityLogs(householdId: String, uid: String): Flow<List<ActivityLog>> = flow {
        if (householdId.isBlank() || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households/$householdId/activityLogs"
        emitAll(
            db.collection("households").document(householdId).collection("activityLogs")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    val sorted = snap.toObjects(ActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    com.example.util.FinanceCalculator.filterLogsAfterReset(sorted).take(50)
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }
}
