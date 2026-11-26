const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

// Matches the provisioned named Firestore database in firebase_applet_config.xml
const DATABASE_ID = "ai-studio-android-coupleca-ec268288-eb12-4847-9cf9-9bc41d2f9903";
const db = getFirestore(DATABASE_ID);

const SUBCOLLECTIONS = [
  "members",
  "transactions",
  "budgets",
  "savingsGoals",
  "savingsTransactions",
  "activityLogs",
];

function formatRupiah(amount) {
  return "Rp" + Number(amount || 0).toLocaleString("id-ID");
}

async function syncMemberUidsAcrossSubcollections(householdId, memberUids) {
  for (const sub of SUBCOLLECTIONS) {
    const snap = await db.collection(`households/${householdId}/${sub}`).get();
    if (snap.empty) continue;

    let batch = db.batch();
    let ops = 0;
    for (const doc of snap.docs) {
      batch.update(doc.ref, {
        memberUids,
        ...(sub !== "activityLogs" ? { updatedAt: FieldValue.serverTimestamp() } : {}),
      });
      ops++;
      if (ops >= 400) {
        await batch.commit();
        batch = db.batch();
        ops = 0;
      }
    }
    if (ops > 0) {
      await batch.commit();
    }
  }
}

/**
 * Atomic callable for joining a shared household via invite code.
 */
exports.joinHouseholdByCode = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Pengguna wajib login terlebih dahulu.");
  }

  const uid = request.auth.uid;
  const code = String(request.data?.code || "").trim().toUpperCase();
  const displayName = String(request.data?.displayName || "Pasangan").trim().slice(0, 80);
  const partnerLabel = ["Suami", "Istri", "Pasangan"].includes(request.data?.partnerLabel)
    ? request.data.partnerLabel
    : "Pasangan";
  const photoUrl = request.data?.photoUrl ? String(request.data.photoUrl).slice(0, 600) : null;

  if (!/^[A-Z0-9]{6,16}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Format kode undangan tidak valid.");
  }

  const result = await db.runTransaction(async (tx) => {
    const codeRef = db.collection("inviteCodes").doc(code);
    const codeSnap = await tx.get(codeRef);
    if (!codeSnap.exists) {
      throw new HttpsError("not-found", "Kode undangan tidak ditemukan.");
    }

    const codeData = codeSnap.data();
    if (!codeData.active) {
      throw new HttpsError("failed-precondition", "Kode undangan sudah digunakan atau dibatalkan.");
    }
    if (codeData.expiresAt.toMillis() <= Date.now()) {
      throw new HttpsError("failed-precondition", "Kode undangan sudah kedaluwarsa.");
    }
    if (codeData.ownerId === uid) {
      throw new HttpsError("failed-precondition", "Anda sudah menjadi pemilik buku kas ini.");
    }

    const householdRef = db.collection("households").doc(codeData.householdId);
    const householdSnap = await tx.get(householdRef);
    if (!householdSnap.exists) {
      throw new HttpsError("not-found", "Buku kas tidak ditemukan.");
    }

    const hhData = householdSnap.data();
    const currentMembers = Array.isArray(hhData.memberUids) ? hhData.memberUids : [];
    if (currentMembers.includes(uid)) {
      throw new HttpsError("already-exists", "Anda sudah terdaftar di buku kas ini.");
    }
    if (currentMembers.length >= 2) {
      throw new HttpsError("resource-exhausted", "Buku kas sudah mencapai batas maksimal 2 anggota.");
    }

    const updatedMembers = [...currentMembers, uid];
    const now = FieldValue.serverTimestamp();

    tx.update(householdRef, {
      memberUids: updatedMembers,
      updatedAt: now,
    });

    tx.update(codeRef, {
      active: false,
      usedByUid: uid,
      updatedAt: now,
    });

    const memberPayload = {
      uid,
      householdId: codeData.householdId,
      memberUids: updatedMembers,
      displayName,
      role: "PARTNER",
      partnerLabel,
      createdAt: now,
      updatedAt: now,
    };
    if (photoUrl) memberPayload.photoUrl = photoUrl;

    tx.set(householdRef.collection("members").doc(uid), memberPayload);

    tx.set(householdRef.collection("activityLogs").doc(), {
      householdId: codeData.householdId,
      memberUids: updatedMembers,
      actorUid: uid,
      actorName: displayName,
      actorRoleLabel: partnerLabel,
      activityType: "MEMBER_JOINED",
      detail: `${displayName} bergabung ke Buku Kas Bersama`,
      createdAt: now,
    });

    tx.update(db.collection("users").doc(uid), {
      activeHouseholdId: codeData.householdId,
      updatedAt: now,
    });

    return { householdId: codeData.householdId, memberUids: updatedMembers };
  });

  await syncMemberUidsAcrossSubcollections(result.householdId, result.memberUids);
  return { success: true, householdId: result.householdId };
});

/**
 * Syncs denormalized memberUids whenever household membership changes.
 */
exports.onHouseholdMembersChanged = onDocumentUpdated(
  {
    document: "households/{householdId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    const prev = JSON.stringify(before.memberUids || []);
    const next = JSON.stringify(after.memberUids || []);
    if (prev !== next) {
      await syncMemberUidsAcrossSubcollections(event.params.householdId, after.memberUids || []);
    }
  }
);

/**
 * Sends FCM notifications to the partner when a transaction is recorded,
 * and checks budget threshold warnings for EXPENSE transactions.
 */
