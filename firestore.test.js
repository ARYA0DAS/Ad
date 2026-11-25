const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_hmgrup_01";
const BOB_UID = "bob_other_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    companyName: "HM GRUP",
    developerCredit: "All Development by Mc Company",
    tagline: "Quality Commercial Supplies & Services",
    phone: "+91 98765 43210",
    email: "billing@hmgrup.com",
    address: "Industrial Estate, Sector 4",
    taxNumber: "27AABCH1234F1Z5",
    currencySymbol: "₹",
    defaultTaxRate: 18,
    defaultTerms: "Payment due within 15 days. Developed by Mc Company.",
    signatoryName: "HM GRUP Authorized Signatory",
    nextBillNumber: 1,
    nextQuotationNumber: 1,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCustomerPayload(uid, ts) {
  return {
    userId: uid,
    name: "Apex Constructions Pvt Ltd",
    phone: "+91 91234 56789",
    email: "accounts@apex.in",
    address: "Plot 19, Commercial Hub",
    taxNumber: "27AAACA5555B1Z2",
    notes: "Preferred corporate client",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validBillingDocPayload(uid, docType, status, ts) {
  return {
    userId: uid,
    docType,
    docNumber: docType === "BILL" ? "HMG-INV-2026-001" : "HMG-QTN-2026-001",
    status,
    customerId: "cust_1",
    customerName: "Apex Constructions Pvt Ltd",
    customerPhone: "+91 91234 56789",
    customerEmail: "accounts@apex.in",
    customerAddress: "Plot 19, Commercial Hub",
    customerTaxNumber: "27AAACA5555B1Z2",
    issueDate: "2026-11-25",
    dueDate: "2026-12-10",
    currencySymbol: "₹",
    items: [
      {
        description: "Structural Steel Fabrication",
        quantity: 2,
        unit: "lot",
        unitPrice: 15000,
        lineTotal: 30000,
      },
    ],
    subtotal: 30000,
    discountPercent: 5,
    discountAmount: 1500,
    taxRate: 18,
    taxAmount: 5130,
    grandTotal: 33630,
    notes: "Thank you for your business with HM GRUP.",
    developerCredit: "All Development by Mc Company",
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

test("Unauthenticated user cannot read or write profiles, customers, or documents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );
});

test("CompanyProfile: owner create, partial update, counter increment, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).get()
  );

  // Partial update of profile fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      companyName: "HM GRUP ENTERPRISES",
      defaultTaxRate: 12,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Counter increment action
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nextBillNumber: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost/shadow field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp tampering
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      companyName: "HM GRUP",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject listing /users root collection
  await assertFails(aliceDb.collection("users").get());
});

test("Customer CRUD, list query parity, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Owner create (no @DocumentId `id` field in payload)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set(validCustomerPayload(ALICE_UID, created))
  );

  // Reject payload containing `@DocumentId` `id` key
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_bad")
      .set({
        id: "cust_bad",
        ...validCustomerPayload(ALICE_UID, created),
      })
  );

  // Owner list subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );

  // Owner partial update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .update({
        phone: "+91 90000 11111",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Cross-user read/list/write/delete denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .update({ name: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").delete()
  );
});

test("BillingDocument: Bill & Quotation create, status transitions, terminal locking, and validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create BILL
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("bill_1")
      .set(validBillingDocPayload(ALICE_UID, "BILL", "UNPAID", created))
  );

  // Reject invalid status for BILL (e.g., DRAFT belongs to QUOTATION)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("bill_bad_status")
      .set(validBillingDocPayload(ALICE_UID, "BILL", "DRAFT", created))
  );

  // Reject empty items list
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("bill_empty")
      .set({
        ...validBillingDocPayload(ALICE_UID, "BILL", "UNPAID", created),
        items: [],
      })
  );

  // Partial update on UNPAID bill
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("bill_1")
      .update({
        status: "PAID",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Terminal state lock: PAID bill cannot be updated afterwards
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("bill_1")
      .update({
        status: "UNPAID",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Create QUOTATION and convert it to a bill
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("quote_1")
      .set(validBillingDocPayload(ALICE_UID, "QUOTATION", "SENT", created))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("quote_1")
      .update({
        status: "CONVERTED",
        convertedToBillId: "bill_1",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Terminal state lock: CONVERTED quotation cannot be edited
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("quote_1")
      .update({
        notes: "Tampered after conversion",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Owner list documents query
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );

  // Cross-user isolation on documents
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("documents").doc("bill_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );
});
