const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HUSBAND_UID = "husband_uid_101";
const WIFE_UID = "wife_uid_202";
const INTRUDER_UID = "intruder_uid_303";
const HOUSEHOLD_ID = "hh_wijaya_001";
const INVITE_CODE = "CC89XYZ1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function futureDate(msOffset = 3600000) {
  return new Date(Date.now() + msOffset);
}

async function seedPairedHousehold() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const past = pastDate(30000);
    await adminDb.collection("households").doc(HOUSEHOLD_ID).set({
      name: "Buku Kas Wijaya",
      ownerId: HUSBAND_UID,
      ownerName: "Budi Wijaya",
      memberUids: [HUSBAND_UID, WIFE_UID],
      createdAt: past,
      updatedAt: past,
    });
    await adminDb
      .collection("households")
      .doc(HOUSEHOLD_ID)
      .collection("members")
      .doc(HUSBAND_UID)
      .set({
        uid: HUSBAND_UID,
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        displayName: "Budi Wijaya",
        role: "OWNER",
        partnerLabel: "Suami",
        createdAt: past,
        updatedAt: past,
      });
    await adminDb
      .collection("households")
      .doc(HOUSEHOLD_ID)
      .collection("members")
      .doc(WIFE_UID)
      .set({
        uid: WIFE_UID,
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        displayName: "Sari Wijaya",
        role: "PARTNER",
        partnerLabel: "Istri",
        createdAt: past,
        updatedAt: past,
      });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Users collection: strict PII isolation (owner only, no cross-user read or ghost fields)", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const husbandDb = testEnv.authenticatedContext(HUSBAND_UID).firestore();
  const wifeDb = testEnv.authenticatedContext(WIFE_UID).firestore();

  const now = pastDate();
  await assertFails(unauthDb.collection("users").doc(HUSBAND_UID).get());

  await assertSucceeds(
    husbandDb.collection("users").doc(HUSBAND_UID).set({
      uid: HUSBAND_UID,
      displayName: "Budi Wijaya",
      email: "budi@example.com",
      partnerRoleLabel: "Suami",
      notifPartnerTx: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    husbandDb.collection("users").doc(HUSBAND_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  await assertFails(wifeDb.collection("users").doc(HUSBAND_UID).get());
  await assertFails(wifeDb.collection("users").where("uid", "==", HUSBAND_UID).get());

  await assertSucceeds(husbandDb.collection("users").doc(HUSBAND_UID).get());
});

test("TEST 7 & TEST 8: Atomic invite pairing, 3rd user rejected on redeemed code, and unregistered user rejected by rules", async () => {
  const husbandDb = testEnv.authenticatedContext(HUSBAND_UID).firestore();
  const wifeDb = testEnv.authenticatedContext(WIFE_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();
  const created = pastDate(10000);

  // 1. Husband creates household + owner member
  const createBatch = husbandDb.batch();
  createBatch.set(husbandDb.collection("households").doc(HOUSEHOLD_ID), {
    name: "Buku Kas Wijaya",
    ownerId: HUSBAND_UID,
    ownerName: "Budi Wijaya",
    memberUids: [HUSBAND_UID],
    createdAt: created,
    updatedAt: created,
  });
  createBatch.set(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("members").doc(HUSBAND_UID),
    {
      uid: HUSBAND_UID,
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID],
      displayName: "Budi Wijaya",
      role: "OWNER",
      partnerLabel: "Suami",
      createdAt: created,
      updatedAt: created,
    }
  );
  await assertSucceeds(createBatch.commit());

  // 2. Husband generates invite code
  const codeBatch = husbandDb.batch();
  codeBatch.set(husbandDb.collection("inviteCodes").doc(INVITE_CODE), {
    code: INVITE_CODE,
    householdId: HOUSEHOLD_ID,
    householdName: "Buku Kas Wijaya",
    ownerId: HUSBAND_UID,
    ownerName: "Budi Wijaya",
    active: true,
    expiresAt: futureDate(86400000),
    createdAt: pastDate(5000),
    updatedAt: pastDate(5000),
  });
  codeBatch.update(husbandDb.collection("households").doc(HOUSEHOLD_ID), {
    activeInviteCode: INVITE_CODE,
    updatedAt: pastDate(5000),
  });
  await assertSucceeds(codeBatch.commit());

  // 3. Wife joins atomically
  const joinBatch = wifeDb.batch();
  joinBatch.update(wifeDb.collection("households").doc(HOUSEHOLD_ID), {
    memberUids: [HUSBAND_UID, WIFE_UID],
    updatedAt: pastDate(1000),
  });
  joinBatch.update(wifeDb.collection("inviteCodes").doc(INVITE_CODE), {
    active: false,
    usedByUid: WIFE_UID,
    updatedAt: pastDate(1000),
  });
  joinBatch.set(
    wifeDb.collection("households").doc(HOUSEHOLD_ID).collection("members").doc(WIFE_UID),
    {
      uid: WIFE_UID,
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      displayName: "Sari Wijaya",
      role: "PARTNER",
      partnerLabel: "Istri",
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    }
  );
  await assertSucceeds(joinBatch.commit());

  // TEST 7: Third user attempts to join using already-redeemed invite code -> REJECTED
  const thirdBatch = intruderDb.batch();
  thirdBatch.update(intruderDb.collection("households").doc(HOUSEHOLD_ID), {
    memberUids: [HUSBAND_UID, WIFE_UID, INTRUDER_UID],
    updatedAt: pastDate(500),
  });
  thirdBatch.update(intruderDb.collection("inviteCodes").doc(INVITE_CODE), {
    active: false,
    usedByUid: INTRUDER_UID,
    updatedAt: pastDate(500),
  });
  thirdBatch.set(
    intruderDb.collection("households").doc(HOUSEHOLD_ID).collection("members").doc(INTRUDER_UID),
    {
      uid: INTRUDER_UID,
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID, INTRUDER_UID],
      displayName: "Orang Ketiga",
      role: "PARTNER",
      partnerLabel: "Pasangan",
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    }
  );
  await assertFails(thirdBatch.commit());

  // TEST 8: Unregistered user attempts to read Household ID -> REJECTED
  await assertFails(intruderDb.collection("households").doc(HOUSEHOLD_ID).get());
  await assertFails(
    intruderDb.collection("households").where("memberUids", "array-contains", HUSBAND_UID).get()
  );
});

test("TEST 1, 2, 5: Real-time shared transparency between Husband and Wife + immutable audit & soft-delete", async () => {
  await seedPairedHousehold();
  const husbandDb = testEnv.authenticatedContext(HUSBAND_UID).firestore();
  const wifeDb = testEnv.authenticatedContext(WIFE_UID).firestore();
  const now = pastDate(1000);

  // Set up real-time listener on Wife's device for transactions and budgets
  let wifeObservedIncome = null;
  let wifeObservedBudget = null;
  const unsubTx = wifeDb
    .collection("households")
    .doc(HOUSEHOLD_ID)
    .collection("transactions")
    .where("memberUids", "array-contains", WIFE_UID)
    .onSnapshot((snap) => {
      snap.forEach((doc) => {
        if (doc.id === "tx_income_5m") wifeObservedIncome = doc.data();
      });
    });

  const unsubBudget = wifeDb
    .collection("households")
    .doc(HOUSEHOLD_ID)
    .collection("budgets")
    .where("memberUids", "array-contains", WIFE_UID)
    .onSnapshot((snap) => {
      snap.forEach((doc) => {
        if (doc.id === "2026-11_Makanan") wifeObservedBudget = doc.data();
      });
    });

  // TEST 1: Husband adds Income Rp5.000.000
  await assertSucceeds(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("tx_income_5m").set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      type: "INCOME",
      amount: 5000000,
      category: "Gaji suami",
      recordedByUid: HUSBAND_UID,
      recordedByName: "Budi Wijaya",
      recordedByRoleLabel: "Suami",
      transactionDateMillis: Date.now() - 1000,
      clientMutationId: "mut_inc_5m",
      createdAt: now,
      updatedAt: now,
    })
  );

  // TEST 2: Wife adds Expense Rp500.000 -> Husband reads it
  await assertSucceeds(
    wifeDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("tx_exp_500k").set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      type: "EXPENSE",
      amount: 500000,
      category: "Makanan",
      recordedByUid: WIFE_UID,
      recordedByName: "Sari Wijaya",
      recordedByRoleLabel: "Istri",
      transactionDateMillis: Date.now() - 900,
      clientMutationId: "mut_exp_500k",
      createdAt: now,
      updatedAt: now,
    })
  );

  // TEST 5: Husband updates monthly budget -> Wife receives real-time update
  await assertSucceeds(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("budgets").doc("2026-11_Makanan").set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      monthKey: "2026-11",
      category: "Makanan",
      limitAmount: 2500000,
      alertThresholdPercent: 80,
      updatedByUid: HUSBAND_UID,
      updatedByName: "Budi Wijaya",
      createdAt: now,
      updatedAt: now,
    })
  );

  await new Promise((r) => setTimeout(r, 300));
  unsubTx();
  unsubBudget();

  assert.equal(wifeObservedIncome?.amount, 5000000);
  assert.equal(wifeObservedBudget?.limitAmount, 2500000);

  // Husband sees Wife's Rp500.000 expense
  const husbandTxSnap = await assertSucceeds(
    husbandDb
      .collection("households")
      .doc(HOUSEHOLD_ID)
      .collection("transactions")
      .where("memberUids", "array-contains", HUSBAND_UID)
      .get()
  );
  const expDoc = husbandTxSnap.docs.find((d) => d.id === "tx_exp_500k");
  assert.equal(expDoc.data().amount, 500000);

  // Hard delete on transactions MUST be rejected; soft-delete + audit log succeeds
  await assertFails(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("tx_exp_500k").delete()
  );
  await assertSucceeds(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("tx_exp_500k").update({
      deleted: true,
      deletedAtMillis: Date.now(),
      deletedByUid: HUSBAND_UID,
      deletedByName: "Budi Wijaya",
      deletedByRoleLabel: "Suami",
      updatedAt: pastDate(200),
    })
  );

  // Audit log created and immutable against modification/deletion
  await assertSucceeds(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("activityLogs").doc("log_1").set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      actorUid: HUSBAND_UID,
      actorName: "Budi Wijaya",
      actorRoleLabel: "Suami",
      activityType: "TRANSACTION_DELETED",
      detail: "Budi Wijaya (Suami) menghapus Makanan sebesar Rp500.000",
      createdAt: now,
    })
  );
  await assertFails(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("activityLogs").doc("log_1").update({
      detail: "Tampered log",
    })
  );
  await assertFails(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("activityLogs").doc("log_1").delete()
  );
});

