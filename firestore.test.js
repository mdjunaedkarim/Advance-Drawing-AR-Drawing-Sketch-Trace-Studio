const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_junaed_001";
const ADMIN_EMAIL = "junaedkarim263@gmail.com";
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
    adminNote: "",
    createdAt: ts,
    lastActiveAt: ts,
    updatedAt: ts,
  };
}

function validGlobalConfig(ts) {
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
    updatedBy: ADMIN_EMAIL,
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
  await assertFails(unauthDb.collection("admins").doc(ADMIN_UID).get());
});

test("Authenticated user: valid create, telemetry update, and strict privilege/immutability guards", async () => {
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

  // Owner can read own profile
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Owner cannot list all users (PII protection)
  await assertFails(aliceDb.collection("users").get());

  // Valid telemetry partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeScreen: "CAMERA",
      drawingsCount: 3,
      lastActiveAt: later,
      updatedAt: later,
    })
  );

  // Reject self-escalation of admin-only fields (vipGranted, bonusCoins, blocked)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      vipGranted: true,
      updatedAt: later,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bonusCoins: 9999,
      updatedAt: later,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      blocked: true,
      updatedAt: later,
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: later,
      updatedAt: later,
    })
  );

  // Reject shadow ghost field on create
  await assertFails(
    aliceDb.collection("users").doc("extra_doc").set({
      ...validUserPayload("extra_doc", "Alice", "alice@example.com", created),
      isVerified: true,
    })
  );

  // Reject tampered future timestamp
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
      action: "SESSION_START",
      detail: "Opened app",
      createdAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Alice cannot read or modify Bob's profile or activity logs
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ displayName: "Hacked", updatedAt: ts }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("activity_logs").get());

  // Alice can write & read her own append-only activity log, but cannot update it
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activity_logs").doc("log_a1").set({
      action: "SAVE_DRAWING",
      detail: "Saved AR sketch",
      createdAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("activity_logs").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("activity_logs").doc("log_a1").update({
      detail: "Tampered",
    })
  );
});

test("Email spoofing attack (unverified admin email) is strictly rejected", async () => {
  const ts = new Date(Date.now() - 5000);
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofDb.collection("users").get());
  await assertFails(spoofDb.collection("app_config").doc("global").set(validGlobalConfig(ts)));
});

test("Primary Admin: full user monitoring, moderation, and global remote app config control", async () => {
  const ts = new Date(Date.now() - 10000);
  const later = new Date(Date.now() - 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", "alice@example.com", ts));
    await db.collection("users").doc(ALICE_UID).collection("activity_logs").doc("log_1").set({
      action: "OPEN_LIGHTBOX",
      detail: "Display tracing",
      createdAt: ts,
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  // Admin can list all users & view any user's activity logs
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(adminDb.collection("users").doc(ALICE_UID).collection("activity_logs").get());

  // Admin can block/grant VIP/grant bonus coins on a user
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      blocked: true,
      vipGranted: true,
      bonusCoins: 200,
      adminNote: "VIP tester",
      updatedAt: later,
    })
  );

  // Admin can create and update /app_config/global
  await assertSucceeds(adminDb.collection("app_config").doc("global").set(validGlobalConfig(ts)));
  await assertSucceeds(
    adminDb.collection("app_config").doc("global").update({
      maintenanceMode: true,
      adsEnabled: false,
      updatedBy: ADMIN_EMAIL,
      updatedAt: later,
    })
  );

  // Signed-in regular user can read /app_config/global, cannot list or write it
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(aliceDb.collection("app_config").doc("global").get());
  await assertFails(aliceDb.collection("app_config").get());
  await assertFails(
    aliceDb.collection("app_config").doc("global").update({
      adsEnabled: false,
      updatedAt: later,
    })
  );
});