exports.onTransactionCreatedNotify = onDocumentCreated(
  {
    document: "households/{householdId}/transactions/{transactionId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const txData = event.data?.data();
    if (!txData) return;

    const householdId = event.params.householdId;
    const memberUids = Array.isArray(txData.memberUids) ? txData.memberUids : [];
    const recipientUids = memberUids.filter((id) => id !== txData.recordedByUid);

    let title = "Transaksi Baru";
    let body = `${txData.recordedByName} mencatat transaksi ${formatRupiah(txData.amount)}`;

    if (txData.type === "INCOME") {
      title = "Pemasukan Baru";
      body = `${txData.recordedByName} mencatat pemasukan ${txData.category}: ${formatRupiah(txData.amount)}`;
    } else if (txData.type === "EXPENSE") {
      title = "Pengeluaran Baru";
      body = `${txData.recordedByName} mencatat pengeluaran ${txData.category}: ${formatRupiah(txData.amount)}`;
    } else if (txData.type === "SAVINGS_DEPOSIT") {
      title = "Setoran Tabungan";
      body = `${txData.recordedByName} menyetor ${formatRupiah(txData.amount)} ke ${txData.savingsGoalName || "Tabungan"}`;
    }

    for (const uid of recipientUids) {
      const userSnap = await db.collection("users").doc(uid).get();
      if (!userSnap.exists) continue;
      const user = userSnap.data();
      if (!user.fcmToken) continue;

      if (user.notifPartnerTx === false) continue;
      if (txData.type === "INCOME" && user.notifIncome === false) continue;
      if (txData.type === "EXPENSE" && user.notifExpense === false) continue;
      if (txData.type === "SAVINGS_DEPOSIT" && user.notifSavings === false) continue;

      await getMessaging().send({
        token: user.fcmToken,
        notification: { title, body },
        data: { householdId, type: txData.type },
      });
    }

    // Check monthly budget warning if EXPENSE
    if (txData.type === "EXPENSE") {
      const txDate = new Date(txData.transactionDateMillis || Date.now());
      const monthKey = `${txDate.getUTCFullYear()}-${String(txDate.getUTCMonth() + 1).padStart(2, "0")}`;
      const budgetsSnap = await db
        .collection(`households/${householdId}/budgets`)
        .where("monthKey", "==", monthKey)
        .get();

      if (budgetsSnap.empty) return;

      const monthStart = Date.UTC(txDate.getUTCFullYear(), txDate.getUTCMonth(), 1);
      const monthEnd = Date.UTC(txDate.getUTCFullYear(), txDate.getUTCMonth() + 1, 1);

      const monthTxSnap = await db
        .collection(`households/${householdId}/transactions`)
        .where("type", "==", "EXPENSE")
        .where("transactionDateMillis", ">=", monthStart)
        .where("transactionDateMillis", "<", monthEnd)
        .get();

      const tagihanSubs = new Set(["Tagihan listrik", "Air", "Internet", "Cicilan"]);
      let totalExpense = 0;
      const byCategory = {};
      monthTxSnap.forEach((doc) => {
        const d = doc.data();
        if (d.deleted === true) return;
        const amt = Number(d.amount || 0);
        totalExpense += amt;
        byCategory[d.category] = (byCategory[d.category] || 0) + amt;
        if (tagihanSubs.has(d.category)) {
          byCategory["Tagihan"] = (byCategory["Tagihan"] || 0) + amt;
        }
      });

      for (const bDoc of budgetsSnap.docs) {
        const b = bDoc.data();
        const spent = b.category === "TOTAL_BULANAN" ? totalExpense : (byCategory[b.category] || 0);
        const pct = Math.round((spent / Math.max(1, b.limitAmount)) * 100);
        if (pct >= (b.alertThresholdPercent || 80)) {
          const warnTitle = pct >= 100 ? "Anggaran Terlampaui!" : "Anggaran Hampir Habis";
          const warnBody = `Anggaran ${b.category} telah terpakai ${pct}% (${formatRupiah(spent)} / ${formatRupiah(b.limitAmount)})`;

          for (const uid of memberUids) {
            const uSnap = await db.collection("users").doc(uid).get();
            const u = uSnap.data();
            if (u?.fcmToken && u.notifBudget !== false) {
              await getMessaging().send({
                token: u.fcmToken,
                notification: { title: warnTitle, body: warnBody },
                data: { householdId, type: "BUDGET_ALERT" },
              });
            }
          }
        }
      }
    }
  }
);

/**
 * Notifies both members when a savings goal reaches 100%.
 */
exports.onSavingsGoalCompleted = onDocumentUpdated(
  {
    document: "households/{householdId}/savingsGoals/{goalId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    if (before.status !== "COMPLETED" && after.status === "COMPLETED") {
      const memberUids = Array.isArray(after.memberUids) ? after.memberUids : [];
      for (const uid of memberUids) {
        const uSnap = await db.collection("users").doc(uid).get();
        const u = uSnap.data();
        if (u?.fcmToken && u.notifGoal !== false) {
          await getMessaging().send({
            token: u.fcmToken,
            notification: {
              title: "Target Tabungan Tercapai!",
              body: `Selamat! Target "${after.name}" sebesar ${formatRupiah(after.targetAmount)} telah tercapai.`,
            },
            data: { householdId: event.params.householdId, type: "GOAL_COMPLETED" },
          });
        }
      }
    }
  }
);