test("TEST 3, 4, 6, 9: Atomic Shared Savings deposits & withdrawals, goal balance protection, and concurrent updates", async () => {
  await seedPairedHousehold();
  const husbandDb = testEnv.authenticatedContext(HUSBAND_UID).firestore();
  const wifeDb = testEnv.authenticatedContext(WIFE_UID).firestore();
  const now = pastDate(1000);

  // Goal MUST start with currentAmount == 0
  await assertFails(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsGoals").doc("goal_bad").set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      name: "Dana Darurat",
      category: "Dana Darurat",
      targetAmount: 10000000,
      currentAmount: 5000000, // cannot arbitrarily pre-inflate goal balance!
      startDateMillis: Date.now() - 86400000,
      deadlineMillis: Date.now() + 86400000 * 180,
      status: "ACTIVE",
      createdByUid: HUSBAND_UID,
      createdByName: "Budi Wijaya",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsGoals").doc("goal_1").set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      name: "Dana Darurat Keluarga",
      category: "Dana Darurat",
      targetAmount: 10000000,
      currentAmount: 0,
      startDateMillis: Date.now() - 86400000,
      deadlineMillis: Date.now() + 86400000 * 180,
      status: "ACTIVE",
      createdByUid: HUSBAND_UID,
      createdByName: "Budi Wijaya",
      createdAt: now,
      updatedAt: now,
    })
  );

  // TEST 3: Husband deposits Rp1.000.000 atomically via Firestore Transaction
  await assertSucceeds(
    husbandDb.runTransaction(async (tx) => {
      const goalRef = husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsGoals").doc("goal_1");
      const snap = await tx.get(goalRef);
      const nextAmt = snap.data().currentAmount + 1000000;
      tx.update(goalRef, { currentAmount: nextAmt, status: "ACTIVE", updatedAt: pastDate(500) });
      tx.set(husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsTransactions").doc("stx_dep_1"), {
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        goalId: "goal_1",
        goalName: "Dana Darurat Keluarga",
        type: "SAVINGS_DEPOSIT",
        amount: 1000000,
        recordedByUid: HUSBAND_UID,
        recordedByName: "Budi Wijaya",
        recordedByRoleLabel: "Suami",
        transactionDateMillis: Date.now() - 500,
        createdAt: pastDate(500),
        updatedAt: pastDate(500),
      });
      tx.set(husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("tx_sav_dep_1"), {
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        type: "SAVINGS_DEPOSIT",
        amount: 1000000,
        category: "Dana Darurat",
        savingsGoalId: "goal_1",
        savingsGoalName: "Dana Darurat Keluarga",
        recordedByUid: HUSBAND_UID,
        recordedByName: "Budi Wijaya",
        recordedByRoleLabel: "Suami",
        transactionDateMillis: Date.now() - 500,
        clientMutationId: "mut_dep_1",
        createdAt: pastDate(500),
        updatedAt: pastDate(500),
      });
    })
  );

  // TEST 4: Wife withdraws Rp300.000 atomically -> Goal becomes Rp700.000
  await assertSucceeds(
    wifeDb.runTransaction(async (tx) => {
      const goalRef = wifeDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsGoals").doc("goal_1");
      const snap = await tx.get(goalRef);
      const nextAmt = snap.data().currentAmount - 300000;
      assert.equal(nextAmt, 700000);
      tx.update(goalRef, { currentAmount: nextAmt, status: "ACTIVE", updatedAt: pastDate(300) });
      tx.set(wifeDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsTransactions").doc("stx_wd_1"), {
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        goalId: "goal_1",
        goalName: "Dana Darurat Keluarga",
        type: "SAVINGS_WITHDRAWAL",
        amount: 300000,
        recordedByUid: WIFE_UID,
        recordedByName: "Sari Wijaya",
        recordedByRoleLabel: "Istri",
        transactionDateMillis: Date.now() - 300,
        createdAt: pastDate(300),
        updatedAt: pastDate(300),
      });
    })
  );

  // TEST 9: Concurrent writes from Husband and Wife on transactions + goal balance stay consistent
  await assertSucceeds(
    Promise.all([
      husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("concurrent_h").set({
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        type: "EXPENSE",
        amount: 150000,
        category: "Transportasi",
        recordedByUid: HUSBAND_UID,
        recordedByName: "Budi Wijaya",
        recordedByRoleLabel: "Suami",
        transactionDateMillis: Date.now() - 200,
        clientMutationId: "mut_conc_h",
        createdAt: pastDate(200),
        updatedAt: pastDate(200),
      }),
      wifeDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("concurrent_w").set({
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        type: "EXPENSE",
        amount: 250000,
        category: "Makanan",
        recordedByUid: WIFE_UID,
        recordedByName: "Sari Wijaya",
        recordedByRoleLabel: "Istri",
        transactionDateMillis: Date.now() - 100,
        clientMutationId: "mut_conc_w",
        createdAt: pastDate(100),
        updatedAt: pastDate(100),
      }),
    ])
  );

  const finalTxSnap = await husbandDb
    .collection("households")
    .doc(HOUSEHOLD_ID)
    .collection("transactions")
    .where("memberUids", "array-contains", HUSBAND_UID)
    .get();
  assert.equal(finalTxSnap.size, 3); // tx_sav_dep_1 + concurrent_h + concurrent_w

  const goalSnap = await husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsGoals").doc("goal_1").get();
  assert.equal(goalSnap.data().currentAmount, 700000);
});

