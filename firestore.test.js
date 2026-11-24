const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_supervisor_1";
const BOB_UID = "bob_supervisor_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read or write profiles, workers, or attendance_records", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("workers").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("attendance_records").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "مجهول",
      projectName: "موقع",
      shiftStartHour: 7,
      shiftStartMinute: 30,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("UserProfile: owner can create and update profile; ghost fields and immutable mutations fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "سعد المنصور",
      projectName: "برج النخيل - الرياض",
      shiftStartHour: 7,
      shiftStartMinute: 30,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      projectName: "مجمع الواحة - جدة",
      shiftStartHour: 8,
      shiftStartMinute: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Workers: owner can create, list, partial update, and reject invalid/ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create without optional phoneNumber and without @DocumentId `id`
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workers").doc("worker_1").set({
      userId: ALICE_UID,
      fullName: "أحمد محمود",
      roleTitle: "كهربائي",
      siteName: "برج النخيل",
      badgeCode: "W-101",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection (matches Kotlin repository query)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workers").get()
  );

  // Partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workers").doc("worker_1").update({
      phoneNumber: "0501234567",
      active: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject document containing @DocumentId `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("workers").doc("worker_2").set({
      id: "worker_2",
      userId: ALICE_UID,
      fullName: "خالد السالم",
      roleTitle: "نجار",
      siteName: "برج النخيل",
      badgeCode: "W-102",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("workers").doc("worker_3").set({
      userId: ALICE_UID,
      fullName: "علي الحربي",
      roleTitle: "سباك",
      siteName: "برج النخيل",
      badgeCode: "W-103",
      active: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("AttendanceRecords: requires existing worker, enforces valid enums, supports check-out update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Orphaned record without worker must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .doc("2026-11-24_missing_worker")
      .set({
        userId: ALICE_UID,
        workerId: "missing_worker",
        workerName: "عامل غير موجود",
        roleTitle: "عامل",
        siteName: "برج النخيل",
        dateKey: "2026-11-24",
        status: "PRESENT",
        checkInTimeLabel: "07:25 ص",
        proofMethod: "GPS_VERIFIED",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Create worker first
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workers").doc("w_101").set({
      userId: ALICE_UID,
      fullName: "أحمد محمود",
      roleTitle: "كهربائي",
      siteName: "برج النخيل",
      badgeCode: "W-101",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid attendance check-in record
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .doc("2026-11-24_w_101")
      .set({
        userId: ALICE_UID,
        workerId: "w_101",
        workerName: "أحمد محمود",
        roleTitle: "كهربائي",
        siteName: "برج النخيل",
        dateKey: "2026-11-24",
        status: "PRESENT",
        checkInTimeLabel: "07:25 ص",
        proofMethod: "GPS_VERIFIED",
        locationSummary: "24.7136, 46.6753",
        notes: "تم التحقق عند البوابة",
        checkInTimestamp: created,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Check-out partial update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .doc("2026-11-24_w_101")
      .update({
        checkOutTimeLabel: "04:10 م",
        checkOutTimestamp: new Date(Date.now() - 1000),
        notes: "انصراف بنهاية الوردية",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject invalid status enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .doc("2026-11-24_w_101")
      .update({
        status: "UNKNOWN_STATUS",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject immutable workerId/dateKey tampering
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .doc("2026-11-24_w_101")
      .update({
        dateKey: "2026-11-25",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Query matching Kotlin repository
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .where("dateKey", "==", "2026-11-24")
      .get()
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's workers or attendance records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const created = new Date(Date.now() - 10000);
    await adminDb.collection("users").doc(ALICE_UID).collection("workers").doc("w_1").set({
      userId: ALICE_UID,
      fullName: "أحمد محمود",
      roleTitle: "كهربائي",
      siteName: "برج النخيل",
      badgeCode: "W-101",
      active: true,
      createdAt: created,
      updatedAt: created,
    });
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .doc("2026-11-24_w_1")
      .set({
        userId: ALICE_UID,
        workerId: "w_1",
        workerName: "أحمد محمود",
        roleTitle: "كهربائي",
        siteName: "برج النخيل",
        dateKey: "2026-11-24",
        status: "PRESENT",
        checkInTimeLabel: "07:25 ص",
        proofMethod: "SUPERVISOR_LOG",
        createdAt: created,
        updatedAt: created,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("workers").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("attendance_records").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attendance_records")
      .doc("2026-11-24_w_1")
      .update({ notes: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
});
