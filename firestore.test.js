const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALLOWED_ADMINS = [
  "md.junaed.karim@gmail.com",
  "mdjunaidkarim85@gmail.com",
  "mdjunaidkarim26@gmail.com",
  "junaedkarim20@gmail.com",
];
const ALICE_UID = "alice_user_123";
const BOB_UID = "bob_user_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, name, email, ts) {
  return {
    userId: uid,
    displayName: name,
    email: email,
    deviceModel: "Google Pixel 8",
    appVersion: "1.1.0",
    language: "bn",
    activeScreen: "HOME",
    online: true,
    blocked: false,
    vipGranted: false,
    bonusCoins: 0,
    drawingsCount: 0,
    sessionsCount: 1,
    adsWatchedCount: 0,
    purchasesCount: 0,
    totalSpentCents: 0,
    totalUsageSeconds: 0,
    adminNote: "",
    createdAt: ts,
    lastActiveAt: ts,
    updatedAt: ts,
  };
}

function validGlobalConfig(ts, updatedBy) {
  return {
    maintenanceMode: false,
    maintenanceMessage: "Scheduled studio upgrade in progress.",
    announcementEnabled: true,
    announcementTitle: "New Pro Features",
    announcementText: "Enjoy AR Sketch & Display Lightbox 2.0!",
    adsEnabled: true,
    arCameraEnabled: true,
    lightboxEnabled: true,
    bingSearchEnabled: true,
    storeEnabled: true,
    minSupportedVersion: "1.0.0",
    welcomeBonusCoins: 25,
    updatedBy: updatedBy,
    createdAt: ts,
    updatedAt: ts,
  };
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

test("Unauthenticated user: denied on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", "alice@example.com", now))
  );
  await assertFails(unauthDb.collection("app_config").doc("global").get());
});

test("Authenticated user: valid create, ads/purchase/usage telemetry updates, and strict privilege guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const created = new Date(Date.now() - 10000);
  const later = new Date(Date.now() - 1000);

  // Valid create matching Kotlin payload (NO @DocumentId 'id' key)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", "alice@example.com", created))
  );

  // Owner can read own profile, cannot list all users
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Valid telemetry partial update (ads watched, purchases, USD cents spent, usage time)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeScreen: "CAMERA",
      drawingsCount: 3,
      adsWatchedCount: 5,
      purchasesCount: 2,
      totalSpentCents: 1102,
      totalUsageSeconds: 1840,
      lastActiveAt: later,
      updatedAt: later,
    })
  );

  // Reject self-escalation of admin-only fields (vipGranted, bonusCoins, blocked)
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ vipGranted: true, updatedAt: later }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ bonusCoins: 9999, updatedAt: later }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ blocked: true, updatedAt: later }));

  // Reject immutable createdAt modification & future timestamp tampering
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ createdAt: later, updatedAt: later }));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeScreen: "EDITOR",
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

test("Cross-user PII isolation and activity log ownership", async () => {
  const ts = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", "bob@example.com", ts));
    await db.collection("users").doc(BOB_UID).collection("activity_logs").doc("log_1").set({
      action: "PURCHASE",
      detail: "Gold Reserve ($10.01)",
      createdAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ displayName: "Hacked", updatedAt: ts }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("activity_logs").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activity_logs").doc("log_a1").set({
      action: "AD_WATCHED",
      detail: "Rewarded Interstitial",
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("activity_logs").doc("log_a1").update({
      detail: "Tampered",
    })
  );
});

test("Strict 4-Gmail Admin Allowlist: unverified spoofing and non-listed Gmails are strictly rejected", async () => {
  const ts = new Date(Date.now() - 5000);

  // Unverified email spoofing of an allowed admin email -> DENIED
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ALLOWED_ADMINS[0],
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("users").get());
  await assertFails(spoofDb.collection("app_config").doc("global").set(validGlobalConfig(ts, ALLOWED_ADMINS[0])));

  // Former or random verified Gmail NOT in the 4-email allowlist -> DENIED
  const oldEmailDb = testEnv.authenticatedContext("non_admin_uid", {
    email: "junaedkarim263@gmail.com",
    email_verified: true,
  }).firestore();
  await assertFails(oldEmailDb.collection("users").get());
  await assertFails(oldEmailDb.collection("app_config").doc("global").set(validGlobalConfig(ts, "junaedkarim263@gmail.com")));
});

test("All 4 Allowed Admin Gmails: full user analytics monitoring, moderation, and global remote config control", async () => {
  const ts = new Date(Date.now() - 10000);
  const later = new Date(Date.now() - 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", "alice@example.com", ts));
    await db.collection("users").doc(ALICE_UID).collection("activity_logs").doc("log_1").set({
      action: "PURCHASE",
      detail: "Starter Pack ($1.01)",
      createdAt: ts,
    });
  });

  // Verify every single one of the 4 allowed Gmail addresses has full admin privileges
  for (let i = 0; i < ALLOWED_ADMINS.length; i++) {
    const adminEmail = ALLOWED_ADMINS[i];
    const adminDb = testEnv.authenticatedContext(`admin_uid_${i}`, {
      email: adminEmail,
      email_verified: true,
    }).firestore();

    await assertSucceeds(adminDb.collection("users").get());
    await assertSucceeds(adminDb.collection("users").doc(ALICE_UID).collection("activity_logs").get());

    if (i === 0) {
      await assertSucceeds(adminDb.collection("app_config").doc("global").set(validGlobalConfig(ts, adminEmail)));
    } else {
      await assertSucceeds(
        adminDb.collection("app_config").doc("global").update({
          adsEnabled: i % 2 === 0,
          updatedBy: adminEmail,
          updatedAt: later,
        })
      );
    }
  }

  // Primary allowed admin can moderate user
  const primaryAdminDb = testEnv.authenticatedContext("admin_uid_0", {
    email: ALLOWED_ADMINS[0],
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    primaryAdminDb.collection("users").doc(ALICE_UID).update({
      blocked: true,
      vipGranted: true,
      bonusCoins: 200,
      adminNote: "Verified buyer",
      updatedAt: later,
    })
  );
});
