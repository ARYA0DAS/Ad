package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BillingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)
    private fun customersCollection(uid: String) = userDocRef(uid).collection("customers")
    private fun documentsCollection(uid: String) = userDocRef(uid).collection("documents")

    fun observeCompanyProfile(userId: String): Flow<CompanyProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            userDocRef(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CompanyProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeCustomers(userId: String): Flow<List<Customer>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/customers"
        emitAll(
            customersCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Customer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDocuments(userId: String): Flow<List<BillingDocument>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/documents"
        emitAll(
            documentsCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BillingDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureCompanyProfileExists(defaultEmail: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = userDocRef(uid)
            docPath = ref.path
            val snap = ref.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "companyName" to DEFAULT_COMPANY_NAME,
                    "developerCredit" to DEFAULT_DEVELOPER_CREDIT,
                    "tagline" to "Commercial Billing & Quotations",
                    "phone" to "",
                    "email" to defaultEmail.trim().take(120),
                    "address" to "",
                    "taxNumber" to "",
                    "currencySymbol" to DEFAULT_CURRENCY,
                    "defaultTaxRate" to 0.0,
                    "defaultTerms" to "Thank you for your business with HM GRUP. Payment is due within 15 days.",
                    "signatoryName" to "Authorized Signatory",
                    "nextBillNumber" to 1L,
                    "nextQuotationNumber" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCompanyProfile(profile: CompanyProfile): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanCompanyName = profile.companyName.trim().take(100)
            require(cleanCompanyName.isNotEmpty()) { "Company name cannot be empty." }
            val cleanCredit = profile.developerCredit.trim().ifEmpty { DEFAULT_DEVELOPER_CREDIT }.take(120)
            val cleanCurrency = profile.currencySymbol.trim().ifEmpty { DEFAULT_CURRENCY }.take(8)
            val cleanTax = profile.defaultTaxRate.coerceIn(0.0, 100.0)

            val ref = userDocRef(uid)
            docPath = ref.path
            val exists = ref.get().await().exists()
            if (exists) {
                val updates = mapOf(
                    "companyName" to cleanCompanyName,
                    "developerCredit" to cleanCredit,
                    "tagline" to profile.tagline.trim().take(120),
                    "phone" to profile.phone.trim().take(40),
                    "email" to profile.email.trim().take(120),
                    "address" to profile.address.trim().take(300),
                    "taxNumber" to profile.taxNumber.trim().take(60),
                    "currencySymbol" to cleanCurrency,
                    "defaultTaxRate" to cleanTax,
                    "defaultTerms" to profile.defaultTerms.trim().take(500),
                    "signatoryName" to profile.signatoryName.trim().take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.update(updates).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "companyName" to cleanCompanyName,
                    "developerCredit" to cleanCredit,
                    "tagline" to profile.tagline.trim().take(120),
                    "phone" to profile.phone.trim().take(40),
                    "email" to profile.email.trim().take(120),
                    "address" to profile.address.trim().take(300),
                    "taxNumber" to profile.taxNumber.trim().take(60),
                    "currencySymbol" to cleanCurrency,
                    "defaultTaxRate" to cleanTax,
                    "defaultTerms" to profile.defaultTerms.trim().take(500),
                    "signatoryName" to profile.signatoryName.trim().take(100),
                    "nextBillNumber" to profile.nextBillNumber.coerceIn(1L, 9_999_999L),
                    "nextQuotationNumber" to profile.nextQuotationNumber.coerceIn(1L, 9_999_999L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCustomer(customer: Customer): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/customers"
        return try {
            val uid = requireUserId()
            val cleanName = customer.name.trim().take(120)
            require(cleanName.isNotEmpty()) { "Customer name is required." }

            if (customer.id.isBlank()) {
                val docRef = customersCollection(uid).document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "phone" to customer.phone.trim().take(40),
                    "email" to customer.email.trim().take(120),
                    "address" to customer.address.trim().take(300),
                    "taxNumber" to customer.taxNumber.trim().take(60),
                    "notes" to customer.notes.trim().take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = customersCollection(uid).document(customer.id)
                docPath = docRef.path
                val updates = mapOf(
                    "name" to cleanName,
                    "phone" to customer.phone.trim().take(40),
                    "email" to customer.email.trim().take(120),
                    "address" to customer.address.trim().take(300),
                    "taxNumber" to customer.taxNumber.trim().take(60),
                    "notes" to customer.notes.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                Result.success(docRef.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/customers/$customerId"
        return try {
            val uid = requireUserId()
            require(customerId.isNotBlank()) { "Customer ID cannot be blank." }
            val docRef = customersCollection(uid).document(customerId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveDocument(
        document: BillingDocument,
        incrementSequenceCounter: Boolean
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents"
        return try {
            val uid = requireUserId()
            val cleanCustomerName = document.customerName.trim().take(120)
            require(cleanCustomerName.isNotEmpty()) { "Customer name is required." }
            val cleanDocNumber = document.docNumber.trim().take(50)
            require(cleanDocNumber.isNotEmpty()) { "Document number is required." }
            val cleanItems = document.items
                .filter { it.description.isNotBlank() }
                .take(50)
            require(cleanItems.isNotEmpty()) { "Add at least one line item with a description." }

            val docType = document.parsedType
            val validStatus = DocumentStatus.fromCode(document.status, docType).let { parsed ->
                if (parsed in DocumentStatus.validFor(docType)) parsed
                else if (docType == DocumentType.BILL) DocumentStatus.UNPAID else DocumentStatus.DRAFT
            }
            val totals = calculateTotals(cleanItems, document.discountPercent, document.taxRate)
            val cleanCurrency = document.currencySymbol.trim().ifEmpty { DEFAULT_CURRENCY }.take(8)
            val cleanCredit = document.developerCredit.trim().ifEmpty { DEFAULT_DEVELOPER_CREDIT }.take(120)

            val itemMaps = cleanItems.map { it.toFirestoreMap() }

            if (document.id.isBlank()) {
                val docRef = documentsCollection(uid).document()
                docPath = docRef.path
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "docType" to docType.code,
                    "docNumber" to cleanDocNumber,
                    "status" to validStatus.code,
                    "customerId" to document.customerId.trim().take(128).takeIf { it.isNotEmpty() },
                    "customerName" to cleanCustomerName,
                    "customerPhone" to document.customerPhone.trim().take(40),
                    "customerEmail" to document.customerEmail.trim().take(120),
                    "customerAddress" to document.customerAddress.trim().take(300),
                    "customerTaxNumber" to document.customerTaxNumber.trim().take(60),
                    "issueDate" to document.issueDate.trim().ifEmpty { todayDateString() }.take(30),
                    "dueDate" to document.dueDate.trim().take(30),
                    "currencySymbol" to cleanCurrency,
                    "items" to itemMaps,
                    "subtotal" to totals.subtotal,
                    "discountPercent" to document.discountPercent.coerceIn(0.0, 100.0),
                    "discountAmount" to totals.discountAmount,
                    "taxRate" to document.taxRate.coerceIn(0.0, 100.0),
                    "taxAmount" to totals.taxAmount,
                    "grandTotal" to totals.grandTotal,
                    "notes" to document.notes.trim().take(1000),
                    "developerCredit" to cleanCredit,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>

                docRef.set(payload).await()

                if (incrementSequenceCounter) {
                    val counterField = if (docType == DocumentType.BILL) {
                        "nextBillNumber"
                    } else {
                        "nextQuotationNumber"
                    }
                    runCatching {
                        userDocRef(uid).update(
                            mapOf(
                                counterField to FieldValue.increment(1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                Result.success(docRef.id)
            } else {
                val docRef = documentsCollection(uid).document(document.id)
                docPath = docRef.path
                val updates = mapOf<String, Any?>(
                    "docNumber" to cleanDocNumber,
                    "status" to validStatus.code,
                    "customerId" to document.customerId.trim().take(128),
                    "customerName" to cleanCustomerName,
                    "customerPhone" to document.customerPhone.trim().take(40),
                    "customerEmail" to document.customerEmail.trim().take(120),
                    "customerAddress" to document.customerAddress.trim().take(300),
                    "customerTaxNumber" to document.customerTaxNumber.trim().take(60),
                    "issueDate" to document.issueDate.trim().ifEmpty { todayDateString() }.take(30),
                    "dueDate" to document.dueDate.trim().take(30),
                    "currencySymbol" to cleanCurrency,
                    "items" to itemMaps,
                    "subtotal" to totals.subtotal,
                    "discountPercent" to document.discountPercent.coerceIn(0.0, 100.0),
                    "discountAmount" to totals.discountAmount,
                    "taxRate" to document.taxRate.coerceIn(0.0, 100.0),
                    "taxAmount" to totals.taxAmount,
                    "grandTotal" to totals.grandTotal,
                    "notes" to document.notes.trim().take(1000),
                    "developerCredit" to cleanCredit,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>

                docRef.update(updates).await()
                Result.success(docRef.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDocumentStatus(documentId: String, newStatus: DocumentStatus): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents/$documentId"
        return try {
            val uid = requireUserId()
            require(documentId.isNotBlank()) { "Document ID cannot be blank." }
            val docRef = documentsCollection(uid).document(documentId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to newStatus.code,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun convertQuotationToBill(
        quotation: BillingDocument,
        nextBillSequence: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents/${quotation.id}"
        return try {
            val uid = requireUserId()
            require(quotation.parsedType == DocumentType.QUOTATION) { "Only quotations can be converted to bills." }
            require(!quotation.parsedStatus.isTerminal) { "Quotation is already finalized." }

            val newBillDoc = quotation.copy(
                id = "",
                docType = DocumentType.BILL.code,
                docNumber = formatNextDocumentNumber(DocumentType.BILL, nextBillSequence),
                status = DocumentStatus.UNPAID.code,
                issueDate = todayDateString(),
                dueDate = defaultDueDateString(),
                convertedToBillId = ""
            )

            val createdBillResult = saveDocument(newBillDoc, incrementSequenceCounter = true)
            val newBillId = createdBillResult.getOrElse { return Result.failure(it) }

            val quoteRef = documentsCollection(uid).document(quotation.id)
            docPath = quoteRef.path
            quoteRef.update(
                mapOf(
                    "status" to DocumentStatus.CONVERTED.code,
                    "convertedToBillId" to newBillId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(newBillId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(documentId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents/$documentId"
        return try {
            val uid = requireUserId()
            require(documentId.isNotBlank()) { "Document ID cannot be blank." }
            val docRef = documentsCollection(uid).document(documentId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