test("Restore soft-deleted transaction and reset all household data obey firestore.rules", async () => {
  await seedPairedHousehold();
  const husbandDb = testEnv.authenticatedContext(HUSBAND_UID).firestore();
  const wifeDb = testEnv.authenticatedContext(WIFE_UID).firestore();
  const now = pastDate(2000);

  // Create expense & soft-delete it
  const txRef = husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("tx_restore_1");
  await assertSucceeds(
    txRef.set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      type: "EXPENSE",
      amount: 250000,
      category: "Makanan",
      recordedByUid: HUSBAND_UID,
      recordedByName: "Budi Wijaya",
      recordedByRoleLabel: "Suami",
      transactionDateMillis: Date.now() - 2000,
      clientMutationId: "mut_restore_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    txRef.update({
      deleted: true,
      deletedAtMillis: Date.now() - 1000,
      deletedByUid: HUSBAND_UID,
      deletedByName: "Budi Wijaya",
      deletedByRoleLabel: "Suami",
      updatedAt: pastDate(1000),
    })
  );

  // Wife restores soft-deleted transaction atomically with audit log
  const wifeTxRef = wifeDb.collection("households").doc(HOUSEHOLD_ID).collection("transactions").doc("tx_restore_1");
  await assertSucceeds(
    wifeDb.runTransaction(async (tx) => {
      const snap = await tx.get(wifeTxRef);
      assert.equal(snap.data().deleted, true);
      tx.update(wifeTxRef, {
        deleted: false,
        deletedAtMillis: null,
        deletedByUid: null,
        deletedByName: null,
        deletedByRoleLabel: null,
        editCount: 1,
        lastEditedByUid: WIFE_UID,
        lastEditedByName: "Sari Wijaya",
        lastEditedByRoleLabel: "Istri",
        lastEditSummary: "Dipulihkan oleh Sari Wijaya (Istri)",
        updatedAt: pastDate(500),
      });
      tx.set(wifeDb.collection("households").doc(HOUSEHOLD_ID).collection("activityLogs").doc("log_restore_1"), {
        householdId: HOUSEHOLD_ID,
        memberUids: [HUSBAND_UID, WIFE_UID],
        actorUid: WIFE_UID,
        actorName: "Sari Wijaya",
        actorRoleLabel: "Istri",
        activityType: "TRANSACTION_RESTORED",
        detail: "Sari Wijaya (Istri) memulihkan Makanan sebesar Rp250.000",
        createdAt: pastDate(500),
      });
    })
  );

  const restoredSnap = await txRef.get();
  assert.equal(restoredSnap.data().deleted, false);

  // Create non-zero goal, budget, and reset all household data in 2 passes
  const goalRef = husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("savingsGoals").doc("goal_reset_1");
  await assertSucceeds(
    goalRef.set({
      householdId: HOUSEHOLD_ID,
      memberUids: [HUSBAND_UID, WIFE_UID],
      name: "Liburan",
      category: "Liburan Keluarga",
      targetAmount: 5000000,
      currentAmount: 0,
      startDateMillis: Date.now() - 86400000,
      deadlineMillis: Date.now() + 86400000 * 90,
      status: "ACTIVE",
      createdByUid: HUSBAND_UID,
      createdByName: "Budi Wijaya",
      createdAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );
  await assertSucceeds(goalRef.update({ currentAmount: 1000000, status: "ACTIVE", updatedAt: pastDate(1000) }));

  // Pass 1: Zero goal
  await assertSucceeds(goalRef.update({ currentAmount: 0, status: "ACTIVE", updatedAt: pastDate(400) }));

  // Pass 2: Batch delete goal + soft-delete transaction with RESET_KESELURUHAN + log SYSTEM_RESET
  const resetBatch = husbandDb.batch();
  resetBatch.delete(goalRef);
  resetBatch.update(txRef, {
    deleted: true,
    deletedAtMillis: Date.now(),
    deletedByUid: HUSBAND_UID,
    deletedByName: "Budi Wijaya",
    deletedByRoleLabel: "Suami",
    lastEditSummary: "RESET_KESELURUHAN",
    updatedAt: pastDate(200),
  });
  resetBatch.set(husbandDb.collection("households").doc(HOUSEHOLD_ID).collection("activityLogs").doc("log_reset_1"), {
    householdId: HOUSEHOLD_ID,
    memberUids: [HUSBAND_UID, WIFE_UID],
    actorUid: HUSBAND_UID,
    actorName: "Budi Wijaya",
    actorRoleLabel: "Suami",
    activityType: "SYSTEM_RESET",
    detail: "Budi Wijaya (Suami) mereset seluruh data keuangan buku kas",
    createdAt: pastDate(200),
  });
  await assertSucceeds(resetBatch.commit());
});
