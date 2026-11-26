package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType(val label: String) {
    INCOME("Pemasukan"),
    EXPENSE("Pengeluaran"),
    SAVINGS_DEPOSIT("Setoran Tabungan"),
    SAVINGS_WITHDRAWAL("Penarikan Tabungan");

    companion object {
        fun fromRaw(raw: String?): TransactionType =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

enum class GoalStatus(val label: String) {
    ACTIVE("Aktif"),
    COMPLETED("Tercapai");

    companion object {
        fun fromRaw(raw: String?): GoalStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class MemberRole(val label: String) {
    OWNER("Pemilik"),
    PARTNER("Pasangan");

    companion object {
        fun fromRaw(raw: String?): MemberRole =
            entries.firstOrNull { it.name == raw } ?: PARTNER
    }
}

enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE
}

object CoupleCashCategories {
    val incomeCategories = listOf(
        "Gaji suami",
        "Gaji istri",
        "Bonus",
        "Bisnis",
        "Freelance",
        "Investasi",
        "Hadiah",
        "Lainnya"
    )

    val expenseCategories = listOf(
        "Makanan",
        "Belanja bulanan",
        "Transportasi",
        "Tagihan listrik",
        "Air",
        "Internet",
        "Kesehatan",
        "Pendidikan",
        "Cicilan",
        "Hiburan",
        "Kebutuhan rumah",
        "Lainnya"
    )

    val budgetCategories = listOf(
        "TOTAL_BULANAN",
        "Makanan",
        "Transportasi",
        "Belanja bulanan",
        "Tagihan",
        "Kesehatan",
        "Pendidikan",
        "Hiburan",
        "Kebutuhan rumah",
        "Lainnya"
    )

    fun budgetDisplayName(category: String): String =
        if (category == "TOTAL_BULANAN") "Anggaran Total Bulanan" else category

    val savingsCategories = listOf(
        "Dana Darurat",
        "Nikah",
        "Beli Rumah",
        "Kendaraan",
        "Liburan Keluarga",
        "Pendidikan Anak",
        "Tujuan Lainnya"
    )

    val paymentMethods = listOf(
        "Transfer Bank",
        "Tunai",
        "QRIS",
        "E-Wallet",
        "Kartu Debit",
        "Kartu Kredit"
    )

    val partnerRoleLabels = listOf("Suami", "Istri", "Pasangan")

    const val OPENING_SAVINGS_CATEGORY = "Saldo Awal Tabungan"
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val activeHouseholdId: String? = null,
    val partnerRoleLabel: String = "Suami",
    val fcmToken: String? = null,
    val notifPartnerTx: Boolean = true,
    val notifIncome: Boolean = true,
    val notifExpense: Boolean = true,
    val notifSavings: Boolean = true,
    val notifBudget: Boolean = true,
    val notifGoal: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Household(
    @DocumentId val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val memberUids: List<String> = emptyList(),
    val activeInviteCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HouseholdMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = MemberRole.OWNER.name,
    val partnerLabel: String = "Suami",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: MemberRole get() = MemberRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class InviteCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val householdId: String = "",
    val householdName: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val active: Boolean = true,
    val expiresAt: Timestamp? = null,
    val usedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CashTransaction(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = TransactionType.EXPENSE.name,
    val amount: Long = 0L,
    val category: String = "Lainnya",
    val source: String? = null,
    val paymentMethod: String? = null,
    val note: String? = null,
    val receiptUrl: String? = null,
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val recordedByRoleLabel: String = "Suami",
    val savingsGoalId: String? = null,
    val savingsGoalName: String? = null,
    val transactionDateMillis: Long = 0L,
    val clientMutationId: String = "",
    val deleted: Boolean = false,
    val deletedAtMillis: Long? = null,
    val deletedByUid: String? = null,
    val deletedByName: String? = null,
    val deletedByRoleLabel: String? = null,
    val editCount: Long = 0L,
    val lastEditedByUid: String? = null,
    val lastEditedByName: String? = null,
    val lastEditedByRoleLabel: String? = null,
    val lastEditSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: TransactionType get() = TransactionType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class MonthlyBudget(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val monthKey: String = "",
    val category: String = "TOTAL_BULANAN",
    val limitAmount: Long = 0L,
    val alertThresholdPercent: Long = 80L,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavingsGoal(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val category: String = "Dana darurat",
    val targetAmount: Long = 0L,
    val currentAmount: Long = 0L,
    val startDateMillis: Long = 0L,
    val deadlineMillis: Long = 0L,
    val status: String = GoalStatus.ACTIVE.name,
    val createdByUid: String = "",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: GoalStatus get() = GoalStatus.fromRaw(status)
    val progressFraction: Float
        get() = if (targetAmount <= 0L) 0f else (currentAmount.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f)
    val progressPercent: Int
        get() = if (targetAmount <= 0L) 0 else ((currentAmount * 100L) / targetAmount).toInt().coerceIn(0, 100)
}

@Keep
@IgnoreExtraProperties
data class SavingsTransaction(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val goalId: String = "",
    val goalName: String = "",
    val type: String = TransactionType.SAVINGS_DEPOSIT.name,
    val amount: Long = 0L,
    val note: String? = null,
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val recordedByRoleLabel: String = "Suami",
    val transactionDateMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: TransactionType get() = TransactionType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val actorUid: String = "",
    val actorName: String = "",
    val actorRoleLabel: String = "",
    val activityType: String = "",
    val detail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class InviteCodePreview(
    val code: String,
    val householdId: String,
    val householdName: String,
    val ownerName: String,
    val expiresAtMillis: Long
)
